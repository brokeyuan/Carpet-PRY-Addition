package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
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
    /** 满蓄力所需 tick */
    private final int chargeTime;

    private int attackTime = -1;    // 射击冷却倒计时（>0 时不许开弓）
    private int seeTime;            // 视线连续可见计数（负值为丢失累计）
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    /** 弓构造（默认 20 tick 满弦） */
    public PlayerRangedAttackGoal(PryMob mob, double speedModifier, int attackIntervalMin, float attackRadius) {
        this(mob, speedModifier, attackIntervalMin, attackRadius, Items.BOW, BOW_CHARGE_TICKS);
    }

    /** 通用构造：掠夺者传 {@code Items.CROSSBOW} + 25 tick 上弦 */
    public PlayerRangedAttackGoal(PryMob mob, double speedModifier, int attackIntervalMin, float attackRadius,
                                  Item weapon, int chargeTime) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.maxAttackRadiusSqr = attackRadius * attackRadius * 3.0F;
        this.weapon = weapon;
        this.chargeTime = chargeTime;
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

        // 移动模式：已在射程内且盯着目标 → 停下来风筝；否则逼近
        if (distSq <= this.attackRadiusSqr && this.seeTime >= 20) {
            this.mob.getNavigation().stop();
            this.strafingTime++;
        } else {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
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
            // 风筝：太近后退、太远前进，侧移绕圈
            if (distSq > this.maxAttackRadiusSqr) {
                this.strafingBackwards = false;
            } else if (distSq < this.attackRadiusSqr) {
                this.strafingBackwards = true;
            }
            this.mob.getMoveControl().strafe(
                    this.strafingBackwards ? -0.5F : 0.5F,
                    this.strafingClockwise ? 0.5F : -0.5F);
            this.mob.lookAt(target, 30.0F, 30.0F); // 风筝时视线钉死目标
        } else {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        // 射击状态机：蓄力 → 满弦放箭 → 冷却 → 再开弓
        if (this.mob.isUsingItem()) {
            if (!hasLineOfSight && this.seeTime < -60) {
                this.mob.stopUsingItem(); // 跟丢目标：静默收弓重新瞄准（不发射）
            } else if (hasLineOfSight) {
                int charge = this.mob.getTicksUsingItem();
                if (charge >= this.chargeTime) {
                    // ★ 原生流程：releaseUsingItem 触发 ItemStack.releaseUsing →
                    //   BowItem/CrossbowItem.releaseUsing，扣除背包真实箭矢、
                    //   按满蓄力力学射出并广播生成包（零凭空造物）★
                    this.mob.releaseUsingItem();
                    this.mob.performRangedAttack(target, getPowerForTime(charge, this.chargeTime));
                    this.attackTime = SHOOT_COOLDOWN;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            ItemStack weapon = this.mob.getMainHandItem();
            if (weapon.is(this.weapon)
                    && !this.mob.asPlayer().getProjectile(weapon).isEmpty()) {
                this.mob.startUsingItem(InteractionHand.MAIN_HAND); // 拉弦/上弦
            } else {
                this.attackTime = 10; // 背包无箭：稍后再试（原生流程无箭不发射，别空转空查）
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