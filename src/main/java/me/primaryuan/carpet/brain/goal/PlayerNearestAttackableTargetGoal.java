package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTeams;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * 移植版"最近可攻击目标"选择目标（← 原版 {@code NearestAttackableTargetGoal}）。
 *
 * <p>这是各模式 targetSelector 的核心：按 {@code targetType} + {@code Predicate}
 * 在玩家周围搜最近的目标并写入 {@code getTarget/setTarget}。语义对齐原版：</p>
 * <ul>
 *   <li>{@code randomInterval}=10：原版构造器先 reducedTickDelay（26.3 字节码
 *       ctor: randomInterval = reducedTickDelay(param)），即 1/10 次评估（≈每 20
 *       游戏 tick）掷骰一次决定是否真正重搜——移植版同一换算；</li>
 *   <li>{@code mustSee}=true：新目标必须直接可见（对齐原版 TargetingConditions）；
 *       视线记忆只服务<b>当前目标</b>的丢失缓冲（原版 unseenMemoryTicks=60，经
 *       reducedTickDelay 比较 → 30 次隔 tick 更新 ≈ 60 游戏 tick，26.3
 *       TargetGoal.canContinueToUse 字节码）；</li>
 *   <li>搜索范围显式传入（玩家没有 {@code FOLLOW_RANGE} 属性，不能用
 *       原版的 getFollowDistance）。</li>
 * </ul>
 *
 * <p><b>为何不直接用原版 {@code TargetingConditions}</b>：其 {@code check}/{@code test}
 * 与 {@code selector} 的签名在 1.21.x 与 26.x 之间多次漂移（26.x 收紧为
 * {@code Selector} 接口且要求 {@code ServerLevel} 实参），跨 10 个版本维护成本高。
 * 它本质只做"范围 + 视线 + 选择器谓词"三件事，这里用
 * {@link PlayerSensing}（视线）与显式距离判定自包含等价实现。</p>
 */
public class PlayerNearestAttackableTargetGoal<T extends LivingEntity> extends PlayerGoal {

    private final PryMob mob;
    private final Class<T> targetType;
    private final double searchRange;
    private final int randomInterval;
    private final boolean mustSee;
    private final Predicate<? super T> selector;

    /** 可见性记忆（原版 unseenMemoryTicks=60，比较时经 reducedTickDelay → 30 次隔 tick 更新） */
    private static final int MEMORY_EVALUATIONS = reducedTickDelay(60);
    /** 运行中复扫周期（评估次数）：出现明显更近的合法目标时切换（自建防抖增强，非原版默认行为，披露） */
    private static final int RETARGET_SCAN_EVALUATIONS = 10;
    private int retargetCountdown = RETARGET_SCAN_EVALUATIONS;
    private int unseenTicks;
    /** 本周期搜索到的目标（等待 start 时写入） */
    private LivingEntity pendingTarget;
    /** 扫描调用计数（测试观测缝：掷骰通过才递增） */
    int scanCalls;

    public PlayerNearestAttackableTargetGoal(PryMob mob, Class<T> targetType, double searchRange,
                                             boolean mustSee, Predicate<? super T> predicate) {
        this(mob, targetType, searchRange, 10, mustSee, predicate);
    }

    public PlayerNearestAttackableTargetGoal(PryMob mob, Class<T> targetType, double searchRange,
                                             int randomInterval, boolean mustSee, Predicate<? super T> predicate) {
        this.mob = mob;
        this.targetType = targetType;
        this.searchRange = searchRange;
        this.mustSee = mustSee;
        this.selector = predicate;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.TARGET));
        // 原版构造器：this.randomInterval = reducedTickDelay(param)——移植版同一
        // 换算（param=10 → 5 次评估 ≈ 10 游戏 tick 的候选窗起点，见下方掷骰）
        this.randomInterval = reducedTickDelay(randomInterval);
        this.setInterval(1); // 评估频率对齐原版（每 2 tick 一次），掷骰在 canUse 内
    }

    @Override
    public boolean canUse() {
        // 原版 canUse 首分支：randomInterval>0 时 nextInt(randomInterval)!=0 → 跳过
        // 本次评估（1/randomInterval 的概率真正重搜）；移植版同一掷骰，不叠加第二层节流
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        }
        this.scanCalls++;
        LivingEntity best = this.findBestTarget();
        this.pendingTarget = best;
        return best != null && this.canAttack(best);
    }

    /** 范围内最近候选（原版 getNearestTarget 的实体查询；离线测试覆写为零查询） */
    protected LivingEntity findBestTarget() {
        return findBestTargetDefault();
    }

    private LivingEntity findBestTargetDefault() {
        AABB box = this.mob.getBoundingBox().inflate(this.searchRange, 4.0, this.searchRange);
        double bestSq = this.searchRange * this.searchRange;
        LivingEntity best = null;
        for (T candidate : this.mob.level().getEntitiesOfClass(this.targetType, box)) {
            if (!candidate.isAlive() || !this.selector.test(candidate)) {
                continue;
            }
            if (!this.canAttack(candidate)) {
                continue;
            }
            double sq = this.mob.distanceToSqr(candidate);
            if (sq <= bestSq) {
                bestSq = sq;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        // 战斗队伍：目标中途改为同队 → 立即放弃（含追击维持）。友军判定置于
        // 链首：目标变更竞态下也先按友军裁决（与存活/难度等门禁结果等价）
        if (BrainTeams.isTeamFriendly(this.mob.asLiving(), target)) {
            return false;
        }
        if (target == null || !target.isAlive()) {
            return false;
        }
        // 跨维度：/tp 换世界后旧世界目标引用失效（距离/视线对异世界对象无意义）
        if (target.level() != this.mob.level()) {
            return false;
        }
        // 丢失半径 = 搜索半径 × 1.25（滞后回差）：目标在搜索半径边缘走位时，
        // 等半径的"锁定/丢失"判定会来回闪烁（表现为"莫名丢失索敌"）
        double loseRangeSq = this.searchRange * this.searchRange * 1.5625;
        if (this.mob.distanceToSqr(target) > loseRangeSq || !this.acceptsTarget(target)) {
            return false;
        }
        if (this.mustSee && !this.mob.getSensing().hasLineOfSight(target)
                && this.unseenTicks > MEMORY_EVALUATIONS) {
            return false; // 视野记忆窗口耗尽
        }
        return true;
    }

    @Override
    public void start() {
        // 新目标从零计视野记忆与复扫节奏：上一轮运行残留的 unseenTicks 会让
        // 新目标在首次 canContinueToUse 就被判"视野记忆耗尽"（闪断）
        this.unseenTicks = 0;
        this.retargetCountdown = RETARGET_SCAN_EVALUATIONS;
        this.mob.setTarget(this.pendingTarget);
    }

    @Override
    public void stop() {
        this.mob.setTarget(null);
        this.pendingTarget = null;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        // 保持可见性记忆滚动
        if (target == null || !target.isAlive()) {
            this.unseenTicks = 0;
            return;
        }
        this.unseenTicks = this.mob.getSensing().hasLineOfSight(target) ? 0 : this.unseenTicks + 1;
        // 运行中周期复扫：出现明显更近的合法目标时切换。
        // 原版 NearestAttackableTargetGoal 只在目标失效后重搜，锁死首个目标；
        // 这里每 20 tick 扫一次，新目标须比当前目标近 20% 以上才切换，避免等距抖动
        if (--this.retargetCountdown <= 0) {
            this.retargetCountdown = RETARGET_SCAN_EVALUATIONS;
            LivingEntity best = this.findBestTarget();
            if (best != null && best != target
                    && this.mob.distanceToSqr(best) < this.mob.distanceToSqr(target) * 0.8) {
                this.mob.setTarget(best);
                this.unseenTicks = 0;
            }
        }
    }

    /** 原版 TargetingConditions 语义：mustSee 时新候选必须直接可见 */
    private boolean canAttack(LivingEntity target) {
        if (target == null || !this.inRange(target) || !this.acceptsTarget(target)) {
            return false;
        }
        // 战斗队伍：同队候选一律不可选（友军保护优先于一切索敌）
        if (BrainTeams.isTeamFriendly(this.mob.asLiving(), target)) {
            return false;
        }
        if (this.mustSee) {
            // 新候选必须直接可见：视线记忆只服务于"当前目标"的丢失缓冲
            //（canContinueToUse），不解锁从未看见的隔墙目标——原目标暂时不可见时，
            // 凭残余记忆切换到隔墙候选是越权索敌
            return this.mob.getSensing().hasLineOfSight(target);
        }
        return true;
    }

    /** 距离复核（目标可能跑到搜索半径外） */
    private boolean inRange(LivingEntity target) {
        return this.mob.distanceToSqr(target) <= this.searchRange * this.searchRange;
    }

    /**
     * 类型化谓词复核（运行期目标可能是父类型引用）。
     * 和平难度排除玩家目标——对齐原版 {@code LivingEntity.canAttack} 的门禁
     * （1.21.11 字节码：目标为玩家且 difficulty==PEACEFUL → false，敌对生物
     * 和平难度不索敌玩家）；对怪物的目标不受影响（原版生物互斗与难度无关）。
     */
    @SuppressWarnings("unchecked")
    private boolean acceptsTarget(LivingEntity target) {
        // 中央自体/玩家门禁：任何模式都不得把自己当目标；玩家旁观/创造不参战。
        // 这是所有模式共享的底线，不改变“哪些脑主动攻击”的既有语义。
        if (target == this.mob.asLiving()) {
            return false;
        }
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        if (target instanceof Player && this.mob.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return this.targetType.isInstance(target) && this.selector.test((T) target);
    }
}
