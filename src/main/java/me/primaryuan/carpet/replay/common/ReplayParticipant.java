package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 录像参与者：稳定 UUID、显示名、加入方式，以及按在线片段切分的双轨数据。
 * 一段录像只有一个共同时间轴；参与者只在真实在线区间内拥有视觉/输入轨道，
 * 离线空档不写空帧，重连后开启新的片段（绝对 tick 继续推进）。
 */
public final class ReplayParticipant
{
    public enum JoinMethod
    {
        OWNER,
        INVITE,
        ADMIN;

        public static JoinMethod byOrdinal(int ordinal)
        {
            JoinMethod[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INVITE;
        }
    }

    public final String uuid;
    public final String name;
    public JoinMethod joinMethod;
    /** 按开始 Tick 升序、互不重叠的在线片段 */
    public final List<Segment> segments = new ArrayList<>();
    /** 幻影皮肤：参与者自己的 profile（离线也可显示） */
    public String skinName = "";
    public String skinUuid = "";
    public String skinTextures = "";
    public String skinSignature = "";
    public String model = "wide";

    public ReplayParticipant(String uuid, String name, JoinMethod joinMethod)
    {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name == null ? "" : name;
        this.joinMethod = joinMethod == null ? JoinMethod.INVITE : joinMethod;
    }

    public Segment segmentAt(int tick)
    {
        for (Segment seg : segments)
        {
            if (seg.covers(tick))
            {
                return seg;
            }
        }
        return null;
    }

    public Segment openSegment(int startTick)
    {
        Segment seg = new Segment(startTick);
        segments.add(seg);
        return seg;
    }

    public Segment firstSegment()
    {
        return segments.isEmpty() ? null : segments.get(0);
    }

    public VisualFrame firstFrame()
    {
        Segment seg = firstSegment();
        return seg == null ? null : seg.visual.frameAt(0);
    }

    public boolean hasContent()
    {
        for (Segment seg : segments)
        {
            if (seg.duration() > 0)
            {
                return true;
            }
        }
        return false;
    }

    /** 单段连续在线区间 */
    public static final class Segment
    {
        public final int startTick;
        /** 半开区间终点；在线期间为 -1，离开/结束录制时固化 */
        public int endTick = -1;
        public final VisualTrack visual = new VisualTrack();
        public final InputTrack input = new InputTrack();

        public Segment(int startTick)
        {
            this.startTick = startTick;
        }

        public int duration()
        {
            return endTick < 0 ? input.duration() : endTick - startTick;
        }

        public boolean covers(int tick)
        {
            return tick >= startTick && (endTick < 0 || tick < endTick);
        }
    }
}
