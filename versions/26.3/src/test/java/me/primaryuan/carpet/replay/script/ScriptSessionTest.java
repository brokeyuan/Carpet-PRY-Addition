package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.script.core.SessionCore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 脚本会话状态机：时序忠实、循环边界、暂停恢复、停止释放 */
class ScriptSessionTest {

    @Test
    void onceRunsHalfOpenIntervalAndFinishesAfterConsume() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(3);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(2, 0, InputEventType.ATTACK_ENTITY));
        SessionCore session = new SessionCore(data, s.driver(), 1);

        session.tick();
        session.tick();
        session.tick();
        // 尾帧已提交，等待实体 Tick 消费：不得同次结算
        assertEquals(SessionCore.State.RUNNING, session.state());
        assertEquals(3, s.driver().count("look="), "单次执行恰好 3 帧");
        assertEquals(1, s.driver().pending.size(), "尾帧动作仍在待消费队列");
        assertEquals(0, s.driver().released, "消费前不得释放");

        s.driver().consumeTick();
        session.tick();
        assertEquals(SessionCore.State.FINISHED, session.state());
        assertEquals(20f, s.driver().lastYaw, 0f, "最后帧 t2 的 yaw=20");
        assertEquals(1, s.driver().released, "消费完成后才释放控制");

        // 第 4 tick 无操作：半开区间，尾后不再执行
        session.tick();
        assertEquals(3, s.driver().count("look="), "单次执行恰好 3 帧");
    }

    @Test
    void repeatRunsExactRoundsWithoutTailDuplicate() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        SessionCore session = new SessionCore(data, s.driver(), 3);
        for (int i = 0; i < 20; i++) {
            session.tick();
            s.driver().consumeTick();
        }
        assertEquals(SessionCore.State.FINISHED, session.state());
        assertEquals(6, s.driver().count("look="), "2 帧 × 3 轮，不得重复尾帧");
        assertEquals(3, session.loopsDone());
    }

    @Test
    void loopRestoresFirstFrameLookEachRound() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        SessionCore session = new SessionCore(data, s.driver(), -1);
        session.tick(); // 轮1 t0
        s.driver().consumeTick();
        session.tick(); // 轮1 t1
        s.driver().consumeTick();
        session.tick(); // 结算 + 轮2 t0
        assertEquals(0f, s.driver().lastYaw, 0f, "每轮首帧恢复首帧绝对朝向");
        assertEquals(1, session.loopsDone());
        session.stop();
        assertEquals(SessionCore.State.STOPPED, session.state());
        assertEquals(0, s.driver().mask, "停止后移动输入归零");
        assertEquals(1, s.driver().released, "循环轮界不释放，仅 stop() 释放一次");
    }

    @Test
    void continuousInputCarriesAcrossLoopBoundaryWithoutRestart() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        // 尾帧与首帧同为 FORWARD：跨边界保持，不得重复下发 mask
        SessionCore session = new SessionCore(data, s.driver(), -1);
        session.tick();
        s.driver().consumeTick();
        session.tick();
        s.driver().consumeTick();
        session.tick();
        assertEquals(1, s.driver().count("mask="), "FORWARD 全程保持，只应用一次");
    }

    @Test
    void continuousInputStopsWhenFirstFrameDropsIt() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        ScriptTestSupport.input(data).movementMasks[1] = (byte) 0; // 尾帧松开
        SessionCore session = new SessionCore(data, s.driver(), -1);
        session.tick();
        s.driver().consumeTick();
        session.tick();
        s.driver().consumeTick();
        session.tick();
        assertEquals(3, s.driver().count("mask="), "首按 + 尾松 + 下轮首帧重按 = 3 次");
    }

    @Test
    void pauseFreezesTimelineAndResumeDoesNotRepeatInstantEvents() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(4);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(1, 0, InputEventType.ATTACK_ENTITY));
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.tick(); // t0
        session.tick(); // t1: attackOnce
        s.driver().consumeTick();
        session.pause();
        assertEquals(SessionCore.State.PAUSED, session.state());
        for (int i = 0; i < 5; i++) {
            session.tick();
        }
        assertEquals(1, s.driver().count("attackOnce"), "暂停期间时间轴冻结、瞬时事件不重复");
        assertEquals(2, session.playTick(), "暂停保存的是下一帧索引");
        session.resume();
        session.tick(); // t2
        session.tick(); // t3
        s.driver().consumeTick();
        session.tick(); // 结算
        assertEquals(SessionCore.State.FINISHED, session.state());
        assertEquals(4, s.driver().count("look="), "恢复后从暂停点继续且不重复 t1");
        assertEquals(1, s.driver().count("attackOnce"), "恢复不重发瞬时事件");
    }

    @Test
    void invalidDriverMarksSessionInvalidAndReleases() {
        var s = new ScriptSessionTest.Ctx();
        SessionCore session = new SessionCore(
                ScriptTestSupport.data(2), s.driver(), 1);
        s.driver().valid = false;
        session.tick();
        assertEquals(SessionCore.State.INVALID, session.state());
        assertTrue(s.driver().released >= 1, "假人失效必须释放控制");
        assertTrue(session.failureReason().contains("失效"));
    }

    @Test
    void pausedInvalidDriverStillReleasesSession() {
        var s = new ScriptSessionTest.Ctx();
        SessionCore session = new SessionCore(ScriptTestSupport.data(3), s.driver(), 1);
        session.tick();
        session.pause();
        s.driver().valid = false;
        session.tick();
        assertEquals(SessionCore.State.INVALID, session.state(), "暂停状态也必须检查死亡/断线");
        assertTrue(s.driver().released >= 1, "暂停失效同样释放控制");
    }

    @Test
    void digHoldSurvivesAcrossBoundaryAndStopsOnFinishEvent() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(3);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(1, 0, InputEventType.DIG_START));
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(2, 0, InputEventType.DIG_FINISH));
        SessionCore session = new SessionCore(data, s.driver(), -1);
        session.tick(); // t0 无
        session.tick(); // t1 开始挖掘
        assertTrue(s.driver().attackHold, "DIG_START 后持续左键保持");
        session.tick(); // t2 结束挖掘
        assertFalse(s.driver().attackHold, "DIG_FINISH 后释放");
        // 跨入下一轮 t0：持有状态不跨轮重启
        session.tick();
        assertFalse(s.driver().attackHold);
    }

    @Test
    void swingAloneIsSingleAttemptButNotDuringDigHold() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(4);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(0, 0, InputEventType.DIG_START));
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(1, 0, InputEventType.SWING));
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(2, 0, InputEventType.DIG_FINISH));
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(3, 0, InputEventType.SWING));
        SessionCore session = new SessionCore(data, s.driver(), 1);
        for (int i = 0; i < 4; i++) {
            session.tick();
        }
        assertEquals(1, s.driver().count("attackHold=start"));
        assertEquals(1, s.driver().count("attackHold=stop"));
        // t1 挖掘持有期间挥动不额外攻击；t3 空挥一次
        assertEquals(1, s.driver().count("attackOnce"));
    }

    @Test
    void useDownAirHoldsUntilUseUp() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(3);
        InputEvent down = ScriptTestSupport.event(0, 0, InputEventType.USE_DOWN);
        down.a = InputEventType.USE_TARGET_AIR;
        down.hand = 0;
        ScriptTestSupport.input(data).events.add(down);
        InputEvent up = ScriptTestSupport.event(2, 0, InputEventType.USE_UP);
        up.hand = 0;
        ScriptTestSupport.input(data).events.add(up);
        SessionCore session = new SessionCore(data, s.driver(), 1);
        for (int i = 0; i < 3; i++) {
            session.tick();
        }
        assertEquals(1, s.driver().count("useHold=start0"));
        assertEquals(1, s.driver().count("useHold=stop0"));
        assertFalse(s.driver().useHoldMain);
    }

    @Test
    void useUpMinusOneReleasesCurrentUse() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(3);
        InputEvent down = ScriptTestSupport.event(0, 0, InputEventType.USE_DOWN);
        down.a = InputEventType.USE_TARGET_AIR;
        down.hand = 0;
        ScriptTestSupport.input(data).events.add(down);
        // 真实录制格式：RELEASE_USE_ITEM 固定 hand=-1
        InputEvent up = ScriptTestSupport.event(2, 0, InputEventType.USE_UP);
        up.hand = -1;
        ScriptTestSupport.input(data).events.add(up);
        SessionCore session = new SessionCore(data, s.driver(), 1);
        for (int i = 0; i < 3; i++) {
            session.tick();
        }
        assertEquals(1, s.driver().count("useHold=start0"));
        assertEquals(1, s.driver().count("useHold=stop0"), "hand=-1 必须释放持续使用");
        assertFalse(s.driver().useHoldMain);
    }

    @Test
    void useDownBlockIsInstantEachTime() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        InputEvent down = ScriptTestSupport.event(0, 0, InputEventType.USE_DOWN);
        down.a = InputEventType.USE_TARGET_BLOCK;
        down.hand = 0;
        ScriptTestSupport.input(data).events.add(down);
        InputEvent down2 = ScriptTestSupport.event(1, 0, InputEventType.USE_DOWN);
        down2.a = InputEventType.USE_TARGET_BLOCK;
        down2.hand = 0;
        ScriptTestSupport.input(data).events.add(down2);
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.tick();
        session.tick();
        assertEquals(2, s.driver().count("useOnce0"), "对方块按录制点击节奏逐次尝试");
        assertFalse(s.driver().useHoldMain);
    }

    @Test
    void slotDropSwapAndJumpSneakSprintFlow() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        ScriptTestSupport.input(data).movementMasks[0] = (byte) (InputFlags.FORWARD | InputFlags.JUMP | InputFlags.SNEAK | InputFlags.SPRINT);
        ScriptTestSupport.input(data).movementMasks[1] = (byte) InputFlags.BACKWARD;
        InputEvent slot = ScriptTestSupport.event(0, 1, InputEventType.SELECT_SLOT);
        slot.a = 5;
        ScriptTestSupport.input(data).events.add(slot);
        InputEvent drop = ScriptTestSupport.event(1, 0, InputEventType.DROP_ITEM);
        drop.hand = 0;
        drop.a = 1;
        ScriptTestSupport.input(data).events.add(drop);
        InputEvent swap = ScriptTestSupport.event(1, 1, InputEventType.SWAP_HANDS);
        ScriptTestSupport.input(data).events.add(swap);
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.tick();
        session.tick();
        assertTrue(s.driver().slots.contains(5));
        assertTrue(s.driver().drops.contains("0,true"));
        assertTrue(s.driver().log.contains("swap"));
        assertTrue(s.driver().log.contains("jump=true"));
        assertTrue(s.driver().log.contains("sneak=true"));
        assertTrue(s.driver().log.contains("sprint=true"));
        assertTrue(s.driver().log.contains("jump=false"));
    }

    @Test
    void nineSlotSelectionUsesRecordedZeroToEight() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(9);
        for (int i = 0; i < 9; i++) {
            InputEvent e = ScriptTestSupport.event(i, 0, InputEventType.SELECT_SLOT);
            e.a = i;
            ScriptTestSupport.input(data).events.add(e);
        }
        SessionCore session = new SessionCore(data, s.driver(), 1);
        for (int i = 0; i < 9; i++) {
            session.tick();
        }
        assertEquals(java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), s.driver().slots,
                "逻辑层保持录像 0~8，转换只在 Carpet 驱动边界");
        assertEquals(1, CarpetFakeDriver.carpetSlot(0));
        assertEquals(9, CarpetFakeDriver.carpetSlot(8));
        assertEquals(-1, CarpetFakeDriver.carpetSlot(-1));
        assertEquals(-1, CarpetFakeDriver.carpetSlot(9));
        assertEquals(-1, CarpetFakeDriver.carpetDropSlot(), "丢弃当前选中槽，不能把 hand 当槽号");
    }

    @Test
    void pauseResumeRebuildsAttackAndUseHolds() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(6);
        ScriptTestSupport.input(data).events.add(ScriptTestSupport.event(0, 0, InputEventType.DIG_START));
        InputEvent use = ScriptTestSupport.event(0, 1, InputEventType.USE_DOWN);
        use.a = InputEventType.USE_TARGET_AIR;
        use.hand = 0;
        ScriptTestSupport.input(data).events.add(use);
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.tick(); // 两个持续动作建立
        assertTrue(s.driver().attackHold);
        assertTrue(s.driver().useHoldMain);
        session.pause();
        assertFalse(s.driver().attackHold, "暂停释放物理动作");
        assertFalse(s.driver().useHoldMain);
        session.resume();
        session.tick(); // 恢复后的第一帧：重建持续动作
        assertTrue(s.driver().attackHold, "恢复后挖掘继续");
        assertTrue(s.driver().useHoldMain, "恢复后使用继续");
        assertEquals(2, s.driver().count("attackHold=start"), "暂停前建立 + 恢复时重建");
        assertEquals(2, s.driver().count("useHold=start0"), "暂停前建立 + 恢复时重建");
        assertEquals(1, s.driver().count("attackHold=stop"), "暂停只停一次物理动作");
    }

    @Test
    void takeoverClearsActionsAndForceAppliesZeroFirstFrame() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(1);
        ScriptTestSupport.input(data).movementMasks[0] = 0;
        s.driver().mask = InputFlags.FORWARD; // 外部假人原有的持续移动
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.prepareTakeover();
        assertEquals(1, s.driver().released, "接管成功后才清空原动作");
        session.tick();
        assertEquals(0, s.driver().mask, "首帧全零必须真正落到驱动");
        assertTrue(s.driver().log.contains("mask=" + InputFlags.describe(0)));
        assertEquals(1, s.driver().count("mask="), "强制应用不依赖差量判断");
    }

    @Test
    void initialSlotBaselineComesFromVisualFirstFrame() {
        var s = new ScriptSessionTest.Ctx();
        ReplayData data = ScriptTestSupport.data(2);
        ScriptTestSupport.visual(data).segments.get(0).frames.get(0).slot = 6;
        SessionCore session = new SessionCore(data, s.driver(), 1);
        session.tick();
        assertEquals(java.util.List.of(6), s.driver().slots, "旧录像无 SELECT_SLOT 时用视觉首帧槽位作基线");
        assertEquals(0f, s.driver().lastYaw, 0f, "首帧朝向仍照常重放");
    }

    /** 测试上下文：包一层驱动桩 */
    static final class Ctx
    {
        final ScriptTestSupport.StubDriver driver = new ScriptTestSupport.StubDriver();

        ScriptTestSupport.StubDriver driver()
        {
            return driver;
        }
    }
}
