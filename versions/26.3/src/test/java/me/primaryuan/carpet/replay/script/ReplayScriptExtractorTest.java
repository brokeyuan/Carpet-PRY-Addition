package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多人脚本提取：去离线空档、顺接在线片段、保留片段边界、拒绝无输入 */
class ReplayScriptExtractorTest {

    @Test
    void extractsOnlyOnlineSegmentsAndKeepsBoundary() throws Exception {
        ReplayData data = data();
        ReplayData script = ReplayScriptExtractor.extract(data, "a");
        assertEquals(10, script.durationTicks(), "两段各 5 Tick，离线空档被去掉");
        ReplayParticipant p = script.participants.get(0);
        assertEquals(1, p.segments.size());
        assertEquals(10, p.segments.get(0).duration());
        assertEquals(10, p.segments.get(0).visual.totalFrames());
        assertEquals(5, p.segments.get(0).visual.frameAt(0).x, 0.0);
        assertEquals(20, p.segments.get(0).visual.frameAt(5).x, 0.0,
                "第二段首帧顺接到第一段末帧之后");
        assertEquals(java.util.List.of(5), p.segments.get(0).input.segmentStarts);
        InputEvent event = p.segments.get(0).input.events.get(0);
        assertEquals(1, event.tick);
        assertEquals(InputEventType.DIG_START, event.type);
        assertEquals(7, p.segments.get(0).input.events.get(1).tick, "第二段事件按拼接偏移");
    }

    @Test
    void rejectsParticipantWithoutInput() {
        ReplayData data = data();
        ReplayParticipant idle = new ReplayParticipant("idle", "Idle", ReplayParticipant.JoinMethod.INVITE);
        ReplayParticipant.Segment seg = idle.openSegment(0);
        seg.endTick = 4;
        seg.input.ensureDuration(4);
        VisualSegment v = new VisualSegment("minecraft:overworld", 0);
        for (int i = 0; i < 4; i++) {
            v.frames.add(new VisualFrame());
        }
        seg.visual.segments.add(v);
        InputEvent slot = new InputEvent(0, 0, InputEventType.SELECT_SLOT);
        slot.a = 0;
        seg.input.events.add(slot);
        data.participants.add(idle);
        assertThrows(ReplayScriptException.class, () -> ReplayScriptExtractor.extract(data, "idle"));
    }

    @Test
    void participantSelectionListsOnlyTracksWithContent() {
        ReplayData data = data();
        assertEquals(2, ReplayScriptExtractor.selectable(data).size());
    }

    private static ReplayData data() {
        ReplayData data = new ReplayData();
        data.metadata.recordingId = "script-test";
        data.metadata.ownerUuid = "a";
        data.metadata.ownerName = "A";
        data.metadata.name = "n";
        data.metadata.durationTicks = 15;
        data.metadata.initialDimension = "minecraft:overworld";

        ReplayParticipant a = new ReplayParticipant("a", "A", ReplayParticipant.JoinMethod.OWNER);
        segment(a, 0, 5, 5);
        InputEvent e1 = new InputEvent(1, 0, InputEventType.DIG_START);
        a.segments.get(0).input.events.add(e1);
        segment(a, 10, 5, 20);
        InputEvent e2 = new InputEvent(2, 0, InputEventType.SWING);
        a.segments.get(1).input.events.add(e2);
        data.participants.add(a);

        ReplayParticipant b = new ReplayParticipant("b", "B", ReplayParticipant.JoinMethod.INVITE);
        segment(b, 5, 5, 10);
        data.participants.add(b);
        return data;
    }

    private static void segment(ReplayParticipant p, int start, int length, double xBase) {
        ReplayParticipant.Segment seg = p.openSegment(start);
        seg.endTick = start + length;
        seg.input.ensureDuration(length);
        VisualSegment v = new VisualSegment("minecraft:overworld", 0);
        for (int i = 0; i < length; i++) {
            seg.input.movementMasks[i] = (byte) InputFlags.FORWARD;
            VisualFrame f = new VisualFrame();
            f.x = xBase + i;
            f.y = 64;
            v.frames.add(f);
        }
        seg.visual.segments.add(v);
    }
}
