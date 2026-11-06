package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * 移植版空闲注视目标（← 原版 {@code LookAtPlayerGoal}，装配 pri 同位）。
 *
 * <p>原版 canUse 以概率掷骰（{@code nextFloat() < probability}，默认 0.02，
 * 26.3 字节码同款）选中范围内最近实体注视 40~80 游戏刻（{@code
 * adjustedTickDelay(40+nextInt(40))}，非 rUE 每 2 tick 递减 1 次）。本移植版
 * 同构：掷骰在 canUse（恢复原版随机判定，无第二层节流），lookTime 在 tick
 * 递减、canContinueToUse 只判断。注视期间视线跟随，距离超出或丢失视线即
 * 结束；与漫步（MOVE）并行、被战斗 LOOK 目标抢占。</p>
 */
public class PlayerLookAtPlayerGoal extends PlayerGoal {

    /** 注视时长基准（tick）：原版 40 + nextInt(40)（非 rUE，adjustedTickDelay 折半） */
    private static final int LOOK_TIME = 40;

    private final PryMob mob;
    private final Class<? extends LivingEntity> lookAtType;
    private final float lookDistance;
    private LivingEntity lookAt;
    private int lookTime;

    public PlayerLookAtPlayerGoal(PryMob mob, Class<? extends LivingEntity> lookAtType, float lookDistance) {
        this(mob, lookAtType, lookDistance, 0.02F);
    }

    /** 完整构造：probability 透传（原版 DEFAULT_PROBABILITY 0.02） */
    public PlayerLookAtPlayerGoal(PryMob mob, Class<? extends LivingEntity> lookAtType, float lookDistance,
                                  float probability) {
        this.mob = mob;
        this.lookAtType = lookAtType;
        this.lookDistance = lookDistance;
        this.probability = probability;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版无冷却层（掷骰在 canUse 内），每 2 tick 评估一次
    }

    private final float probability;

    @Override
    public boolean canUse() {
        // 原版掷骰：nextFloat() < probability 才选人（rUE 与否不改变掷骰位置）
        if (this.mob.getRandom().nextFloat() >= this.probability) {
            return false;
        }
        LivingEntity best = null;
        double bestSq = this.lookDistance * this.lookDistance;
        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(this.lookAtType,
                this.mob.getBoundingBox().inflate(this.lookDistance, 3.0, this.lookDistance))) {
            if (candidate == this.mob.asLiving() || !candidate.isAlive() || candidate.isRemoved()) {
                continue;
            }
            double sq = this.mob.distanceToSqr(candidate);
            if (sq < bestSq) {
                bestSq = sq;
                best = candidate;
            }
        }
        this.lookAt = best;
        return best != null;
    }

    @Override
    public void start() {
        // 原版 start() = adjustedTickDelay(40 + nextInt(40))：非 rUE 每 2 tick 递减
        // 1 次 → 折半后实际时长仍为 40~80 游戏刻
        this.lookTime = this.adjustedTickDelay(LOOK_TIME + this.mob.getRandom().nextInt(LOOK_TIME));
    }

    @Override
    public boolean canContinueToUse() {
        return this.lookAt != null && this.lookAt.isAlive()
                && this.lookTime-- > 0
                && this.mob.distanceToSqr(this.lookAt) < (double) (this.lookDistance * this.lookDistance)
                && this.mob.getSensing().hasLineOfSight(this.lookAt);
    }

    @Override
    public void stop() {
        this.lookAt = null;
        this.lookTime = 0;
    }

    @Override
    public void tick() {
        if (this.lookAt != null) {
            this.mob.getLookControl().setLookAt(this.lookAt, 10.0F, 10.0F);
        }
    }
}
