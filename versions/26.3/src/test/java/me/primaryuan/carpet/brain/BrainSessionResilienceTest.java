package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脑子会话韧性：tick 崩溃不卸脑不清 keep（瞬时竞态），连续崩溃达阈值才卸脑
 * （确定性故障的明确退出路径）。onPlayerTick 的 catch 块只是对
 * {@code handleTickCrash + detach} 的三行转接（instanceof 门挡住测试桩，
 * 由 boot check 验证接线），断言落在逻辑本体上。
 */
class BrainSessionResilienceTest {

    private static final int THRESHOLD = PlayerBrainController.MAX_CONSECUTIVE_CRASHES;

    private static BrainTestStubs.FakePlayer player;
    private static BrainTestStubs.ScriptedController brain;

    @BeforeAll
    static void setUp() throws Exception {
        TestRegistries.bootstrap();
        player = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        brain = new BrainTestStubs.ScriptedController(player);
    }

    @Test
    void transientCrashKeepsSession() {
        brain.tickAction = () -> {
            throw new IllegalStateException("boom");
        };
        // 保活路径：handleTickCrash 返回 true，调用方不 detach
        assertTrue(BrainManager.handleTickCrash(brain, new IllegalStateException("boom")),
                "单次崩溃应保活会话");
        assertEquals(1, brain.consecutiveCrashes());

        brain.onTickHealthy();
        assertEquals(0, brain.consecutiveCrashes(), "正常 tick 后崩溃计数归零");
    }

    @Test
    void persistentCrashSignalsDetach() {
        BrainTestStubs.ScriptedController crashing = new BrainTestStubs.ScriptedController(player);
        IllegalStateException boom = new IllegalStateException("boom");
        for (int i = 0; i < THRESHOLD - 1; i++) {
            assertTrue(BrainManager.handleTickCrash(crashing, boom),
                    "阈值前应保活（第 " + (i + 1) + " 次）");
        }
        assertFalse(BrainManager.handleTickCrash(crashing, boom), "达阈值应信号卸脑");
    }

    @Test
    void detachOnThresholdClearsKeep() throws Exception {
        UUID fakeId = UUID.randomUUID();
        BrainTestStubs.FakePlayer victim = BrainTestStubs.FakePlayer.createFake(fakeId)
                .stubWorld(BrainTestStubs.stubWorld());
        BrainTestStubs.ScriptedController victimBrain = new BrainTestStubs.ScriptedController(victim);
        brainMap().put(fakeId, victimBrain);
        keepMap().put(fakeId, newKeepRecord("zombie", null));

        IllegalStateException boom = new IllegalStateException("boom");
        for (int i = 0; i < THRESHOLD; i++) {
            BrainManager.handleTickCrash(victimBrain, boom);
        }
        // 阈值分支的调用方语义 = detach（清 keep，重挂必然复现）
        BrainManager.detach(victim);

        assertFalse(brainMap().containsKey(fakeId), "会话已卸载");
        assertFalse(keepMap().containsKey(fakeId), "确定性故障卸脑须清 keep");
    }

    @Test
    void autoDetachWithoutClearKeepForRuleOff() throws Exception {
        // 规则开关自动卸载语义（detach(player,false)）：keep 保留
        UUID fakeId = UUID.randomUUID();
        BrainTestStubs.FakePlayer victim = BrainTestStubs.FakePlayer.createFake(fakeId)
                .stubWorld(BrainTestStubs.stubWorld());
        brainMap().put(fakeId, new BrainTestStubs.ScriptedController(victim));
        keepMap().put(fakeId, newKeepRecord("zombie", null));

        BrainManager.detach(victim, false);
        assertTrue(keepMap().containsKey(fakeId), "规则自动卸载保留 keep");
    }

    // ===== 反射工具 =====

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Object> brainMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("BRAINS");
        field.setAccessible(true);
        return (java.util.Map<UUID, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Object> keepMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("KEEP");
        field.setAccessible(true);
        return (java.util.Map<UUID, Object>) field.get(null);
    }

    private static Object newKeepRecord(String mode, UUID owner) throws Exception {
        var ctor = Class.forName("me.primaryuan.carpet.brain.BrainManager$KeepRecord")
                .getDeclaredConstructor(String.class, UUID.class);
        ctor.setAccessible(true);
        return ctor.newInstance(mode, owner);
    }
}
