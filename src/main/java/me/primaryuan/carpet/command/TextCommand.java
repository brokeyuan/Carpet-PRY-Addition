package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.textAnimation.TextAnimationHandler;
import me.primaryuan.carpet.handler.textAnimation.TextOptions;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /text 命令：向在线真人玩家（假人除外）播放米塔风格字幕（规则 textAnimation 控制可用性）。
 *
 * 结构（消息单 greedy 参数，空格自由、无需引号；行内 | 分隔 options）：
 *   /text @a &lt;文本&gt;[|options]      全服广播
 *   /text &lt;玩家名&gt; &lt;文本&gt;[|options]  定向单个在线玩家（不区分大小写，按当前在线表匹配）
 *
 * 目标参数必填：首词既非 @a 也非在线玩家名时报错（不再有"整行为消息"的旧式）。
 * 目标前缀在处理器内剥离（首个空格切分）——不用 EntityArgument 独立分支：其对非在线
 * 名字在执行期抛 "No player was found" 且 Brigadier 不回落其它分支，会报废所有消息。
 * 字幕为全服/定向可见效果，每人 10 秒发送冷却（控制台不受限）防连发刷屏。
 * 文本内字面 | 写 ||；色码用 &amp;（&amp;c 等），字面 &amp; 写 &amp;&amp;。
 */
public final class TextCommand {

    private TextCommand() {}

    /** options 键：bad_option 提示与行内补全共用同一份 */
    private static final String[] OPTION_KEYS = {"distance", "scale", "spacing", "hold", "glow", "sound", "drop"};

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> text = Commands.literal("text")
                    // 主规则 = false 时整棵命令树不可见（boolean 规则直接判值）
                    .requires(source -> CarpetPrimaryuanSettings.textAnimation)
                    // 单 greedy 分支：目标前缀（@a / 在线玩家名）在 execute 内剥离，
                    // 目标参数必填，首词非目标时报错
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                            .suggests(TextCommand::suggestInput)
                            .executes(ctx -> execute(ctx)));
            dispatcher.register(text);
        });
    }

    /**
     * 输入补全：目标前缀可发现（"目标必填"契约的提示面）。
     * 首词建议 "@a " 与在线真人玩家名（假人不可接收字幕，排除；带尾随空格直达消息）；
     * 消息区出现 | 分隔符后建议未用过的 options 键（k=v 语法发现）。
     */
    private static CompletableFuture<Suggestions> suggestInput(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int space = remaining.indexOf(' ');
        if (space < 0) {
            List<String> candidates = new ArrayList<>();
            candidates.add("@a ");
            for (ServerPlayer p : context.getSource().getServer().getPlayerList().getPlayers()) {
                if (!(p instanceof EntityPlayerMPFake)) {
                    candidates.add(CommandSupport.profileName(p) + " ");
                }
            }
            CommandSupport.suggestMatching(builder, candidates);
            return builder.buildFuture();
        }
        int pipe = Math.max(remaining.lastIndexOf('|'), remaining.lastIndexOf('｜'));
        if (pipe > space) {
            String typed = remaining.substring(pipe + 1);
            // createOffset：同 fullInput、start 推进到分隔符后——建议只替换分隔符右侧
            SuggestionsBuilder optBuilder = builder.createOffset(pipe + 1);
            for (String key : OPTION_KEYS) {
                String entry = key + "=";
                if (!typed.toLowerCase(Locale.ROOT).contains(key) && entry.startsWith(typed)) {
                    optBuilder.suggest(entry);
                }
            }
            return optBuilder.buildFuture();
        }
        return Suggestions.empty();
    }

    /**
     * 冷却 → 目标前缀剥离 → 解析文本|options → 播放。
     * 首词 = "@a"（全服）或在线玩家名（定向单人，不区分大小写）时剥离，余下为消息；
     * 首词非目标时报 need_target 错误（目标参数必填）。
     */
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int cooldown = TextAnimationHandler.cooldownRemainSeconds(source);
        if (cooldown > 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.cooldown", cooldown));
            return 0;
        }

        String raw = StringArgumentType.getString(context, "text");
        Collection<ServerPlayer> targets = null;
        String message = raw;

        String trimmed = raw.stripLeading();
        int space = trimmed.indexOf(' ');
        String first = space < 0 ? trimmed : trimmed.substring(0, space);
        if (first.equals("@a")) {
            // 全服广播
            message = space < 0 ? "" : trimmed.substring(space + 1).stripLeading();
        } else if (!first.startsWith("@")) {
            ServerPlayer target = first.isEmpty() ? null
                    : source.getLevel().getServer().getPlayerList().getPlayerByName(first);
            if (target != null) {
                targets = java.util.List.of(target);
                message = space < 0 ? "" : trimmed.substring(space + 1).stripLeading();
            }
        }
        if (targets == null && message.equals(raw)) {
            // 首词既非 @a 也非在线玩家名：目标参数必填（旧式无目标写法已移除）
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.need_target"));
            return 0;
        }

        String[] parts = splitPipe(message);
        message = parts[0];

        TextOptions options = TextOptions.defaults();
        if (!parts[1].isBlank()) {
            try {
                options = parseOptions(parts[1]);
            } catch (IllegalArgumentException e) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.text.bad_option",
                        e.getMessage(),
                        String.join(", ", OPTION_KEYS)));
                return 0;
            }
        }

        int groups = TextAnimationHandler.play(source, targets, message, options);
        if (groups > 0) {
            TextAnimationHandler.markSent(source);
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
