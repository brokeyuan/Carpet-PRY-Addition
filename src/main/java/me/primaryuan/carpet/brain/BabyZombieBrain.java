package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

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
        // 追击目标（与成年僵尸同构，范围 16/戴僵尸头 8 = 减半）：
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 8.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 16.0, 10, false,
                v -> v.isAlive() && !v.isBaby()));
        this.targetSelector.addGoal(4, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 16.0, 10, true,
                g -> g.isAlive()));
        this.targetSelector.addGoal(5, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 16.0, 10, true,
                t -> t.isBaby()));
        // 行为：疾速近战（1.25 → 原生疾跑，小僵尸的"窜"）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.25, true));
        // 空闲漫游
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.25));
    }
}
