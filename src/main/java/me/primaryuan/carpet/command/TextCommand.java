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
 * /text 命令：向所有在线真人玩家（假人除外）广播米塔风格字幕（规则 textAnimation 控制可用性）。
 *
 * 结构（单 greedy 参数，消息空格自由、无需引号）：
 *   /text <文本内容>                整行作为消息，按默认参数播放
 *   /text <文本内容>|<options>      行内 | 分隔，后半为 k=v;k=v 串（如 scale=2.5;hold=60）
 *
 * 文本内字面 | 写 ||；色码用 &（&c 等），字面 & 写 &&。
 */
public final class TextCommand {

    private TextCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> text = Commands.literal("text")
                    // 主规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equals(CarpetPrimaryuanSettings.textAnimation))
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> play(ctx, StringArgumentType.getString(ctx, "text"))));
            dispatcher.register(text);
        });
    }

    // ==================== 执行 ====================

    private static int play(CommandContext<CommandSourceStack> context, String raw) {
        CommandSourceStack source = context.getSource();
        String[] parts = splitPipe(raw);
        String message = parts[0];

        TextOptions options = TextOptions.defaults();
        if (!parts[1].isBlank()) {
            try {
                options = parseOptions(parts[1]);
            } catch (IllegalArgumentException e) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.bad_option",
                        e.getMessage(),
                        "distance, scale, spacing, hold, glow, sound, drop"));
                return 0;
            }
        }

        int groups = TextAnimationHandler.play(source, message, options);
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
     * 行内 |（含全角｜）拆分：首个未转义分隔符右侧整体为 options；|| /「｜｜」为字面 |（仅消息段）。
     *
     * @return [消息, options 串（可能为空）]
     */
    static String[] splitPipe(String raw) {
        StringBuilder message = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean pipe = c == '|' || c == '｜';
            if (!pipe) {
                message.append(c);
                continue;
            }
            if (i + 1 < raw.length() && (raw.charAt(i + 1) == '|' || raw.charAt(i + 1) == '｜')) {
                message.append('|');
                i++;
                continue;
            }
            return new String[]{message.toString(), raw.substring(i + 1)};
        }
        return new String[]{message.toString(), ""};
    }

    /**
     * options 串 k=v;k=v 解析；非法键或非法值抛 IllegalArgumentException（消息 = 键名）。
     */
    static TextOptions parseOptions(String raw) {
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
        // clamp：防滥用（巨型字符/脑后生成/长占广播上限），正常使用不受影响；
        // spacing/hold 未显式传入时保持 null（走默认/自动派生路径），clamp 仅作用于显式值
        distance = Math.clamp(distance, 1.0, 10.0);
        if (scale != null) {
            scale = Math.clamp(scale, 0.5f, 4.0f);
        }
        if (spacing != null) {
            spacing = Math.clamp(spacing, 0.05f, 2.0f);
        }
        if (hold != null) {
            hold = Math.clamp(hold, 20, 600);
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
}
