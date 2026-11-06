package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.script.core.SessionCore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本轮次与次数边界：
 * roundReset 轮间回起点只在上一轮尾帧消费之后、下一轮首帧提交之前触发；
 * 1 次/N 次/无限三种次数边界；末帧动作在结算前获得实体 Tick 消费机会。
 */
class SessionRoundTest {

    private static ScriptTestSupport.StubDriver run(ReplayData data, int maxLoops, boolean roundReset, int ticks) {
        ScriptTestSupport.StubDriver d = new ScriptTestSupport.StubDriver();
        SessionCore core = new SessionCore(data, d, maxLoops, roundReset);
        for (int i = 0; i < ticks; i++) {
            core.tick();
            d.consumeTick();
        }
        return d;
    }

    @Test
    void count1IsSingleRound() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(5);
        SessionCore core = new SessionCore(data, s, 1, false);
        for (int i = 0; i < 20; i++) {
            core.tick();
            s.consumeTick();
        }
        assertEquals(SessionCore.State.FINISHED, core.state());
        assertEquals(5, s.count("look="), "1 次 = 恰好一轮 5 帧");
        assertEquals(1, core.loopsDone());
        assertEquals(0, s.newRounds, "单次不触发轮间重置");
    }

    @Test
    void countNRunsExactlyNRounds() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(4);
        SessionCore core = new SessionCore(data, s, 3, false);
        for (int i = 0; i < 40; i++) {
            core.tick();
            s.consumeTick();
        }
        assertEquals(SessionCore.State.FINISHED, core.state());
        assertEquals(12, s.count("look="), "4 帧 × 3 轮");
        assertEquals(3, core.loopsDone());
        assertEquals(0, s.newRounds, "roundReset=false 不触发轮间重置");
    }

    @Test
    void infiniteSurvivesManyRounds() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(3);
        SessionCore core = new SessionCore(data, s, -1, false);
        for (int i = 0; i < 99; i++) {
            core.tick();
            s.consumeTick();
        }
        assertEquals(SessionCore.State.RUNNING, core.state());
        assertEquals(99, s.count("look="), "每 Tick 都提交一帧（轮间重置不额外占 Tick）");
        assertTrue(core.loopsDone() >= 32);
        core.stop();
        assertEquals(SessionCore.State.STOPPED, core.state());
    }

    @Test
    void roundResetFiresAfterTailConsumeBeforeNextFirstFrame() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(3);
        SessionCore core = new SessionCore(data, s, -1, true);
        for (int i = 0; i < 4; i++) {
            core.tick();
            s.consumeTick();
        }
        // 前 3 Tick 提交 t0/t1/t2，第 4 Tick 消费尾帧后回起点并提交新轮 t0
        assertEquals(1, s.newRounds, "轮边界恰好触发一次");
        int resetAt = s.log.indexOf("newRound");
        assertTrue(s.log.subList(0, resetAt).contains("look=20.0,0.0"),
                "上一轮尾帧在 reset 之前已提交");
        assertTrue(s.log.subList(resetAt + 1, s.log.size()).contains("look=0.0,0.0"),
                "新轮首帧在 reset 之后才提交");
        assertEquals(1, core.loopsDone(), "第 4 Tick 已进入第 2 轮");
    }

    @Test
    void roundResetDoesNotFireMidRound() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(4);
        SessionCore core = new SessionCore(data, s, -1, true);
        for (int i = 0; i < 4; i++) {
            core.tick();
            s.consumeTick();
            if (i < 3) {
                assertEquals(0, s.newRounds, "轮内不得触发轮间重置");
            }
        }
        assertEquals(0, s.newRounds, "尾帧尚未消费，不得提前回起点");
        core.tick();
        assertEquals(1, s.newRounds, "消费尾帧后的下一个 Tick 才回起点");
    }

    @Test
    void lastFrameActionExecutesBeforeFinish() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(3);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(2, 0, InputEventType.ATTACK_ENTITY));
        SessionCore core = new SessionCore(data, s, 1, false);
        for (int i = 0; i < 10; i++) {
            core.tick();
            s.consumeTick();
        }
        assertEquals(SessionCore.State.FINISHED, core.state());
        assertEquals(1, s.count("attackOnce"), "最后一帧动作必须真正执行");
        assertTrue(s.executed.contains("attackOnce"), "动作经实体 Tick 消费而非仅收到调用");
    }

    @Test
    void tailFrameConsumedForOneTwoAndThreeTickRecordings() {
        for (int ticks = 1; ticks <= 3; ticks++) {
            var s = new ScriptTestSupport.StubDriver();
            ReplayData data = ScriptTestSupport.data(ticks);
            ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(ticks - 1, 0, InputEventType.ATTACK_ENTITY));
            SessionCore core = new SessionCore(data, s, 1, false);
            for (int i = 0; i < ticks; i++) {
                core.tick();
            }
            assertEquals(SessionCore.State.RUNNING, core.state(), ticks + " Tick 录像尾帧待消费");
            assertEquals(1, s.pending.size(), "尾帧动作已提交未消费");
            s.consumeTick();
            core.tick();
            assertEquals(SessionCore.State.FINISHED, core.state());
            assertTrue(s.executed.contains("attackOnce"), ticks + " Tick 录像尾帧必须被消费");
            assertEquals(0, s.pending.size(), "结束后无未消费动作");
        }
    }

    @Test
    void heldDigDoesNotLeakAcrossFinish() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(2);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(0, 0, InputEventType.DIG_START));
        SessionCore core = new SessionCore(data, s, 1, false);
        core.tick();
        assertTrue(s.attackHold, "DIG_START 后持续左键保持");
        core.tick(); // 尾帧
        s.consumeTick();
        core.tick(); // 结算
        assertEquals(SessionCore.State.FINISHED, core.state());
        assertFalse(s.attackHold, "结束时持续动作不得残留");
    }

    @Test
    void pauseStillLetsWorldRunButFreezesTimeline() {
        var s = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(4);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(1, 0, InputEventType.ATTACK_ENTITY));
        SessionCore core = new SessionCore(data, s, 1, false);
        core.tick();
        core.tick();
        s.consumeTick();
        core.pause();
        for (int i = 0; i < 10; i++) {
            core.tick();
        }
        assertEquals(SessionCore.State.PAUSED, core.state());
        assertEquals(1, s.count("attackOnce"));
        assertEquals(2, core.playTick());
        core.resume();
        core.tick();
        core.tick();
        s.consumeTick();
        core.tick();
        assertEquals(SessionCore.State.FINISHED, core.state());
        assertEquals(4, s.count("look="), "恢复后从暂停点继续且不重复");
    }

    @Test
    void noCoordinateDrivenMovement() {
        // 红线验证：驱动端口只暴露 look 与掩码/开关，不存在任何坐标驱动入口
        var s = new ScriptTestSupport.StubDriver();
        SessionCore core = new SessionCore(ScriptTestSupport.data(2), s, 1, false);
        for (int i = 0; i < 2; i++) {
            core.tick();
        }
        assertEquals(0, s.log.stream().filter(l -> l.startsWith("teleport=")).count(),
                "roundReset=false 时不得有任何传送");
    }
}
