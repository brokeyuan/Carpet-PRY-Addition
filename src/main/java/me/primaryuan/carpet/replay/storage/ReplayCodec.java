package me.primaryuan.carpet.replay.storage;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputTrack;
import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayFormatException;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayMetadata;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import me.primaryuan.carpet.replay.common.VisualTrack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 录像编解码 v2：多人参与者、按在线片段分块的自定义二进制 + GZIP。
 * 不依赖 MC NBT；格式带版本号，v1 明确拒绝；读取全程限额防解压炸弹与损坏文件。
 * 每名参与者的每个在线片段是独立数据块，长录像不会退化成单一巨型逐 Tick 数组。
 */
public final class ReplayCodec
{
    private ReplayCodec() {}

    public static byte[] encode(ReplayData data)
    {
        try
        {
            validate(data);
        }
        catch (ReplayFormatException e)
        {
            throw new IllegalStateException("录像数据未通过保存校验: " + e.getMessage(), e);
        }
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
            GZIPOutputStream gos = new GZIPOutputStream(bos, 1 << 15);
            DataOutputStream out = new DataOutputStream(gos);
            writeData(out, data);
            out.close();
            byte[] bytes = bos.toByteArray();
            if (bytes.length > ReplayLimits.MAX_FILE_BYTES)
            {
                throw new IllegalStateException("录像编码超过大小上限: " + bytes.length);
            }
            return bytes;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("ReplayCodec 编码失败", e);
        }
    }

    public static ReplayData decode(byte[] bytes) throws ReplayFormatException
    {
        try
        {
            if (bytes == null || bytes.length == 0)
            {
                throw new ReplayFormatException("录像文件为空");
            }
            if (bytes.length > ReplayLimits.MAX_FILE_BYTES)
            {
                throw new ReplayFormatException("录像超过大小上限: " + bytes.length);
            }
            DataInputStream in = new DataInputStream(new LimitedGzipInputStream(bytes, ReplayLimits.MAX_FILE_BYTES));
            ReplayData data = readData(in);
            in.close();
            return data;
        }
        catch (ReplayFormatException e)
        {
            throw e;
        }
        catch (IOException | RuntimeException e)
        {
            throw new ReplayFormatException("录像解析失败: " + e.getMessage(), e);
        }
    }

    private static void writeData(DataOutputStream out, ReplayData data) throws IOException
    {
        ReplayMetadata m = data.metadata;
        out.writeInt(ReplayMetadata.FORMAT_VERSION);
        writeUtf(out, m.recordingId);
        writeUtf(out, m.ownerUuid);
        writeUtf(out, m.ownerName);
        writeUtf(out, m.name);
        out.writeLong(m.createdAtEpochMillis);
        out.writeInt(m.durationTicks);
        out.writeByte(m.access == null ? ReplayAccess.PUBLIC.ordinal() : m.access.ordinal());
        writeUtf(out, m.initialDimension);
        out.writeDouble(m.initialX);
        out.writeDouble(m.initialY);
        out.writeDouble(m.initialZ);
        out.writeFloat(m.initialYaw);
        out.writeFloat(m.initialPitch);
        out.writeBoolean(m.recoveredFromCrash);

        out.writeInt(data.participants.size());
        for (ReplayParticipant p : data.participants)
        {
            writeUtf(out, p.uuid);
            writeUtf(out, p.name);
            out.writeByte(p.joinMethod.ordinal());
            writeUtf(out, p.skinName);
            writeUtf(out, p.skinUuid);
            writeUtf(out, p.skinTextures);
            writeUtf(out, p.skinSignature);
            writeUtf(out, p.model);
            out.writeInt(p.segments.size());
            for (ReplayParticipant.Segment seg : p.segments)
            {
                int duration = seg.duration();
                out.writeInt(seg.startTick);
                out.writeInt(duration);
                out.writeInt(seg.visual.segments.size());
                for (VisualSegment v : seg.visual.segments)
                {
                    writeUtf(out, v.dimension);
                    out.writeInt(v.startTick);
                    out.writeInt(v.frames.size());
                    for (VisualFrame f : v.frames)
                    {
                        out.writeDouble(f.x);
                        out.writeDouble(f.y);
                        out.writeDouble(f.z);
                        out.writeFloat(f.vx);
                        out.writeFloat(f.vy);
                        out.writeFloat(f.vz);
                        out.writeFloat(f.bodyYaw);
                        out.writeFloat(f.headYaw);
                        out.writeFloat(f.pitch);
                        out.writeByte(f.flags);
                        out.writeInt(f.slot);
                    }
                }
                out.writeInt(seg.visual.equipmentEvents.size());
                for (VisualTrack.EquipmentEvent e : seg.visual.equipmentEvents)
                {
                    out.writeInt(e.tick());
                    out.writeByte(e.slot());
                    byte[] item = e.itemBytes() == null ? new byte[0] : e.itemBytes();
                    out.writeInt(item.length);
                    out.write(item);
                }
                out.writeInt(seg.visual.animEvents.size());
                for (VisualTrack.AnimEvent e : seg.visual.animEvents)
                {
                    out.writeInt(e.tick());
                    out.writeInt(e.seq());
                    out.writeByte(e.type());
                    out.writeByte(e.hand());
                }
                int ticks = duration;
                out.writeInt(ticks);
                for (int i = 0; i < ticks; i++)
                {
                    out.writeByte(i < seg.input.movementMasks.length ? seg.input.movementMasks[i] : 0);
                    out.writeFloat(i < seg.input.yaw.length ? seg.input.yaw[i] : 0f);
                    out.writeFloat(i < seg.input.pitch.length ? seg.input.pitch[i] : 0f);
                }
                out.writeInt(seg.input.events.size());
                for (InputEvent e : seg.input.events)
                {
                    out.writeInt(e.tick);
                    out.writeInt(e.seq);
                    out.writeByte(e.type.ordinal());
                    out.writeByte(e.hand);
                    out.writeInt(e.a);
                    out.writeInt(e.b);
                    out.writeInt(e.c);
                    out.writeLong(e.d);
                }
                out.writeInt(seg.input.segmentStarts.size());
                for (int start : seg.input.segmentStarts)
                {
                    out.writeInt(start);
                }
            }
        }

        out.writeInt(data.diagnostics.captureLimitations.size());
        for (String s : data.diagnostics.captureLimitations)
        {
            writeUtf(out, s);
        }
    }

    private static ReplayData readData(DataInputStream in) throws IOException, ReplayFormatException
    {
        ReplayData data = new ReplayData();
        ReplayMetadata m = data.metadata;
        m.formatVersion = in.readInt();
        if (m.formatVersion != ReplayMetadata.FORMAT_VERSION)
        {
            throw new ReplayFormatException("不支持的录像格式版本: " + m.formatVersion
                    + "（当前版本不兼容旧单人录像）");
        }
        m.recordingId = in.readUTF();
        m.ownerUuid = in.readUTF();
        m.ownerName = in.readUTF();
        m.name = in.readUTF();
        m.createdAtEpochMillis = in.readLong();
        m.durationTicks = in.readInt();
        m.access = ReplayAccess.byOrdinal(in.readByte());
        m.initialDimension = in.readUTF();
        m.initialX = in.readDouble();
        m.initialY = in.readDouble();
        m.initialZ = in.readDouble();
        m.initialYaw = in.readFloat();
        m.initialPitch = in.readFloat();
        m.recoveredFromCrash = in.readBoolean();

        int participants = readLimitedCount(in, ReplayLimits.MAX_PARTICIPANTS);
        for (int pi = 0; pi < participants; pi++)
        {
            ReplayParticipant p = new ReplayParticipant(in.readUTF(), in.readUTF(),
                    ReplayParticipant.JoinMethod.byOrdinal(in.readByte()));
            p.skinName = in.readUTF();
            p.skinUuid = in.readUTF();
            p.skinTextures = in.readUTF();
            p.skinSignature = in.readUTF();
            p.model = in.readUTF();
            int segCount = readLimitedCount(in, ReplayLimits.MAX_SEGMENTS_PER_PARTICIPANT);
            for (int si = 0; si < segCount; si++)
            {
                int start = in.readInt();
                int duration = readIntBounded(in, ReplayLimits.MAX_DURATION_TICKS, "片段时长");
                ReplayParticipant.Segment seg = new ReplayParticipant.Segment(start);
                seg.endTick = start + duration;
                int vSegCount = readLimitedCount(in, ReplayLimits.MAX_SEGMENTS_PER_PARTICIPANT);
                for (int vi = 0; vi < vSegCount; vi++)
                {
                    VisualSegment v = new VisualSegment(in.readUTF(), in.readInt());
                    int frames = readLimitedCount(in, ReplayLimits.MAX_DURATION_TICKS);
                    for (int fi = 0; fi < frames; fi++)
                    {
                        VisualFrame f = new VisualFrame();
                        f.x = in.readDouble();
                        f.y = in.readDouble();
                        f.z = in.readDouble();
                        f.vx = in.readFloat();
                        f.vy = in.readFloat();
                        f.vz = in.readFloat();
                        f.bodyYaw = in.readFloat();
                        f.headYaw = in.readFloat();
                        f.pitch = in.readFloat();
                        f.flags = in.readByte();
                        f.slot = in.readInt();
                        v.frames.add(f);
                    }
                    seg.visual.segments.add(v);
                }
                int equipCount = readLimitedCount(in, ReplayLimits.MAX_TOTAL_EVENTS);
                for (int ei = 0; ei < equipCount; ei++)
                {
                    int tick = in.readInt();
                    byte slot = in.readByte();
                    int len = readLimitedCount(in, ReplayLimits.MAX_FILE_BYTES);
                    byte[] item = new byte[len];
                    in.readFully(item);
                    seg.visual.equipmentEvents.add(new VisualTrack.EquipmentEvent(tick, slot, item));
                }
                int animCount = readLimitedCount(in, ReplayLimits.MAX_TOTAL_EVENTS);
                for (int ai = 0; ai < animCount; ai++)
                {
                    seg.visual.animEvents.add(new VisualTrack.AnimEvent(
                            in.readInt(), in.readInt(), in.readByte(), in.readByte()));
                }
                int ticks = readIntBounded(in, ReplayLimits.MAX_DURATION_TICKS, "输入时长");
                seg.input.ensureDuration(ticks);
                for (int i = 0; i < ticks; i++)
                {
                    seg.input.movementMasks[i] = in.readByte();
                    seg.input.yaw[i] = in.readFloat();
                    seg.input.pitch[i] = in.readFloat();
                }
                seg.input.trimToDuration();
                int eventCount = readLimitedCount(in, ReplayLimits.MAX_TOTAL_EVENTS);
                InputEventType[] types = InputEventType.values();
                for (int ei = 0; ei < eventCount; ei++)
                {
                    int tick = in.readInt();
                    int seq = in.readInt();
                    int ord = in.readByte();
                    if (ord < 0 || ord >= types.length)
                    {
                        throw new ReplayFormatException("未知输入事件类型: " + ord);
                    }
                    InputEvent e = new InputEvent(tick, seq, types[ord]);
                    e.hand = in.readByte();
                    e.a = in.readInt();
                    e.b = in.readInt();
                    e.c = in.readInt();
                    e.d = in.readLong();
                    seg.input.events.add(e);
                }
                int startCount = readLimitedCount(in, ReplayLimits.MAX_SEGMENTS_PER_PARTICIPANT);
                for (int i = 0; i < startCount; i++)
                {
                    seg.input.segmentStarts.add(in.readInt());
                }
                p.segments.add(seg);
            }
            data.participants.add(p);
        }

        int limCount = readLimitedCount(in, 1 << 12);
        for (int i = 0; i < limCount; i++)
        {
            data.diagnostics.captureLimitations.add(in.readUTF());
        }

        validate(data);
        return data;
    }

    private static void validate(ReplayData data) throws ReplayFormatException
    {
        int total = data.metadata.durationTicks;
        if (total <= 0 || total > ReplayLimits.MAX_DURATION_TICKS)
        {
            throw new ReplayFormatException("录像时长非法: " + total);
        }
        if (data.participants.isEmpty() || data.participants.size() > ReplayLimits.MAX_PARTICIPANTS)
        {
            throw new ReplayFormatException("参与者数量非法: " + data.participants.size());
        }
        Set<String> ids = new HashSet<>();
        int frames = 0;
        int events = 0;
        for (ReplayParticipant p : data.participants)
        {
            if (p.uuid.isEmpty() || !ids.add(p.uuid))
            {
                throw new ReplayFormatException("参与者 UUID 重复或为空");
            }
            int lastEnd = -1;
            for (ReplayParticipant.Segment seg : p.segments)
            {
                int duration = seg.duration();
                if (duration < 0 || seg.startTick < 0 || seg.startTick < lastEnd
                        || seg.endTick > total)
                {
                    throw new ReplayFormatException("参与者片段越界: " + seg.startTick + "+" + duration);
                }
                lastEnd = seg.endTick;
                if (seg.visual.totalFrames() != duration || seg.input.duration() != duration)
                {
                    throw new ReplayFormatException("轨道长度与片段时长不一致");
                }
                int visualEnd = 0;
                int visualFrames = 0;
                for (VisualSegment v : seg.visual.segments)
                {
                    if (v.startTick < visualEnd || v.startTick < 0
                            || v.startTick + v.frames.size() > duration)
                    {
                        throw new ReplayFormatException("视觉段越界或重叠");
                    }
                    visualEnd = v.startTick + v.frames.size();
                    visualFrames += v.frames.size();
                }
                if (visualFrames != duration)
                {
                    throw new ReplayFormatException("视觉段未覆盖完整在线片段");
                }
                frames += duration;
                events += seg.visual.equipmentEvents.size() + seg.visual.animEvents.size()
                        + seg.input.events.size();
                for (VisualTrack.EquipmentEvent e : seg.visual.equipmentEvents)
                {
                    if (e.tick() < 0 || e.tick() >= duration)
                    {
                        throw new ReplayFormatException("装备事件越界: tick=" + e.tick());
                    }
                }
                for (VisualTrack.AnimEvent e : seg.visual.animEvents)
                {
                    if (e.tick() < 0 || e.tick() >= duration || e.seq() < 0)
                    {
                        throw new ReplayFormatException("动画事件越界: tick=" + e.tick());
                    }
                }
                for (InputEvent e : seg.input.events)
                {
                    if (e.tick < 0 || e.tick >= duration || e.seq < 0)
                    {
                        throw new ReplayFormatException("输入事件越界: tick=" + e.tick);
                    }
                }
                for (int start : seg.input.segmentStarts)
                {
                    if (start <= 0 || start >= duration)
                    {
                        throw new ReplayFormatException("脚本片段边界越界: " + start);
                    }
                }
            }
        }
        if (frames > ReplayLimits.MAX_TOTAL_FRAMES)
        {
            throw new ReplayFormatException("视觉帧总数超限: " + frames);
        }
        if (events > ReplayLimits.MAX_TOTAL_EVENTS)
        {
            throw new ReplayFormatException("事件总数超限: " + events);
        }
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException
    {
        out.writeUTF(s == null ? "" : s);
    }

    private static int readIntBounded(DataInputStream in, int max, String what)
            throws IOException, ReplayFormatException
    {
        int n = in.readInt();
        if (n < 0 || n > max)
        {
            throw new ReplayFormatException(what + "越界: " + n);
        }
        return n;
    }

    private static int readLimitedCount(DataInputStream in, int max) throws IOException, ReplayFormatException
    {
        int n = in.readInt();
        if (n < 0 || n > max)
        {
            throw new ReplayFormatException("计数越界: " + n);
        }
        return n;
    }

    /** 带 GZIP 解压总量限制的输入流：防止解压炸弹 */
    private static final class LimitedGzipInputStream extends GZIPInputStream
    {
        private long remaining;

        LimitedGzipInputStream(byte[] bytes, int limit) throws IOException
        {
            super(new ByteArrayInputStream(bytes), 1 << 15);
            this.remaining = limit;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException
        {
            int n = super.read(buf, off, (int) Math.min(len, remaining + 1));
            if (n > 0)
            {
                remaining -= n;
                if (remaining < 0)
                {
                    throw new EOFException("解压超过大小上限");
                }
            }
            return n;
        }
    }
}
