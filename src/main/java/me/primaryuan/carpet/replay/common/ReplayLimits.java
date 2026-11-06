package me.primaryuan.carpet.replay.common;

/** 录像资源限制基线（可按压测结果调整常量） */
public final class ReplayLimits
{
    /** 单段最长 10 分钟（12000 Tick），也是服务器允许的最大录制时长 */
    public static final int MAX_DURATION_TICKS = 12000;
    /** 默认录制时长 10 分钟 */
    public static final int DEFAULT_DURATION_TICKS = MAX_DURATION_TICKS;
    /** 最短录制时长 30 秒 */
    public static final int MIN_DURATION_TICKS = 600;
    /** 单个任务最多参与者 */
    public static final int MAX_PARTICIPANTS = 16;
    /** 每人最多 20 段 */
    public static final int MAX_PER_PLAYER = 20;
    /** 单文件解压前大小上限 16MB */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    /** 录像名称长度上限 */
    public static final int MAX_NAME_LENGTH = 32;
    /** 回放实例总数上限 */
    public static final int MAX_ACTIVE_PLAYBACKS = 16;
    /** 单个观看者同时回放实例上限 */
    public static final int MAX_PLAYBACKS_PER_VIEWER = 4;
    /** 每个参与者的在线片段上限（含暂停切段） */
    public static final int MAX_SEGMENTS_PER_PARTICIPANT = 256;
    /** 单段视觉帧总数上限（防多人×时长爆炸式文件） */
    public static final int MAX_TOTAL_FRAMES = 200_000;
    /** 输入事件、装备事件、动画事件的总量上限 */
    public static final int MAX_TOTAL_EVENTS = 200_000;
    /** 同时运行的假人脚本上限 */
    public static final int MAX_ACTIVE_SCRIPTS = 16;
    /** 等待假人上线的启动请求上限 */
    public static final int MAX_PENDING_SCRIPTS = 8;
    /** IO 队列最多排队的保存任务数 */
    public static final int MAX_PENDING_SAVES = 8;
    /** 排队保存的内存估算上限 */
    public static final long MAX_PENDING_SAVE_BYTES = 64L * 1024 * 1024;

    private ReplayLimits() {}

    public static int clampDurationTicks(int ticks)
    {
        if (ticks < MIN_DURATION_TICKS)
        {
            return MIN_DURATION_TICKS;
        }
        return Math.min(ticks, MAX_DURATION_TICKS);
    }
}
