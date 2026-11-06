package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;


import java.util.EnumSet;

/**
 * 移植版随机张望目标（← 原版 {@code RandomLookAroundGoal}，装配 pri 同位）。
 *
 * <p>原版 canUse 以 2% 掷骰（{@code nextFloat() < 0.02f}，26.3 字节码同款）
 * 随机选一个水平方向注视 20~40 游戏刻（lookTime=20+nextInt(20)，rUE=true）；
 * 递减在 <b>tick()</b> 内、canContinueToUse 只判断 {@code lookTime >= 0}
 * （26.3 字节码逐条）。本移植版同构：掷骰在 canUse（恢复原版随机判定，
 * 无第二层节流——选择器每 2 tick 评估一次即是原版节奏）、递减在 tick。
 * 注视点 = 位置 + 方向 × 10 格（原版同款），限速转头；与漫步（MOVE）并行。</p>
 */
public class PlayerRandomLookAroundGoal extends PlayerGoal {

    /** 注视时长基准（游戏 tick）：原版 20 + nextInt(20)，rUE 每 tick 递减 */
    private static final int LOOK_TIME = 20;
    /** 注视点水平距离（格）：原版同款常量 */
    private static final double LOOK_DIST = 10.0;

    private final PryMob mob;
    private double wantX;
    private double wantZ;
    private int lookTime;

    public PlayerRandomLookAroundGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版无冷却层（掷骰在 canUse 内），每 2 tick 评估一次
    }

    /** 原版 RandomLookAroundGoal 覆写 requiresUpdateEveryTick：注视时长按游戏 tick 递减 */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        // 原版掷骰：nextFloat() < 0.02f 才选方向（rUE 目标每 tick canUse，
        // 选择器奇偶门下偶相评估——与原版节奏一致，无第二层节流）
        if (this.mob.getRandom().nextFloat() >= 0.02F) {
            return false;
        }
        // 全圆随机方向（原版 cos/sin of random 2π）
        double angle = this.mob.getRandom().nextDouble() * (Math.PI * 2.0);
        this.wantX = this.mob.getX() + Math.cos(angle) * LOOK_DIST;
        this.wantZ = this.mob.getZ() + Math.sin(angle) * LOOK_DIST;
        this.lookTime = LOOK_TIME + this.mob.getRandom().nextInt(LOOK_TIME);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.lookTime >= 0; // 原版同款：只判断，不递减
    }

    @Override
    public void tick() {
        this.lookTime--; // 原版递减位置：tick 首行（rUE → 每游戏刻 1 次）
        // 限速转头（原版 setLookAt(DDD) 走 LookControl 默认限速）
        this.mob.getLookControl().setLookAt(this.wantX, this.mob.getEyeY(), this.wantZ, 10.0F, 10.0F);
    }
}
