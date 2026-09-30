package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;

/**
 * 移植版史莱姆/岩浆怪跳行追击（← 原版 {@code CubeMobAttackGoal} +
 * {@code SlimeMoveControl} 的等效语义，26.3 字节码：攻击 Goal pri 2）。
 *
 * <p>与标准近战的差别：不做 A* 寻路——原版史莱姆本就无寻路、直线跳行。
 * 落地蓄力片刻后原地起跳，滞空期间朝目标方向持续前进（MOVE_TO 短脉冲，
 * 仍走玩家原生 zza/xxa + travel 物理），落地即停，节奏性逼近；
 * 贴近后按玩家原生攻击管线挥砍（伤害取自主手，非原版按体积定值）。</p>
 */
public class PlayerSlimeAttackGoal extends PlayerGoal {

    /** 落地蓄力时长（tick）：原版史莱姆落地小歇后再度起跳的节奏 */
    private static final int HOP_DELAY = 12;
    private static final int ATTACK_COOLDOWN = 20;

    private final PryMob mob;
    private int hopDelay;
    private int attackCooldown;

    public PlayerSlimeAttackGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(4);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.mob.getMoveControl().setStop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.hopDelay > 0) {
            this.hopDelay--;
        }
        double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        if (distSq <= this.mob.attackReachSq() && this.attackCooldown <= 0) {
            this.attackCooldown = ATTACK_COOLDOWN;
            this.mob.doHurtTarget(target);
        }
        if (this.mob.asPlayer().onGround()) {
            if (this.hopDelay <= 0) {
                // 起跳 + 朝目标的 MOVE_TO 短脉冲（滞空期间持续朝目标前进）
                this.mob.getMoveControl().setWantedPosition(
                        target.getX(), target.getY(), target.getZ(), 1.0);
                this.mob.asPlayer().jumpFromGround();
                this.hopDelay = HOP_DELAY;
            } else {
                this.mob.getMoveControl().setStop(); // 落地小歇：清输入防滑行
            }
        }
    }
}
