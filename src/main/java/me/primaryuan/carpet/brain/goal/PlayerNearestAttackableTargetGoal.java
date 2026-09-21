package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * 移植版"最近可攻击目标"选择目标（← 原版 {@code NearestAttackableTargetGoal}）。
 *
 * <p>这是各模式 targetSelector 的核心：按 {@code targetType} + {@code Predicate}
 * 在玩家周围搜最近的目标并写入 {@code getTarget/setTarget}。语义对齐原版：</p>
 * <ul>
 *   <li>{@code randomInterval}=10：每 tick 只有 1/10 概率真正重新搜索
 *       （原版的目标搜索节流，避免每 tick 全量扫描实体列表）；</li>
 *   <li>{@code mustSee}=true：需要视野；看不到的目标靠 {@code unseenTicks} 短暂记忆
 *       （原版 5 秒遗忘窗口，避免"转身即丢目标"的抖动）；</li>
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

    /** 可见性记忆：连续多少 tick 看不见才放弃 */
    private final int memoryTicks = 60;
    /** 运行中复扫周期（tick）：出现明显更近的合法目标时切换（原版锁定语义的增强） */
    private static final int RETARGET_SCAN_INTERVAL = 20;
    private int retargetCountdown = RETARGET_SCAN_INTERVAL;
    private int unseenTicks;
    /** 本周期搜索到的目标（等待 start 时写入） */
    private LivingEntity pendingTarget;

    public PlayerNearestAttackableTargetGoal(PryMob mob, Class<T> targetType, double searchRange,
                                             boolean mustSee, Predicate<? super T> predicate) {
        this(mob, targetType, searchRange, 10, mustSee, predicate);
    }

    public PlayerNearestAttackableTargetGoal(PryMob mob, Class<T> targetType, double searchRange,
                                             int randomInterval, boolean mustSee, Predicate<? super T> predicate) {
        this.mob = mob;
        this.targetType = targetType;
        this.searchRange = searchRange;
        this.randomInterval = randomInterval;
        this.mustSee = mustSee;
        this.selector = predicate;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false; // 原版节流：10 tick 才真正搜一次
        }
        LivingEntity best = this.findBestTarget();
        this.pendingTarget = best;
        return best != null && this.canAttack(best);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        // 范围/谓词复核（原版 TargetGoal 的 range 复检）
        if (!this.inRange(target) || !this.acceptsTarget(target)) {
            return false;
        }
        if (this.mustSee && !this.mob.getSensing().hasLineOfSight(target)
                && this.unseenTicks > this.memoryTicks) {
            return false; // 视野记忆窗口耗尽
        }
        return true;
    }

    @Override
    public void start() {
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
            this.retargetCountdown = RETARGET_SCAN_INTERVAL;
            LivingEntity best = this.findBestTarget();
            if (best != null && best != target
                    && this.mob.distanceToSqr(best) < this.mob.distanceToSqr(target) * 0.8) {
                this.mob.setTarget(best);
                this.unseenTicks = 0;
            }
        }
    }

    /** 在范围内找最近满足谓词的候选 */
    private LivingEntity findBestTarget() {
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

    /** 原版 TargetGoal.canAttack：视野门控（无视野用记忆窗口兜底） */
    private boolean canAttack(LivingEntity target) {
        if (target == null || !this.inRange(target) || !this.acceptsTarget(target)) {
            return false;
        }
        if (this.mustSee) {
            if (this.mob.getSensing().hasLineOfSight(target)) {
                this.unseenTicks = 0;
                return true;
            }
            return this.unseenTicks > 0; // 曾见过：短暂记忆期仍可锁定
        }
        return true;
    }

    /** 距离复核（目标可能跑到搜索半径外） */
    private boolean inRange(LivingEntity target) {
        return this.mob.distanceToSqr(target) <= this.searchRange * this.searchRange;
    }

    /** 类型化谓词复核（运行期目标可能是父类型引用） */
    @SuppressWarnings("unchecked")
    private boolean acceptsTarget(LivingEntity target) {
        return this.targetType.isInstance(target) && this.selector.test((T) target);
    }
}
