package me.primaryuan.carpet.handler.countdown;

import java.util.EnumSet;
import java.util.Locale;

/**
 * 展示位置（纯 Java，零 MC 依赖；渠道约束先于效果——不允许的渠道绝不发包）。
 *
 * BAR 顶部 BossBar；CENTER 中央大字（全程）；ACTIONBAR 动作栏；
 * BOTH 顶部全程 + 中央仅事件与最后倒数窗口。
 */
public enum TimerLayout {
    BAR("bar"),
    CENTER("center"),
    ACTIONBAR("actionbar"),
    BOTH("both");

    private final String id;

    TimerLayout(String id) {
        this.id = id;
    }

    /** 小写规范名 */
    public String id() {
        return id;
    }

    /** 大小写不敏感解析；无法识别返回 null（由调用方报错） */
    public static TimerLayout parse(String raw) {
        String name = raw.toLowerCase(Locale.ROOT);
        for (TimerLayout layout : values()) {
            if (layout.id.equals(name)) {
                return layout;
            }
        }
        return null;
    }

    /**
     * 布局允许的显示渠道；CountdownDisplay 据此门控发包，
     * 选择 bar/actionbar 时绝不发 Title/Action 之外的中央包。
     */
    public EnumSet<CountdownChannel> channels() {
        return switch (this) {
            case BAR -> EnumSet.of(CountdownChannel.BOSSBAR);
            case CENTER -> EnumSet.of(CountdownChannel.TITLE, CountdownChannel.SUBTITLE);
            case ACTIONBAR -> EnumSet.of(CountdownChannel.ACTIONBAR);
            case BOTH -> EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE, CountdownChannel.SUBTITLE);
        };
    }

    /** 中央大字是否属于该布局 */
    public boolean hasCentral() {
        return this == CENTER || this == BOTH;
    }

    /** 顶部 BossBar 是否属于该布局 */
    public boolean hasBar() {
        return this == BAR || this == BOTH;
    }

    /** 动作栏是否属于该布局 */
    public boolean hasActionbar() {
        return this == ACTIONBAR;
    }

    /**
     * 启动默认布局：显式选择优先；fancy 未显式选布局时两种方向都用 both；
     * 其余按方向取默认（down 顶部、up 动作栏）。
     */
    public static TimerLayout resolve(TimerMode mode, TimerStyle style, TimerLayout explicit) {
        if (explicit != null) {
            return explicit;
        }
        if (style == TimerStyle.FANCY) {
            return BOTH;
        }
        return mode == TimerMode.DOWN ? BAR : ACTIONBAR;
    }
}
