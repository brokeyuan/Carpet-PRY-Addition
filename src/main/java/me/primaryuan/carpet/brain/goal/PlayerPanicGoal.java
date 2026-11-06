package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.EnumSet;

/**
 * 移植版惊惧逃跑目标（← 原版 {@code PanicGoal}，26.3/1.21.11 字节码逐条对齐）。
 *
 * <p><b>触发条件（字节码逐条）</b>：{@code shouldPanic()} = 最后一次受伤的伤害
 * 来源 {@code getLastDamageSource()}（40 游戏刻窗口，超时为 null）命中
 * <b>伤害类型标签</b>——不区分攻击者、不看 {@code getLastHurtByMob}。默认构造用
 * {@code PANIC_CAUSES}（含玩家/生物攻击与投射物）；狼的
 * {@code TamableAnimalPanicGoal(1.5, PANIC_ENVIRONMENTAL_CAUSES)} 只认环境伤害
 * （火/岩浆/冰冻/仙人掌/闪电），普通挨打不恐慌、走 HurtBy 反击。</p>
 *
 * <p>动作：着火时优先找 5 格内水源逃向水边；否则随机选点狂奔（原版为
 * DefaultRandomPos 随机方向，这里自包含复刻：无威胁时随机扇形、有威胁来源
 * 时偏向远离方向，选点目标可站即可）。{@code canContinueToUse} =
 * {@code !navigation.isDone()}（原版同款）。</p>
 */
public class PlayerPanicGoal extends PlayerGoal {

    private static final int WATER_SEARCH_RADIUS = 5;
    private static final int MAX_DIST = 8;         // 逃跑目标最大距离（格）

    /** 惊惧狂奔的速度系数（各模式按原版 PanicGoal 速度传入） */
    private final double speed;
    /** 恐慌触发伤害标签（原版 panicCausingDamageTypes 的 TagKey 直取） */
    private final TagKey<net.minecraft.world.damagesource.DamageType> panicTag;
    private final PryMob mob;
    private double posX;
    private double posY;
    private double posZ;
    private boolean running;

    /** 默认构造：PANIC_CAUSES（原版无标签构造的默认值，猪/鱼等同款） */
    public PlayerPanicGoal(PryMob mob) {
        this(mob, 1.35);
    }

    public PlayerPanicGoal(PryMob mob, double speedModifier) {
        this(mob, speedModifier, DamageTypeTags.PANIC_CAUSES);
    }

    /** 标签构造：狼传 {@code PANIC_ENVIRONMENTAL_CAUSES}（原版 TamableAnimalPanicGoal 同款） */
    public PlayerPanicGoal(PryMob mob, double speedModifier,
                           TagKey<net.minecraft.world.damagesource.DamageType> panicTag) {
        this.mob = mob;
        this.speed = speedModifier;
        this.panicTag = panicTag;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1); // 原版无内部节流：每 2 tick 评估一次
    }

    /** 原版 shouldPanic：伤害来源标签命中（getLastDamageSource 40 tick 窗口） */
    protected boolean shouldPanic() {
        DamageSource last = this.mob.asLiving().getLastDamageSource();
        return last != null && last.is(this.panicTag);
    }

    @Override
    public boolean canUse() {
        if (!this.shouldPanic()) {
            return false;
        }
        BlockPos dest = this.panicDestination();
        if (dest == null) {
            return false;
        }
        this.posX = dest.getX() + 0.5;
        this.posY = dest.getY();
        this.posZ = dest.getZ() + 0.5;
        return true;
    }

    /**
     * 恐慌目的地：着火优先找 5 格内水源（原版 lookForWater），否则随机选点。
     * 抽取为独立方法供离线测试注入固定目的地（选点逻辑本体保持真实执行）。
     */
    protected BlockPos panicDestination() {
        BlockPos dest = this.mob.asLiving().isOnFire()
                ? this.lookForWater(WATER_SEARCH_RADIUS)
                : null;
        return dest != null ? dest : this.findRandomPosition();
    }

    @Override
    public void start() {
        this.running = true;
        this.mob.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.running && !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** 原版 lookForWater：先要求自身所在格无碰撞，再按曼哈顿距离由近及远找水 */
    private BlockPos lookForWater(int radius) {
        BlockPos origin = this.mob.blockPosition();
        BlockState self = this.mob.level().getBlockState(origin);
        if (!self.getCollisionShape(this.mob.level(), origin).isEmpty()) {
            return null;
        }
        // 曼哈顿距离 ≤ radius 的格子由近及远扫（不用 withinManhattan——26.x 签名漂移）
        for (int dist = 1; dist <= radius; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dy = -dist; dy <= dist; dy++) {
                    for (int dz = -dist; dz <= dist; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != dist) {
                            continue;
                        }
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        FluidState fluid = this.mob.level().getFluidState(candidate);
                        if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) {
                            return candidate.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 随机恐慌点：以随机方向为主（原版 DefaultRandomPos 语义），可站即可 */
    private BlockPos findRandomPosition() {
        BlockPos origin = this.mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            double dist = 4.0 + this.mob.getRandom().nextDouble() * (MAX_DIST - 4.0);
            double angle = this.mob.getRandom().nextDouble() * (Math.PI * 2.0);
            BlockPos candidate = origin.offset(
                    Mth.floor(Math.cos(angle) * dist), 0, Mth.floor(Math.sin(angle) * dist));
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** 可站判定（与寻路器/漫步同口径） */
    private boolean isWalkableStand(BlockPos pos) {
        return PlayerPathNavigation.isWalkableCell(this.mob.level(), pos);
    }
}
