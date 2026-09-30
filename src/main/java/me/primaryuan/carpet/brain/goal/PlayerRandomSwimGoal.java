package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

import java.util.EnumSet;

/**
 * 移植版水中随机游动目标（← 原版 {@code AbstractFish$FishSwimGoal}/
 * {@code RandomSwimmingGoal}，装配 pri 4）。
 *
 * <p>选点 = 周围 ±8 格内浸水的格，随后<b>直线游过去</b>——刻意不走寻路器：
 * 水中是开阔空间，直线可达；A* 的节点口径是"可站立格"（脚下需支撑），水层
 * 中间不成节点，且卡死重算节奏会把本就缓慢的水中加速度反复打断（表现为
 * 原地蠕动）。移动指令直接喂移动控制器，上浮/下潜由其游泳层翻译（跳跃
 * 输入走原版 {@code jumpInLiquid(WATER)} 上浮路径，下潜交给自然沉降）。
 * 撞到塘壁（水平碰撞）即放弃本轮换点，与原版鱼"贴壁游"的笨拙一致。</p>
 */
public class PlayerRandomSwimGoal extends PlayerGoal {

    /** 选点半径（格） */
    private static final int RADIUS = 8;
    /** 到达判定距离的平方（移动控制器同口径 0.6 格） */
    private static final double ARRIVE_SQ = 0.36;
    /** 单程时间盒（tick）：防两条平行判定互相卡死 */
    private static final int BUDGET = 100;

    private final PryMob mob;
    private final double speedModifier;
    private double wantX;
    private double wantY;
    private double wantZ;
    private boolean inProgress;
    private int budget;

    public PlayerRandomSwimGoal(PryMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(40); // 游游停停的鱼节奏（原版 RandomSwimmingGoal 的间歇感）
    }

    @Override
    public boolean canUse() {
        if (!this.mob.asLiving().isInWater()) {
            return false; // 离水交给扑腾 Goal
        }
        BlockPos dest = this.findRandomWaterPos();
        if (dest == null) {
            return false;
        }
        this.wantX = dest.getX() + 0.5;
        this.wantY = dest.getY();
        this.wantZ = dest.getZ() + 0.5;
        return true;
    }

    @Override
    public void start() {
        this.inProgress = true;
        this.budget = BUDGET;
        this.mob.getMoveControl().setWantedPosition(this.wantX, this.wantY, this.wantZ, this.speedModifier);
    }

    @Override
    public void tick() {
        // 周期性重发指令：目标点在移动控制器被其他指令清掉后保持方向新鲜
        this.mob.getMoveControl().setWantedPosition(this.wantX, this.wantY, this.wantZ, this.speedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.inProgress) {
            return false;
        }
        // 离水即中止（直线游不认水体边界，冲出水面落到岸上就交给扑腾 Goal，
        // 也防止沿着水体边缘把身体带出水面）
        if (!this.mob.asLiving().isInWater()) {
            return false;
        }
        if (this.mob.asPlayer().horizontalCollision) {
            return false; // 撞塘壁：放弃本轮，冷却后换点
        }
        if (--this.budget <= 0) {
            return false;
        }
        double dx = this.wantX - this.mob.getX();
        double dy = this.wantY - this.mob.getY();
        double dz = this.wantZ - this.mob.getZ();
        return dx * dx + dz * dz > ARRIVE_SQ || Math.abs(dy) > 1.0;
    }

    @Override
    public void stop() {
        this.inProgress = false;
        this.mob.getMoveControl().setStop();
    }

    /** 随机选一个浸水格（水面/中层/水底均可，直线可达即可） */
    private BlockPos findRandomWaterPos() {
        BlockPos around = this.mob.blockPosition();
        for (int i = 0; i < 50; i++) {
            int dx = this.mob.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
            int dz = this.mob.getRandom().nextInt(RADIUS * 2 + 1) - RADIUS;
            if (dx * dx + dz * dz < 9) {
                continue; // 别在原地打转
            }
            BlockPos candidate = around.offset(dx, this.mob.getRandom().nextInt(5) - 2, dz);
            if (this.isWaterCell(candidate) && this.isWaterCell(candidate.above())) {
                return candidate;
            }
        }
        return null;
    }

    /** 该格及其上一格是否都浸在水中（身体整体入水，游泳观感成立） */
    private boolean isWaterCell(BlockPos pos) {
        return this.mob.level().getFluidState(pos).is(FluidTags.WATER);
    }
}
