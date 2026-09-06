package me.primaryuan.carpet.i18n;

import carpet.utils.Translations;
import net.minecraft.network.chat.Component;

/**
 * 服务端翻译工具类。
 *
 * 使用 Carpet 的 {@link Translations#tr(String)} 在服务端解析翻译，
 * 按 {@code /carpet language <lang>} 设置的全局语言查找翻译。
 *
 * 背景：本 mod 为服务端 only，客户端没有翻译文件。
 * {@link Component#translatable(String, Object...)} 的翻译查找发生在客户端，
 * 客户端找不到翻译键时会直接显示原始键字符串。
 *
 * 本类在服务端完成翻译查找与占位符替换，返回 {@link Component#literal(String)}，
 * 这样客户端无需任何翻译资源即可正确显示。
 *
 * 注意：Carpet 的翻译为全局语言，不区分玩家；因此本类刻意不提供
 * 以 ServerPlayer/CommandSourceStack 为首参的重载，避免调用方误以为按玩家语言翻译。
 */
public class ServerI18n {
    private ServerI18n() {}

    /** 返回翻译后的 Component，按 Carpet 全局语言解析（不区分玩家语言） */
    public static Component tr(String key, Object... args) {
        String template = Translations.tr(key);
        if (args == null || args.length == 0) {
            return Component.literal(template);
        }
        return Component.literal(String.format(template, args));
    }
}
