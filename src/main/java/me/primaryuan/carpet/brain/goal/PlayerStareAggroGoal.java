package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 末影人"被凝视激怒"目标（← 原版 {@code EnderMan#isStaredAt} 的逐行移植）。
 *
 * <p>原版末影人没有把这个检查做成 Goal，而是每 tick 在 {@code aiStep} 里扫描
 * 周围玩家；这里等价封装为 TARGET 旗标目标（进 targetSelector，节流 2 tick）：</p>
 * <ul>
 *   <li><b>凝视判定（原版算法 1:1）</b>：玩家视线单位向量 · （玩家 → 假人）单位向量
 *       &gt; {@code 1.0 - 0.025/距离} ——距离越近容差越宽（贴脸余光也算盯），且需
 *       双向可见（{@code hasLineOfSight}）；</li>
 *   <li><b>触发效果</b>：写入攻击目标（近战 Goal 接管扑击）并调用假人原生的
 *       {@code setSprinting(true)}——规范要求的"被盯就疾跑扑来"；</li>
 *   <li><b>仇恨不轻易消退</b>：目标存活且未远离就持续锁定（原版末影人一旦激怒
 *       不会因为移开视线而平静）。</li>
 * </ul>
 */
public class PlayerStareAggroGoal extends PlayerGoal {

    /** 凝视扫描半径（格，对齐末影人 follow_range 量级） */
    private static final double RANGE = 24.0;

    private final PryMob mob;
    private LivingEntity pendingTarget;

    public PlayerStareAggroGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.TARGET));
        this.setInterval(2); // 凝视检测要灵敏（原版每 tick 扫，这里 2 tick 节流）
    }

    @Override
    public boolean canUse() {
        for (Player looker : this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(RANGE, 4.0, RANGE))) {
            if (looker == this.mob.asPlayer() || !looker.isAlive()
                    || looker.isSpectator() || looker.isCreative()) {
                continue;
            }
            if (this.isStaredAt(looker)) {
                this.pendingTarget = looker;
                return true;
            }
        }
        return false;
    }

    @Override
    public void start() {
        // 规范要求：被凝视触发时调用假人原生 setSprinting(true)（疾跑扑击的姿态）
        this.mob.setTarget(this.pendingTarget);
        this.mob.setSprinting(true);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        // 原版末影人激怒后不因移开视线而平静：只按存活/距离维持仇恨
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) <= RANGE * RANGE;
    }

    @Override
    public void stop() {
        // 目标脱离索敌范围/死亡：清空攻击目标，近战 Goal 随之收手，
        // 行为链回落到漫游/反击——避免"人在视野外仍持有幽灵目标"
        this.mob.setTarget(null);
        this.pendingTarget = null;
    }

    /** 原版 {@code EnderMan#isStaredAt} 的逐行移植（点积阈值随距离放宽） */
    private boolean isStaredAt(Player looker) {
        Vec3 view = looker.getViewVector(1.0F).normalize();
        Vec3 toMe = new Vec3(
                this.mob.getX() - looker.getX(),
                this.mob.getEyeY() - looker.getEyeY(),
                this.mob.getZ() - looker.getZ());
        double length = toMe.length();
        toMe = toMe.normalize();
        double dot = view.dot(toMe);
        return dot > 1.0D - 0.025D / length
                && this.mob.getSensing().hasLineOfSight(looker);
    }
}
