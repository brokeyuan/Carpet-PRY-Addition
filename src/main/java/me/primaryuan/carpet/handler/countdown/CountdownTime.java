package me.primaryuan.carpet.handler.countdown;

import java.util.Locale;

/**
 * 倒计时时间解析与格式化（纯 Java，零 MC 依赖，供单测直接覆盖）。
 *
 * 输入格式：{@code 60}、{@code 60s}、{@code 1m}、{@code 1m30s}、{@code 2h}、{@code 1h30m}——
 * 单位 s/m/h（大小写不敏感）按 h>m>s 降序各至多一次，每段正整数；无单位整数按秒。
 * 合法范围 [1, 86400] 秒（1 秒 ~ 24 小时）；非法输入抛 IllegalArgumentException，
 * 消息为原始输入，由调用方补足格式说明。
 */
public final class CountdownTime {

    /** 上限 24 小时（秒） */
    public static final long MAX_SECONDS = 24L * 3600;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private CountdownTime() {}

    /**
     * 解析时间输入为秒数。
     *
     * @throws IllegalArgumentException 输入为空、含非 ASCII 数字字符、单位非法或乱序、
     *                                  段值非正、总和越界（[1, 86400]）时抛出，消息为原始输入
     */
    public static long parseSeconds(String raw) {
        String input = raw.trim();
        if (input.isEmpty()) {
            throw new IllegalArgumentException(raw);
        }
        // 无单位整数按秒（仅 ASCII 数字，拒绝 "+60"/"-1"/全角数字）
        if (isAsciiDigits(input)) {
            return checked(Long.parseLong(input), raw);
        }
        long total = 0;
        int lastOrder = Integer.MAX_VALUE; // 单位降序：h(2) > m(1) > s(0)，后段必须更小
        int i = 0;
        while (i < input.length()) {
            int digitsEnd = i;
            while (digitsEnd < input.length() && isAsciiDigit(input.charAt(digitsEnd))) {
                digitsEnd++;
            }
            if (digitsEnd == i || digitsEnd >= input.length()) {
                throw new IllegalArgumentException(raw);
            }
            long value;
            try {
                value = Long.parseLong(input.substring(i, digitsEnd));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(raw);
            }
            char unit = Character.toLowerCase(input.charAt(digitsEnd));
            int order = switch (unit) {
                case 'h' -> 2;
                case 'm' -> 1;
                case 's' -> 0;
                default -> -1;
            };
            if (order < 0 || order >= lastOrder || value <= 0) {
                throw new IllegalArgumentException(raw);
            }
            lastOrder = order;
            if (value > MAX_SECONDS) {
                throw new IllegalArgumentException(raw);
            }
            long unitSeconds = switch (unit) {
                case 'h' -> value * 3600;
                case 'm' -> value * 60;
                default -> value;
            };
            total = checked(total + unitSeconds, raw);
            i = digitsEnd + 1;
        }
        return checked(total, raw);
    }

    private static long checked(long seconds, String raw) {
        if (seconds < 1 || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException(raw);
        }
        return seconds;
    }

    private static boolean isAsciiDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isAsciiDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** 剩余纳秒向上取整为整秒（<=0 返回 0；用于避免提前出现 00:00） */
    public static long ceilSeconds(long remainingNanos) {
        if (remainingNanos <= 0) {
            return 0;
        }
        return (remainingNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }

    /** 秒 → {@code MM:SS} 或 {@code H:MM:SS}（>=1 小时），如 5→00:05、65→01:05、3600→01:00:00 */
    public static String formatClock(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    /** 纯 Title 数字：60 秒内无前导零（5→"5"），超过则回落 MM:SS */
    public static String formatTitleNumber(long seconds) {
        if (seconds > 59) {
            return formatClock(seconds);
        }
        return String.valueOf(seconds);
    }

    /**
     * 正计时运行显示：纳秒向下截断到十分之一秒 → {@code MM:SS.t} 或 {@code H:MM:SS.t}
     * （>=1 小时），如 1.234s→00:01.2；截断保证不提前进位。小时可超过两位，不设 24 小时回卷。
     */
    public static String formatUpTenths(long elapsedNanos) {
        long tenths = Math.max(0, elapsedNanos) / 100_000_000L;
        return formatWithFraction(tenths, 1);
    }

    /** 正计时最终结果：向下截断到毫秒 → {@code MM:SS.mmm} 或 {@code H:MM:SS.mmm} */
    public static String formatUpMillis(long elapsedNanos) {
        long millis = Math.max(0, elapsedNanos) / 1_000_000L;
        return formatWithFraction(millis, 3);
    }

    /** fractionDigits 位小数（1=十分之一秒 / 3=毫秒），整体向下截断 */
    private static String formatWithFraction(long scaledValue, int fractionDigits) {
        long whole = scaledValue / powersOfTen[fractionDigits];
        long fraction = scaledValue % powersOfTen[fractionDigits];
        long h = whole / 3600;
        long m = whole % 3600 / 60;
        long s = whole % 60;
        String head = h > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%02d:%02d", m, s);
        return String.format(Locale.ROOT, "%s.%0" + fractionDigits + "d", head, fraction);
    }

    private static final long[] powersOfTen = {1, 10, 100, 1000};
}
