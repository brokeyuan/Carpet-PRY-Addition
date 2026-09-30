package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;

/**
 * 移植版史莱姆/岩浆怪空闲跳行（← 原版 {@code SlimeRandomDirectionGoal} +
 * {@code SlimeKeepOnJumpingGoal} 的等效语义）。
 *
 * <p>原版史莱姆 idle 时永不静止：落地即再起跳、方向随机漂移。这里单跳即止
 * （{@code canContinueToUse} 恒 false），靠短冷却节奏接续，地面判定防滞空起跳。</p>
 */
public class PlayerSlimeWanderGoal extends PlayerGoal {

    private final PryMob mob;

    public PlayerSlimeWanderGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(2);
    }

    @Override
    public boolean canUse() {
        return this.mob.asPlayer().onGround();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        // 随机小幅转向（原版 RandomDirection 的漂移节奏）后原地起跳
        float drift = (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 30.0F;
        this.mob.asPlayer().setYRot(this.mob.asPlayer().getYRot() + drift);
        this.mob.asPlayer().jumpFromGround();
    }
}
