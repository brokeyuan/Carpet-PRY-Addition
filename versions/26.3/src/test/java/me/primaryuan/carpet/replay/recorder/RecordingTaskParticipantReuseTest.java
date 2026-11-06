package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayMetadata;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import me.primaryuan.carpet.replay.storage.ReplayCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 退出后再次邀请必须复用同一参与者对象并追加片段，不能制造重复 UUID。 */
class RecordingTaskParticipantReuseTest {

    @Test
    void rejoinReusesParticipantAndAppendsSegment() throws Exception {
        ReplayData data = new ReplayData();
        data.metadata.recordingId = "participant-reuse";
        data.metadata.ownerUuid = "owner";
        data.metadata.ownerName = "Owner";
        data.metadata.name = "reuse";
        data.metadata.durationTicks = 20;

        ReplayParticipant first = RecordingTask.ensureParticipant(
                data, "u1", "Alice", ReplayParticipant.JoinMethod.INVITE);
        ReplayParticipant.Segment s1 = first.openSegment(0);
        fill(s1, 5);
        s1.endTick = 5;

        ReplayParticipant second = RecordingTask.ensureParticipant(
                data, "u1", "Alice", ReplayParticipant.JoinMethod.INVITE);
        assertSame(first, second, "同一 UUID 必须复用原参与者对象");
        assertEquals(1, data.participants.size(), "不能新增重复参与者");
        ReplayParticipant.Segment s2 = second.openSegment(10);
        fill(s2, 5);
        s2.endTick = 15;
        assertEquals(2, second.segments.size());

        // 真实保存路径会做同样校验；确保两段不同 UUID 参与者可编码解码
        ReplayData encoded = ReplayCodec.decode(ReplayCodec.encode(data));
        assertEquals(1, encoded.participants.size());
        assertEquals(2, encoded.participants.get(0).segments.size());
    }

    private static void fill(ReplayParticipant.Segment seg, int ticks) {
        seg.input.ensureDuration(ticks);
        VisualSegment visual = new VisualSegment("minecraft:overworld", 0);
        for (int i = 0; i < ticks; i++) {
            visual.frames.add(new VisualFrame());
        }
        seg.visual.segments.add(visual);
    }
}
