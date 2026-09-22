package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * 移植版远程拉弓攻击目标（← 原版 {@code RangedBowAttackGoal}，逐行复刻算法）。
 *
 * <p>行为与原版骷髅 1:1：锁定目标 → 远距离直线接近、进入射程后横向风筝
 * （strafing 走位，近身倒退）→ 拉弓蓄力（{@code startUsingItem}）→
 * 满弦（20 tick）后 {@code stopUsingItem} 放箭 → 冷却 20 tick 再来。</p>
 *
 * <p><b>零凭空造物关键</b>：箭矢不是代码"造"出来的——{@code PryMob}
 * 的 {@code performRangedAttack} 是空实现，真正射箭的是假人
 * {@code stopUsingItem} 触发的<b>原生 {@code BowItem.releaseUsing}</b>流程：
 * 扣除背包真实箭矢、飞行力学/暴击/音效/客户端拉弓动画全部免费且服务端权威。
 * 因此本类全程不出现任何 {@code new Arrow}。</p>
 *
 * <p>主手非弓时 {@code canUse} 恒 false（摘弓即熄火，戴回自动恢复）。</p>
 */
public class PlayerRangedAttackGoal extends PlayerGoal {

    private static final int BOW_CHARGE_TICKS = 20;   // 弓满弦蓄力 tick（对齐原版弓）
    private static final int CROSSBOW_CHARGE_TICKS = 25; // 弩上弦蓄力 tick（对齐原版弩）
    private static final int SHOOT_COOLDOWN = 20;   // 射击冷却 tick

    private final PryMob mob;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private final float maxAttackRadiusSqr;
    /** 认可的远程武器（弓/弩）：主手（或副手）持有才允许开弓 */
    private final Item weapon;
    /** 是否为弩（上弦完成由 isCharged 状态驱动射击；弓按蓄力 tick 驱动） */
    private final boolean crossbow;
    /** 满蓄力所需 tick */
    private final int chargeTime;

    private int attackTime = -1;    // 射击冷却倒计时（>0 时不许开弓）
    private int seeTime;            // 视线连续可见计数（负值为丢失累计）
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;
    /** 射击时是否左右走位：弓类生物（骷髅）会绕圈风筝；猪灵持弩"射箭时不左右移动" */
    private boolean strafingEnabled = true;
    /** 接近阶段的重算路径倒计时（tick）：A* 每次最多 4096 次迭代，
     *  每 tick 全量重算会把多假人服务器的 tick 拖垮（表现为全员卡顿） */
    private int pathRecalcCooldown;

    /** 弓构造（默认 20 tick 满弦） */
    public PlayerRangedAttackGoal(PryMob mob, double speedModifier, int attackIntervalMin, float attackRadius) {
        this(mob, speedModifier, attackIntervalMin, attackRadius, Items.BOW, BOW_CHARGE_TICKS);
    }

    /** 通用构造：掠夺者传 {@code Items.CROSSBOW} + 25 tick 上弦 */
    public PlayerRangedAttackGoal(PryMob mob, double speedModifier, int attackIntervalMin, float attackRadius,
                                  Item weapon, int chargeTime) {
        this(mob, speedModifier, attackIntervalMin, attackRadius, weapon, chargeTime, true);
    }

    /** 完整构造：strafing=false 时射击带内只前进/后退、不左右绕圈（对齐猪灵） */
    public PlayerRangedAttackGoal(PryMob mob, double speedModifier, int attackIntervalMin, float attackRadius,
                                  Item weapon, int chargeTime, boolean strafing) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.maxAttackRadiusSqr = attackRadius * attackRadius * 3.0F;
        this.weapon = weapon;
        this.crossbow = weapon == Items.CROSSBOW;
        this.chargeTime = chargeTime;
        this.strafingEnabled = strafing;
        this.attackTime = attackIntervalMin;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingWeapon();
    }

    /** 是否手持认可武器（原版 isHoldingBow：主手或副手均可） */
    protected boolean isHoldingWeapon() {
        return this.mob.isHolding(stack -> stack.is(this.weapon));
    }

    @Override
    public boolean canContinueToUse() {
        return (this.canUse() || !this.mob.getNavigation().isDone()) && this.isHoldingWeapon();
    }

    @Override
    public void start() {
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = -1;
        this.mob.stopUsingItem(); // 收弓：原生流程，箭不发射
        // 必须连同导航器一起停：风筝走位最后的 STRAFE 指令会永久粘滞在
        // 移动控制器里（其他 goal 的 stop 均经 nav.stop() 清理，唯此处曾漏），
        // 表现为"目标停止后假人仍朝固定方向蹭/原地冻结"
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean hasLineOfSight = this.mob.getSensing().hasLineOfSight(target);
        boolean sawTarget = this.seeTime > 0;
        if (hasLineOfSight != sawTarget) {
            this.seeTime = 0; // 视线状态翻转：重置连续计数
        }
        this.seeTime += hasLineOfSight ? 1 : -1;

        // 移动模式：已在射程内且盯着目标 → 停下来风筝；否则按 5 tick 节奏重算路径逼近。
        // strafingEnabled=false（猪灵持弩）时保留前后进退（太近后退拉开）、去掉左右绕圈
        if (distSq <= this.attackRadiusSqr && this.seeTime >= 20) {
            this.mob.getNavigation().stop();
            this.strafingTime++;
        } else {
            if (--this.pathRecalcCooldown <= 0) {
                this.mob.getNavigation().moveTo(target, this.speedModifier);
                this.pathRecalcCooldown = 5;
            }
            this.strafingTime = -1;
        }
        // 风筝方向变速（原版随机翻转，制造不那么死板的绕圈）
        if (this.strafingTime >= 20) {
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }
        if (this.strafingTime > -1) {
            // 风筝：太近后退、太远前进；左右绕圈仅弓类（猪灵持弩不走位）
            if (distSq > this.maxAttackRadiusSqr) {
                this.strafingBackwards = false;
            } else if (distSq < this.attackRadiusSqr) {
                this.strafingBackwards = true;
            }
            this.mob.getMoveControl().strafe(
                    this.strafingBackwards ? -0.5F : 0.5F,
                    this.strafingEnabled && this.strafingClockwise ? 0.5F : 0.0F);
            this.mob.lookAt(target, 30.0F, 30.0F); // 风筝时视线钉死目标
        } else {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        // 射击状态机：蓄力 → 满弦/上弦完成放箭 → 冷却 → 再开弓
        if (this.mob.isUsingItem()) {
            if (!hasLineOfSight && this.seeTime < -60) {
                this.mob.stopUsingItem(); // 跟丢目标：静默收弓重新瞄准（不发射）
            } else if (hasLineOfSight) {
                int charge = this.mob.getTicksUsingItem();
                // 弩在蓄满瞬间由原版 onUseTick 自动装填背包真箭（isCharged 翻 true），
                // 弓无此状态，按 elapsed 蓄力 tick 判定满弦
                boolean loaded = this.crossbow
                        ? CrossbowItem.isCharged(this.mob.getMainHandItem())
                        : charge >= this.chargeTime;
                if (loaded) {
                    // ★ 原生流程：releaseUsingItem 触发 ItemStack.releaseUsing →
                    //   BowItem.releaseUsing / CrossbowItem.releaseUsing，
                    //   按满蓄力力学射出（弩射出的正是 onUseTick 从背包装填的真箭，
                    //   零凭空造物）★
                    this.mob.releaseUsingItem();
                    this.mob.performRangedAttack(target, getPowerForTime(charge, this.chargeTime));
                    this.attackTime = SHOOT_COOLDOWN;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            ItemStack weapon = this.mob.getMainHandItem();
            if (this.crossbow && CrossbowItem.isCharged(weapon)) {
                // 已上弦但"使用中"状态被原版蓄满自动完成（completeUsingItem）收走：
                // 走原版 use() 扣扳机射击（等价真人右键击发已上弦的弩）
                weapon.getItem().use(this.mob.asPlayer().level(), this.mob.asPlayer(),
                        InteractionHand.MAIN_HAND);
                this.attackTime = SHOOT_COOLDOWN;
            } else if (weapon.is(this.weapon)
                    && !this.mob.asPlayer().getProjectile(weapon).isEmpty()) {
                this.mob.startUsingItem(InteractionHand.MAIN_HAND); // 拉弦/上弦
            } else {
                this.attackTime = 10; // 无弹药/武器不符：稍后再试，避免每 tick 空转空查
            }
        }
    }

    /** 蓄力进度 → 伤害系数（原版 getPowerForTime：0~1） */
    private static float getPowerForTime(int ticks, int chargeTime) {
        float f = (float) ticks / chargeTime;
        f = (f * f + f * 2.0F) / 3.0F; // 二次缓升，与原版一致
        return Mth.clamp(f, 0.0F, 1.0F);
    }
}