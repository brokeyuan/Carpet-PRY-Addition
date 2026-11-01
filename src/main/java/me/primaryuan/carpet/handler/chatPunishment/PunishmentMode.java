package me.primaryuan.carpet.handler.chatPunishment;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 聊天惩罚模式（chatPunishment）：每名玩家同时只生效一种。
 *
 * <p>固定替换型（胶带/呆子/屏蔽词/村民）使用固定文本，不做随机词库；追加型
 * （猫娘）保留原文加后缀；结巴/反转为确定性文本转换。文本转换均为纯函数，
 * 包可见供单测直接调用；禁言不参与文本转换（在广播层取消，见
 * {@link ChatPunishmentManager}）。</p>
 */
public enum PunishmentMode {
    MUTE("mute"),
    TAPE("tape"),
    CAT("cat"),
    IDIOT("idiot"),
    CENSOR("censor"),
    VILLAGER("villager"),
    STUTTER("stutter"),
    REVERSE("reverse");

    public static final String TAPE_TEXT = "呜呜...呜呜......呜呜........";
    public static final String IDIOT_TEXT = "阿巴巴阿巴……阿巴！";
    public static final String CENSOR_TEXT = "#$%%%！##！！";
    public static final String VILLAGER_TEXT = "嗯哼～哼嗯～";
    public static final String CAT_SUFFIX = "喵~";

    /** 结巴复读步长：每数 3 个字符复读一次（今、今晚去挖、挖矿） */
    static final int STUTTER_STRIDE = 3;

    /** 结巴复读连接符 */
    static final String STUTTER_JOINER = "、";

    private final String id;

    PunishmentMode(String id) {
        this.id = id;
    }

    /** 模式标识（命令字面量与存储值） */
    public String id() {
        return id;
    }

    /** 按 id 反查模式；未知 id 返回 null（存储读取容错，不抛异常） */
    public static PunishmentMode byId(String id) {
        for (PunishmentMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return null;
    }

    /** 转换后的聊天文本；禁言不走文本转换，恒返回 null */
    public String transform(String original) {
        return switch (this) {
            case MUTE -> null;
            case TAPE -> TAPE_TEXT;
            case IDIOT -> IDIOT_TEXT;
            case CENSOR -> CENSOR_TEXT;
            case VILLAGER -> VILLAGER_TEXT;
            case CAT -> original + CAT_SUFFIX;
            case STUTTER -> stutter(original);
            case REVERSE -> reverse(original);
        };
    }

    /**
     * 结巴：按原文码点计数，每数到第 3 个字符复读该字符一次、以顿号连接
     * （今、今晚去挖、挖矿，谁、谁一起？）。标点、空白、emoji 不复读但仍占
     * 步长，重复频率与内容无关、完全确定；按码点处理，中英文与代理对安全。
     */
    static String stutter(String text) {
        StringBuilder out = new StringBuilder(text.length() * 2);
        int index = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (index % STUTTER_STRIDE == 0 && Character.isLetterOrDigit(cp)) {
                out.appendCodePoint(cp).append(STUTTER_JOINER);
            }
            out.appendCodePoint(cp);
            index++;
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /**
     * 反转：按字素簇（grapheme cluster）整体反转，emoji 组合序列、代理对与
     * 组合附标不被拆散；普通中英文逐字反转（今晚去挖矿，谁一起？→
     * ？起一谁，矿挖去晚今）。
     */
    static String reverse(String text) {
        BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
        clusters.setText(text);
        List<String> parts = new ArrayList<>();
        int prev = clusters.first();
        for (int bound = clusters.next(); bound != BreakIterator.DONE; bound = clusters.next()) {
            parts.add(text.substring(prev, bound));
            prev = bound;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = parts.size() - 1; i >= 0; i--) {
            out.append(parts.get(i));
        }
        return out.toString();
    }
}
