package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.Path;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 移植版近战攻击目标（← 原版 {@code MeleeAttackGoal}，逐行复刻算法）。
 *
 * <p>语义与原版 1:1：锁定攻击目标 → 视线内且按节奏重算路径逼近 →
 * 进入玩家原生近战距离后按攻击冷却挥砍。差异只有三处：</p>
 * <ul>
 *   <li>{@code mob} 字段换成 {@link PryMob}（玩家假面），寻路/视线/转向
 *       走注入的控制器；</li>
 *   <li>{@code doHurtTarget} 由 {@code PryMob} 转写为玩家原生
 *       {@code Player#attack}（伤害/暴击/击退/扫击/附魔/耐久全走原版玩家战斗）；</li>
 *   <li>攻击距离改用玩家原生 {@code entity_interaction_range}（默认 3 格），
 *       而非常用 1.2 格左右的生物贴身距离——毕竟这是"拿武器的人"。</li>
 * </ul>
 */
public class PlayerMeleeAttackGoal extends PlayerGoal {

    private final PryMob mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;

    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;

    /** 追击速度系数：1.0 为正常行走（僵尸/铁傀儡的出场速度） */
    public PlayerMeleeAttackGoal(PryMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return false;
        }
        if (!target.isAlive()) {
            return false;
        }
        // 先试算一条路；算不出来但目标已在攻击距离内则仍然可以打
        this.path = this.mob.getNavigation().createPath(target, 0);
        if (this.path != null) {
            return true;
        }
        return this.getAttackReachSqr(target) >= this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (!this.followingTargetEvenIfNotSeen && !this.mob.getSensing().hasLineOfSight(target)) {
            return false; // 跟丢视线：放弃追击（僵尸同款"看不见就收手"）
        }
        if (!this.mob.isWithinRestriction(target.blockPosition())) {
            return false;
        }
        return !(target instanceof Player player) || (!player.isSpectator() && !player.isCreative());
    }

    @Override
    public void start() {
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.mob.getTarget();
        if (target != null && !this.mob.getSensing().hasLineOfSight(target)) {
            this.mob.setTarget(null); // 目标完全看不见了：清目标，回待机
        }
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        // 眼睛始终盯住目标（限速 30°/tick）
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        // 距离 = 双方边界盒外沿间距的平方（原版口径，避免贴脸时仍"需要往中心挤"）
        double distSq = Math.max(0.0, this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ())
                - this.mob.getBbWidth() * this.mob.getBbWidth());
        boolean targetMoved = this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                || this.mob.getRandom().nextFloat() < 0.05F;
        if (this.ticksUntilNextPathRecalculation <= 0
                && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(target))
                && targetMoved) {
            this.pathedTargetX = target.getX();
            this.pathedTargetY = target.getY();
            this.pathedTargetZ = target.getZ();
            this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7); // 原版 4~10 tick 重算节奏
            if (distSq > 1024.0) {
                this.ticksUntilNextPathRecalculation += 10;
            } else if (distSq > 256.0) {
                this.ticksUntilNextPathRecalculation += 5;
            }
            if (!this.mob.getNavigation().moveTo(target, this.speedModifier)) {
                this.ticksUntilNextPathRecalculation += 15; // 算不出路：暂缓重试
            }
        }
        this.checkAndPerformAttack(target, distSq);
    }

    /** 距离足够且攻击冷却结束 → 挥砍（转写为玩家原生 attack） */
    protected void checkAndPerformAttack(LivingEntity target, double distSq) {
        if (distSq <= this.getAttackReachSqr(target) && this.ticksUntilNextAttack <= 0) {
            this.resetAttackCooldown();
            this.mob.doHurtTarget(target);
        }
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = 20; // 攻击冷却 1 秒，与原版一致
    }

    /** 攻击距离判定 = 玩家原生交互距离的平方（entity_interaction_range） */
    protected double getAttackReachSqr(LivingEntity target) {
        return this.mob.attackReachSq();
    }
}