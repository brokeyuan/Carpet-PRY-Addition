package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;

/**
 * 移植版离水扑腾目标（← 原版 {@code AbstractFish.aiStep} 的离水分支，26.3
 * 字节码：{@code !isInWater && onGround && verticalCollision} → 竖直 +0.4 起跳 +
 * 横向 ±0.05 抖动 + 随机转向 + 扑腾音效）。
 *
 * <p>原版把扑腾写在 aiStep（不经 Goal 调度，恐慌中同样扑腾）；移植版是 Goal，
 * 以最高优先级表达"离水时扑腾压过一切移动"——被冲上岸的鱼只会原地扑腾，
 * 不会像陆地生物一样迈步逃跑（原版水生寻路在陆地算不出路径，行为一致）。
 * 起跳用玩家原生 {@code jumpFromGround}（+0.42，对应原版 +0.4），前冲由
 * MOVE_TO 短脉冲提供（仍走玩家原生 zza/travel 物理），零 setDeltaMovement。</p>
 */
public class PlayerFlopGoal extends PlayerGoal {

    /** 每次扑腾的转向漂移（度）：原版横向随机抖动的等价表达 */
    private static final float DRIFT_DEGREES = 30.0F;

    private final PryMob mob;

    public PlayerFlopGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(4);
    }

    @Override
    public boolean canUse() {
        return !this.mob.asLiving().isInWater() && this.mob.asPlayer().onGround();
    }

    /** 单次扑跳即止：滞空期间维持 MOVE 旗标防移动指令被兜底清掉，落地后靠冷却接续 */
    @Override
    public boolean canContinueToUse() {
        return !this.mob.asPlayer().onGround();
    }

    @Override
    public void start() {
        // 随机转向 + 原生起跳 + 朝新方向的 MOVE_TO 短脉冲（空中微幅前冲）
        float drift = (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * DRIFT_DEGREES;
        float yaw = this.mob.asPlayer().getYRot() + drift;
        this.mob.asPlayer().setYRot(yaw);
        this.mob.asPlayer().jumpFromGround();
        double rad = Math.toRadians(yaw);
        this.mob.getMoveControl().setWantedPosition(
                this.mob.getX() - Math.sin(rad) * 1.0,
                this.mob.getY(),
                this.mob.getZ() + Math.cos(rad) * 1.0,
                0.3);
    }

    @Override
    public void stop() {
        this.mob.getMoveControl().setStop();
    }
}
