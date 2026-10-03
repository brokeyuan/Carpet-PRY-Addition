package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.textAnimation.TextAnimationHandler;
import me.primaryuan.carpet.handler.textAnimation.TextOptions;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Locale;

/**
 * /text 命令：在执行者眼前播放米塔风格的对话字幕（规则 textAnimation 控制可用性）。
 *
 * 结构：
 *   text <text>                    按默认参数播放
 *   text <text> <options>          options 为 k=v;k=v 串（见 docs/commands.md）
 *
 * text 含空格时需加引号；色码用 &（&c 等），字面 & 写 &&。
 */
public final class TextCommand {

    private TextCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> text = Commands.literal("text")
                    // 主规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equals(CarpetPrimaryuanSettings.textAnimation))
                    .executes(ctx -> failure(ctx, "carpetprimaryuan.command.text.empty"))
                    .then(Commands.argument("text", StringArgumentType.string())
                            .executes(ctx -> play(ctx, null))
                            .then(Commands.argument("options", StringArgumentType.greedyString())
                                    .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "options")))));
            dispatcher.register(text);
        });
    }

    // ==================== 执行 ====================

    private static int play(CommandContext<CommandSourceStack> context, String optionsRaw) {
        CommandSourceStack source = context.getSource();
        String raw = StringArgumentType.getString(context, "text");

        TextOptions options = TextOptions.defaults();
        if (optionsRaw != null && !optionsRaw.isBlank()) {
            try {
                options = parseOptions(optionsRaw);
            } catch (IllegalArgumentException e) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.bad_option",
                        e.getMessage(),
                        "distance, scale, spacing, hold, glow, sound, drop"));
                return 0;
            }
        }

        int groups = TextAnimationHandler.play(source, raw, options);
        if (groups > 0) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.text.success", groups), false);
            return groups;
        }
        if (groups == -2) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.too_long",
                    TextAnimationHandler.MAX_CHARS));
        } else if (groups == -3) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.busy",
                    TextAnimationHandler.MAX_BROADCASTS));
        } else if (groups == -5) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.no_player"));
        } else {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.empty"));
        }
        return 0;
    }

    /**
     * options 串 k=v;k=v 解析；非法键或非法值抛 IllegalArgumentException（消息 = 键名）。
     */
    private static TextOptions parseOptions(String raw) {
        double distance = TextOptions.defaults().distance;
        Float scale = null;
        Float spacing = null;
        Integer hold = null;
        Boolean glow = null;
        Boolean sound = null;
        Boolean drop = null;

        for (String part : raw.split(";")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            String key = eq < 0 ? entry : entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = eq < 0 ? "" : entry.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "distance" -> distance = Double.parseDouble(value);
                    case "scale" -> scale = Float.parseFloat(value);
                    case "spacing" -> spacing = Float.parseFloat(value);
                    case "hold" -> hold = Integer.parseInt(value);
                    case "glow" -> glow = parseBool(value);
                    case "sound" -> sound = parseBool(value);
                    case "drop" -> drop = parseBool(value);
                    default -> throw new IllegalArgumentException(key);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key, e);
            }
        }
        return TextOptions.with(TextOptions.defaults(), distance, scale, spacing, hold, glow, sound, drop);
    }

    private static boolean parseBool(String value) {
        if (value.equalsIgnoreCase("true") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equals("0")) {
            return false;
        }
        throw new NumberFormatException(value);
    }

    private static int failure(CommandContext<CommandSourceStack> context, String key) {
        context.getSource().sendFailure(ServerI18n.tr(key));
        return 0;
    }
}
