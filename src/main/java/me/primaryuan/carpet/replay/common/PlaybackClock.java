package me.primaryuan.carpet.replay.common;

/**
 * 播放时钟：半开区间 [0, durationTicks) 的时间轴推进。
 * 支持单次（maxLoops=1）、指定次数、无限循环；暂停时不推进、不重复瞬时事件。
 */
public final class PlaybackClock
{
    public final int durationTicks;
    /** 总轮数：1=单次，n=执行 n 轮，-1=无限循环 */
    public final int maxLoops;
    public int tick;
    /** 已完成的整轮数 */
    public int loopsDone;
    public boolean finished;
    public boolean paused;

    public PlaybackClock(int durationTicks, int maxLoops)
    {
        if (durationTicks <= 0)
        {
            throw new IllegalArgumentException("durationTicks 必须为正");
        }
        this.durationTicks = durationTicks;
        this.maxLoops = maxLoops;
    }

    /** 是否无限循环 */
    public boolean looping()
    {
        return maxLoops < 0;
    }

    /**
     * 推进一 Tick。
     * @return 本 Tick 应播放的时间轴位置；暂停或已结束返回 -1（不执行任何帧）
     */
    public int advance()
    {
        if (finished || paused)
        {
            return -1;
        }
        int current = tick;
        tick++;
        if (tick >= durationTicks)
        {
            if (maxLoops < 0 || loopsDone + 1 < maxLoops)
            {
                tick = 0;
                loopsDone++;
            }
            else
            {
                tick = durationTicks;
                loopsDone++;
                finished = true;
            }
        }
        return current;
    }

    /** 是否刚进入新一轮（本轮 tick==0 且非首轮），供边界处理 */
    public boolean atRoundStart()
    {
        return tick == 0 && loopsDone > 0 && !finished;
    }
}
