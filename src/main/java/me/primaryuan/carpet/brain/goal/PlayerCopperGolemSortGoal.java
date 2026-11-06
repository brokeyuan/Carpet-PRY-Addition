package me.primaryuan.carpet.brain.goal;

//#if MC >= 12110

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 铜傀儡分类目标（← 原版 {@code TransportItemsBetweenContainers} + 26.3
 * {@code CopperGolemAi} 装配，javap 逐项核实）。从铜箱取货，按物品分类转入
 * 普通箱子——全部经玩家原生容器操作（右键开箱 → 菜单点击 → 关箱）。
 *
 * <p><b>原版契约（26.3 字节码）</b>：源 = {@code #minecraft:copper_chests}
 * 标签方块且有物品（任意非空即取）；目标 = 普通箱/陷阱箱，条件 = 空箱或含
 * 与携带物 {@code isSameItem}（不看 NBT）的箱子；携带 = 源箱第一个非空槽
 * {@code removeItem(i, min(count, 16))} 入主手（单趟上限 16）；放置 = 首个
 * 空槽整组放入 或 并入 {@code isSameItemSameComponents} 同物组；驻箱交互
 * 60 tick（TARGET_INTERACTION_TIME）；无匹配目标冷却 140 tick 并清"已访问"
 * 记忆；搜索半径水平 32 / 垂直 8；锁箱（漏斗锁）与被其他铜傀儡占用的箱子跳过
 * （原版 QUEUING/interacting 记忆语义，这里以在册目标登记表等价）。</p>
 *
 * <p><b>玩家身体映射（披露差异）</b>：原版铜傀儡直接读写 {@code Container}
 * （生物身体）；假人是玩家，取放全部走 {@code AbstractContainerMenu.clicked}
 * 玩家点击管线——取货 = PICKUP 整组到光标 → 右键逐件放回超出 16 的部分
 * （玩家合法的分件操作）→ 光标余量点入背包 → {@code pickSlot} 选中到手
 * （携带物在主手，与原版 getMainHandItem 判定一致）；放货 = 对目标箱菜单
 * QUICK_MOVE（原版 same-item-same-components 合并规则原生生效）。单趟上限
 * 16 与原版一致。搬运途中货物只存在于 源箱/假人背包/目标箱/正常掉落 之一，
 * 关箱掉落走玩家原生物品归还（光标栈自动回背包），不复制不吞物。</p>
 *
 * <p>仅 1.21.10+（铜箱/铜傀儡所在版本）启用；旧版本无 {@code COPPER_CHESTS}
 * 标签，模式在命令树中不可见（{@code MC >= 12110} 预处理门）。</p>
 */
public class PlayerCopperGolemSortGoal extends PlayerGoal {

    /** 驻箱交互时长（原版 TARGET_INTERACTION_TIME，字节码 60） */
    private static final int INTERACTION_TICKS = 60;
    /** 无匹配目标冷却（原版 IDLE_COOLDOWN，字节码 140） */
    private static final int NO_TARGET_COOLDOWN_TICKS = 140;
    /** 单趟携带上限（原版 TRANSPORTED_ITEM_MAX_STACK_SIZE，字节码 16） */
    private static final int CARRY_CAP = 16;
    /** 搜索半径（原版构造参数：水平 32 / 垂直 8） */
    private static final int SEARCH_HORIZONTAL = 32;
    private static final int SEARCH_VERTICAL = 8;
    /** 扫描节流（重扫描间隔，近似原版 Brain 惰性评估节奏） */
    private static final int SCAN_INTERVAL_TICKS = 40;
    /** 容器不可达退避（项目 POI 退避口径） */
    private static final long BLACKLIST_TICKS = 200;
    /** 到位距离（2.5 格：容器为完整方块，对角相邻驻点 distSq 可达 4.7，须覆盖） */
    private static final double ARRIVE_DIST_SQ = 6.25;

    /** 运送阶段（原版 TransportItemState 状态机的玩家化收窄版） */
    private enum Phase { SEEK_SOURCE, TRAVEL_SOURCE, INTERACT_SOURCE, SEEK_DEST, TRAVEL_DEST, INTERACT_DEST }

    /** 容器引用（维度键对象等值比较，跨版本中立） */
    record ContainerRef(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos) {}

    /** 其他铜傀儡脑正在处理的容器登记表（brain UUID → 容器引用） */
    private static final Map<ServerPlayer, ContainerRef> ACTIVE_CONTAINER_BY_BRAIN = new HashMap<>();

    private Phase phase = Phase.SEEK_SOURCE;
    /** 本趟货物：类型+组件（交付匹配基准），不是库存本体 */
    private ItemStack carryItem = ItemStack.EMPTY;
    /** 本趟尚未送出的数量（≤16；按点击后的实际库存差量结账） */
    private int carryRemaining;
    /** 本趟货物所在的玩家背包槽（不随菜单/主手换槽漂移） */
    private int carryInventorySlot = -1;
    private BlockPos target;
    /** 无进展检测（实际位置不变 ≥40 游戏刻 → 退避换点） */
    private Vec3 lastProgressPos;
    private long lastProgressGameTime = Long.MIN_VALUE;
    /** 当前目标容器的维度（跨维失效判定） */
    private net.minecraft.resources.ResourceKey<Level> targetDimension;
    private long cooldownUntil;
    /** 当前真实菜单会话（只在成功开箱后存在；关箱/异常/换目标即清空） */
    private AbstractContainerMenu openMenu;
    private BlockPos openMenuTarget;
    private long openMenuStartedGameTime = Long.MIN_VALUE;
    private boolean transferredThisSession;
    /** 开箱失败有限重试：最多 3 次、间隔 10t；持续失败退避换箱，不永久停留 INTERACT */
    private static final int MAX_OPEN_ATTEMPTS = 3;
    private static final int OPEN_RETRY_TICKS = 10;
    private int openFailures;
    private long nextOpenAttemptTime = Long.MIN_VALUE;
    /** 寻路失败有限重试：3 次（轮换目标点）、间隔 10t；全败才退避并拉黑 */
    private static final int MAX_MOVE_ATTEMPTS = 3;
    private static final int MOVE_RETRY_TICKS = 10;
    private int moveFailures;
    private long nextMoveAttemptTime = Long.MIN_VALUE;
    private long nextScanTime;
    private final Map<BlockPos, Long> blacklist = new HashMap<>();
    /** 已访问容器（原版 VISITED_BLOCK_POSITIONS：6000t 记忆、超 10 个进 140t 冷却并清空） */
    private static final int VISITED_MEMORY_TICKS = 6000;
    private static final int MAX_VISITED_POSITIONS = 10;
    private final Map<BlockPos, Long> visited = new HashMap<>();

    protected final PryMob mob;
    protected final ServerPlayer player;

    public PlayerCopperGolemSortGoal(PryMob mob) {
        this.mob = mob;
        this.player = mob.asPlayer() instanceof ServerPlayer sp ? sp : null;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    // ==================== 世界读缝（离线测试覆写） ====================

    /** 世界读方块（桩世界不可读，测试覆写注入方块表） */
    protected BlockState readStateAt(Level level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    /** 加载区块判定缝（桩世界无 chunkSource → 测试覆写为 true） */
    protected boolean isChunkLoaded(Level level, BlockPos pos) {
        return level.isLoaded(pos);
    }

    /** 容器解析缝（生产走方块实体；测试注入 SimpleContainer 表） */
    protected net.minecraft.world.Container containerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof net.minecraft.world.Container c ? c : null;
    }

    // ==================== 目标选择（原版匹配规则） ====================

    /** 源箱候选：只按原版方块标签与搜索规则选箱，内容必须开箱后才能检查。 */
    protected BlockPos findSource(Level level) {
        return scanContainer(level, s -> s.is(BlockTags.COPPER_CHESTS));
    }

    /** 目标箱候选：只按原版方块类型与搜索规则选箱；空/同物/不匹配都在
     *  真实打开后判断，不在全局搜索阶段读箱内物品。 */
    protected BlockPos findDestination(Level level) {
        if (!this.hasCargo()) {
            return null;
        }
        return scanContainer(level,
                s -> s.is(Blocks.CHEST) || s.is(Blocks.TRAPPED_CHEST));
    }

    /** 盒扫候选箱（水平 32 / 垂直 8，跳过退避/已访问/他人占用），取最近。
     *  原版目标扫描只筛方块类型/已访问/锁箱，不读容器内容。 */
    private BlockPos scanContainer(Level level,
                                   java.util.function.Predicate<BlockState> stateTest) {
        long now = this.bodyLevel().getGameTime();
        this.blacklist.entrySet().removeIf(e -> now >= e.getValue());
        this.visited.entrySet().removeIf(e -> now >= e.getValue());
        BlockPos origin = this.mob.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_HORIZONTAL, -SEARCH_VERTICAL, -SEARCH_HORIZONTAL),
                origin.offset(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL))) {
            BlockPos immutable = pos.immutable();
            if (!this.isChunkLoaded(level, immutable)) {
                continue; // 不读未加载区块（避免扫描触发区块加载/生成）
            }
            BlockState state = this.readStateAt(level, immutable);
            if (!stateTest.test(state)) {
                continue; // 先筛方块类型，命中才做双箱归一/容器查询（分摊开销）
            }
            BlockPos identity = canonicalContainerPos(level, immutable, state);
            if (this.blacklist.containsKey(identity) || this.visited.containsKey(identity)
                    || isClaimedByOtherBrain(level, immutable)) {
                continue;
            }
            double dist = this.mob.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = immutable;
            }
        }
        return best;
    }

    /** 目标容器是否被其他铜傀儡脑占用（原版 isAnotherMobInteractingWithTarget 等价）。
     *  双箱两半归一到同一资源身份，避免两个脑各占一半同时打开同一物理箱子。 */
    boolean isClaimedByOtherBrain(Level level, BlockPos pos) {
        return ACTIVE_CONTAINER_BY_BRAIN.containsValue(containerRef(level, pos));
    }

    private ContainerRef containerRef(Level level, BlockPos pos) {
        return new ContainerRef(level.dimension(), canonicalContainerPos(level, pos));
    }

    /**
     * 双箱归一键：同种箱子且四邻中存在另一半时，取坐标序最小的一侧作为
     * 资源身份（单箱返回自身）。用于占用登记/已访问/退避，避免左右半箱
     * 被当成两个可并行使用的容器。
     */
    BlockPos canonicalContainerPos(Level level, BlockPos pos) {
        return canonicalContainerPos(level, pos, this.readStateAt(level, pos));
    }

    private BlockPos canonicalContainerPos(Level level, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.TRAPPED_CHEST)) {
            return pos;
        }
        var type = state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
        if (type == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            return pos;
        }
        BlockPos best = pos;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos other = pos.relative(dir);
            BlockState otherState = this.readStateAt(level, other);
            if (!otherState.is(state.getBlock())) {
                continue;
            }
            var otherType = otherState.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            if (otherType == type.getOpposite() && comparePos(other, best) < 0) {
                best = other;
            }
        }
        return best;
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        int byX = Integer.compare(a.getX(), b.getX());
        if (byX != 0) return byX;
        int byY = Integer.compare(a.getY(), b.getY());
        if (byY != 0) return byY;
        return Integer.compare(a.getZ(), b.getZ());
    }

    private Level bodyLevel() {
        return this.mob.asPlayer().level();
    }

    // ==================== 目标选择器接口 ====================

    @Override
    public boolean canUse() {
        this.normalizeSeekPhase();
        // 停用后只允许从两个“寻点”阶段恢复：TRAVEL/INTERACT 在 stop 时已归一。
        if (this.phase != Phase.SEEK_SOURCE && this.phase != Phase.SEEK_DEST) {
            return false;
        }
        long now = this.bodyLevel().getGameTime();
        return now >= this.cooldownUntil && now >= this.nextScanTime;
    }

    @Override
    public void start() {
        // 恢复会话：不假定旧 target 仍在；寻点在 tick 内重扫。
        this.normalizeSeekPhase();
        this.lastProgressPos = null;
        this.lastProgressGameTime = Long.MIN_VALUE;
    }

    @Override
    public boolean canContinueToUse() {
        // SEEK_SOURCE 交给选择器每轮重评（canUse 带冷却）；SEEK_DEST/
        // TRAVEL/INTERACT 保持运行，冷却由 tick 顶部按 gameTime 执行。
        return this.phase != Phase.SEEK_SOURCE;
    }

    @Override
    public void tick() {
        Level level = this.bodyLevel();
        long now = level.getGameTime();
        // 运行中的执行路径同样受冷却/扫描节流约束——canUse 只作用于未运行目标，
        // 不能限制已经在跑的 SEEK_DEST（无目标/部分放货后的等待必须真正生效）。
        if ((this.phase == Phase.SEEK_SOURCE || this.phase == Phase.SEEK_DEST)
                && now < Math.max(this.cooldownUntil, this.nextScanTime)) {
            return;
        }
        switch (this.phase) {
            case SEEK_SOURCE -> {
                this.nextScanTime = now + SCAN_INTERVAL_TICKS;
                BlockPos source = this.findSource(level);
                if (source == null) {
                    this.cooldownUntil = now + NO_TARGET_COOLDOWN_TICKS; // 原版：无源可取进冷却
                    this.visited.clear();
                    return;
                }
                this.beginTravel(source, Phase.TRAVEL_SOURCE);
            }
            case SEEK_DEST -> {
                if (!this.hasCargo()) {
                    this.toSeekSource();
                    return;
                }
                this.nextScanTime = now + SCAN_INTERVAL_TICKS;
                BlockPos dest = this.findDestination(level);
                if (dest == null) {
                    // 原版 enterCooldownAfterNoMatchingTargetFound：140t + 清已访问
                    this.cooldownUntil = now + NO_TARGET_COOLDOWN_TICKS;
                    this.visited.clear();
                    return;
                }
                this.beginTravel(dest, Phase.TRAVEL_DEST);
            }
            case TRAVEL_SOURCE, TRAVEL_DEST -> {
                if (this.targetInvalid(level)) {
                    this.failToSeek(SCAN_INTERVAL_TICKS);
                    return;
                }
                // 防卡路：导航"进行中"但身体不动 ≥40 游戏刻 → 退避换点。
                // 只看水平分量：撞墙自动跳的垂直位移不算进展（否则顶墙原地跳
                // 永远不算卡死，与导航器内 stuck 检测同一口径）
                Vec3 p = this.mob.position();
                if (this.lastProgressPos == null
                        || (p.x - this.lastProgressPos.x) * (p.x - this.lastProgressPos.x)
                                + (p.z - this.lastProgressPos.z) * (p.z - this.lastProgressPos.z) > 1.0E-4) {
                    this.lastProgressPos = p;
                    this.lastProgressGameTime = now;
                } else if (now - this.lastProgressGameTime >= 40) {
                    this.blacklist.put(canonicalContainerPos(level, this.target), now + BLACKLIST_TICKS);
                    this.failToSeek(SCAN_INTERVAL_TICKS);
                    return;
                }
                double distSq = this.mob.distanceToSqr(
                        this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);
                if (distSq > ARRIVE_DIST_SQ) {
                    if (this.mob.getNavigation().isDone()) {
                        // 寻路失败有限重试（侧向节点也可能瞬时不可达）：3 次尝试、
                        // 间隔 10t，全败才退避换箱，不单次失败就拉黑
                        if (now < this.nextMoveAttemptTime) {
                            return;
                        }
                        Vec3 attempt = navTargetFor(level, this.target);
                        if (this.mob.getNavigation().moveTo(attempt.x, attempt.y, attempt.z, 1.0)) {
                            this.moveFailures = 0;
                            this.nextMoveAttemptTime = Long.MIN_VALUE;
                        } else {
                            this.moveFailures++;
                            if (this.moveFailures >= MAX_MOVE_ATTEMPTS) {
                                this.blacklist.put(canonicalContainerPos(level, this.target), now + BLACKLIST_TICKS);
                                this.failToSeek(SCAN_INTERVAL_TICKS);
                            } else {
                                this.nextMoveAttemptTime = now + MOVE_RETRY_TICKS;
                            }
                        }
                    }
                    return;
                }
                this.mob.getNavigation().stop();
                this.mob.getLookControl().setLookAt(
                        this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5, 30.0F, 30.0F);
                this.phase = this.phase == Phase.TRAVEL_SOURCE ? Phase.INTERACT_SOURCE : Phase.INTERACT_DEST;
                this.lastProgressPos = null;
            }
            case INTERACT_SOURCE -> {
                if (!this.ensureChestSession(level, now)) {
                    return;
                }
                if (now - this.openMenuStartedGameTime < INTERACTION_TICKS) {
                    return; // 真实菜单保持打开：原版第 1 刻 startOpen、第 60 刻 stopOpen
                }
                if (this.transferredThisSession) {
                    this.closeChestSession();
                    return;
                }
                this.transferredThisSession = true;
                this.doTake(this.openMenu);
            }
            case INTERACT_DEST -> {
                if (!this.ensureChestSession(level, now)) {
                    return;
                }
                if (now - this.openMenuStartedGameTime < INTERACTION_TICKS) {
                    return;
                }
                if (this.transferredThisSession) {
                    this.closeChestSession();
                    return;
                }
                this.transferredThisSession = true;
                this.doPlace(this.openMenu);
            }
        }
    }

    private void beginTravel(BlockPos pos, Phase travel) {
        this.target = pos;
        this.targetDimension = this.bodyLevel().dimension();
        this.phase = travel;
        this.openFailures = 0; // 新目标重新计开箱重试
        this.nextOpenAttemptTime = Long.MIN_VALUE;
        this.moveFailures = 0; // 新目标重新计寻路重试
        this.nextMoveAttemptTime = Long.MIN_VALUE;
        this.visited.put(canonicalContainerPos(this.bodyLevel(), pos),
                this.bodyLevel().getGameTime() + VISITED_MEMORY_TICKS);
        // 原版：visited 记忆达到上限后进 140t 冷却并清空，防止无限排除集
        if (this.visited.size() > MAX_VISITED_POSITIONS) {
            this.visited.clear();
            this.cooldownUntil = this.bodyLevel().getGameTime() + NO_TARGET_COOLDOWN_TICKS;
        }
        ACTIVE_CONTAINER_BY_BRAIN.put(this.player, containerRef(this.bodyLevel(), pos));
        Vec3 navTarget = navTargetFor(this.bodyLevel(), pos);
        this.mob.getNavigation().moveTo(navTarget.x, navTarget.y, navTarget.z, 1.0);
    }

    /**
     * 导航目标点：容器方块本身不可站立，瞄准其中心会让寻路目标节点落在
     * 方块内部（实机 A* 可达性临界、时成时败）。改为取容器四邻中
     * “可走且有支撑”的侧向节点（离身体最近者）；无合格侧位再退回容器中心，
     * 交给 A* 部分路径兜底。
     */
    private Vec3 navTargetFor(Level level, BlockPos pos) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            BlockState at = this.readStateAt(level, side);
            if (!at.getCollisionShape(level, side).isEmpty()) {
                continue; // 侧位被占：不可走
            }
            if (this.readStateAt(level, side.below()).getCollisionShape(level, side.below()).isEmpty()) {
                continue; // 无支撑：会掉落
            }
            double d = this.mob.distanceToSqr(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = side;
            }
        }
        if (best == null) {
            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
        return new Vec3(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
    }

    private boolean targetInvalid(Level level) {
        if (this.target == null || !this.bodyLevel().dimension().equals(this.targetDimension)) {
            return true; // 跨维：目标箱属于获取时的维度
        }
        BlockState state = this.readStateAt(level, this.target);
        boolean stillContainer = state.is(BlockTags.COPPER_CHESTS)
                || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST);
        return !stillContainer;
    }

    /** 测试缝：设置本趟货物（验证 findDestination 只认 cargo，不读主手） */
    void setCarryForTest(ItemStack item, int count) {
        this.carryItem = item.copy();
        this.carryItem.setCount(count);
        this.carryRemaining = count;
    }

    /** 测试缝：本趟货物记录的背包槽（-1 = 未记录） */
    int carrySlotForTest() {
        return this.carryInventorySlot;
    }

    /** 本趟是否还有货（数量与组件快照同时有效） */
    private boolean hasCargo() {
        return this.carryRemaining > 0 && !this.carryItem.isEmpty();
    }

    /** 暂停/放弃当前目标：清目标与占用登记，按货物归属恢复寻点阶段 */
    private void pauseOrAbandon() {
        this.closeChestSession();
        this.target = null;
        this.targetDimension = null;
        this.phase = this.hasCargo() ? Phase.SEEK_DEST : Phase.SEEK_SOURCE;
        ACTIVE_CONTAINER_BY_BRAIN.remove(this.player);
        this.mob.getNavigation().stop();
        this.lastProgressPos = null;
        this.lastProgressGameTime = Long.MIN_VALUE;
    }

    /** 失败出口统一口径：保留货物，按有无余货回到对应寻点阶段并计入等待 */
    private void failToSeek(long cooldownTicks) {
        this.pauseOrAbandon();
        this.cooldownUntil = this.bodyLevel().getGameTime() + cooldownTicks;
    }

    /** 归一阶段：有货必须寻目的地，无货必须寻来源（防止旧态/异常态卡死） */
    private void normalizeSeekPhase() {
        if (this.phase == Phase.SEEK_DEST && !this.hasCargo()) {
            this.phase = Phase.SEEK_SOURCE;
        } else if (this.phase == Phase.SEEK_SOURCE && this.hasCargo()) {
            this.phase = Phase.SEEK_DEST;
        }
    }

    /** 本趟送完：清货物归属并回到寻源 */
    private void toSeekSource() {
        this.carryItem = ItemStack.EMPTY;
        this.carryRemaining = 0;
        this.carryInventorySlot = -1;
        this.pauseOrAbandon();
        this.phase = Phase.SEEK_SOURCE;
        this.cooldownUntil = this.bodyLevel().getGameTime() + SCAN_INTERVAL_TICKS;
    }

    // ==================== 取货/放货（玩家菜单点击管线） ====================

    /** 已打开菜单中的取货：只在原版判定的取货分支执行本趟转移。 */
    private void doTake(AbstractContainerMenu menu) {
        int[] take = new int[]{-1, -1};
        int[] invSlot = new int[]{-1};
        int[] r = takeViaMenu(menu, this.player, take);
        if (r != null && r[0] > 0 && r[1] >= 0) {
            var slot = menu.getSlot(r[1]);
            this.carryItem = slot.getItem().copy(); // 货物组件快照（实时槽）
            this.carryRemaining = r[0];
            this.carryInventorySlot = slot.container == this.player.getInventory()
                    ? slot.getContainerSlot() : -1;
        }
        this.closeChestSession();
        if (this.carryRemaining > 0 && !this.carryItem.isEmpty()) {
            // 原版 pickUpItems 成功后 clearMemoriesAfterMatchingTargetFound
            this.visited.clear();
            this.equipCarried();
            this.target = null;
            this.phase = Phase.SEEK_DEST;
            ACTIVE_CONTAINER_BY_BRAIN.remove(this.player);
            this.cooldownUntil = this.bodyLevel().getGameTime() + SCAN_INTERVAL_TICKS;
        } else {
            // 打开后无货/无净增量：原版 PICKUP_NO_ITEM，回寻源
            this.failToSeek(SCAN_INTERVAL_TICKS);
        }
    }

    /** 已打开菜单中的放货：原版分类判定 = 空箱或含同 Item；实际堆叠 =
     *  组件匹配/空槽。不匹配或无接收空间时只关箱不投放。 */
    private void doPlace(AbstractContainerMenu menu) {
        if (!this.hasCargo()) {
            this.closeChestSession();
            this.toSeekSource();
            return;
        }
        int chestSlots = chestSlotCount(menu, menu.getSlot(0).container);
        boolean accepts = classificationAccepts(menu, this.carryItem, chestSlots);
        int placed = accepts
                ? placeViaMenu(menu, this.player, this.carryItem,
                        this.carryRemaining, this.carryInventorySlot)
                : 0;
        this.closeChestSession();
        if (placed > 0) {
            // 原版 putDownItem 成功后 clearMemoriesAfterMatchingTargetFound
            this.visited.clear();
            this.carryRemaining -= placed; // 按实际放入量结账
        }
        if (this.carryRemaining <= 0) {
            this.toSeekSource(); // 本趟送完：重新取货
        } else {
            // 不匹配/满/只放入一部分：刷新本轮访问，继续找下一个候选箱，不吞不清
            this.target = null;
            this.phase = Phase.SEEK_DEST;
            ACTIVE_CONTAINER_BY_BRAIN.remove(this.player);
            this.cooldownUntil = this.bodyLevel().getGameTime() + SCAN_INTERVAL_TICKS;
        }
    }

    /**
     * 取货点击序列（原版 pickupItemFromContainer 的玩家化转写）：PICKUP 整组
     * 到光标 → 右键逐件放回超出 16 的部分 → 光标余量点入背包空槽。
     * 返回取到的货物（用于选中到手；无货返回 EMPTY）。
     */
    static int[] takeViaMenu(AbstractContainerMenu menu, ServerPlayer player, int[] scratch) {
        int chestSlots = chestSlotCount(menu, menu.getSlot(0).container);
        int chestSlot = -1;
        for (int i = 0; i < chestSlots; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
                chestSlot = i;
                break;
            }
        }
        if (chestSlot < 0) {
            return null;
        }
        // 点击前保存不可变快照：点击会改写槽内 ItemStack 引用/数量
        ItemStack source = menu.getSlot(chestSlot).getItem().copy();
        int total = source.getCount();
        int want = Math.min(total, CARRY_CAP);
        if (want <= 0) {
            return null;
        }
        ItemStack cargo = source.copy();
        cargo.setCount(want);
        if (!menu.getCarried().isEmpty()) {
            recoverCarried(menu, player, chestSlot); // 旧光标残留：先按正常点击回收
        }
        int before = countMatchingInInventory(player, cargo);
        clickStatic(menu, player, chestSlot, 0, false); // 整组到光标
        int cursor = menu.getCarried().getCount();
        int putBack = Math.max(0, cursor - want);
        for (int k = 0; k < putBack; k++) {
            clickStatic(menu, player, chestSlot, 1, false); // 右键逐件放回
        }
        // 光标 = 本趟货物（≤16）；入背包空槽优先（货物隔离），无空槽并入
        // 同组件且仍有空间的组。放不下的部分退回源槽，不把合并后整栈当增量。
        int invSlot = firstEmptyPlayerSlot(menu);
        if (invSlot < 0) {
            invSlot = sameItemPlayerSlotWithSpace(menu, cargo);
        }
        if (invSlot >= 0) {
            ItemStack slotStack = menu.getSlot(invSlot).getItem();
            if (slotStack.isEmpty()) {
                clickStatic(menu, player, invSlot, 0, false); // 光标入空槽（容量不足时原版只入一部分）
            } else {
                int space = Math.max(0, slotStack.getMaxStackSize() - slotStack.getCount());
                int merge = Math.min(space, menu.getCarried().getCount());
                for (int k = 0; k < merge; k++) {
                    clickStatic(menu, player, invSlot, 1, false); // 逐件并入同组件组
                }
            }
        }
        // 光标余量退回源槽（正常菜单操作回收；关菜单不再依赖掉落归还）
        int residual = menu.getCarried().getCount();
        for (int k = 0; k < residual; k++) {
            clickStatic(menu, player, chestSlot, 1, false);
        }
        if (!menu.getCarried().isEmpty()) {
            recoverCarried(menu, player, chestSlot);
        }
        int after = countMatchingInInventory(player, cargo);
        int taken = Math.max(0, after - before); // 真实库存差量，不是合并后整栈读数
        scratch[0] = taken;
        scratch[1] = taken > 0 ? invSlot : -1;
        return scratch;
    }

    /** 玩家主背包内与给定栈同物同组件的总数（真实差量基准） */
    private static int countMatchingInInventory(ServerPlayer player, ItemStack template) {
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 把光标残留经正常点击回收：先空背包槽，再逐件退回源槽；失败留给关菜单原生归还 */
    private static void recoverCarried(AbstractContainerMenu menu, ServerPlayer player, int sourceSlot) {
        int guard = 0;
        while (!menu.getCarried().isEmpty() && guard++ < 128) {
            int empty = firstEmptyPlayerSlot(menu);
            if (empty >= 0) {
                clickStatic(menu, player, empty, 0, false);
                continue;
            }
            ItemStack source = menu.getSlot(sourceSlot).getItem();
            if (source.isEmpty() || source.getCount() < source.getMaxStackSize()) {
                clickStatic(menu, player, sourceSlot, 1, false);
                continue;
            }
            break; // 无空槽且源槽已满：交回关菜单的原生归还路径
        }
    }

    /**
     * 原版目标分类判定（26.3 matchesLeavingItemsRequirement）：
     * 空箱，或含与携带物 {@code isSameItem}（不看组件）的组。
     * 组件是否真的能堆叠交给 placeViaMenu 的组件匹配。
     */
    static boolean classificationAccepts(AbstractContainerMenu menu, ItemStack carried, int chestSlots) {
        boolean empty = true;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack in = menu.getSlot(i).getItem();
            if (in.isEmpty()) {
                continue;
            }
            empty = false;
            if (ItemStack.isSameItem(in, carried)) {
                return true;
            }
        }
        return empty;
    }

    /**
     * 放货点击序列（原版 addItemsToContainer 语义的玩家化转写）：优先并入
     * same-item-same-components 同物组（右键逐件），余量入首个空箱槽，
     * 无空槽则留在背包（光标点回背包槽，不吞物）。
     */
    static int placeViaMenu(AbstractContainerMenu menu, ServerPlayer player,
                            ItemStack carryItem, int carryRemaining) {
        return placeViaMenu(menu, player, carryItem, carryRemaining, -1);
    }

    /**
     * 放货点击序列：按“本趟货物槽”精确定位（carryInventorySlot，-1 = 测试兼容
     * 的组件匹配），点击前保存不可变计数，只转移 min(本趟余量, 槽内数量)；
     * 旧库存/私人物品/异组件组原样保留，放不下的余量退回本趟货物槽。
     */
    static int placeViaMenu(AbstractContainerMenu menu, ServerPlayer player,
                            ItemStack carryItem, int carryRemaining, int cargoInventorySlot) {
        if (carryItem.isEmpty() || carryRemaining <= 0) {
            return 0;
        }
        int chestSlots = chestSlotCount(menu, menu.getSlot(0).container);
        int cargoSlot = findCargoMenuSlot(menu, player, carryItem,
                carryRemaining, cargoInventorySlot, chestSlots);
        if (cargoSlot < 0) {
            return 0;
        }
        // 点击前保存：ItemStack 引用会被点击改写，不能点后再读数量
        int cargoCount = menu.getSlot(cargoSlot).getItem().getCount();
        int deliverable = Math.min(carryRemaining, cargoCount);
        if (deliverable <= 0) {
            return 0;
        }
        // 目标箱接收槽：同组件可合并（取最大空间）> 空槽
        int mergeSlot = -1, mergeSpace = 0, emptySlot = -1;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack in = menu.getSlot(i).getItem();
            if (in.isEmpty()) {
                if (emptySlot < 0) {
                    emptySlot = i;
                }
                continue;
            }
            if (ItemStack.isSameItemSameComponents(in, carryItem)) {
                int space = in.getMaxStackSize() - in.getCount();
                if (space > mergeSpace) {
                    mergeSpace = space;
                    mergeSlot = i;
                }
            }
        }
        if (mergeSpace <= 0 && emptySlot < 0) {
            return 0; // 目标满：不点，不动货物
        }
        if (!menu.getCarried().isEmpty()) {
            recoverCarried(menu, player, cargoSlot);
        }
        // 整组到光标 → 旧库存部分右键回原槽 → 光标恰为本趟数量
        clickStatic(menu, player, cargoSlot, 0, false);
        int back = Math.max(0, cargoCount - deliverable);
        for (int k = 0; k < back; k++) {
            clickStatic(menu, player, cargoSlot, 1, false);
        }
        // 投递本趟数量：优先并入同组件槽，余量入空槽（全部为本趟货物）
        int toMerge = Math.min(deliverable, mergeSpace);
        if (toMerge > 0) {
            for (int k = 0; k < toMerge; k++) {
                clickStatic(menu, player, mergeSlot, 1, false);
            }
        }
        if (menu.getCarried().getCount() > 0 && emptySlot >= 0) {
            clickStatic(menu, player, emptySlot, 0, false);
        }
        // 实际结账：光标残留 = 未送出；退回本趟货物槽（正常菜单回收），
        // 不依赖关菜单后的掉落/自动归还，也不把旧库存算成本趟。
        int placed = deliverable - menu.getCarried().getCount();
        int residual = menu.getCarried().getCount();
        for (int k = 0; k < residual; k++) {
            clickStatic(menu, player, cargoSlot, 1, false);
        }
        if (!menu.getCarried().isEmpty()) {
            recoverCarried(menu, player, cargoSlot);
        }
        return Math.max(0, placed);
    }

    /** 定位本趟货物所在玩家菜单槽：优先用保存的背包槽，其次组件+数量匹配 */
    private static int findCargoMenuSlot(AbstractContainerMenu menu, ServerPlayer player,
                                         ItemStack carryItem, int carryRemaining,
                                         int cargoInventorySlot, int chestSlots) {
        int fallback = -1;
        int exact = -1;
        for (int i = chestSlots; i < menu.slots.size(); i++) {
            var slot = menu.getSlot(i);
            if (slot.container != player.getInventory()) {
                continue;
            }
            ItemStack s = slot.getItem();
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, carryItem)) {
                continue;
            }
            if (cargoInventorySlot >= 0) {
                if (slot.getContainerSlot() == cargoInventorySlot) {
                    return i;
                }
                continue; // 精确槽存在时绝不误取私人同物库存
            }
            if (fallback < 0) {
                fallback = i;
            }
            if (s.getCount() == carryRemaining) {
                exact = i;
            }
        }
        return cargoInventorySlot >= 0 ? -1 : (exact >= 0 ? exact : fallback);
    }


    private void click(AbstractContainerMenu menu, int slot, int button, boolean quickMove) {
        clickStatic(menu, this.player, slot, button, quickMove);
    }

    private static void clickStatic(AbstractContainerMenu menu, ServerPlayer player,
                                    int slot, int button, boolean quickMove) {
        //#if MC >= 260102
        //$$ menu.clicked(slot, button, quickMove
        //$$         ? net.minecraft.world.inventory.ContainerInput.QUICK_MOVE
        //$$         : net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        //#else
        menu.clicked(slot, button, quickMove
                ? net.minecraft.world.inventory.ClickType.QUICK_MOVE
                : net.minecraft.world.inventory.ClickType.PICKUP, player);
        //#endif
    }

    /**
     * 建立真实菜单会话：玩家正常右键打开目标容器，确认菜单身份后登记会话。
     * 不在这里关箱；关闭由 60 游戏刻检查后的转移出口或中断路径执行。
     */
    protected boolean openChestSession(BlockPos pos,
                                       java.util.function.Consumer<AbstractContainerMenu> onOpened) {
        AbstractContainerMenu menu = this.openChestMenu(pos);
        if (menu == null) {
            return false;
        }
        this.registerOpenSession(menu, pos, this.bodyLevel().getGameTime());
        onOpened.accept(menu);
        return true;
    }

    /**
     * 右键开箱 → 校验菜单身份；不调用 openMenu 强制绕过锁箱/遮挡/正常交互失败。
     * 未打开目标容器时只清理本次打开产生的菜单，不碰其他系统接管的菜单。
     */
    protected AbstractContainerMenu openChestMenu(BlockPos pos) {
        if (!(this.bodyLevel() instanceof ServerLevel) || this.player == null) {
            return null;
        }
        AbstractContainerMenu before = this.player.containerMenu;
        boolean wasShift = this.mob.asPlayer().isShiftKeyDown();
        this.mob.asPlayer().setShiftKeyDown(false); // 非潜行右键：容器 use 优先
        try {
            // 空手栈开容器：容器 use 本就先于物品 use，且失败时不会放置手持方块
            boolean ok = this.mob.pryOpenContainer(pos, net.minecraft.world.InteractionHand.MAIN_HAND);
            AbstractContainerMenu menu = this.player.containerMenu;
            if (!ok || menu == null || menu == this.player.inventoryMenu
                    || !this.menuIsForTarget(menu, pos)) {
                // 只清理本次尝试打开产生的菜单；已有的无关菜单不碰
                if (before == this.player.inventoryMenu
                        && this.player.containerMenu != this.player.inventoryMenu) {
                    this.player.doCloseContainer();
                }
                return null;
            }
            return menu;
        } finally {
            this.mob.asPlayer().setShiftKeyDown(wasShift);
        }
    }

    /** 会话登记：成功开箱的时间从这里开始计 60 游戏刻。 */
    protected void registerOpenSession(AbstractContainerMenu menu, BlockPos pos, long gameTime) {
        this.openMenu = menu;
        this.openMenuTarget = pos.immutable();
        this.openMenuStartedGameTime = gameTime;
        this.transferredThisSession = false;
        this.openFailures = 0; // 本次已成功打开
        this.nextOpenAttemptTime = Long.MIN_VALUE;
    }

    /**
     * 只关闭本脑打开的会话并按原版流程归还光标；菜单已被他人关闭/接管时，
     * 仅清本脑状态，不强制关闭无关菜单。
     */
    protected void closeChestSession() {
        if (this.openMenu == null) {
            return;
        }
        if (this.player != null && this.player.containerMenu == this.openMenu) {
            this.player.doCloseContainer();
        }
        this.openMenu = null;
        this.openMenuTarget = null;
        this.openMenuStartedGameTime = Long.MIN_VALUE;
        this.transferredThisSession = false;
    }

    /**
     * 每次推进前的会话/目标复核：目标存在、同维、距离内、菜单仍是本次菜单、
     * 菜单仍有效；任一失败只关闭本脑会话。
     */
    private boolean ensureChestSession(Level level, long now) {
        if (this.targetInvalid(level)) {
            // 目标已不存在：该位置的“已访问”记录不再阻止重试
            this.visited.remove(canonicalContainerPos(level, this.target));
            this.failToSeek(SCAN_INTERVAL_TICKS); // 货物留在背包，不吞物
            return false;
        }
        if (this.openMenu != null) {
            boolean stillOurs = this.player != null && !this.player.isRemoved()
                    && !this.player.hasDisconnected()
                    && this.player.containerMenu == this.openMenu
                    && this.openMenuTarget != null
                    && this.openMenuTarget.equals(this.target)
                    && this.menuIsForTarget(this.openMenu, this.target)
                    && this.openMenu.stillValid(this.player)
                    && this.withinInteractionRange(this.target);
            if (!stillOurs) {
                this.closeChestSession();
                this.failToSeek(SCAN_INTERVAL_TICKS);
                return false;
            }
            return true;
        }
        // 第 1 刻：真实打开容器并登记会话；失败有限重试，持续失败退避换箱
        if (!this.withinInteractionRange(this.target)) {
            this.failToSeek(SCAN_INTERVAL_TICKS);
            return false;
        }
        if (now < this.nextOpenAttemptTime) {
            return false; // 重试间隔内
        }
        if (this.openChestSession(this.target, menu -> {
        })) {
            return true;
        }
        this.openFailures++;
        if (this.openFailures >= MAX_OPEN_ATTEMPTS) {
            // “打不开”≠“检查过”：退避后允许再试，但先换下一个候选
            this.visited.remove(canonicalContainerPos(level, this.target));
            this.blacklist.put(canonicalContainerPos(level, this.target), now + BLACKLIST_TICKS);
            this.failToSeek(SCAN_INTERVAL_TICKS);
        } else {
            this.nextOpenAttemptTime = now + OPEN_RETRY_TICKS;
        }
        return false;
    }

    /** 玩家方块交互范围门；直调 gameMode 不经过网络层距离校验。 */
    protected boolean withinInteractionRange(BlockPos pos) {
        double reachSq = this.mob.blockReachSq();
        return this.mob.asPlayer().getEyePosition().distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reachSq;
    }

    /**
     * 菜单归属校验：以真实容器身份对应，不用容量/首槽内容猜。
     * 单箱 = 菜单容器就是目标方块实体容器；双箱 = 菜单容器是
     * {@link net.minecraft.world.CompoundContainer} 且包含目标半箱的真实容器。
     */
    boolean menuIsForTarget(AbstractContainerMenu menu, BlockPos pos) {
        net.minecraft.world.Container be = this.containerAt(this.bodyLevel(), pos);
        if (be == null || menu.slots.isEmpty()) {
            return false;
        }
        var first = menu.getSlot(0).container;
        if (first == this.player.inventoryMenu) {
            return false;
        }
        if (first == be) {
            return true;
        }
        return first instanceof net.minecraft.world.CompoundContainer compound && compound.contains(be);
    }

    /** 目标容器的箱槽段长度（菜单前导共享容器的连续槽位） */
    static int chestSlotCount(AbstractContainerMenu menu, net.minecraft.world.Container target) {
        int run = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).container == target) {
                run++;
            } else {
                break;
            }
        }
        return run;
    }

    /** 菜单玩家背包段的第一个空槽（箱槽段之后） */
    static int firstEmptyPlayerSlot(AbstractContainerMenu menu) {
        for (int i = chestSlotCount(menu, menu.getSlot(0).container); i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** 菜单玩家背包段中与给定栈同 Item 同组件且有剩余空间的槽 */
    static int sameItemPlayerSlotWithSpace(AbstractContainerMenu menu, ItemStack carried) {
        int best = -1;
        int bestSpace = 0;
        for (int i = chestSlotCount(menu, menu.getSlot(0).container); i < menu.slots.size(); i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, carried)) {
                int space = s.getMaxStackSize() - s.getCount();
                if (space > bestSpace) {
                    bestSpace = space;
                    best = i;
                }
            }
        }
        return best;
    }

    /** 把本趟货物选中到手（只认保存的背包槽 + 同组件；不拿私人同类库存顶替） */
    private void equipCarried() {
        var inv = this.player.getInventory();
        int slot = this.carryInventorySlot;
        if (slot < 0 || slot >= inv.getContainerSize()) {
            return;
        }
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, this.carryItem)) {
            return;
        }
        if (slot < net.minecraft.world.entity.player.Inventory.getSelectionSize()) {
            inv.setSelectedSlot(slot);
        } else {
            inv.pickSlot(slot); // 玩家数字键交换语义；槽位内容随交换，身份仍按背包槽保存
            this.carryInventorySlot = inv.getSelectedSlot();
        }
    }

    @Override
    public void stop() {
        // Goal 暂停/卸脑：先只关闭本脑打开的菜单会话；本趟货物归属与数量保留，
        // 有货恢复寻目的地，无货恢复寻来源。
        this.closeChestSession();
        this.pauseOrAbandon();
    }

    /** 卸脑清理（换脑/off/下线）：只清本脑登记，货物留在背包 */
    public static void releaseBrain(ServerPlayer brain) {
        ACTIVE_CONTAINER_BY_BRAIN.remove(brain);
    }
}

//#else
//$$ /**
//$$  * 铜傀儡分类目标：仅 1.21.10+（铜箱所在版本）存在。
//$$  */
//#endif
