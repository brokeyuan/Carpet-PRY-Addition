package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * 移植版日光逃生目标（← 原版 {@code FleeSunGoal}，26.3/1.21.11 字节码逐条对齐，
 * 骷髅基座 pri 3，速度 1.0）。
 *
 * <p>触发条件（canUse 逐条，原版同款）：无攻击目标、{@code isBrightOutside}、
 * <b>着火</b>、自身位置露天、头盔为空。动作 = 10 次随机尝试
 * {@code offset(nextInt(20)-10, nextInt(6)-3, nextInt(20)-10)}（x/z ±10、
 * y -3..+2，向下也找），接受条件 {@code !canSeeSky && 怪物走位价值 < 0}
 * （原版 Monster.getWalkTargetValue = -getPathfindingCostFromLightLevels，即
 * {@code 0.5 - 亮度魔法值}——字节码语义下"荫蔽且亮度 >12"的格才算躲避点，
 * 1.21 与 26.3 双版本一致，按字节码原样移植）。{@code canContinueToUse} =
 * {@code !navigation.isDone()}。</p>
 *
 * <p>与 RestrictSun 分工：本 Goal 是着火后的逃生决策；日常避日由
 * RestrictSun 的寻路开关承担。晒伤本体是身体侧 isOnFire 状态（玩家身体不会
 * 被日照点燃——原版怪物的 BURN_IN_DAYLIGHT 燃烧属身体差异，如实披露，不给
 * 假人强制点火），因此假人仅在受火焰/岩浆等真实伤害而着火时触发本逃生。</p>
 */
public class PlayerFleeSunGoal extends PlayerGoal {

    /** 候选尝试次数（原版 getHidePos 循环上界） */
    private static final int ATTEMPTS = 10;

    protected final PryMob mob;
    private BlockPos wantedPos;

    public PlayerFleeSunGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1); // 原版无内部节流：每 2 tick 评估一次
    }

    /** 原版 getHidePos：10 次随机尝试找"非露天且怪物走位价值 <0"的躲避点 */
    private boolean setWantedPos() {
        Level level = this.mob.level();
        BlockPos pos = this.mob.blockPosition();
        for (int i = 0; i < ATTEMPTS; i++) {
            BlockPos candidate = pos.offset(
                    this.mob.getRandom().nextInt(20) - 10,
                    this.mob.getRandom().nextInt(6) - 3,
                    this.mob.getRandom().nextInt(20) - 10);
            if (this.isSunAvoidCandidate(level, candidate)) {
                this.wantedPos = candidate;
                return true;
            }
        }
        return false;
    }

    /**
     * 原版 Monster.getWalkTargetValue = -(getPathfindingCostFromLightLevels)
     * = 0.5 - getLightLevelDependentMagicValue(pos)。
     * 魔法值 = lerp(维度环境光, x/(4-3x), 1)，x = 最大本地原始亮度/15（26.3 字节码）。
     */
    private static float monsterWalkTargetValue(Level level, BlockPos pos) {
        return monsterWalkTargetValue(level.getMaxLocalRawBrightness(pos), level.dimensionType().ambientLight());
    }

    /** 亮度（0~15）+ 维度环境光 → 怪物走位价值（负值 = 躲避点可接受）。纯函数供离线测试直测。 */
    static float monsterWalkTargetValue(int maxLight, float ambientLight) {
        float brightness = maxLight / 15.0F;
        float magic = Mth.lerp(ambientLight, brightness / (4.0F - 3.0F * brightness), 1.0F);
        return 0.5F - magic;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            return false; // 原版有目标不逃
        }
        if (!this.mob.levelIsBrightOutside() || !this.mob.asLiving().isOnFire()) {
            return false;
        }
        if (!this.isSkyExposed()) {
            return false;
        }
        if (!this.mob.asLiving().getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }
        return this.setWantedPos();
    }

    /** 自身位置是否露天（原版 canSeeSky(blockPosition)）。独立方法供离线测试覆写。 */
    protected boolean isSkyExposed() {
        return this.mob.level().canSeeSky(this.mob.blockPosition());
    }

    /** 候选点是否满足躲避条件（原版 !canSeeSky && 怪物走位价值 <0）。供离线测试覆写。 */
    protected boolean isSunAvoidCandidate(Level level, BlockPos candidate) {
        return !level.canSeeSky(candidate) && monsterWalkTargetValue(level, candidate) < 0.0F;
    }

    @Override
    public void start() {
        if (this.wantedPos != null) {
            this.mob.getNavigation().moveTo(
                    this.wantedPos.getX() + 0.5, this.wantedPos.getY(), this.wantedPos.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.wantedPos = null;
        this.mob.getNavigation().stop();
    }
}
