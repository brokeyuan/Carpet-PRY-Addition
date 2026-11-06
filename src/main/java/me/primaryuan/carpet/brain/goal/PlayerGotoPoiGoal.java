package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 村民日程地点目标（工作站工作 / 钟下集合，对应原版 work/meet 活动的
 * GoToWork/MeetNeighbors 行为骨架）。
 *
 * <p><b>占用生命周期</b>：canUse 只查找、验证并暂存候选（不写登记表）；
 * start 时复核资源有效与容量后才提交占用；占用失败不导航不动作，下一评估
 * 重试；stop 只释放本 Goal 实际持有的资源（含维度归属）。被恐慌/规避挡住的
 * 候选根本不会走到 canUse（选择器先判旗标资格再调 canUse），不存在"未启动
 * 却占位"的幽灵占用。</p>
 *
 * <p><b>候选选择</b>：按与村民的距离升序（原版 POI 搜索取最近）；不可达
 * （连续建路失败/路径走完未到位/总时长超限）的候选局部退避（时长制黑名单，
 * 到期自动解封），释放本次占用后转试下一候选；候选用尽则空闲等下轮扫描，
 * 不做每 tick 全范围扫描或全量 A*。退避/超时为本移植自建防卡保护（原版
 * Brain 由活动重评估承担重试），已如实区分。</p>
 *
 * <p>与原版差异披露：职业系统（工作站类型与村民职业绑定、职业升级）不实现，
 * 工作站集合为原版 13 种职业方块（{@code PoiTypes.bootstrap} 字节码核定）；
 * "工作"只表达到位占用，不含作业动画与物品产出（凭空造物红线）。</p>
 */
public class PlayerGotoPoiGoal extends PlayerGoal {

    /** POI 扫描半径（水平格）：原版 Brain POI 搜索 48 过重，取 16（披露） */
    private static final int SEARCH_RADIUS = 16;
    /** 到点后视为"到位"的距离平方 */
    private static final double ARRIVE_DIST_SQ = 4.0;
    /** 扫描节流（评估次数）：20 次 ≈ 40 tick 重扫 */
    private static final int SCAN_INTERVAL = 20;
    /** 连续寻路失败阈值（连续序列：成功且有实际进展即清零）——超过后候选退避换点 */
    private static final int MAX_PATH_FAILURES = 3;
    /** 单次占用的总寻路预算（游戏 tick；到位后暂停计时） */
    private static final long PATH_TIME_BUDGET_TICKS = 600;
    /** 候选退避时长（游戏 tick）：到期自动解封，环境改变后可重试 */
    private static final long BLACKLIST_TICKS = 200;
    /** 路径走完仍未到位的宽限（游戏 tick，gameTime 差值——单位与执行频率解耦） */
    private static final long DONE_NOT_ARRIVED_GRACE_TICKS = 20;

    /** 原版 13 种职业工作站方块（{@code PoiTypes.bootstrap} 字节码核定，
     *  含盔甲匠高炉；皮革匠与陶匠共用炼药锅） */
    private static final List<net.minecraft.world.level.block.Block> WORKSTATION_BLOCKS = List.of(
            net.minecraft.world.level.block.Blocks.COMPOSTER,
            net.minecraft.world.level.block.Blocks.BARREL,
            net.minecraft.world.level.block.Blocks.SMOKER,
            net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE,
            net.minecraft.world.level.block.Blocks.BREWING_STAND,
            net.minecraft.world.level.block.Blocks.LECTERN,
            net.minecraft.world.level.block.Blocks.LOOM,
            net.minecraft.world.level.block.Blocks.FLETCHING_TABLE,
            net.minecraft.world.level.block.Blocks.CAULDRON,
            net.minecraft.world.level.block.Blocks.STONECUTTER,
            net.minecraft.world.level.block.Blocks.SMITHING_TABLE,
            net.minecraft.world.level.block.Blocks.GRINDSTONE,
            net.minecraft.world.level.block.Blocks.BLAST_FURNACE);

    protected final PryMob mob;
    private final net.minecraft.world.entity.player.Player player;
    private final VillagerSchedule.Activity activity;
    private final VillagerPoiRegistry.Kind poiKind;
    private final double speed;
    /** canUse 暂存的候选（只查找不占位，start 时提交） */
    private BlockPos pendingPoi;
    /** 实际持有的占用（start 提交、stop 释放；维度随获取时固定） */
    private Level claimedLevel;
    private BlockPos claimedPos;
    /** 候选退避表：pos → 解封 gameTime */
    private final Map<BlockPos, Long> blacklist = new HashMap<>();
    private int navigateCooldown;
    /** 连续寻路失败序列（成功且有实际进展即清零） */
    private int pathFailures;
    /** 上次记录的实际位置（进展检测） */
    private net.minecraft.world.phys.Vec3 lastProgressPos;
    /** 无进展宽限起点（gameTime；Long.MIN_VALUE = 未在计时） */
    private long noProgressSince = Long.MIN_VALUE;
    /** 寻路预算已消耗量（游戏 tick；到位后暂停累计） */
    private long pathBudgetUsedTicks;
    /** 上一 tick 的 gameTime（预算差值累计用） */
    private long lastTickGameTime = Long.MIN_VALUE;
    private long claimStartedGameTime;

    /** 工作站目标（日程 WORK） */
    public PlayerGotoPoiGoal(PryMob mob, VillagerSchedule.Activity activity, double speed) {
        this.mob = mob;
        this.player = mob.asPlayer();
        this.activity = activity;
        this.poiKind = activity == VillagerSchedule.Activity.WORK
                ? VillagerPoiRegistry.Kind.WORKSTATION
                : VillagerPoiRegistry.Kind.MEETING;
        this.speed = speed;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(SCAN_INTERVAL);
    }

    /** 当前日程活动（独立方法供离线测试覆写时钟） */
    protected VillagerSchedule.Activity currentActivity() {
        return VillagerSchedule.activityFor(VillagerSchedule.dayTime(this.player.level()), this.player.isBaby());
    }

    /** 当前是否处于本目标的活动段 */
    private boolean isMyTime() {
        return this.currentActivity() == this.activity;
    }

    private Predicate<BlockState> poiBlocks() {
        return this.activity == VillagerSchedule.Activity.WORK
                ? PlayerGotoPoiGoal::isWorkstation
                : state -> state.is(net.minecraft.world.level.block.Blocks.BELL);
    }

    /** 职业工作站判定（13 种原版村民职业方块） */
    static boolean isWorkstation(BlockState state) {
        return WORKSTATION_BLOCKS.contains(state.getBlock());
    }

    @Override
    public boolean canUse() {
        // 活动段外只清暂存（此刻不运行、不持有占用，对登记表零副作用）
        if (!this.isMyTime()) {
            this.pendingPoi = null;
            return false;
        }
        // 暂存候选失效/被拉黑：丢弃重选
        if (this.pendingPoi != null && !this.isCandidateUsable(this.pendingPoi)) {
            this.pendingPoi = null;
        }
        if (this.pendingPoi == null) {
            this.pendingPoi = this.findFreePoi();
        }
        return this.pendingPoi != null;
    }

    @Override
    public void start() {
        // 启动时复核资源并提交占用；本次尝试状态全部复位（无跨会话残留）
        this.navigateCooldown = 0;
        this.pathFailures = 0;
        this.lastProgressPos = null;
        this.noProgressSince = Long.MIN_VALUE;
        this.pathBudgetUsedTicks = 0;
        this.lastTickGameTime = Long.MIN_VALUE;
        this.claimedLevel = null;
        this.claimedPos = null;
        BlockPos candidate = this.pendingPoi;
        this.pendingPoi = null;
        if (candidate == null || !this.isCandidateUsable(candidate)) {
            return;
        }
        if (!VillagerPoiRegistry.claim(this.player, this.player.level(), candidate, this.poiKind)) {
            return; // 容量被抢：空跑一轮即被清理，不假装持有资源
        }
        this.claimedLevel = this.player.level();
        this.claimedPos = candidate;
        this.claimStartedGameTime = this.player.level().getGameTime();
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.isMyTime()) {
            return false;
        }
        if (this.claimedPos == null) {
            return false; // start 未提交占用：立即让位重试
        }
        // 跨维：占用属于获取时的世界，stop 按原维度释放，新世界重新获取
        if (this.player.level() != this.claimedLevel) {
            return false;
        }
        if (!this.isPoiBlock(this.claimedLevel, this.claimedPos)) {
            return false; // POI 失效（方块被破坏/替换）→ stop 释放
        }
        return true;
    }

    @Override
    public void tick() {
        if (this.claimedPos == null || this.claimedLevel == null
                || this.player.level() != this.claimedLevel) {
            return;
        }
        long now = this.player.level().getGameTime();
        BlockPos dest = this.currentDestination();
        if (dest == null) {
            return;
        }
        double distSq = this.player.distanceToSqr(
                dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        if (distSq <= ARRIVE_DIST_SQ) {
            // 到位站定：连续失败序列结束、无进展计时清除、寻路预算暂停
            // （站着工作/集合的时间不算寻路消耗）
            this.pathFailures = 0;
            this.noProgressSince = Long.MIN_VALUE;
            this.mob.getNavigation().stop();
            this.mob.getLookControl().setLookAt(
                    dest.getX() + 0.5, dest.getY() + 0.5, dest.getZ() + 0.5,
                    30.0F, 30.0F);
            if (dest == this.claimedPos) {
                this.onArrivedAtStation();
            } else {
                this.onArrivedAtDestination(dest);
            }
            this.lastTickGameTime = now;
            return;
        }
        // 预算按 tick 差值累计（到位分支不经过此处，不累计）
        if (this.lastTickGameTime != Long.MIN_VALUE && now > this.lastTickGameTime) {
            this.pathBudgetUsedTicks += now - this.lastTickGameTime;
        }
        this.lastTickGameTime = now;
        // 实际进展检测：相对上次记录位置有移动 → 结束旧连续失败序列并清无进展计时
        if (this.lastProgressPos != null
                && this.player.distanceToSqr(this.lastProgressPos) > 1.0E-4) {
            this.pathFailures = 0;
            this.noProgressSince = Long.MIN_VALUE;
        }
        if (this.lastProgressPos == null
                || this.player.distanceToSqr(this.lastProgressPos) > 1.0E-4) {
            this.lastProgressPos = this.player.position();
        }
        // 建路/重算节流：moveTo 返回 false（建不出路）计入连续失败
        if (--this.navigateCooldown <= 0) {
            this.navigateCooldown = this.adjustedTickDelay(10);
            if (!this.mob.getNavigation().moveTo(
                    dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, this.speed)) {
                this.pathFailures++;
            }
        }
        // 有路径但没有实际进展：gameTime 宽限（游戏刻单位，与执行频率解耦）
        if (this.mob.getNavigation().isInProgress() && this.noProgressSince == Long.MIN_VALUE) {
            this.noProgressSince = now;
        }
        if (this.noProgressSince != Long.MIN_VALUE
                && now - this.noProgressSince >= DONE_NOT_ARRIVED_GRACE_TICKS) {
            // 无进展宽限耗尽：计一次连续失败并立即重试建路
            this.pathFailures++;
            this.noProgressSince = Long.MIN_VALUE;
            this.navigateCooldown = 0;
        }
        // 总寻路预算超限：候选不可达（独立于连续失败序列）
        if (this.pathBudgetUsedTicks > PATH_TIME_BUDGET_TICKS) {
            this.pathFailures = MAX_PATH_FAILURES;
        }
        if (this.pathFailures >= MAX_PATH_FAILURES) {
            // 目的地是出差目标（非认领岗位）时，失败只放弃出差不放弃岗位
            if (dest == this.claimedPos) {
                this.giveUpCandidate();
            } else {
                this.onDestinationFailed(dest);
            }
        }
    }

    // ==================== 目的地接缝（默认行为=始终朝向认领岗位） ====================

    /** 本次推进的目的地（默认=认领的工作站；子类可指到短期出差点，认领关系不变） */
    protected BlockPos currentDestination() {
        return this.claimedPos;
    }

    /** 到达工作站后的工作动作（默认无：站定望向岗位，即"到位站定"分支原语义） */
    protected void onArrivedAtStation() {
    }

    /** 到达出差目的地后的动作（默认无） */
    protected void onArrivedAtDestination(BlockPos dest) {
    }

    /** 出差目的地寻路失败（默认等价放弃岗位；子类应改为放弃本次出差） */
    protected void onDestinationFailed(BlockPos dest) {
        this.giveUpCandidate();
    }

    /** 目的地切换后重置失败/进展/预算跟踪（子类变更目的地时调用） */
    protected void resetDestinationTracking() {
        this.navigateCooldown = 0;
        this.lastProgressPos = null;
        this.noProgressSince = Long.MIN_VALUE;
        this.pathBudgetUsedTicks = 0;
        this.lastTickGameTime = Long.MIN_VALUE;
    }

    /** 认领的工作站位置（职业判定等外部查询用；未认领为 null） */
    public BlockPos claimedStationPos() {
        return this.claimedPos;
    }

    @Override
    public void stop() {
        this.releaseClaim();
        this.mob.getNavigation().stop();
    }

    /** 放弃当前候选：时长制退避拉黑 → 释放占用 → 加速重评换下一候选 */
    private void giveUpCandidate() {
        if (this.claimedPos != null && this.claimedLevel != null) {
            this.blacklist.put(this.claimedPos,
                    this.claimedLevel.getGameTime() + BLACKLIST_TICKS);
        }
        this.releaseClaim();
        this.expediteNextEvaluation();
    }

    /**
     * 暂停时是否保留占用（长期岗位由控制器持有）。默认 false = 临时资源随
     * Goal 停止释放；职业工作站子类可覆写为“有效长期岗位不释放”。
     */
    protected boolean retainsClaimOnStop(BlockPos pos, Level level) {
        return false;
    }

    /** 释放本 Goal 实际持有的占用（只释放自己名下、按获取时的维度） */
    private void releaseClaim() {
        if (this.claimedPos != null && this.claimedLevel != null) {
            if (!this.retainsClaimOnStop(this.claimedPos, this.claimedLevel)) {
                VillagerPoiRegistry.release(this.player, this.claimedLevel, this.claimedPos);
            }
        }
        this.claimedLevel = null;
        this.claimedPos = null;
        this.pendingPoi = null;
        this.lastProgressPos = null;
        this.noProgressSince = Long.MIN_VALUE;
    }

    /** 候选可用性：未退避（或已解封）、容量未满、方块仍是目标 POI */
    private boolean isCandidateUsable(BlockPos candidate) {
        Long expireAt = this.blacklist.get(candidate);
        if (expireAt != null) {
            if (this.player.level().getGameTime() < expireAt) {
                return false;
            }
            this.blacklist.remove(candidate); // 退避到期：解封（环境改变后可重试）
        }
        // 自己已持有的长期岗位仍然可用（isFull 会把 1 容量点判满，不能把
        // “自己占着”误当成“别人占着”而放弃职业）
        boolean mine = VillagerPoiRegistry.isClaimedBy(
                this.player, this.player.level().dimension(), candidate);
        if (!mine && VillagerPoiRegistry.isFull(this.player.level(), candidate, this.poiKind)) {
            return false;
        }
        return this.isPoiBlock(this.player.level(), candidate);
    }

    /** POI 方块判定（独立方法供离线测试覆写世界读块） */
    protected boolean isPoiBlock(Level level, BlockPos pos) {
        return this.poiBlocks().test(level.getBlockState(pos));
    }

    /**
     * 找未占用 POI：候选按距离升序（原版取最近），跳过退避中/容量满/失效的
     * 点（判定本体真实执行，候选来源经 {@link #nearbyPoiCandidates} 缝注入）。
     */
    protected BlockPos findFreePoi() {
        long now = this.player.level().getGameTime();
        this.blacklist.entrySet().removeIf(e -> now >= e.getValue());
        List<BlockPos> sorted = new ArrayList<>(this.nearbyPoiCandidates());
        sorted.sort(Comparator.comparingDouble(p ->
                this.player.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        for (BlockPos candidate : sorted) {
            if (this.isCandidateUsable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 范围内 POI 候选（方块扫描等价，离线测试覆写） */
    protected List<BlockPos> nearbyPoiCandidates() {
        return PlayerGotoPoiGoal.scanFor(this.player.level(), this.player.blockPosition(),
                SEARCH_RADIUS, this.poiBlocks());
    }

    /** 盒内方块扫描（自上而下）；供两个村民 Goal 共用 */
    public static List<BlockPos> scanFor(Level level, BlockPos origin, int radius,
                                         Predicate<BlockState> matcher) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos from = origin.offset(-radius, -4, -radius);
        BlockPos to = origin.offset(radius, 4, radius);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (matcher.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }
}
