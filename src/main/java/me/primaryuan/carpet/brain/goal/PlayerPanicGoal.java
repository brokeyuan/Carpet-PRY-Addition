package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * 移植版惊惧逃跑目标（← 原版 {@code PanicGoal}）。
 *
 * <p>语义：被攻击（{@code getLastHurtByMob} 非空）或着火时，朝远离威胁的方向
 * 狂奔（快速移动系数）。原版用 {@code RandomPos} 选逃跑点，这里自包含复刻：
 * 沿"威胁 → 玩家"反方向做随机扇形偏移选点，选不到可站点就重试，
 * 保证一定朝远离的方向逃而不是无脑乱撞。</p>
 *
 * <p>村民模式在半血被僵尸逼近时触发，是"遇敌反向逃跑"的落地实现。</p>
 */
public class PlayerPanicGoal extends PlayerGoal {

    private static final double SPEED = 1.35;      // 惊惧狂奔的速度系数（快于行走）
    private static final int MAX_DIST = 8;         // 逃跑目标最大距离（格）

    private final PryMob mob;
    private double posX;
    private double posY;
    private double posZ;
    private boolean running;

    public PlayerPanicGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(2); // 惊惧状态 2 tick 重评一次（反应要快，但别每 tick 全量扫描）
    }

    @Override
    public boolean canUse() {
        LivingEntity threat = this.mob.getLastHurtByMob();
        if (threat == null && !this.mob.asLiving().isOnFire()) {
            return false;
        }
        if (threat != null && !threat.isAlive()) {
            threat = null;
        }
        BlockPos dest = threat != null ? this.fleeFrom(threat) : this.fleeFromFire();
        if (dest == null) {
            return false;
        }
        this.posX = dest.getX() + 0.5;
        this.posY = dest.getY();
        this.posZ = dest.getZ() + 0.5;
        return true;
    }

    @Override
    public void start() {
        this.running = true;
        this.mob.getNavigation().moveTo(this.posX, this.posY, this.posZ, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        return this.running && !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.running = false;
        this.mob.getNavigation().stop();
    }

    /** 朝远离威胁的方向选点（最大 8 格），失败重试 40 次 */
    private BlockPos fleeFrom(LivingEntity threat) {
        double awayX = this.mob.getX() - threat.getX();
        double awayZ = this.mob.getZ() - threat.getZ();
        double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (len < 1.0E-4) {
            awayX = 1.0; // 贴脸时给个默认方向
            awayZ = 0.0;
            len = 1.0;
        }
        double ux = awayX / len;
        double uz = awayZ / len;
        BlockPos origin = this.mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            double dist = 4.0 + this.mob.getRandom().nextDouble() * (MAX_DIST - 4.0);
            double angle = (this.mob.getRandom().nextDouble() - 0.5) * 1.2; // 方向 ±0.6 rad 扇形
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double dirX = ux * cos - uz * sin;
            double dirZ = ux * sin + uz * cos;
            BlockPos candidate = origin.offset(Mth.floor(dirX * dist), 0, Mth.floor(dirZ * dist));
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 着火（无威胁者）：随机乱跑找水/空地，选不到就硬跑一步 */
    private BlockPos fleeFromFire() {
        BlockPos origin = this.mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            BlockPos candidate = origin.offset(
                    this.mob.getRandom().nextInt(7) - 3, 0, this.mob.getRandom().nextInt(7) - 3);
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return origin.offset(2, 0, 2); // 兜底：怎么也得动一下
    }

    /** 可站判定（与寻路器/漫步同口径） */
    private boolean isWalkableStand(BlockPos pos) {
        return PlayerPathNavigation.isWalkableCell(this.mob.level(), pos);
    }
}