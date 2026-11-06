package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.recorder.RecordingTimeline;
import me.primaryuan.carpet.replay.script.core.SessionCore;
import me.primaryuan.carpet.replay.storage.ReplayCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实录制事件契约：录制时间轴 → 编码 → 解码 → 执行。
 * 不使用手填 USE_UP.hand=0 之类的测试特供格式。
 */
class RecordingPlaybackContractTest
{

    private static ReplayData dataFrom(RecordingTimeline t)
    {
        ReplayData d = new ReplayData();
        d.metadata.recordingId = "rec";
        d.metadata.ownerUuid = "owner";
        d.metadata.ownerName = "Owner";
        d.metadata.name = "n";
        d.metadata.durationTicks = t.tick();
        ReplayParticipant p = new ReplayParticipant("owner", "Owner", ReplayParticipant.JoinMethod.OWNER);
        p.segments.add(t.segment());
        d.participants.add(p);
        return d;
    }

    private static me.primaryuan.carpet.replay.common.InputTrack input(ReplayData d)
    {
        return d.participants.get(0).segments.get(0).input;
    }

    private static VisualFrame frame()
    {
        VisualFrame f = new VisualFrame();
        f.y = 64;
        return f;
    }

    @Test
    void recordedEventsSurviveCodecAndDrivePlayback() throws Exception
    {
        RecordingTimeline t = new RecordingTimeline(0);
        t.setInitialSlot(3);
        t.onMovementInput(InputFlags.FORWARD);
        t.addUseDown(0, InputEventType.USE_TARGET_AIR, -1);
        t.addEntityUse(0, 42);
        t.addEntityUse(0, 42); // 同一次点击的 INTERACT_AT + INTERACT
        t.commitTick("minecraft:overworld", frame(), 0f, 0f);

        t.addUseUp(-1);         // 真实松弓格式
        t.addEntityUse(1, 7);   // 副手实体右键
        t.commitTick("minecraft:overworld", frame(), 0f, 0f);
        t.commitTick("minecraft:overworld", frame(), 0f, 0f);
        t.finish();

        ReplayData recorded = dataFrom(t);
        assertEquals(3, input(recorded).duration());
        long entityUses = input(recorded).events.stream()
                .filter(e -> e.type == InputEventType.USE_DOWN
                        && e.a == InputEventType.USE_TARGET_ENTITY)
                .count();
        assertEquals(2, entityUses, "同一次实体点击去重，副手另算一次");
        assertTrue(input(recorded).events.stream().anyMatch(e ->
                e.type == InputEventType.USE_UP && e.hand == -1), "录像保存真实 hand=-1");

        ReplayData decoded = ReplayCodec.decode(ReplayCodec.encode(recorded));
        assertEquals(3, decoded.durationTicks());
        assertEquals(input(recorded).events.size(), input(decoded).events.size());

        var driver = new ScriptTestSupport.StubDriver();
        SessionCore core = new SessionCore(decoded, driver, 1, false);
        for (int i = 0; i < 3; i++)
        {
            core.tick();
        }
        assertTrue(driver.slots.contains(3), "首帧选中槽位来自录制基线");
        assertEquals(1, driver.count("useHold=start0"));
        assertEquals(1, driver.count("useHold=stop0"), "解码后的 hand=-1 能停止持续使用");
        assertFalse(driver.useHoldMain);
        assertEquals(2, driver.count("useOnce"), "两次实体右键各执行一次");
        assertTrue(driver.log.stream().anyMatch(l -> l.equals("useOnce0")));
        assertTrue(driver.log.stream().anyMatch(l -> l.equals("useOnce1")));
    }

    @Test
    void releaseDuringRecordingPauseIsReconciledOnResume()
    {
        RecordingTimeline t = new RecordingTimeline(0);
        t.addUseDown(0, InputEventType.USE_TARGET_AIR, -1);
        t.commitTick("d", frame(), 0f, 0f);
        t.pause();
        t.addUseUp(-1);         // 暂停期间松开：只更新最终状态，不补发事件
        t.onMovementInput(0);
        t.resume();             // 恢复边界补一次释放
        t.commitTick("d", frame(), 0f, 0f);
        t.finish();

        ReplayData data = dataFrom(t);
        InputEvent up = input(data).events.stream()
                .filter(e -> e.type == InputEventType.USE_UP)
                .findFirst().orElse(null);
        assertNotNull(up, "恢复时必须补发释放，避免恢复后仍长按");
        assertEquals(1, up.tick);
        assertEquals(-1, up.hand);

        var driver = new ScriptTestSupport.StubDriver();
        SessionCore core = new SessionCore(data, driver, 1, false);
        core.tick();
        assertTrue(driver.useHoldMain);
        core.tick();
        assertFalse(driver.useHoldMain, "第二帧释放长按");
    }

    @Test
    void digReleaseDuringPauseIsReconciled()
    {
        RecordingTimeline t = new RecordingTimeline(0);
        t.addEvent(InputEventType.DIG_START, -1);
        t.commitTick("d", frame(), 0f, 0f);
        t.pause();
        t.addEvent(InputEventType.DIG_ABORT, -1); // 暂停期间松手
        t.resume();
        t.commitTick("d", frame(), 0f, 0f);
        t.finish();

        var driver = new ScriptTestSupport.StubDriver();
        SessionCore core = new SessionCore(dataFrom(t), driver, 1, false);
        core.tick();
        assertTrue(driver.attackHold);
        core.tick();
        assertFalse(driver.attackHold, "暂停期松开挖掘在恢复边界释放");
    }

    @Test
    void entityClicksOnDifferentTicksAreNotDeduped()
    {
        RecordingTimeline t = new RecordingTimeline(0);
        t.addEntityUse(0, 9);
        t.commitTick("d", frame(), 0f, 0f);
        t.addEntityUse(0, 9);
        t.commitTick("d", frame(), 0f, 0f);
        t.finish();
        long count = input(dataFrom(t)).events.stream()
                .filter(e -> e.type == InputEventType.USE_DOWN)
                .count();
        assertEquals(2, count, "不同 Tick 的两次点击都要记录");
    }
}
