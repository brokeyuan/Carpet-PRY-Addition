package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骷髅索敌与武器形态分离锁（26.3 AbstractSkeleton.reassessWeaponGoal 对齐）：
 * 玩家索敌谓词不看武器（持剑/空手照常索敌，近战 Goal 接敌）；持弓装远程
 * Goal（同位 4）、卸弓换近战 MeleeAttackGoal(1.2, false)，任意时刻只装其一。
 * 变异验证：恢复"谓词要求持弓"→ 持剑索敌用例失败；删 reassess 的 removeGoal →
 * 双 Goal 并存用例失败。
 */
class SkeletonBrainWeaponSwapTest {

    private static BrainTestStubs.FakePlayer skeleton;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        skeleton = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
    }

    @Test
    void playerTargetingDoesNotRequireBow() {
        SkeletonBrain brain = new SkeletonBrain(skeleton);
        brain.assemble();

        BrainTestStubs.FakePlayer swordBearer = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(skeleton.level())
                .stubHeldItems(new ItemStack(Items.IRON_SWORD), null);
        assertTrue(brain.playerTargetPredicate().test(swordBearer),
                "持剑玩家满足索敌谓词（原版目标链不看武器）");

        BrainTestStubs.FakePlayer bareHand = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(skeleton.level())
                .stubHeldItems(null, null);
        assertTrue(brain.playerTargetPredicate().test(bareHand), "空手玩家满足索敌谓词");

        BrainTestStubs.FakePlayer bowBearer = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(skeleton.level())
                .stubHeldItems(new ItemStack(Items.BOW), null);
        assertTrue(brain.playerTargetPredicate().test(bowBearer), "持弓玩家满足索敌谓词");
    }

    @Test
    void weaponFormSwapsWithBow() {
        SkeletonBrain brain = new SkeletonBrain(skeleton);
        brain.assemble();
        PlayerRangedAttackGoal ranged = (PlayerRangedAttackGoal) BrainTestStubs.readField(
                brain, SkeletonBrain.class, "rangedGoal");
        PlayerMeleeAttackGoal melee = (PlayerMeleeAttackGoal) BrainTestStubs.readField(
                brain, SkeletonBrain.class, "meleeGoal");

        // 挂脑时持弓：远程形态
        skeleton.stubHeldItems(new ItemStack(Items.BOW), null);
        brain.reassessWeaponGoal();
        assertTrue(brain.goalSelector.isRegistered(ranged), "持弓 → 远程 Goal 装配");
        assertFalse(brain.goalSelector.isRegistered(melee), "近战 Goal 移除（原版同位互斥）");

        // 换剑：近战形态
        skeleton.stubHeldItems(new ItemStack(Items.IRON_SWORD), null);
        brain.reassessWeaponGoal();
        assertFalse(brain.goalSelector.isRegistered(ranged), "卸弓 → 远程 Goal 移除");
        assertTrue(brain.goalSelector.isRegistered(melee), "近战 MeleeAttackGoal(1.2,false) 装配");

        // 空手：近战形态维持
        skeleton.stubHeldItems(null, null);
        brain.reassessWeaponGoal();
        assertTrue(brain.goalSelector.isRegistered(melee), "空手保持近战形态");

        // 重新拿弓（副手）：远程形态恢复
        skeleton.stubHeldItems(null, new ItemStack(Items.BOW));
        brain.reassessWeaponGoal();
        assertTrue(brain.goalSelector.isRegistered(ranged), "副手持弓同样切远程（原版 getWeaponHoldingHand 双手）");
        assertFalse(brain.goalSelector.isRegistered(melee));
    }
}
