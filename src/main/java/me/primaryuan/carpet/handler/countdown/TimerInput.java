package me.primaryuan.carpet.handler.countdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /timer 启动短选项解析（纯 Java，零 MC 依赖）。
 *
 * 输入为尾部整段文本（不含方向与时间）：选项词任意顺序、大小写不敏感，
 * 每类至多一个；选择器词以 @ 开头整体保留原文；{@code --} 后整段为名称，
 * 不再解析选项。解析结果只携带原始值，默认值回填与目标解析由管理器完成。
 *
 * 分词规则：按空白切分，但选择器词内的引号段与方括号段视为一体，
 * 避免 {@code @a[name="a b"]} 被拆碎。
 */
public final class TimerInput {

    /** 解析结果：全部为原始输入，null = 未提供（默认值由管理器按方向回填） */
    public record Parsed(TimerStyle style, TimerLayout layout, boolean silent,
                         String selectorRaw, String label) {
    }

    /** 分词产物：word 为词原文，start/end 为在尾部文本中的区间（end 后即下一词） */
    record Token(String word, int start, int end) {
    }

    private static final int MAX_LABEL_CODE_POINTS = 128;

    private TimerInput() {}

    /**
     * 解析短选项尾部。
     *
     * @throws TimerSyntaxException 类别未知 / 同类重复 / 选择器未闭合 / 名称非法时抛出，
     *                              携带 i18n 键与占位参数
     */
    public static Parsed parse(String tail) {
        if (tail == null || tail.isBlank()) {
            return new Parsed(null, null, false, null, null);
        }
        TimerStyle style = null;
        TimerLayout layout = null;
        boolean silent = false;
        String selector = null;
        List<Token> tokens = tokenize(tail);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            String word = token.word();
            if (word.equals("--")) {
                String label = tail.substring(Math.min(token.end(), tail.length())).trim();
                if (label.isEmpty() || label.codePointCount(0, label.length()) > MAX_LABEL_CODE_POINTS) {
                    throw new TimerSyntaxException("carpetprimaryuan.command.timer.bad_label", MAX_LABEL_CODE_POINTS);
                }
                return new Parsed(style, layout, silent, selector, label);
            }
            TimerStyle parsedStyle = TimerStyle.parse(word);
            if (parsedStyle != null) {
                if (style != null) {
                    throw new TimerSyntaxException("carpetprimaryuan.command.timer.duplicate_option", word);
                }
                style = parsedStyle;
                continue;
            }
            TimerLayout parsedLayout = TimerLayout.parse(word);
            if (parsedLayout != null) {
                if (layout != null) {
                    throw new TimerSyntaxException("carpetprimaryuan.command.timer.duplicate_option", word);
                }
                layout = parsedLayout;
                continue;
            }
            if (word.equalsIgnoreCase("silent")) {
                if (silent) {
                    throw new TimerSyntaxException("carpetprimaryuan.command.timer.duplicate_option", word);
                }
                silent = true;
                continue;
            }
            if (word.startsWith("@")) {
                if (selector != null) {
                    throw new TimerSyntaxException("carpetprimaryuan.command.timer.duplicate_option", word);
                }
                requireClosedSelector(word);
                selector = word;
                continue;
            }
            throw new TimerSyntaxException("carpetprimaryuan.command.timer.bad_option", word);
        }
        return new Parsed(style, layout, silent, selector, null);
    }

    /** 选择器词必须闭合：方括号成对、引号段闭合（未闭合报错，不启动半配置会话） */
    private static void requireClosedSelector(String word) {
        int brackets = 0;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (inQuote) {
                if (c == '\\' && i + 1 < word.length()) {
                    i++;
                } else if (c == quote) {
                    inQuote = false;
                }
                continue;
            }
            switch (c) {
                case '"', '\'' -> {
                    inQuote = true;
                    quote = c;
                }
                case '[' -> brackets++;
                case ']' -> brackets--;
            }
        }
        if (brackets != 0 || inQuote) {
            throw new TimerSyntaxException("carpetprimaryuan.command.timer.unclosed_selector", word);
        }
    }

    /**
     * 分词：按空白切分；引号段与方括号段（含内部空格）粘连为一个词。
     * 方括号内出现引号时引号段继续粘连（选择器参数值可含空格）。
     */
    static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = input.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(input.charAt(i))) {
                i++;
            }
            if (i >= len) {
                break;
            }
            int start = i;
            boolean inQuote = false;
            char quote = 0;
            int brackets = 0;
            while (i < len) {
                char c = input.charAt(i);
                if (inQuote) {
                    if (c == '\\' && i + 1 < len) {
                        i += 2;
                        continue;
                    }
                    if (c == quote) {
                        inQuote = false;
                    }
                    i++;
                    continue;
                }
                if (c == '"') {
                    inQuote = true;
                    quote = c;
                } else if (c == '[') {
                    brackets++;
                } else if (c == ']') {
                    brackets--;
                } else if (Character.isWhitespace(c) && brackets == 0) {
                    break;
                }
                i++;
            }
            tokens.add(new Token(input.substring(start, i), start, i));
        }
        return tokens;
    }

    /**
     * 下一个待输入词的候选（补全用）：已选类别不再候补；
     * 正在输入选择器词（以 @ 开头且未空格断开）时返回空列表，由原版选择器补全接管。
     * 返回值未按前缀过滤，由 SuggestionsBuilder 统一过滤。
     */
    public static List<String> suggestNext(String tail) {
        List<String> out = new ArrayList<>();
        String lastToken = lastIncompleteToken(tail);
        if (lastToken.startsWith("@")) {
            return out;
        }
        Parsed parsed;
        try {
            parsed = parse(tail);
        } catch (TimerSyntaxException e) {
            return out;
        }
        if (parsed.label() != null) {
            // -- 之后整段为名称，不再建议任何词
            return out;
        }
        if (parsed.style() == null) {
            for (TimerStyle style : TimerStyle.values()) {
                out.add(style.id());
            }
        }
        if (parsed.layout() == null) {
            for (TimerLayout layout : TimerLayout.values()) {
                out.add(layout.id());
            }
        }
        if (!parsed.silent()) {
            out.add("silent");
        }
        if (parsed.selectorRaw() == null) {
            out.add("@a");
            out.add("@s");
            out.add("@a[team=");
        }
        out.add("--");
        return out;
    }

    /** 最后一个尚未以空白结束的词（判断是否处于选择器输入中） */
    private static String lastIncompleteToken(String tail) {
        String trimmed = tail.stripTrailing();
        if (trimmed.isEmpty()) {
            return "";
        }
        int space = trimmed.lastIndexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(space + 1);
    }

    /** 解析失败：key 为 i18n 键，args 为占位参数 */
    public static final class TimerSyntaxException extends RuntimeException {

        private final String key;
        private final Object[] args;

        public TimerSyntaxException(String key, Object... args) {
            super(key);
            this.key = key;
            this.args = args;
        }

        public String key() {
            return key;
        }

        public Object[] args() {
            return args;
        }
    }
}
