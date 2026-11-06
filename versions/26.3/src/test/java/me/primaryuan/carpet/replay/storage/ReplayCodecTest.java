package me.primaryuan.carpet.replay.storage;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.InputTrack;
import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayFormatException;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayNames;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFlags;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import me.primaryuan.carpet.replay.common.VisualTrack;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多人录像编解码：稀疏在线片段往返、版本拒绝、损坏容错、限额 */
class ReplayCodecTest {

    @Test
    void multiParticipantSparseRoundTrip() throws Exception {
        ReplayData data = sampleMulti();
        byte[] first = ReplayCodec.encode(data);
        ReplayData back = ReplayCodec.decode(first);
        byte[] second = ReplayCodec.encode(back);
        assertArrayEquals(first, second, "往返后再编码应字节等价");
        assertEquals("multi-id", back.metadata.recordingId);
        assertEquals(2, back.participants.size());
        ReplayParticipant a = back.participant("uuid-a");
        ReplayParticipant b = back.participant("uuid-b");
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(2, a.segments.size(), "A 有离线空档，两段");
        assertEquals(1, b.segments.size());
        assertEquals(10, a.segments.get(0).duration());
        assertEquals(5, a.segments.get(1).duration());
        assertEquals(0, a.segments.get(1).startTick - 25, "第二段从共同 Tick 25 开始");
        assertEquals(25, b.segments.get(0).startTick);
        assertEquals(15, b.segments.get(0).duration());
        assertEquals(40, back.metadata.durationTicks);
        assertEquals(ReplayAccess.PRIVATE, back.metadata.access);
        assertEquals(2, b.segments.get(0).input.events.size());
        assertEquals(14, b.segments.get(0).input.events.get(1).tick);
    }

    @Test
    void unsupportedVersionRejected() {
        ReplayData data = sampleMulti();
        byte[] bytes = ReplayCodec.encode(data);
        bytes[0] = 99; // 首字段 formatVersion（gzip 头之后第一个 int 的高位字节）
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(bytes));
    }

    @Test
    void corruptedBytesRejectedNotCrashed() {
        ReplayData data = sampleMulti();
        byte[] bytes = ReplayCodec.encode(data);
        byte[] truncated = new byte[bytes.length / 2];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(truncated), "截断文件必须拒绝");
        byte[] flipped = bytes.clone();
        flipped[flipped.length / 2] ^= 0x5A;
        try {
            ReplayData out = ReplayCodec.decode(flipped);
            assertNotNull(out);
        } catch (ReplayFormatException expected) {
            // 合法路径
        } catch (RuntimeException e) {
            throw new AssertionError("只允许 ReplayFormatException，不得抛其他异常", e);
        }
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(new byte[0]));
    }

    @Test
    void gzipBombGuarded() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(bos);
        gos.write(new byte[64 * 1024 * 1024]);
        gos.close();
        byte[] bomb = bos.toByteArray();
        assertThrows(ReplayFormatException.class, () -> ReplayCodec.decode(bomb));
    }

    @Test
    void namesValidatedAgainstTraversal() {
        assertTrue(ReplayNames.isValid("我的录像_1"));
        assertTrue(ReplayNames.isValid("a-b_C9"));
        assertFalse(ReplayNames.isValid("../etc"));
        assertFalse(ReplayNames.isValid("a/b"));
        assertFalse(ReplayNames.isValid("a\\b"));
        assertFalse(ReplayNames.isValid(".."));
        assertFalse(ReplayNames.isValid(".hidden"));
        assertFalse(ReplayNames.isValid(""));
        assertFalse(ReplayNames.isValid(null));
        assertFalse(ReplayNames.isValid("x".repeat(ReplayLimits.MAX_NAME_LENGTH + 1)));
        assertTrue(ReplayNames.isValidId("11111111-2222-3333-4444-555555555555"));
        assertFalse(ReplayNames.isValidId("../x"));
    }

    @Test
    void frameSlotSurvivesRoundTrip() throws Exception {
        ReplayData data = sampleMulti();
        input(data).movementMasks[0] = (byte) 3;
        byte[] bytes = ReplayCodec.encode(data);
        ReplayData back = ReplayCodec.decode(bytes);
        assertEquals(3, back.participants.get(0).segments.get(0).input.maskAt(0));
        assertNull(back.participants.get(0).segments.get(0).visual.frameAt(99));
        assertEquals(7, back.participants.get(0).segments.get(0).visual.frameAt(0).slot,
                "首帧槽位原样保存");
    }

    private static InputTrack input(ReplayData data) {
        return data.participants.get(0).segments.get(0).input;
    }

    static ReplayData sampleMulti() {
        ReplayData data = new ReplayData();
        data.metadata.recordingId = "multi-id";
        data.metadata.ownerUuid = "owner";
        data.metadata.ownerName = "Owner";
        data.metadata.name = "多人测试";
        data.metadata.createdAtEpochMillis = 1730000000000L;
        data.metadata.durationTicks = 40;
        data.metadata.access = ReplayAccess.PRIVATE;
        data.metadata.initialDimension = "minecraft:overworld";
        data.metadata.initialX = 1.5;
        data.metadata.initialY = 64;
        data.metadata.initialZ = -2.25;
        data.metadata.initialYaw = 90f;
        data.metadata.initialPitch = -10f;

        ReplayParticipant a = new ReplayParticipant("uuid-a", "Alice", ReplayParticipant.JoinMethod.OWNER);
        a.skinName = "Alice";
        a.skinTextures = "tex-a";
        ReplayParticipant.Segment a1 = a.openSegment(0);
        fillSegment(a1, 10, 0);
        ReplayParticipant.Segment a2 = a.openSegment(25);
        fillSegment(a2, 5, 25);
        data.participants.add(a);

        ReplayParticipant b = new ReplayParticipant("uuid-b", "Bob", ReplayParticipant.JoinMethod.INVITE);
        b.skinName = "Bob";
        ReplayParticipant.Segment b1 = b.openSegment(25);
        fillSegment(b1, 15, 25);
        InputEvent e1 = new InputEvent(3, 0, InputEventType.SELECT_SLOT);
        e1.a = 2;
        b1.input.events.add(e1);
        InputEvent e2 = new InputEvent(14, 0, InputEventType.DIG_START);
        e2.d = 123L;
        b1.input.events.add(e2);
        data.participants.add(b);

        data.diagnostics.captureLimitations.add("多人片段测试");
        return data;
    }

    private static void fillSegment(ReplayParticipant.Segment seg, int ticks, int tickOffset) {
        seg.endTick = seg.startTick + ticks;
        seg.input.ensureDuration(ticks);
        VisualSegment v = new VisualSegment("minecraft:overworld", 0);
        for (int i = 0; i < ticks; i++) {
            seg.input.movementMasks[i] = (byte) (i % 2 == 0 ? InputFlags.FORWARD : 0);
            seg.input.yaw[i] = (tickOffset + i) * 3f;
            seg.input.pitch[i] = 0;
            VisualFrame f = new VisualFrame();
            f.x = tickOffset + i;
            f.y = 64;
            f.z = 0;
            f.bodyYaw = 0;
            f.headYaw = 0;
            f.pitch = 0;
            f.flags = VisualFlags.ON_GROUND;
            f.slot = 7;
            v.frames.add(f);
        }
        seg.visual.segments.add(v);
        seg.visual.equipmentEvents.add(new VisualTrack.EquipmentEvent(
                0, (byte) 0, new byte[]{1, 2, 3}));
        seg.visual.animEvents.add(new VisualTrack.AnimEvent(0, 0, VisualTrack.ANIM_SWING_MAIN, (byte) 0));
    }
}
