package me.primaryuan.carpet.handler.countdown;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 倒计时显示渠道（纯 Java，零 MC 依赖；MC 侧映射在 CountdownDisplay）。
 *
 * 组合语法：{@code bossbar+title} 等以 + 连接，大小写不敏感、重复去重；
 * 枚举声明顺序即组合的规范展示顺序。
 */
public enum CountdownChannel {
    BOSSBAR("bossbar"),
    TITLE("title"),
    SUBTITLE("subtitle"),
    ACTIONBAR("actionbar");

    private final String id;

    CountdownChannel(String id) {
        this.id = id;
    }

    /** 小写规范名（命令输入、补全与消息展示共用） */
    public String id() {
        return id;
    }

    /**
     * 解析 + 组合表达式。
     *
     * @throws IllegalArgumentException 存在无法识别的渠道段时抛出，消息为该段原文
     */
    public static EnumSet<CountdownChannel> parseCombo(String raw) {
        EnumSet<CountdownChannel> out = EnumSet.noneOf(CountdownChannel.class);
        for (String token : raw.split("\\+")) {
            String name = token.trim().toLowerCase(Locale.ROOT);
            CountdownChannel channel = null;
            for (CountdownChannel candidate : values()) {
                if (candidate.id.equals(name)) {
                    channel = candidate;
                    break;
                }
            }
            if (channel == null) {
                throw new IllegalArgumentException(token.trim());
            }
            out.add(channel);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(raw);
        }
        return out;
    }

    /** 规范组合串，如 "bossbar+subtitle"（按枚举声明顺序） */
    public static String formatCombo(Set<CountdownChannel> channels) {
        StringBuilder out = new StringBuilder();
        for (CountdownChannel channel : channels) {
            if (out.length() > 0) {
                out.append('+');
            }
            out.append(channel.id);
        }
        return out.toString();
    }

    /** 补全候选：未使用且匹配已输入前缀的渠道 */
    public static void suggestNext(String typed, Set<CountdownChannel> used, EnumSet<CountdownChannel> out) {
        for (CountdownChannel channel : values()) {
            if (!used.contains(channel) && channel.id.startsWith(typed)) {
                out.add(channel);
            }
        }
    }
}
