package me.primaryuan.carpet.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * tick 调度模式与下一次触发延迟的统一计算。
 *
 * {@link DropSlotScheduler}（假人丢物品）与 {@link SendtoLinkManager}（假人背包链接转移）
 * 共用同一套调度语义：模式枚举与触发后的延迟计算在此统一维护，
 * 两个调度器只需实现"触发时做什么"。
 */
public enum ScheduleMode {
    /** 有任务/链接但暂停，不自动触发 */
    NONE,
    /** 每 tick 触发一次 */
    CONTINUOUS,
    /** 每 interval tick 触发一次 */
    INTERVAL,
    /** 延迟 interval tick 后触发一次，成功后结束（由调用方移除任务或转 NONE） */
    AFTER,
    /** 固定频率（每秒 times 次，间隔由命令层换算为 interval tick） */
    PERTICK,
    /** 随机 min~max tick 触发一次 */
    RANDOMLY;

    /**
     * 计算本次触发后的下一次延迟。
     *
     * @param interval  INTERVAL / PERTICK / AFTER 的间隔（tick）
     * @param min       RANDOMLY 最小间隔（tick）
     * @param max       RANDOMLY 最大间隔（tick）
     * @param triggered 本次是否成功触发（仅 AFTER 关心：成功即结束）
     * @return 下一次延迟（tick）；-1 表示本周期结束（调用方移除任务或转 NONE）
     */
    public int nextDelay(int interval, int min, int max, boolean triggered) {
        return switch (this) {
            case NONE -> -1;
            case CONTINUOUS -> 1;
            case INTERVAL, PERTICK -> interval;
            case AFTER -> triggered ? -1 : 1;
            case RANDOMLY -> max > min
                    ? ThreadLocalRandom.current().nextInt(max - min + 1) + min
                    : min;
        };
    }

    /**
     * PERTICK 模式换算：每秒 times 次对应的触发间隔（tick），至少间隔 1 tick。
     * 命令层（dropall / sendto 的 perTick 子命令）统一使用本方法换算。
     */
    public static int perTickInterval(int times) {
        return Math.max(1, 20 / Math.max(1, times));
    }
}
