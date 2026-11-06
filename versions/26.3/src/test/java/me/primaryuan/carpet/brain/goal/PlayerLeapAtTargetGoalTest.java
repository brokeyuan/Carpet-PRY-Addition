package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 扑击生命周期锁（26.3/1.21.11 LeapAtTargetGoal 字节码对齐）：
 * 旗标 MOVE+JUMP（同时占用——腾空期间压制近战/跟随的移动）；canUse = 有目标、
 * distanceToSqr ∈ [4,16]（2~4 格）、在地面、nextInt(reducedTickDelay(5))=nextInt(3)；
 * canContinueToUse = !onGround（腾空期间持续占用，落地即结束交还）；
 * start 只写普通移动输入 + 原生 jumping（不得 setDeltaMovement）。
 */
class PlayerLeapAtTargetGoalTest {

    private static BrainTestStubs.FakePlayer wolf;
    private static BrainTestStubs.TargetMob target;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5))
                .stubOnGround(true)
                .stubDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        target = BrainTestStubs.TargetMob.create().stubWorld(wolf.level());
    }

    private static void placeTarget(double dx, double dz) {
        target.putPosition(wolf.position().add(dx, 0, dz));
    }

    @Test
    void flagsAreJumpAndMove() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        assertEquals(java.util.EnumSet.of(PlayerGoal.Flag.JUMP, PlayerGoal.Flag.MOVE),
                goal.getFlags(), "原版 LeapAtTargetGoal 旗标 JUMP+MOVE（旧实现缺 MOVE）");
    }

    @Test
    void distanceBoundsTwoToFourBlocks() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        wolf.stubTarget(target);

        placeTarget(1.0, 0.0); // 1 格 < 2：不扑
        assertTrue(neverLeaps(goal, 30), "1 格内不触发");

        placeTarget(5.0, 0.0); // 5 格 > 4：不扑
        assertTrue(neverLeaps(goal, 30), "4 格外不触发");

        placeTarget(3.0, 0.0); // 3 格（距离平方 9 ∈ [4,16]）：概率触发
        assertTrue(leapsWithin(goal, 60), "2~4 格内 60 次评估应至少触发一次（1/3 概率）");
    }

    @Test
    void airborneGroundAndTargetGates() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        wolf.stubTarget(null);
        placeTarget(3.0, 0.0);
        assertFalse(goal.canUse(), "无目标不扑");

        wolf.stubTarget(target);
        wolf.stubOnGround(false);
        assertTrue(neverLeaps(goal, 30), "不在地面不扑（原版 onGround 门）");
        wolf.stubOnGround(true);
    }

    @Test
    void airborneOccupiesUntilLanding() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        wolf.stubTarget(target);
        placeTarget(3.0, 0.0);
        assertTrue(forceCanUse(goal), "概率门内触发");
        goal.start();

        wolf.stubOnGround(false);
        assertTrue(goal.canContinueToUse(), "腾空期间持续占用（原版 canContinueToUse = !onGround）");
        wolf.stubOnGround(true);
        assertFalse(goal.canContinueToUse(), "落地即结束交还");
    }

    @Test
    void startUsesPlayerMovementWithoutBioMomentum() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        wolf.stubTarget(target);
        placeTarget(4.0, 0.0);
        wolf.stubDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        assertTrue(forceCanUse(goal), "概率门内触发");
        goal.start();

        net.minecraft.world.phys.Vec3 delta = wolf.getDeltaMovement();
        assertEquals(net.minecraft.world.phys.Vec3.ZERO, delta, "不得按生物公式写 deltaMovement");
        assertEquals(me.primaryuan.carpet.brain.PlayerMoveControl.Operation.MOVE_TO,
                wolf.getMoveControl().operation(), "普通移动输入已交给移动控制");
    }

    @Test
    void startDoesNotOverwriteExistingMomentum() {
        PlayerLeapAtTargetGoal goal = new PlayerLeapAtTargetGoal(wolf);
        wolf.stubTarget(target);
        placeTarget(0.0, 2.0);
        var existing = new net.minecraft.world.phys.Vec3(1.0, 0, 0);
        wolf.stubDeltaMovement(existing);
        assertTrue(forceCanUse(goal));
        goal.start();

        assertEquals(existing, wolf.getDeltaMovement(), "既有玩家动量不得被生物冲量覆盖");
        goal.stop();
        assertEquals(me.primaryuan.carpet.brain.PlayerMoveControl.Operation.WAIT,
                wolf.getMoveControl().operation(), "结束/落地归还移动控制");
    }

    /** 概率门内反复评估直到 canUse=true（1/3 概率，30 次全不中概率 ≈ 5e-6） */
    private static boolean forceCanUse(PlayerLeapAtTargetGoal goal) {
        return leapsWithin(goal, 30);
    }

    /** 目标几何下 N 次评估是否一次都不扑（距离/地面等前置门全关） */
    private static boolean neverLeaps(PlayerLeapAtTargetGoal goal, int tries) {
        return !leapsWithin(goal, tries);
    }

    /** 目标几何下 60 次评估内是否触发（P(1/3 概率 60 次全不中) ≈ 1e-11） */
    private static boolean leapsWithin(PlayerLeapAtTargetGoal goal, int tries) {
        for (int i = 0; i < tries; i++) {
            if (goal.canUse()) {
                return true;
            }
        }
        return false;
    }
}
