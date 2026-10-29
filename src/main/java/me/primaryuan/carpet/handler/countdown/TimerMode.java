package me.primaryuan.carpet.handler.countdown;

import java.util.Locale;

/**
 * 计时方向（纯 Java，零 MC 依赖）。
 *
 * DOWN 倒计时：给定时长，向上取整显示剩余整秒，自然归零结束；
 * UP 正计时：从零累计真实经过时间，无上限，仅手动停止结算。
 */
public enum TimerMode {
    DOWN("down"),
    UP("up");

    private final String id;

    TimerMode(String id) {
        this.id = id;
    }

    /** 小写规范名（命令输入与消息共用） */
    public String id() {
        return id;
    }

    /** 大小写不敏感解析；无法识别返回 null（由调用方报错） */
    public static TimerMode parse(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        for (TimerMode mode : values()) {
            if (mode.id.equals(name)) {
                return mode;
            }
        }
        return null;
    }
}
