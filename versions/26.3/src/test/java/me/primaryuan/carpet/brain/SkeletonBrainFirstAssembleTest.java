package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骷髅武器形态三态锁（P1-#1 回归防线）：
 * 全新会话空手/持剑挂脑 → 近战注册、远程未注册；持弓挂脑 → 远程注册、近战
 * 未注册；之后只在形态变化时移旧装新，无变化保持既有 Goal。变异验证：
 * 恢复 rangedEquipped 二值 → 空手首装配用例失败。
 */
class SkeletonBrainFirstAssembleTest {

    private BrainTestStubs.FakePlayer skeleton;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        // 各测试独立初始化（不共享桩状态，避免掩盖首次装配问题）
        skeleton = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubHeldItems(null, null); // 默认空手
    }

    private SkeletonBrain freshBrain() {
        SkeletonBrain brain = new SkeletonBrain(skeleton);
        try {
            var assemble = SkeletonBrain.class.getDeclaredMethod("assemble");
            assemble.setAccessible(true);
            assemble.invoke(brain);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return brain;
    }

    private boolean hasRanged(SkeletonBrain brain) {
        return brain.goalSelector.isRegistered((PlayerRangedAttackGoal)
                reflectField(brain, "rangedGoal"));
    }

    private boolean hasMelee(SkeletonBrain brain) {
        return brain.goalSelector.isRegistered((PlayerMeleeAttackGoal)
                reflectField(brain, "meleeGoal"));
    }

    private static Object reflectField(Object owner, String name) {
        return BrainTestStubs.readField(owner, SkeletonBrain.class, name);
    }

    @Test
    void bareHandedFirstAssembleInstallsMeleeOnly() {
        SkeletonBrain brain = freshBrain();
        assertTrue(hasMelee(brain), "空手挂脑：近战 Goal 已注册（首次装配完成）");
        assertFalse(hasRanged(brain), "空手挂脑：远程 Goal 未注册");
    }

    @Test
    void swordFirstAssembleInstallsMeleeOnly() {
        skeleton.stubHeldItems(new ItemStack(Items.IRON_SWORD), null);
        SkeletonBrain brain = freshBrain();
        assertTrue(hasMelee(brain), "持剑挂脑：近战 Goal 已注册");
        assertFalse(hasRanged(brain), "持剑挂脑：远程 Goal 未注册");
    }

    @Test
    void bowFirstAssembleInstallsRangedOnly() {
        skeleton.stubHeldItems(new ItemStack(Items.BOW), null);
        SkeletonBrain brain = freshBrain();
        assertTrue(hasRanged(brain), "持弓挂脑：远程 Goal 已注册");
        assertFalse(hasMelee(brain), "持弓挂脑：近战 Goal 未注册");
    }

    @Test
    void fullSwapCycleKeepsSingleForm() {
        // 空手 → 弓 → 剑 → 空手 → 副手弓
        SkeletonBrain brain = freshBrain();
        assertTrue(hasMelee(brain) && !hasRanged(brain));

        skeleton.stubHeldItems(new ItemStack(Items.BOW), null);
        brain.reassessWeaponGoal();
        assertTrue(hasRanged(brain) && !hasMelee(brain), "换弓：移近战装远程");

        skeleton.stubHeldItems(new ItemStack(Items.IRON_SWORD), null);
        brain.reassessWeaponGoal();
        assertTrue(hasMelee(brain) && !hasRanged(brain), "换剑：移远程装近战");

        skeleton.stubHeldItems(null, null);
        brain.reassessWeaponGoal();
        assertTrue(hasMelee(brain) && !hasRanged(brain), "空手：近战形态保持");

        skeleton.stubHeldItems(null, new ItemStack(Items.BOW));
        brain.reassessWeaponGoal();
        assertTrue(hasRanged(brain) && !hasMelee(brain), "副手持弓：远程形态");
    }

    @Test
    void noChangeDoesNotResetRunningGoal() {
        skeleton.stubHeldItems(new ItemStack(Items.BOW), null);
        SkeletonBrain brain = freshBrain();
        assertTrue(hasRanged(brain));

        // 记录远程 Goal 实例的运行状态痕迹（attackTime 冷却计，反射写读）：
        // 无变化时 reassess 不移除不重装（同一实例保留在注册表 = 未被重置）
        PlayerRangedAttackGoal ranged = (PlayerRangedAttackGoal) reflectField(brain, "rangedGoal");
        BrainTestStubs.putInt(ranged, PlayerRangedAttackGoal.class, "attackTime", 7);
        brain.reassessWeaponGoal();
        brain.reassessWeaponGoal();
        assertTrue(brain.goalSelector.isRegistered(ranged), "无变化：同一实例仍注册（未重装）");
        org.junit.jupiter.api.Assertions.assertEquals(7,
                (Integer) BrainTestStubs.readField(ranged, PlayerRangedAttackGoal.class, "attackTime"),
                "无变化：Goal 状态未被重置（攻击节奏保留）");
    }
}
