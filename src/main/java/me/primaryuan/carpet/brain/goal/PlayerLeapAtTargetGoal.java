package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * 移植版扑击目标（← 原版 {@code LeapAtTargetGoal}，26.3/1.21.11 字节码逐条对齐，
 * 蜘蛛 pri 3 / 狼 pri 4）。
 *
 * <p>生命周期（原版同款）：{@code canUse} 逐条——有攻击目标、距离 2~4 格
 * （distanceToSqr ∈ [4, 16]）、在地面、{@code nextInt(reducedTickDelay(5))}=
 * {@code nextInt(3)} 掷骰通过；{@code canContinueToUse} = {@code !onGround()}——
 * 腾空期间持续占用（MOVE+JUMP 旗标同时持有，压过近战/跟随的移动），落地即
 * 结束交还。</p>
 *
 * <p><b>身体边界（用户红线）</b>：删除原版生物公式 {@code setDeltaMovement}
 * 路线。扑击 = 面向目标设置普通移动输入 + 原生 {@code jumping} 输入（玩家
 * 自己的起跳物理）；不赋予生物动量、不改变碰撞箱。</p>
 */
public class PlayerLeapAtTargetGoal extends PlayerGoal {

    /** 最小/最大触发距离平方（原版 {@code distanceToSqr} ∈ [4, 16]） */
    private static final double MIN_DIST_SQ = 4.0;
    private static final double MAX_DIST_SQ = 16.0;
    /** 概率分母：原版 nextInt(reducedTickDelay(5)) = nextInt(3)（每 2 tick 评估一次） */
    private static final int PROBABILITY = 3;

    private final PryMob mob;
    private LivingEntity target;

    public PlayerLeapAtTargetGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.JUMP, PlayerGoal.Flag.MOVE));
        this.setInterval(1); // 原版每 2 tick 评估（掷骰在 canUse 内）
    }

    @Override
    public boolean canUse() {
        // 原版 hasControllingPassenger 门：假人不会被骑乘控制，省略
        this.target = this.mob.getTarget();
        if (this.target == null) {
            return false;
        }
        double distSq = this.mob.distanceToSqr(this.target);
        if (distSq < MIN_DIST_SQ || distSq > MAX_DIST_SQ) {
            return false;
        }
        if (!this.mob.asPlayer().onGround()) {
            return false;
        }
        return this.mob.getRandom().nextInt(PROBABILITY) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.asPlayer().onGround(); // 腾空期间持续占用，落地即结束
    }

    @Override
    public void start() {
        LivingEntity target = this.target;
        if (target == null) {
            return;
        }
        // 普通玩家移动：朝目标方向落移动输入；跳跃只置原生 jumping，由玩家
        // 自己的 aiStep 起跳。绝不 setDeltaMovement / 不写坐标。
        this.mob.getMoveControl().setWantedPosition(
                target.getX(), target.getY(), target.getZ(), 1.0);
        this.mob.getMoveControl().requestJump();
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    @Override
    public void stop() {
        this.target = null;
        // 落地/被打断后把移动控制交还给近战/跟随目标
        this.mob.getMoveControl().setStop();
    }
}
