package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 诊断视图：四态区分（卸载/无目标/导航受阻/移动停滞）的字段面。
 * 命令侧组装与三语文案由资源完整性测试覆盖（diag_* 键三语齐全）。
 */
class BrainDiagnosticsTest {

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
    }

    @Test
    void noTargetState() {
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(fake());
        PlayerBrainController.Diagnostics diag = brain.diagnostics();
        assertNull(diag.targetName(), "无目标态 targetName 为 null");
        assertNull(diag.targetDistance());
        assertFalse(diag.navInProgress());
        assertFalse(diag.navStuck());
        assertEquals("WAIT", diag.moveOp(), "无移动指令时移动控制器为 WAIT");
        assertEquals(0, diag.consecutiveCrashes());
    }

    @Test
    void targetStateCarriesNameAndDistance() {
        BrainTestStubs.FakePlayer self = fake().stubBody(new Vec3(0, 64, 0));
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(self);
        BrainTestStubs.TargetPlayer target = BrainTestStubs.TargetPlayer.create()
                .stubBody(new Vec3(3, 64, 4));
        self.setTarget(target);

        PlayerBrainController.Diagnostics diag = brain.diagnostics();
        assertEquals("targetName", diag.targetName());
        assertEquals(5.0, diag.targetDistance(), 0.01, "距离 = sqrt(3²+4²)");
        assertFalse(diag.navInProgress(), "无路径时导航空闲");
    }

    @Test
    void crashCounterExposed() {
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(fake());
        brain.onTickCrashed();
        brain.onTickCrashed();
        assertEquals(2, brain.diagnostics().consecutiveCrashes(), "诊断暴露连续崩溃计数");
        brain.onTickHealthy();
        assertEquals(0, brain.diagnostics().consecutiveCrashes());
    }

    @Test
    void stuckFlagFollowsNavigation() {
        BrainTestStubs.FakePlayer self = fake();
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(self);
        assertFalse(brain.diagnostics().navStuck());
        // 直写卡死计数（isStuck 读 stuckTicks ≥ 20）
        BrainTestStubs.putInt(self.getNavigation(), PlayerPathNavigation.class, "stuckTicks", 20);
        assertTrue(brain.diagnostics().navStuck(), "stuckTicks 达阈值 → 诊断标为导航受阻");
    }

    private static BrainTestStubs.FakePlayer fake() {
        return BrainTestStubs.FakePlayer.createFake(UUID.randomUUID());
    }
}
