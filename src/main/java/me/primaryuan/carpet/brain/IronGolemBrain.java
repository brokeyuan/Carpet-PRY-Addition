package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;

/**
 * 铁傀儡模式脑（装配器）：守护与清怪。
 *
 * <p>两个攻击目标选择器（对应原版铁傀儡的 TargetSelector 差异）：</p>
 * <ul>
 *   <li>优先级 1 —— 敌对玩家（含假人）：处于敌对 AI 模式的假人
 *       （{@link BrainManager#isHostileFake}：zombie/skeleton/pillager/spider/enderman，
 *       即规范要求识别的 {@code isHostile()} 假人），以及攻击过本假人的玩家
 *       （{@code getLastHurtByMob()} 近期伤害来源，报复判定）；</li>
 *   <li>优先级 2 —— 视野内的敌对生物（{@link Monster} 基类），排除苦力怕
 *       （原版铁傀儡的克制：爆炸无益于守护）。</li>
 * </ul>
 * <p>两个目标互相用旗标互斥，同一时刻只锁定一个（优先报复攻击者）。</p>
 */
public class IronGolemBrain extends PlayerBrainController {

    public IronGolemBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "irongolem";
    }

    @Override
    protected void assemble() {
        // 敌对假人（isHostile 判定）+ 攻击过本假人的玩家（报复）——含假人大乱斗：
        // 僵尸假人会被铁傀儡假人追杀，而村民/狼假人不会
        this.targetSelector.addGoal(1, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, ServerPlayer.class, 16.0, 10, false,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && (BrainManager.isHostileFake(p) || this.player.getLastHurtByMob() == p)));
        // 清怪：视野内的敌对生物，排除苦力怕
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Monster.class, 16.0, 10, true,
                m -> m.isAlive() && !(m instanceof Creeper)));
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 空闲漫游（原版铁傀儡同样挂 RandomStrollGoal）：无怪可清时巡逻而非原地挂机
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}