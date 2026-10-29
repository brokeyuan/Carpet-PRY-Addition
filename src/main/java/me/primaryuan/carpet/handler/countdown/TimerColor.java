package me.primaryuan.carpet.handler.countdown;

/**
 * 进度条配色档（纯 Java，零 MC 依赖；MC 侧映射到 BossEvent.BossBarColor）。
 *
 * 原版 BossBar 无青色，CYAN 映射为最接近的 BLUE（设计取舍已在文档说明）。
 */
public enum TimerColor {
    WHITE,
    CYAN,
    GOLD,
    YELLOW,
    RED,
    GREEN,
    /** 旧入口固定紫色 */
    PURPLE
}
