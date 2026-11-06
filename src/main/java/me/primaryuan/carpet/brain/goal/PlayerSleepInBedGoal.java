package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 村民回床入睡目标（日程 REST 段激活；对应原版村民 rest 活动的
 * GoHome/Sleep 行为；26.3 字节码 {@code SleepInBed.checkExtraStartConditions}
 * 核对：HOME 维度、床距离、可睡标签、OCCUPIED=false）。
 *
 * <p><b>床资源语义</b>：双格床以<b>床头</b>为规范资源位置（原版
 * {@code AbstractBedBlock.getConnectedDirection} 求另一半），查找/认领/验证/
 * 释放全用同一规范化；入睡前复核实际床状态——OCCUPIED=true（真人/原版村民
 * 已睡）不抢，仅本人已睡在该床（getSleepingPos==床头）视为自己的有效占用；
 * 睡眠入口是玩家原生 {@code startSleeping}（低层入口不查 OCCUPIED，占用门
 * 在本 Goal 内完成）。入睡未生效（被原版拒绝等）进入恢复流程：退避该床、
 * 释放占用，不保留"成功占床"假状态。</p>
 *
 * <p><b>占用生命周期</b>：canUse 只查找验证暂存（不写登记表）；start 复核后
 * 提交占用；stop 只释放自己名下、按获取时维度的资源；被恐慌打断（MOVE 抢占）
 * 或日程切换即 stop → 原生唤醒 + 释放。被挡住的候选经选择器资格门不会走到
 * canUse，无幽灵占用。</p>
 *
 * <p>与原版差异披露：床位发现是范围方块扫描（原版走区块 POI 数据引擎），
 * 扫描节流 20 次评估；退避/超时为自建防卡保护；只清理自己名下的占用与
 * 睡眠状态，不触碰其他实体的 OCCUPIED。</p>
 */
public class PlayerSleepInBedGoal extends PlayerGoal {

    /** 床位扫描半径（水平格）：原版 Brain 传感器 POI 搜索半径 48 过重，取 16（披露） */
    private static final int SEARCH_RADIUS = 16;
    /** 到床后可入睡的水平距离平方（原版 sleep 状态机 2.5 格内允许躺下） */
    private static final double ARRIVE_DIST_SQ = 2.5 * 2.5;
    /** 床位扫描节流（评估次数）：20 次 ≈ 40 tick 重扫 */
    private static final int SCAN_INTERVAL = 20;
    /** 连续寻路失败阈值（连续序列：成功且有实际进展即清零）——超过后候选退避换床 */
    private static final int MAX_PATH_FAILURES = 3;
    /** 单次占用的总寻路预算（游戏 tick；入睡后暂停计时） */
    private static final long PATH_TIME_BUDGET_TICKS = 600;
    /** 候选退避时长（游戏 tick） */
    private static final long BLACKLIST_TICKS = 200;
    /** 路径走完仍未到位的宽限（游戏 tick，gameTime 差值——单位与执行频率解耦） */
    private static final long DONE_NOT_ARRIVED_GRACE_TICKS = 20;

    /** 床探测结果：isBed + 规范化床头 + 真实占用位（离线测试可注入） */
    public record BedInfo(boolean isBed, BlockPos head, boolean occupied) {
        static final BedInfo NOT_BED = new BedInfo(false, null, false);
    }

    protected final PryMob mob;
    private final net.minecraft.world.entity.player.Player player;
    private BlockPos pendingBed;
    /** 实际持有的占用（start 提交、stop 释放；维度随获取时固定） */
    private Level claimedLevel;
    private BlockPos claimedBed;
    private final Map<BlockPos, Long> blacklist = new HashMap<>();
    private int navigateCooldown;
    /** 连续寻路失败序列（成功且有实际进展即清零） */
    private int pathFailures;
    /** 上次记录的实际位置（进展检测） */
    private net.minecraft.world.phys.Vec3 lastProgressPos;
    /** 无进展宽限起点（gameTime；Long.MIN_VALUE = 未在计时） */
    private long noProgressSince = Long.MIN_VALUE;
    /** 寻路预算已消耗量（游戏 tick；入睡后暂停累计） */
    private long pathBudgetUsedTicks;
    /** 上一 tick 的 gameTime（预算差值累计用） */
    private long lastTickGameTime = Long.MIN_VALUE;
    private long claimStartedGameTime;

    public PlayerSleepInBedGoal(PryMob mob) {
        this.mob = mob;
        this.player = mob.asPlayer();
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(SCAN_INTERVAL);
    }

    /** 当前日程活动（独立方法供离线测试覆写时钟） */
    protected VillagerSchedule.Activity currentActivity() {
        return VillagerSchedule.activityFor(VillagerSchedule.dayTime(this.player.level()), this.player.isBaby());
    }

    /** 当前是否处于 rest 日程段 */
    private boolean isRestTime() {
        return this.currentActivity() == VillagerSchedule.Activity.REST;
    }

    @Override
    public boolean canUse() {
        if (!this.isRestTime()) {
            this.pendingBed = null;
            return false;
        }
        if (this.pendingBed != null && !this.isCandidateUsable(this.pendingBed)) {
            this.pendingBed = null;
        }
        if (this.pendingBed == null) {
            this.pendingBed = this.findFreeBed();
        }
        return this.pendingBed != null;
    }

    @Override
    public void start() {
        // 启动时复核床位并提交占用；本次尝试状态全部复位（无跨会话残留）
        this.navigateCooldown = 0;
        this.pathFailures = 0;
        this.lastProgressPos = null;
        this.noProgressSince = Long.MIN_VALUE;
        this.pathBudgetUsedTicks = 0;
        this.lastTickGameTime = Long.MIN_VALUE;
        this.claimedLevel = null;
        this.claimedBed = null;
        BlockPos candidate = this.pendingBed;
        this.pendingBed = null;
        if (candidate == null || !this.isCandidateUsable(candidate)) {
            return;
        }
        if (!VillagerPoiRegistry.claim(this.player, this.player.level(), candidate,
                VillagerPoiRegistry.Kind.BED)) {
            return;
        }
        this.claimedLevel = this.player.level();
        this.claimedBed = candidate;
        this.claimStartedGameTime = this.player.level().getGameTime();
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.isRestTime()) {
            return false; // 天亮/日程切换 → stop 原生唤醒 + 释放
        }
        if (this.claimedBed == null) {
            return false; // start 未提交占用：让位重试
        }
        if (this.player.level() != this.claimedLevel) {
            return false; // 跨维：原维度占用由 stop 按原资源释放
        }
        return this.isBedUsable(this.claimedLevel, this.claimedBed);
    }

    @Override
    public void tick() {
        if (this.claimedBed == null || this.claimedLevel == null
                || this.player.level() != this.claimedLevel) {
            return;
        }
        if (this.mob.pryIsSleeping()) {
            // 已入睡：预算暂停——差值基准持续刷新到当前，恢复寻路时只累计
            // 恢复后的时间（整段睡眠不进预算）
            this.lastTickGameTime = this.player.level().getGameTime();
            return;
        }
        long now = this.player.level().getGameTime();
        double distSq = this.player.distanceToSqr(
                this.claimedBed.getX() + 0.5, this.claimedBed.getY(), this.claimedBed.getZ() + 0.5);
        if (distSq > ARRIVE_DIST_SQ) {
            // 预算按 tick 差值累计（入睡分支不经过此处，不累计）
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
            // 建路/重算节流：moveTo 返回 false 计入连续失败
            if (--this.navigateCooldown <= 0) {
                this.navigateCooldown = this.adjustedTickDelay(10);
                if (!this.mob.getNavigation().moveTo(
                        this.claimedBed.getX() + 0.5, this.claimedBed.getY(), this.claimedBed.getZ() + 0.5, 0.5)) {
                    this.pathFailures++;
                }
            }
            // 有路径但没有实际进展：gameTime 宽限（游戏刻单位，与执行频率解耦）
            if (this.mob.getNavigation().isInProgress() && this.noProgressSince == Long.MIN_VALUE) {
                this.noProgressSince = now;
            }
            if (this.noProgressSince != Long.MIN_VALUE
                    && now - this.noProgressSince >= DONE_NOT_ARRIVED_GRACE_TICKS) {
                this.pathFailures++;
                this.noProgressSince = Long.MIN_VALUE;
                this.navigateCooldown = 0;
            }
            // 总寻路预算超限：候选不可达（独立于连续失败序列）
            if (this.pathBudgetUsedTicks > PATH_TIME_BUDGET_TICKS) {
                this.pathFailures = MAX_PATH_FAILURES;
            }
            if (this.pathFailures >= MAX_PATH_FAILURES) {
                this.giveUpCandidate();
            }
            return;
        }
        // 已到床边：连续失败序列结束、无进展计时清除、预算暂停
        this.pathFailures = 0;
        this.noProgressSince = Long.MIN_VALUE;
        this.lastTickGameTime = now;
        // 入睡前复核真实床状态（扫描结果不算数）
        if (!this.isBedUsable(this.claimedLevel, this.claimedBed)) {
            this.giveUpCandidate(); // 被别人占/床失效 → 退避释放重试
            return;
        }
        // 原生入睡（躺下姿态/床占用位/醒来广播全走原版）
        this.player.startSleeping(this.claimedBed);
        if (!this.mob.pryIsSleeping()) {
            // 原版拒绝（身体条件不满足等）：恢复流程，不保留"成功占床"假状态
            this.giveUpCandidate();
        }
    }

    @Override
    public void stop() {
        this.releaseClaim();
        this.mob.getNavigation().stop();
    }

    /** 放弃当前床：时长制退避拉黑 → 释放占用 → 加速重评换下一床 */
    private void giveUpCandidate() {
        if (this.claimedBed != null && this.claimedLevel != null) {
            this.blacklist.put(this.claimedBed,
                    this.claimedLevel.getGameTime() + BLACKLIST_TICKS);
        }
        this.releaseClaim();
        this.expediteNextEvaluation();
    }

    /** 释放本 Goal 实际持有的占用；睡眠状态只清自己的（不触碰其他实体 OCCUPIED） */
    private void releaseClaim() {
        if (this.claimedBed != null && this.claimedLevel != null) {
            VillagerPoiRegistry.release(this.player, this.claimedLevel, this.claimedBed);
        }
        this.claimedLevel = null;
        this.claimedBed = null;
        this.pendingBed = null;
        this.lastProgressPos = null;
        this.noProgressSince = Long.MIN_VALUE;
        if (this.mob.pryIsSleeping()) {
            this.mob.pryStopSleepInBed(true, true); // 仅自己被唤醒
        }
    }

    /** 床位可用性（当前世界状态）：仍是床、未被他人占用（本人已睡其中不算） */
    private boolean isBedUsable(Level level, BlockPos bed) {
        BedInfo info = this.probeBed(level, bed);
        if (!info.isBed()) {
            VillagerPoiRegistry.invalidate(level, bed);
            return false;
        }
        if (info.occupied()) {
            // 本人睡在该床是自己的有效占用，不算被占。getSleepingPos 记录实际
            // 躺下格（床头或床尾，取决于 startSleeping 收到的坐标与原版内部处理），
            // 两半任一命中即本人的占用
            BlockPos sleepingPos = this.mob.pryGetSleepingPos().orElse(null);
            if (sleepingPos == null) {
                return false;
            }
            return sleepingPos.equals(info.head()) || isOtherHalfOf(level, info.head(), sleepingPos);
        }
        return true;
    }

    /** sleepingPos 是否为 head 床的另一半（床尾）；走 probeBed 缝，桩安全 */
    private boolean isOtherHalfOf(Level level, BlockPos head, BlockPos sleepingPos) {
        BlockState state = this.readBedState(level, head);
        if (!state.hasProperty(bedPartProperty())) {
            return false;
        }
        // getConnectedDirection(HEAD 状态) 已内部 getOpposite，返回值即指向床尾
        return head.relative(bedConnectedDirection(state)).equals(sleepingPos);
    }

    /**
     * 候选可用性（candidate 必须已是<b>规范床头</b>）：未退避（或已解封）、
     * 容量未满、床状态可用（两半仍是床且未被他人占用）。
     */
    private boolean isCandidateUsable(BlockPos candidate) {
        Long expireAt = this.blacklist.get(candidate);
        if (expireAt != null) {
            if (this.player.level().getGameTime() < expireAt) {
                return false;
            }
            this.blacklist.remove(candidate);
        }
        if (VillagerPoiRegistry.isFull(this.player.level(), candidate, VillagerPoiRegistry.Kind.BED)) {
            return false;
        }
        BedInfo info = this.probeBed(this.player.level(), candidate);
        // candidate 是床头：probeBed 返回的 head 应与其一致；两半仍需都是床
        // （isBedUsable/probeBed 契约：probeBed 对床头入参复核床尾存在性，见其实现）
        return info.isBed() && info.head().equals(candidate) && !info.occupied()
                && this.isOtherHalfStillBed(this.player.level(), candidate);
    }

    /**
     * 床头的另一半仍是床（双格床资源完整性）。经 {@link #probeBed} 缝走世界
     * 读块（桩世界覆写 probeBed 时不触真实 BlockState）。
     */
    protected boolean isOtherHalfStillBed(Level level, BlockPos head) {
        BlockState headState = this.readBedState(level, head);
        if (!headState.hasProperty(bedPartProperty())
                || headState.getValue(bedPartProperty()) != BedPart.HEAD) {
            return false; // 入参必须已是床头
        }
        // getConnectedDirection(HEAD 状态) 已内部 getOpposite，返回值即指向床尾
        BlockPos foot = head.relative(bedConnectedDirection(headState));
        BlockState footState = this.readBedState(level, foot);
        if (!footState.is(bedTag()) || !footState.hasProperty(bedPartProperty())) {
            return false; // 另一半缺失或非床
        }
        // 双向配对：对方必须恰为 FOOT 且 FACING 一致（相互指向），且其床尾回指本床头
        if (footState.getValue(bedPartProperty()) != BedPart.FOOT
                || footState.getValue(facingProperty()) != headState.getValue(facingProperty())) {
            return false;
        }
        return foot.relative(bedConnectedDirection(footState)).equals(head);
    }

    /**
     * 世界读块缝（供离线测试注入真实 BlockState）；生产路径即 level.getBlockState。
     * 方向计算（bedConnectedDirection）不在本缝内——测试覆写本缝不改变生产方向语义。
     */
    protected BlockState readBedState(Level level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    /**
     * 找未占用床位：候选<b>先规范化到床头并去重</b>，再按"与村民的距离"
     * 升序（原版取最近）择优——返回值即规范床头，后续认领/入睡/释放全用
     * 同一坐标（两半不再是两个登记键）。
     */
    protected BlockPos findFreeBed() {
        long now = this.player.level().getGameTime();
        this.blacklist.entrySet().removeIf(e -> now >= e.getValue());
        // 规范化 + 去重：同床两半映射到同一床头
        List<BlockPos> heads = new ArrayList<>();
        for (BlockPos raw : this.nearbyBedCandidates()) {
            BedInfo info = this.probeBed(this.player.level(), raw);
            if (!info.isBed()) {
                continue;
            }
            if (!heads.contains(info.head())) {
                heads.add(info.head());
            }
        }
        heads.sort(Comparator.comparingDouble(p ->
                this.player.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        for (BlockPos head : heads) {
            if (this.isCandidateUsable(head)) {
                return head;
            }
        }
        return null;
    }

    /**
     * 床探测（独立方法供离线测试覆写世界读块）：双格床规范化到床头，
     * 读取真实 OCCUPIED 位。
     */
    protected BedInfo probeBed(Level level, BlockPos pos) {
        BlockState state = this.readBedState(level, pos);
        if (!state.is(bedTag())) {
            return BedInfo.NOT_BED;
        }
        // 规范化：FOOT 半块沿连接方向找到床头，两半共用同一资源位置
        BlockPos head = state.getValue(bedPartProperty()) == BedPart.HEAD
                ? pos.immutable()
                : pos.relative(bedConnectedDirection(state));
        boolean occupied = false;
        BlockState headState = this.readBedState(level, head);
        if (!headState.hasProperty(occupiedProperty())) {
            return BedInfo.NOT_BED; // 映射到的"床头"无床属性：旁边另一床冒充或断裂
        }
        if (headState.getValue(bedPartProperty()) != BedPart.HEAD) {
            return BedInfo.NOT_BED; // 映射点不是床头（PART/朝向不匹配的方块冒充）
        }
        occupied = headState.getValue(occupiedProperty());
        return new BedInfo(true, head, occupied);
    }

    /** 范围内床候选（原版 Brain POI 搜索的方块扫描等价，离线测试覆写） */
    protected List<BlockPos> nearbyBedCandidates() {
        return PlayerGotoPoiGoal.scanFor(this.player.level(), this.player.blockPosition(),
                SEARCH_RADIUS, state -> state.is(bedTag()));
    }

    /** 可睡方块标签（跨版本同键 minecraft:beds） */
    private static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> bedTag() {
        return net.minecraft.tags.BlockTags.BEDS;
    }

    /** FACING 属性（HorizontalDirectionalBlock.FACING，跨版本同名同包） */
    static net.minecraft.world.level.block.state.properties.EnumProperty<Direction> facingProperty() {
        return net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;
    }

    /** 床头/床尾属性（26.3 迁至 AbstractBedBlock，26.2 及更早在 BedBlock） */
    //#if MC >= 260300
    //$$ private static net.minecraft.world.level.block.state.properties.EnumProperty<BedPart> bedPartProperty() {
    //$$     return net.minecraft.world.level.block.AbstractBedBlock.PART;
    //$$ }
    //$$
    //$$ private static net.minecraft.world.level.block.state.properties.BooleanProperty occupiedProperty() {
    //$$     return net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED;
    //$$ }
    //$$
    //$$ private static net.minecraft.core.Direction bedConnectedDirection(BlockState state) {
    //$$     return net.minecraft.world.level.block.AbstractBedBlock.getConnectedDirection(state);
    //$$ }
    //#else
    private static net.minecraft.world.level.block.state.properties.EnumProperty<BedPart> bedPartProperty() {
        return net.minecraft.world.level.block.BedBlock.PART;
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty occupiedProperty() {
        return net.minecraft.world.level.block.BedBlock.OCCUPIED;
    }

    private static net.minecraft.core.Direction bedConnectedDirection(BlockState state) {
        return net.minecraft.world.level.block.BedBlock.getConnectedDirection(state);
    }
    //#endif
}
