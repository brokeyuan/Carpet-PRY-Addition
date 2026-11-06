package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入轨道：操作意图的时间轴（相对当前在线片段，半开区间 [0, duration)）。
 * 逐 Tick 保存移动输入掩码与绝对朝向；事件按 tick+seq 保序。
 * 不保存必须抵达的坐标目标——事件里的目标信息一律是诊断用途。
 */
public final class InputTrack
{
    /** 逐 Tick 移动输入掩码（InputFlags 位或），容量可大于逻辑长度 */
    public byte[] movementMasks = new byte[0];
    /** 逐 Tick 绝对 yaw */
    public float[] yaw = new float[0];
    /** 逐 Tick 绝对 pitch */
    public float[] pitch = new float[0];
    /** 有序事件（tick 升序，同 tick 按 seq） */
    public final List<InputEvent> events = new ArrayList<>();
    /**
     * 脚本片段边界（相对 Tick）：从上一段在线内容拼接到这里的首帧。
     * 播放端先让上一段末帧经过实体 Tick 消费，再释放旧持续动作并重建基线。
     */
    public final List<Integer> segmentStarts = new ArrayList<>();
    /** 有效帧数（区别于预分配容量；编码/播放只认这个长度） */
    private int length;

    public int duration()
    {
        return length;
    }

    public int maskAt(int tick)
    {
        return (tick >= 0 && tick < length) ? movementMasks[tick] & 0xFF : 0;
    }

    public float yawAt(int tick)
    {
        return (tick >= 0 && tick < length) ? yaw[tick] : 0f;
    }

    public float pitchAt(int tick)
    {
        return (tick >= 0 && tick < length) ? pitch[tick] : 0f;
    }

    /**
     * 把有效长度扩到 ticks；容量按 1.5 倍摊销增长，避免逐 Tick 三数组复制。
     * 只允许增长；结束录制时用 {@link #trimToDuration()} 截到实际长度。
     */
    public void ensureDuration(int ticks)
    {
        if (ticks <= length)
        {
            return;
        }
        length = ticks;
        if (movementMasks.length < ticks)
        {
            int grown = Math.max(ticks, Math.max(64, movementMasks.length + (movementMasks.length >> 1)));
            movementMasks = java.util.Arrays.copyOf(movementMasks, grown);
            yaw = java.util.Arrays.copyOf(yaw, grown);
            pitch = java.util.Arrays.copyOf(pitch, grown);
        }
    }

    /** 结束录制/载入后裁掉预分配空间，保证序列化长度与有效帧一致 */
    public void trimToDuration()
    {
        if (movementMasks.length == length)
        {
            return;
        }
        movementMasks = java.util.Arrays.copyOf(movementMasks, length);
        yaw = java.util.Arrays.copyOf(yaw, length);
        pitch = java.util.Arrays.copyOf(pitch, length);
    }

    /** 是否存在真实的输入内容（脚本执行前置检查） */
    public boolean hasInputContent()
    {
        for (int i = 0; i < length; i++)
        {
            if ((movementMasks[i] & 0xFF) != 0)
            {
                return true;
            }
        }
        for (InputEvent e : events)
        {
            if (e.type != InputEventType.SELECT_SLOT)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 保证事件按 (tick, seq) 升序。正常录制已有序，只在检测到逆序时才排序，
     * 使播放端的事件游标可以单调前进。
     */
    public void ensureOrdered()
    {
        for (int i = 1; i < events.size(); i++)
        {
            if (compare(events.get(i - 1), events.get(i)) > 0)
            {
                events.sort(InputTrack::compare);
                return;
            }
        }
    }

    private static int compare(InputEvent a, InputEvent b)
    {
        int byTick = Integer.compare(a.tick, b.tick);
        return byTick != 0 ? byTick : Integer.compare(a.seq, b.seq);
    }

    /** 返回 [from,to) 中该事件类型的全部事件（to 为开区间上界） */
    public List<InputEvent> eventsInRange(int from, int to, InputEventType type)
    {
        List<InputEvent> out = new ArrayList<>();
        for (InputEvent e : events)
        {
            if (e.tick >= from && e.tick < to && e.type == type)
            {
                out.add(e);
            }
        }
        return out;
    }

    /** 深拷贝 */
    public InputTrack copyFrom(InputTrack other)
    {
        ensureDuration(other.length);
        System.arraycopy(other.movementMasks, 0, movementMasks, 0, other.length);
        System.arraycopy(other.yaw, 0, yaw, 0, other.length);
        System.arraycopy(other.pitch, 0, pitch, 0, other.length);
        trimToDuration();
        for (InputEvent e : other.events)
        {
            InputEvent cp = new InputEvent(e.tick, e.seq, e.type);
            cp.hand = e.hand;
            cp.a = e.a;
            cp.b = e.b;
            cp.c = e.c;
            cp.d = e.d;
            events.add(cp);
        }
        segmentStarts.addAll(other.segmentStarts);
        return this;
    }
}
