package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.VisualFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 录制暂停/恢复语义（goal 第六节验证项）：
 * 暂停不入轨、暂停期间输入只同步基线（不遗留长按）、恢复强制段边界、段边界全量位置。
 */
class RecordingTimelineTest {

    private static VisualFrame frame(double x) {
        VisualFrame f = new VisualFrame();
        f.x = x;
        f.y = 64;
        f.z = 0;
        return f;
    }

    @Test
    void pauseFreezesTimeline() {
        RecordingTimeline t = new RecordingTimeline(0);
        assertTrue(t.commitTick("minecraft:overworld", frame(0), 0f, 0f));
        assertTrue(t.pause());
        // 暂停中：提交不入轨、事件拒绝
        assertFalse(t.commitTick("minecraft:overworld", frame(1), 0f, 0f));
        assertNull(t.addEvent(InputEventType.SWING, 0));
        assertTrue(t.resume());
        assertTrue(t.commitTick("minecraft:overworld", frame(2), 0f, 0f));
        assertEquals(2, t.tick());
        assertEquals(2, t.visual().totalFrames());
    }


    @Test
    void resumeWritesCurrentSlotBaseline() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.commitTick("d", frame(0), 0f, 0f);
        t.pause();
        assertTrue(t.resume(4));
        t.commitTick("d", frame(1), 0f, 0f);
        InputEvent slot = t.input().events.stream()
                .filter(e -> e.type == InputEventType.SELECT_SLOT)
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(slot, "恢复后必须补当前槽位基线");
        assertEquals(1, slot.tick);
        assertEquals(4, slot.a);
    }

    @Test
    void pauseDropsEventsAndInput() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.commitTick("d", frame(0), 0f, 0f);
        t.pause();
        t.onMovementInput(InputFlags.FORWARD);
        assertNull(t.addEvent(InputEventType.DIG_START, -1));
        assertTrue(t.resume());
        // 恢复后第一个包之前提交：输入基线不得来自暂停前的遗留
        t.onMovementInput(0);
        t.commitTick("d", frame(1), 0f, 0f);
        assertEquals(0, t.input().maskAt(1), "恢复后不得遗留暂停前的长按");
    }

    @Test
    void pauseSyncsBaselineForStillHeldKeys() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.onMovementInput(InputFlags.FORWARD);
        t.commitTick("d", frame(0), 0f, 0f);
        assertEquals(InputFlags.FORWARD, t.input().maskAt(0));
        t.pause();
        // 暂停期间玩家仍按住 W：无新包。恢复后基线仍为 FORWARD（真实状态）
        assertTrue(t.resume());
        t.commitTick("d", frame(1), 0f, 0f);
        assertEquals(InputFlags.FORWARD, t.input().maskAt(1), "仍按住的键恢复后按真实状态继续");
    }

    @Test
    void resumeStartsNewSegmentOnSameDimension() {
        RecordingTimeline t = new RecordingTimeline(0);
        for (int i = 0; i < 3; i++) {
            t.commitTick("d", frame(i), 0f, 0f);
        }
        assertEquals(1, t.visual().segments.size());
        t.pause();
        // 暂停期间位置变化（模拟玩家移动）
        assertTrue(t.resume());
        for (int i = 0; i < 2; i++) {
            t.commitTick("d", frame(100 + i), 0f, 0f);
        }
        // 同维度也切段：两段，边界不插值
        assertEquals(2, t.visual().segments.size());
        assertEquals(3, t.visual().segments.get(0).frames.size());
        assertEquals(2, t.visual().segments.get(1).frames.size());
        // 段边界标记：恢复后第一帧是段首，播放端据此强制全量位置
        assertTrue(t.visual().isSegmentStart(3));
        assertFalse(t.visual().isSegmentStart(4));
        assertFalse(t.visual().isSegmentStart(1));
    }

    @Test
    void eventsDuringPauseAreNotBackfilledAfterResume() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.commitTick("d", frame(0), 0f, 0f);
        t.pause();
        assertNull(t.addEvent(InputEventType.ATTACK_ENTITY, 0));
        assertTrue(t.resume());
        t.onMovementInput(0);
        t.commitTick("d", frame(1), 0f, 0f);
        assertEquals(0, t.input().events.size(), "暂停期间的事件不得在恢复后补发");
        assertEquals(0, t.visual().animEvents.size());
    }

    @Test
    void dimensionSwitchAlsoStartsSegment() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.commitTick("minecraft:overworld", frame(0), 0f, 0f);
        t.commitTick("minecraft:the_nether", frame(1), 0f, 0f);
        assertEquals(2, t.visual().segments.size());
        assertTrue(t.visual().isSegmentStart(1));
    }

    @Test
    void finishRejectsFurtherInput() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.commitTick("d", frame(0), 0f, 0f);
        t.finish();
        assertFalse(t.pause());
        assertNull(t.addEvent(InputEventType.SWING, 0));
        assertFalse(t.commitTick("d", frame(1), 0f, 0f));
        assertEquals(1, t.tick());
        assertNotNull(t.visual().segmentAt(0));
        assertEquals(1, t.visual().segments.size());
    }

    @Test
    void maskBaselineKeepsHeldStateWhenNoPackets() {
        RecordingTimeline t = new RecordingTimeline(0);
        t.onMovementInput(InputFlags.FORWARD | InputFlags.SPRINT);
        t.commitTick("d", frame(0), 0f, 0f);
        // 无新包（玩家持续按住）：基线延续
        t.commitTick("d", frame(1), 0f, 0f);
        assertEquals(InputFlags.FORWARD | InputFlags.SPRINT, t.input().maskAt(1));
        t.onMovementInput(InputFlags.FORWARD);
        t.commitTick("d", frame(2), 0f, 0f);
        assertEquals(InputFlags.FORWARD, t.input().maskAt(2), "松开疾跑后仅保留前进");
    }

    @Test
    void eventSeqOrdersWithinTick() {
        RecordingTimeline t = new RecordingTimeline(0);
        InputEvent e1 = t.addEvent(InputEventType.SELECT_SLOT, -1);
        assertNotNull(e1);
        InputEvent e2 = t.addEvent(InputEventType.SWING, 0);
        assertNotNull(e2);
        assertEquals(0, e1.tick);
        assertEquals(0, e1.seq);
        assertEquals(1, e2.seq);
        assertEquals(1, t.visual().animEvents.size(), "SWING 转写动画轨");
    }
}
