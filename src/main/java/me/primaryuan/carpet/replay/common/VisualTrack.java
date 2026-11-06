package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉轨道：按维度分段、带绝对段首 Tick 的逐 Tick 真实状态快照，
 * 以及装备变化事件与动画事件（相对当前在线片段的 Tick）。
 * 帧取自参与者实际实体状态，不从按键推算。
 */
public final class VisualTrack
{
    /** 按维度切分的帧段；段内 tick 连续，段间允许空档（暂停恢复/维度切换） */
    public final List<VisualSegment> segments = new ArrayList<>();
    /** 装备变化：tick 时刻某装备槽变为 itemBytes（MC 版本内自序列化，仅同版本可读） */
    public final List<EquipmentEvent> equipmentEvents = new ArrayList<>();
    /** 动画事件（挥手等），同 Tick 内按 seq 保序 */
    public final List<AnimEvent> animEvents = new ArrayList<>();

    public int totalFrames()
    {
        int n = 0;
        for (VisualSegment seg : segments)
        {
            n += seg.frames.size();
        }
        return n;
    }

    /** 返回第 tick 帧所在段，越界返回 null */
    public VisualSegment segmentAt(int tick)
    {
        for (VisualSegment seg : segments)
        {
            if (tick >= seg.startTick && tick < seg.endTick())
            {
                return seg;
            }
        }
        return null;
    }

    /** 第 tick 帧的维度 key，越界返回 null */
    public String dimensionAt(int tick)
    {
        VisualSegment seg = segmentAt(tick);
        return seg == null ? null : seg.dimension;
    }

    /** 第 tick 帧，越界返回 null */
    public VisualFrame frameAt(int tick)
    {
        VisualSegment seg = segmentAt(tick);
        return seg == null ? null : seg.frames.get(tick - seg.startTick);
    }

    /** 第 tick 帧是否为所在段的首帧（播放端强制全量位置，不做跨段插值） */
    public boolean isSegmentStart(int tick)
    {
        VisualSegment seg = segmentAt(tick);
        return seg != null && tick == seg.startTick;
    }

    public VisualTrack copyFrom(VisualTrack other)
    {
        for (VisualSegment seg : other.segments)
        {
            VisualSegment cp = new VisualSegment(seg.dimension, seg.startTick);
            for (VisualFrame f : seg.frames)
            {
                cp.frames.add(f.copy());
            }
            segments.add(cp);
        }
        for (EquipmentEvent e : other.equipmentEvents)
        {
            byte[] bytes = e.itemBytes() == null ? new byte[0] : e.itemBytes().clone();
            equipmentEvents.add(new EquipmentEvent(e.tick(), e.slot(), bytes));
        }
        animEvents.addAll(other.animEvents);
        return this;
    }

    /** 装备槽序（主手/副手/脚/腿/胸/头=0~5） */
    public record EquipmentEvent(int tick, byte slot, byte[] itemBytes) {}

    public record AnimEvent(int tick, int seq, byte type, byte hand) {}

    /** 动画事件类型 */
    public static final byte ANIM_SWING_MAIN = 0;
    public static final byte ANIM_SWING_OFF = 1;
    public static final byte ANIM_DAMAGE = 2;
    public static final byte ANIM_EAT = 3;
    public static final byte ANIM_USE_START = 4;
    public static final byte ANIM_USE_STOP = 5;
}
