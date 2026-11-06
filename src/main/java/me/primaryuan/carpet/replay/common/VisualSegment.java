package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉轨道的同维度连续帧段。
 * startTick 是相对于参与者当前在线片段的 Tick；段内第 i 帧对应 startTick+i。
 */
public final class VisualSegment
{
    /** 维度 key，如 minecraft:overworld */
    public final String dimension;
    /** 段首相对 Tick（同维度暂停恢复会产生新段） */
    public final int startTick;
    public final List<VisualFrame> frames = new ArrayList<>();

    public VisualSegment(String dimension)
    {
        this(dimension, 0);
    }

    public VisualSegment(String dimension, int startTick)
    {
        this.dimension = dimension;
        this.startTick = startTick;
    }

    public int endTick()
    {
        return startTick + frames.size();
    }
}
