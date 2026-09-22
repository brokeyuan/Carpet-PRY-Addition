package me.primaryuan.carpet.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 玩家身上的真·A* 寻路器（零实体：不依赖任何代理生物/隐形实体算路）。
 *
 * <p>对齐原版 {@code PathNavigation} 的被调用面（{@code moveTo} / {@code isDone} /
 * {@code stop} / {@code createPath}…），内部实现 <b>自研紧凑 A*</b>：
 * 只用稳定 API（{@code Level.getBlockState} / {@code isPathfindable(LAND)} /
 * {@code getCollisionShape}）在方块网格上搜索一条可站立路径，按节点喂给
 * {@link PlayerMoveControl}——最终仍由移动控制转成玩家的原生移动输入。</p>
 *
 * <p>行为约定的对齐点：</p>
 * <ul>
 *   <li>{@code isDone()}：路径为空或已推进完 → 语义与原版一致（stroll 目标靠它收尾）；</li>
 *   <li>卡死检测：位移停滞 20 tick 自动重算，重算 2 次仍卡则放弃路径（防原地抖腿），
 *       与原版 {@code GroundPathNavigation} 的 stuck 语义等价；</li>
 *   <li>跨维度：检测到 {@code level()} 变化立即作废路径（/tp 后不残留旧世界节点）。</li>
 * </ul>
 */
public class PlayerPathNavigation {

    private static final int MAX_ITERATIONS = 4096;
    private static final int MAX_STUCK_TICKS = 20;
    private static final int MAX_RECOMPUTE = 2;
    /** 到达节点的横向判定距离（格，约半格余量） */
    private static final double REACH_DIST_SQ = 0.36;
    /** 视线裁剪时允许的最大直线距离（超过则放弃直走，按节点折线走） */
    private static final double LOOKAHEAD_MAX_DIST_SQ = 49.0;

    private final Player player;
    private final PryMob prowler;
    private Level level;

    private Path path;                 // 当前路径（null = 空闲）
    private double pathFollowSpeed = 1.0;
    private int nodeIndex = 0;         // 路径推进索引
    private boolean navDriven = false; // 当前移动指令是否由本寻路器驱动（停机语义）
    private BlockPos lastDest;         // 卡死重算时保留的原始目标
    private Vec3 lastTickPos;
    private int stuckTicks;
    private int recomputeCount;

    public PlayerPathNavigation(Player player) {
        this.player = player;
        this.prowler = (PryMob) (Object) player;
        this.level = player.level();
    }

    /** 当前所在世界（跨维度检测用） */
    public Level level() {
        return this.level;
    }

    // ==================== 移植 Goal 的被调用面 ====================

    /** 走向目标实体（近战/远程追逐常用入口）：返回是否成功建立路径 */
    public boolean moveTo(LivingEntity target, double speed) {
        this.pathFollowSpeed = speed;
        Path p = this.createPathToEntity(target, 0);
        if (p == null || p.isEmpty()) {
            this.path = null;
            return false;
        }
        this.setPath(p, target.blockPosition());
        return true;
    }

    /** 走向坐标点（漫步/逃跑/规避常用入口） */
    public boolean moveTo(double x, double y, double z, double speed) {
        return this.moveTo(BlockPos.containing(x, y, z), speed);
    }

    /** 走向坐标点 */
    public boolean moveTo(BlockPos pos, double speed) {
        this.pathFollowSpeed = speed;
        Path p = this.computePath(pos);
        if (p == null || p.isEmpty()) {
            this.path = null;
            return false;
        }
        this.setPath(p, pos);
        return true;
    }

    /** 装载一条现成路径（规避目标用） */
    public void moveTo(Path path, double speed) {
        this.pathFollowSpeed = speed;
        this.setPath(path, path != null && !path.isEmpty() ? path.first() : null);
    }

    /** 为目标实体算一条路径（0 = 精确到目标脚下；>0 = 停在目标周边该距离处） */
    public Path createPath(LivingEntity entity, int distance) {
        return this.createPathToEntity(entity, distance);
    }

    /** 为坐标点算一条路径 */
    public Path createPath(double x, double y, double z, int distance) {
        BlockPos goal = BlockPos.containing(x, y, z);
        if (distance > 0) {
            BlockPos near = this.nearestWalkable(goal, distance);
            goal = near != null ? near : goal;
        }
        return this.computePath(goal);
    }

    /** 修改当前跟随速度（规避目标进/退切换用） */
    public void setSpeedModifier(double speed) {
        this.pathFollowSpeed = speed;
    }

    /** 停止寻路并停下脚步 */
    public void stop() {
        this.path = null;
        this.nodeIndex = 0;
        this.navDriven = false;
        this.prowler.getMoveControl().setStop();
    }

    /** 路径是否已推进完毕（无路径也算 done，对齐原版语义） */
    public boolean isDone() {
        return this.path == null || this.nodeIndex >= this.path.nodes.size();
    }

    /** 是否正在沿路径移动 */
    public boolean isInProgress() {
        return !this.isDone();
    }

    /** 是否陷入卡死（供 AI 兜底判断） */
    public boolean isStuck() {
        return this.stuckTicks >= MAX_STUCK_TICKS;
    }

    // ==================== 路径跟进（BrainManager 每 tick 驱动） ====================

    public void tick() {
        if (this.level != this.player.level()) {
            // 跨维度（/tp 其它世界）：旧路径物理上已不存在，作废
            this.level = this.player.level();
            this.path = null;
            this.navDriven = false;
            return;
        }
        if (this.path == null || this.nodeIndex >= this.path.nodes.size()) {
            // 无活跃路径：若寻路器先前在驱动移动，停掉防残留输入
            if (this.navDriven) {
                this.navDriven = false;
                this.prowler.getMoveControl().setStop();
            }
            return;
        }

        Vec3 feet = this.player.position();
        // 卡死检测：10 tick 内几乎没位移 → 重算（初始目标保留）
        if (this.lastTickPos != null && feet.distanceToSqr(this.lastTickPos) < 4.0E-4) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastTickPos = feet;
        if (this.stuckTicks >= MAX_STUCK_TICKS) {
            this.recomputeCount++;
            if (this.recomputeCount > MAX_RECOMPUTE) {
                this.stop(); // 多次重算仍卡：放弃（Ai 会重新 moveTo 触发全新路径）
                return;
            }
            // 重算到同一目标（从玩家当前位置重新搜索）
            Path p = this.lastDest != null ? this.computePath(this.lastDest) : null;
            if (p != null && !p.isEmpty()) {
                this.path = p;
                this.nodeIndex = 0;
                this.stuckTicks = 0;
            } else {
                this.stop();
            }
            return;
        }

        // 推进：越过已到达的节点（横向 < 0.6 且高度差 < 1）
        while (this.nodeIndex < this.path.nodes.size()) {
            BlockPos n = this.path.nodes.get(this.nodeIndex);
            double dx = n.getX() + 0.5 - feet.x;
            double dz = n.getZ() + 0.5 - feet.z;
            if (dx * dx + dz * dz < REACH_DIST_SQ && Math.abs(n.getY() - feet.y) < 1.0) {
                this.nodeIndex++;
            } else {
                break;
            }
        }
        if (this.nodeIndex >= this.path.nodes.size()) {
            this.navDriven = false;
            this.prowler.getMoveControl().setStop(); // 到达终点，停
            return;
        }

        // 视线裁剪：选"当前位置可直线走到的、距离最远的节点"——减少折线抖动
        BlockPos target = this.path.nodes.get(this.selectLookAhead(feet));
        this.prowler.getMoveControl().setWantedPosition(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5, this.pathFollowSpeed);
        this.navDriven = true;
    }

    /** 从后往前找直线可达的最远节点下标 */
    private int selectLookAhead(Vec3 feet) {
        int best = this.nodeIndex;
        for (int i = this.path.nodes.size() - 1; i > this.nodeIndex; i--) {
            BlockPos n = this.path.nodes.get(i);
            double dx = n.getX() + 0.5 - feet.x;
            double dz = n.getZ() + 0.5 - feet.z;
            if (dx * dx + dz * dz > LOOKAHEAD_MAX_DIST_SQ) {
                continue; // 太远不看
            }
            if (Math.abs(n.getY() - feet.y) > 2.0) {
                continue; // 高度差太大不直走（避免隔着悬崖/高台直线冲刺）
            }
            if (this.isStraightWalkable(feet, n)) {
                best = i;
            }
        }
        return best;
    }

    /** 从 feet 直线步进到节点格，检查途经格子均可站（粗视野裁剪） */
    private boolean isStraightWalkable(Vec3 feet, BlockPos target) {
        double dx = target.getX() + 0.5 - feet.x;
        double dz = target.getZ() + 0.5 - feet.z;
        int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dz * dz) * 2));
        int y = target.getY();
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int x = Mth.floor(feet.x + dx * t);
            int z = Mth.floor(feet.z + dz * t);
            if (!this.isWalkableCell(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 自研紧凑 A* ====================

    /** 目标实体 → 目标格（距离 >0 时停在目标旁边）→ 算路 */
    private Path createPathToEntity(Entity entity, int distance) {
        BlockPos goal = entity.blockPosition();
        if (distance > 0) {
            BlockPos near = this.nearestWalkable(goal, distance);
            goal = near != null ? near : goal;
        }
        if (this.isWalkableCell(goal.getX(), goal.getY(), goal.getZ())) {
            return this.computePath(goal);
        }
        // 目标格不可站（目标在水里/墙里）：找其周边最近可站格
        BlockPos near = this.nearestWalkable(goal, 3);
        return near != null ? this.computePath(near) : null;
    }

    /** 在 goal 的曼哈顿距离 ≤ radius 的格子里找一个最近可站点（含 goal 本身） */
    private BlockPos nearestWalkable(BlockPos goal, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r) {
                        continue;
                    }
                    BlockPos candidate = goal.offset(dx, 0, dz);
                    if (this.isWalkableCell(candidate.getX(), candidate.getY(), candidate.getZ())) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private void setPath(Path p, BlockPos dest) {
        this.path = p;
        this.nodeIndex = 0;
        this.lastDest = dest;
        this.stuckTicks = 0;
        this.recomputeCount = 0;
        this.lastTickPos = null;
    }

    /** A* 主循环：从玩家脚下到 goal 的最短可站路径 */
    private Path computePath(BlockPos goal) {
        BlockPos start = this.player.blockPosition();
        if (this.isGoalReached(start, goal)) {
            return new Path(List.of());
        }
        if (!this.isWalkableCell(goal.getX(), goal.getY(), goal.getZ())) {
            BlockPos near = this.nearestWalkable(goal, 3);
            if (near == null) {
                return null;
            }
            goal = near;
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, Node> openMap = new java.util.HashMap<>();
        Set<Long> closed = new HashSet<>();
        long startKey = key(start.getX(), start.getY(), start.getZ());
        Node root = new Node(start.getX(), start.getY(), start.getZ(), null, 0.0, heuristic(start, goal));
        open.add(root);
        openMap.put(startKey, root);

        Node best = root; // 记录离目标最近的已展开节点（搜索超限时退回）
        double bestH = root.h;
        int iterations = 0;

        while (!open.isEmpty()) {
            Node node = open.poll();
            long nodeKey = key(node.x, node.y, node.z);
            if (openMap.get(nodeKey) != node) {
                continue; // 过期条目（被更优 g 更新过）
            }
            closed.add(nodeKey);
            openMap.remove(nodeKey);
            if (++iterations > MAX_ITERATIONS) {
                break;
            }
            if (node.h < bestH) {
                best = node;
                bestH = node.h;
            }
            if (this.isGoalReached(new BlockPos(node.x, node.y, node.z), goal)) {
                return this.buildPath(node);
            }
            this.expand(open, openMap, closed, node, goal);
        }
        // 搜索超限：退回离目标最近的已展开点（原版"到最近可达点"语义）
        return closed.contains(key(best.x, best.y, best.z)) ? this.buildPath(best) : null;
    }

    /** 展开上下左右 + 三种高度（持平/爬一格/降一格），带爬坡代价与危险地形惩罚 */
    private void expand(PriorityQueue<Node> open, java.util.Map<Long, Node> openMap,
                        Set<Long> closed, Node node, BlockPos goal) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                // 邻居：优先同层，其次向上、向下（各尝试最近的可站高度）
                int[] yCandidates = {node.y, node.y + 1, node.y - 1};
                for (int dy : yCandidates) {
                    if (!this.isWalkableCell(node.x + dx, dy, node.z + dz)) {
                        continue;
                    }
                    // 高度差超过 1 的邻居本次不支持（爬 1 / 落 1 已覆盖）
                    double cost = 1.0;
                    int climb = dy - node.y;
                    if (climb == 1) {
                        cost = 1.75; // 爬台阶代价惩罚（尽量走平路）
                    } else if (climb == -1) {
                        cost = 1.1;
                    }
                    // 危险地形规避（对齐原版僵尸寻路 malus 语义）：
                    // 熔岩/火焰格（含头顶）→ 禁行——熔岩与火焰无碰撞箱，
                    // 可站立判定本身不排除它们，必须显式拦截；
                    // 铁轨格 → 高代价软惩罚（原版僵尸"不尝试穿过铁轨"，
                    // 绕不过去才走）。每步最多 ±1 高差，A* 天然不会生成
                    // 下落超过 3 格的路径，悬崖规避由此得到保证。
                    double hazard = this.hazardPenalty(node.x + dx, dy, node.z + dz);
                    if (hazard < 0) {
                        continue;
                    }
                    cost += hazard;
                    long nKey = key(node.x + dx, dy, node.z + dz);
                    if (closed.contains(nKey)) {
                        continue;
                    }
                    double g = node.g + cost;
                    Node existing = openMap.get(nKey);
                    if (existing != null && existing.g <= g) {
                        continue;
                    }
                    Node next = new Node(node.x + dx, dy, node.z + dz, node, g, heuristic(new BlockPos(node.x + dx, dy, node.z + dz), goal));
                    openMap.put(nKey, next);
                    open.add(next);
                }
            }
        }
    }

    /**
     * 危险地形代价（对齐原版僵尸"避开熔岩/火焰、不穿过铁轨"）。
     *
     * @return 附加代价；{@code -1} 表示该格禁行
     */
    private double hazardPenalty(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        var state = this.level.getBlockState(pos);
        // 身体/头部所在格是熔岩或火焰 → 禁行（两者的碰撞箱为空，
        // 仅靠可站立判定会认为"能站"，必须显式排除）
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA)
                || state.is(net.minecraft.world.level.block.Blocks.FIRE)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
            return -1;
        }
        // 铁轨（含充能/探测/激活铁轨，BlockTags.RAILS 全版本一致）：
        // 高代价软惩罚，绕行优先
        if (state.is(net.minecraft.tags.BlockTags.RAILS)) {
            return 8.0;
        }
        return 0.0;
    }

    /** 曼哈顿启发（三轴），系数略大于 1 保证朝目标收敛且同分判定稳定 */
    private static double heuristic(BlockPos from, BlockPos goal) {
        return (Math.abs(from.getX() - goal.getX()) + Math.abs(from.getY() - goal.getY())
                + Math.abs(from.getZ() - goal.getZ())) * 1.001;
    }

    /** 是否已到目标：同格或 1 格内 */
    private boolean isGoalReached(BlockPos pos, BlockPos goal) {
        return Math.abs(pos.getX() - goal.getX()) <= 1
                && Math.abs(pos.getZ() - goal.getZ()) <= 1
                && Math.abs(pos.getY() - goal.getY()) <= 1
                && this.isWalkableCellForGoal(pos);
    }

    /** 目标判定专用：目标格本身可站即可（不做待选邻居的严格校验） */
    private boolean isWalkableCellForGoal(BlockPos pos) {
        return this.isWalkableCell(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 从终点沿 parent 回溯构建路径（去掉起点格自身） */
    private Path buildPath(Node end) {
        List<BlockPos> nodes = new ArrayList<>();
        Node n = end;
        while (n != null) {
            nodes.add(new BlockPos(n.x, n.y, n.z));
            n = n.parent;
        }
        java.util.Collections.reverse(nodes);
        BlockPos start = this.player.blockPosition();
        // 去掉路径开头的"脚下格"（起点不可作为移动目标）
        if (!nodes.isEmpty() && nodes.get(0).equals(start)) {
            nodes.remove(0);
        }
        return new Path(nodes);
    }

    /**
     * 方格可否站立（玩家占位 1 格宽、2 格高）：
     * 本格可通行（LAND）+ 脚下有碰撞支撑 + 头顶不挡（2 格净高）。
     */
    private boolean isWalkableCell(int x, int y, int z) {
        return isWalkableCell(this.level, new BlockPos(x, y, z));
    }

    /**
     * 全版本稳定的"可站立"判定（供寻路器与漫步/恐慌/规避 Goal 共用同一口径）。
     *
     * <p>刻意不使用 {@code BlockState.isPathfindable/isSolidRender}：前者 26.x 起
     * 收紧为 protected 且签名变化，后者 26.x 移除。改用碰撞箱语义——</p>
     * <ul>
     *   <li>本格与头顶格碰撞箱为空：保证玩家 0.6×1.8 碰撞箱站得下（2 格净高）；</li>
     *   <li>脚下格碰撞箱非空：地面/台阶/半砖均视为合法支撑。</li>
     * </ul>
     */
    public static boolean isWalkableCell(Level level, BlockPos pos) {
        // 本格：自身有碰撞（栅栏/墙/玻璃）→ 不可站
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        // 脚下支撑：下方格有碰撞体（地面/台阶/半砖都可）
        BlockPos below = pos.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return false;
        }
        // 头顶净空：+1 格必须无碰撞（保证 2 格身高站得下；原 isSolidRender 的替代语义）
        BlockPos above = pos.above();
        return level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }

    private static long key(int x, int y, int z) {
        return (long) (x & 0x3FFFFFF) << 38 | (long) (y & 0xFFF) << 26 | (z & 0x3FFFFFF);
    }

    /** A* 搜索节点 */
    private static final class Node {
        final int x, y, z;
        final Node parent;
        final double g;
        final double h;
        final double f;

        Node(int x, int y, int z, Node parent, double g, double h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }
    }
}