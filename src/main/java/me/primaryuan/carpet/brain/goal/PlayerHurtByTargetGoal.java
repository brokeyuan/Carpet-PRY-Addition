package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 移植版"被打反击"目标（← 原版 {@code HurtByTargetGoal} 的最小语义）。
 *
 * <p>谁打了假人，假人就立刻锁定谁——补上这一环之前，僵尸/末影人等模式在
 * "丢失远距离目标"后会站在原地挨打（用户实测"被打死都不动"）。原版所有
 * 战斗生物的 targetSelector 里它都挂在 {@code NearestAttackableTargetGoal}
 * 之前（优先级 1），这里保持同样的装配顺序。</p>
 *
 * <ul>
 *   <li>反击对象 = {@code getLastHurtByMob()}（近期伤害来源），须存活、同维度、
 *       非自己；攻击者为真人玩家时沿用全局攻击可见性（旁观/创造不可选）；</li>
 *   <li>锁定持续到对方死亡（原版仇恨语义），期间 TARGET 旗标互斥压制
 *       常规索敌目标；</li>
 *   <li>可传入豁免对象（狼模式用它豁免主人——主人误伤不还手，与原版
 *       驯服生物的 {@code HurtByTargetGoal} 行为一致）。</li>
 * </ul>
 */
public class PlayerHurtByTargetGoal extends PlayerGoal {

    private final PryMob mob;
    /** 豁免对象（狼模式 = 主人 UUID；可为 null） */
    private final UUID ignored;
    private LivingEntity pendingTarget;

    public PlayerHurtByTargetGoal(PryMob mob, UUID ignored) {
        this.mob = mob;
        this.ignored = ignored;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.TARGET));
        this.setInterval(2); // 被打要第一时间还手（2 tick 节流）
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()
                || attacker == this.mob.asLiving()
                || attacker.level() != this.mob.level()
                || (this.ignored != null && attacker.getUUID().equals(this.ignored))) {
            return false;
        }
        if (attacker instanceof Player p && (p.isSpectator() || p.isCreative())) {
            return false;
        }
        this.pendingTarget = attacker;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.pendingTarget);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        // 追击上限 48 格（原版 HurtByTargetGoal 无距离上限会追到世界边缘），
        // 攻击者跑出范围即清目标，交还给常规索敌/漫游
        return target != null && target.isAlive()
                && target.level() == this.mob.level()
                && this.mob.distanceToSqr(target) <= 48.0 * 48.0;
    }

    @Override
    public void stop() {
        this.mob.setTarget(null);
        this.pendingTarget = null;
    }
}
