package me.primaryuan.carpet.handler.countdown;

import java.util.Locale;

/**
 * 效果档位（纯 Java，零 MC 依赖；颜色与音效配方映射在 Planner/Display）。
 *
 * SIMPLE：白字白条、无声音、无节点，仅布局允许的常态显示；
 * NORMAL：青条、最后 10 秒黄 3 秒红，both 最后 5 秒中央倒数并配 3/2/1 短音，结束短音；
 * FANCY：60 秒内金色强调，both 最后 10 秒逐秒音高倒数，60/30 秒节点提示，开场与结束音更完整。
 */
public enum TimerStyle {
    SIMPLE("simple"),
    NORMAL("normal"),
    FANCY("fancy");

    private final String id;

    TimerStyle(String id) {
        this.id = id;
    }

    /** 小写规范名 */
    public String id() {
        return id;
    }

    /** 大小写不敏感解析；无法识别返回 null（由调用方报错） */
    public static TimerStyle parse(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        for (TimerStyle style : values()) {
            if (style.id.equals(name)) {
                return style;
            }
        }
        return null;
    }

    /** 档位默认音效层（无声档显式 sound on 时提升到 NORMAL，见会话 soundTier） */
    public boolean hasDefaultSound() {
        return this != SIMPLE;
    }
}
