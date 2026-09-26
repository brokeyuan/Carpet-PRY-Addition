package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.Path;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * 移植版规避目标（← 原版 {@code AvoidEntityGoal}）。
 *
 * <p>语义：检测到视距内出现某类实体（如村民遇僵尸）时，朝远离它的方向
 * 逃跑并盯着它看；距离近（7 格内）加速（sprint 疾跑），远了恢复常速。
 * 原版依赖 {@code RandomPos} 选点，这里自包含复刻（反方向 + 随机扇形偏移）。
 * 仅规避存活实体（旧实现收下 Predicate 参数却从未存储，isAlive 过滤随之丢失）。</p>
 */
public class PlayerAvoidEntityGoal<T extends LivingEntity> extends PlayerGoal {

    private final PryMob mob;
    private final Class<T> avoidClass;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    private final float maxDist;   // 视野距离：该距离内出现目标就开始规避
    private final float tooClose;  // 该距离内加速（本实现取 7 格，原版 49 平方）

    private LivingEntity toAvoid;
    private Path path;

    public PlayerAvoidEntityGoal(PryMob mob, Class<T> avoidClass, float maxDist,
                                 double walkSpeedModifier, double sprintSpeedModifier) {
        this.mob = mob;
        this.avoidClass = avoidClass;
        this.maxDist = maxDist;
        this.tooClose = 7.0F;
        this.walkSpeedModifier = walkSpeedModifier;
        this.sprintSpeedModifier = sprintSpeedModifier;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(10);
    }

    @Override
    public boolean canUse() {
        double sx = this.mob.getX();
        double sy = this.mob.getEyePosition().y;
        double sz = this.mob.getZ();
        this.toAvoid = null;
        double bestSq = this.maxDist * this.maxDist;
        for (T candidate : this.mob.level().getEntitiesOfClass(this.avoidClass,
                this.mob.getBoundingBox().inflate(this.maxDist, 3.0, this.maxDist),
                e -> e.isAlive() && !e.isRemoved())) {
            double sq = candidate.distanceToSqr(sx, sy, sz);
            if (sq < bestSq) {
                bestSq = sq;
                this.toAvoid = candidate;
            }
        }
        if (this.toAvoid == null) {
            return false;
        }
        BlockPos flee = this.fleePoint(this.toAvoid);
        if (flee == null) {
            return false;
        }
        // 逃跑点必须比"当前位置→威胁"更远才算有效（否则拦在脸上没意义）
        if (this.toAvoid.distanceToSqr(flee.getX() + 0.5, flee.getY(), flee.getZ() + 0.5)
                < this.toAvoid.distanceToSqr(this.mob.getX(), this.mob.getY(), this.mob.getZ())) {
            return false;
        }
        this.path = this.mob.getNavigation().createPath(flee.getX() + 0.5, flee.getY(), flee.getZ() + 0.5, 0);
        return this.path != null;
    }

    @Override
    public void start() {
        this.mob.getNavigation().moveTo(this.path, this.walkSpeedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone(); // 逃到安全距离为止
    }

    @Override
    public void stop() {
        this.toAvoid = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.toAvoid != null) {
            this.mob.getLookControl().setLookAt(this.toAvoid, 30.0F, 30.0F); // 边逃边盯
            // 距离越近跑得越快（原版：小于 7 格切换疾跑速度）
            this.mob.getNavigation().setSpeedModifier(
                    this.mob.distanceToSqr(this.toAvoid) < this.tooClose * this.tooClose
                            ? this.sprintSpeedModifier : this.walkSpeedModifier);
        }
    }

    /** 朝远离威胁方向选逃跑点（最远 8 格），失败重试 40 次 */
    private BlockPos fleePoint(LivingEntity threat) {
        double awayX = this.mob.getX() - threat.getX();
        double awayZ = this.mob.getZ() - threat.getZ();
        double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (len < 1.0E-4) {
            awayX = 1.0;
            awayZ = 0.0;
            len = 1.0;
        }
        double ux = awayX / len;
        double uz = awayZ / len;
        BlockPos origin = this.mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            double dist = 4.0 + this.mob.getRandom().nextDouble() * 4.0;
            double angle = (this.mob.getRandom().nextDouble() - 0.5) * 1.2;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            BlockPos candidate = origin.offset(
                    Mth.floor((ux * cos - uz * sin) * dist), 0, Mth.floor((ux * sin + uz * cos) * dist));
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isWalkableStand(BlockPos pos) {
        return PlayerPathNavigation.isWalkableCell(this.mob.level(), pos);
    }
}