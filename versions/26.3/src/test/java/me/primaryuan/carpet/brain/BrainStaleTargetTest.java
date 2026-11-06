package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 陈旧攻击目标回收：外部直写目标（群体警报/狼仇恨同步等）死亡或跨维度后，
 * 无 TARGET 旗标 Goal 运行时由驱动链统一回收——消除"有脑无目标也不动"的粘滞态。
 */
class BrainStaleTargetTest {

    private static BrainTestStubs.FakePlayer self;
    private static BrainTestStubs.ScriptedController brain;
    private static Level otherWorld;

    @BeforeAll
    static void setUp() throws Exception {
        TestRegistries.bootstrap();
        self = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID());
        brain = new BrainTestStubs.ScriptedController(self);
        otherWorld = BrainTestStubs.stubWorld();
    }

    @Test
    void deadTargetClearedWhenNoTargetGoalRunning() {
        BrainTestStubs.TargetPlayer target = target();
        target.alive = false;
        self.setTarget(target);
        assertTrue(brain.clearStaleTarget(), "死亡目标应被回收");
        assertFalse(hasTarget(), "回收后目标字段为空");
    }

    @Test
    void removedTargetCleared() {
        BrainTestStubs.TargetPlayer target = target();
        target.alive = true;
        target.stubRemoved(Entity.RemovalReason.KILLED);
        self.setTarget(target);
        assertTrue(brain.clearStaleTarget(), "移除目标应被回收");
    }

    @Test
    void aliveTargetKept() {
        BrainTestStubs.TargetPlayer target = target();
        target.alive = true;
        self.setTarget(target);
        assertFalse(brain.clearStaleTarget(), "存活目标不得回收");
        assertTrue(hasTarget());
    }

    @Test
    void crossDimensionTargetCleared() {
        BrainTestStubs.TargetPlayer target = target();
        target.alive = true;
        target.stubLevel(otherWorld); // 目标在另一个世界：sameLevel 判定失败
        self.setTarget(target);
        assertTrue(brain.clearStaleTarget(), "跨维度目标应被回收");
    }

    @Test
    void runningTargetGoalSuppressesCleanup() {
        BrainTestStubs.TargetPlayer target = target();
        target.alive = false;
        self.setTarget(target);
        // 目标选择器里有 TARGET 旗标 Goal 在跑：目标生命周期归它管，兜底不越权
        BrainTestStubs.StubGoal targetGoal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.TARGET);
        targetGoal.canUseResult = true;
        self.getTargetSelector().addGoal(1, targetGoal);
        try {
            self.getTargetSelector().tick(true);
            assertFalse(brain.clearStaleTarget(), "TARGET 目标运行期间兜底不得清目标");
        } finally {
            self.getTargetSelector().removeAllGoals();
        }
    }

    private static BrainTestStubs.TargetPlayer target() {
        return BrainTestStubs.TargetPlayer.create();
    }

    private static boolean hasTarget() {
        return self.getTarget() != null;
    }
}
