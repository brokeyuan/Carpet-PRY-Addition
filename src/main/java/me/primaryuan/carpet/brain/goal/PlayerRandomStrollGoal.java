package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.EnumSet;

/**
 * 移植版随机漫步目标（← 原版 {@code RandomStrollGoal}）。
 *
 * <p>语义：每 tick 有 1/120 概率（并受冷却门控）挑一个周围 6~10 格内可站立的
 * 随机点走过去；走到就停，等下一轮。原版实现依赖 {@code RandomPos} 工具类
 * （跨版本常漂移），这里用自包含的随机选点逻辑等价复刻：种群判断用的是
 * {@code isPathfindable(LAND)} + 下方碰撞支撑 + 2 格净高（与寻路器同口径）。</p>
 *
 * <p>玩家替身漫步还有个附加价值：假人闲着时会像村民一样在村里乱转，
 * 不再是一根呆立的木桩。</p>
 */
public class PlayerRandomStrollGoal extends PlayerGoal {

    private final PryMob mob;
    private final double speedModifier;
    /** 选点半径 */
    private static final int RADIUS = 10;

    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private boolean inProgress;

    public PlayerRandomStrollGoal(PryMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(60); // 平均 3 秒尝试选一个漫步点（原版 1/120 掷骰的等效节奏）
    }

    @Override
    public boolean canUse() {
        // 节流由 canStart() 的 interval 统一承担（原版是每 tick 评估 × 1/120 掷骰，
        // 等效平均 120 tick 一步；此处 60 tick 评估一次 + 稳定尝试，节奏一致且
        // 避免"两层节流相乘 = 平均 3 分钟才挪一步"的假死观感）
        BlockPos dest = this.findRandomWalkablePos();
        if (dest == null) {
            return false;
        }
        this.wantedX = dest.getX() + 0.5;
        this.wantedY = dest.getY();
        this.wantedZ = dest.getZ() + 0.5;
        return true;
    }

    @Override
    public void start() {
        this.inProgress = true;
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        return this.inProgress && !this.mob.getNavigation().isDone(); // 走到为止
    }

    @Override
    public void stop() {
        this.inProgress = false;
        this.mob.getNavigation().stop();
    }

    /** 随机选一个可站立点：随机半径/角度转直角偏移，撞不可站就重试（最多 50 次） */
    private BlockPos findRandomWalkablePos() {
        BlockPos around = this.mob.blockPosition();
        for (int i = 0; i < 50; i++) {
            int dx = this.mob.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
            int dz = this.mob.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
            if (dx * dx + dz * dz < 9) {
                continue; // 别在脚边转圈
            }
            BlockPos candidate = around.offset(dx, Mth.clamp(this.mob.getRandom().nextInt(3) - 1, -1, 1), dz);
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 与寻路器同口径的可站判定：本格可通行 + 脚下有支撑 + 头顶 2 格净高 */
    private boolean isWalkableStand(BlockPos pos) {
        return PlayerPathNavigation.isWalkableCell(this.mob.level(), pos);
    }
}