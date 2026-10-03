package me.primaryuan.carpet.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * & 色码文本解析（多规则共用）：&0~&f 颜色、&l/&o/&n/&m/&k 样式、&r 复位、
 * && 为字面 &；色码按原版 § 语义重置已积累的样式标志。非法码按字面 & 处理。
 */
public final class ColorText {

    /** 默认字色（米塔系文本统一白） */
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    /** 一个已解析字符：字形 + 样式 */
    public record Char(char c, Style style) {}

    private ColorText() {}

    /** 解析 & 色码为逐字符样式序列 */
    public static List<Char> parse(String raw) {
        return parse(raw, DEFAULT_COLOR);
    }

    public static List<Char> parse(String raw, int defaultColor) {
        List<Char> out = new ArrayList<>();
        int color = defaultColor;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if (code == '&') {
                    out.add(new Char('&', styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
                    i++;
                    continue;
                }
                ChatFormatting format = ChatFormatting.getByCode(code);
                if (format != null) {
                    // 颜色判定走 TextColor.fromLegacyFormat（26.2 起 ChatFormatting
                    // 自身的 isColor/getColor 被移除，而此方法全版本可用）
                    TextColor legacy = TextColor.fromLegacyFormat(format);
                    if (legacy != null) {
                        color = legacy.getValue();
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    } else if (format == ChatFormatting.RESET) {
                        color = defaultColor;
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    } else if (format == ChatFormatting.BOLD) {
                        bold = true;
                    } else if (format == ChatFormatting.ITALIC) {
                        italic = true;
                    } else if (format == ChatFormatting.UNDERLINE) {
                        underlined = true;
                    } else if (format == ChatFormatting.STRIKETHROUGH) {
                        strikethrough = true;
                    } else if (format == ChatFormatting.OBFUSCATED) {
                        obfuscated = true;
                    }
                    i++;
                    continue;
                }
                // 非法码：按字面 & 处理，下一个字符照常解析
                out.add(new Char('&', styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
                continue;
            }
            out.add(new Char(c, styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
        }
        return out;
    }

    /** 解析并合并连续同样式字符为 Component 树 */
    public static MutableComponent build(String raw) {
        List<Char> chars = parse(raw);
        MutableComponent out = Component.empty();
        int start = 0;
        while (start < chars.size()) {
            Style style = chars.get(start).style();
            int end = start;
            while (end < chars.size() && chars.get(end).style().equals(style)) {
                end++;
            }
            StringBuilder sb = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                sb.append(chars.get(i).c());
            }
            out.append(Component.literal(sb.toString()).setStyle(style));
            start = end;
        }
        return out;
    }

    private static Style styleOf(int color, boolean bold, boolean italic, boolean underlined,
                                 boolean strikethrough, boolean obfuscated) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));
        if (bold) {
            style = style.withBold(true);
        }
        if (italic) {
            style = style.withItalic(true);
        }
        if (underlined) {
            style = style.withUnderlined(true);
        }
        if (strikethrough) {
            style = style.withStrikethrough(true);
        }
        if (obfuscated) {
            style = style.withObfuscated(true);
        }
        return style;
    }
}
