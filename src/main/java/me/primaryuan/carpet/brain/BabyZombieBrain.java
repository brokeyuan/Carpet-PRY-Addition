package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 小僵尸模式脑（装配器）：比僵尸更快更急躁的近战追击。
 *
 * <p>对齐原版幼年僵尸的战斗特征：移动速度加成（原版 baby zombie 速度 ~1.15×
 * 成年僵尸，取 1.25 匹配玩家体感的"窜得飞快"）、搜索范围略小（视觉半径按
 * 小个头缩短）。受身体锁定红线约束，假人不会真的变小——"小"体现在行为节奏：
 * 更快的追击、更急的索敌评估。其余与 {@link ZombieBrain} 一致：</p>
 * <ul>
 *   <li>被击反击（HurtBy，优先级 1）；</li>
 *   <li>锁定最近玩家（优先级 2）；</li>
 *   <li>近战挥砍（原生 attack，优先级 1 行为）；</li>
 *   <li>空闲漫步（优先级 2 行为）。</li>
 * </ul>
 */
public class BabyZombieBrain extends PlayerBrainController {

    public BabyZombieBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "babyzombie";
    }

    @Override
    protected void assemble() {
        // 被打反击：小僵尸被打同样火速还手
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标：最近的存活玩家（范围略小于成年僵尸）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // 行为：疾速近战（1.25 → 原生疾跑，小僵尸的"窜"）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.25, true));
        // 空闲漫游
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.25));
    }
}
