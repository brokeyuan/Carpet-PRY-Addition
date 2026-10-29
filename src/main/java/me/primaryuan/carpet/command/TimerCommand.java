package me.primaryuan.carpet.command;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.countdown.CountdownChannel;
import me.primaryuan.carpet.handler.countdown.CountdownManager;
import me.primaryuan.carpet.handler.countdown.CountdownSession;
import me.primaryuan.carpet.handler.countdown.CountdownTargets;
import me.primaryuan.carpet.handler.countdown.CountdownTime;
import me.primaryuan.carpet.handler.countdown.TimerInput;
import me.primaryuan.carpet.handler.countdown.TimerLayout;
import me.primaryuan.carpet.handler.countdown.TimerMode;
import me.primaryuan.carpet.handler.countdown.TimerSoundPlan;
import me.primaryuan.carpet.handler.countdown.TimerStyle;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /timer 命令：正计时与倒计时的统一主入口（规则 countdown 控制可用性）。
 *
 * <pre>
 * /timer down &lt;时间&gt; [simple|normal|fancy] [bar|center|actionbar|both] [silent] [选择器] [-- 名称]
 * /timer up   [同上短选项]
 * /timer pause | resume | stop | status
 * /timer time|add|remove &lt;时间&gt;（仅倒计时）
 * /timer style|display|sound|label|players &lt;值&gt;
 * </pre>
 *
 * <p>最短命令 {@code /timer down 60s}、{@code /timer up} 即可用；选项词任意顺序、
 * 大小写不敏感、每类至多一个，{@code --} 后整段为名称。选择器词保留原文，由原版
 * EntityArgument 解析与补全；尾部用 greedyString 接收（原版已注册参数类型，不引入
 * 自定义 ArgumentType——未注册类型会导致玩家登录被踢）。权限等级 2，命令方块可用。
 * 规则关闭时命令树保持可见并提示"功能已关闭"。</p>
 */
public final class TimerCommand {

    private static final List<String> TIME_SUGGESTIONS = List.of("10s", "30s", "60s", "5m", "1m30s", "2h");

    private TimerCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("timer")
                    .requires(TimerCommand::hasLevel2)
                    .executes(TimerCommand::executeRoot)
                    .then(Commands.literal("down")
                            .then(timeNode(TimerCommand::executeDown)
                                    .then(optionsNode(TimerMode.DOWN))))
                    .then(Commands.literal("up")
                            .executes(ctx -> executeStart(ctx, TimerMode.UP, null))
                            .then(optionsNode(TimerMode.UP)))
                    .then(Commands.literal("pause").executes(TimerCommand::executePause))
                    .then(Commands.literal("resume").executes(TimerCommand::executeResume))
                    .then(Commands.literal("stop").executes(TimerCommand::executeStop))
                    .then(Commands.literal("status").executes(TimerCommand::executeStatus))
                    .then(Commands.literal("time").then(timeNode(TimerCommand::executeSetTime)))
                    .then(Commands.literal("add").then(timeNode(TimerCommand::executeAdd)))
                    .then(Commands.literal("remove").then(timeNode(TimerCommand::executeRemove)))
                    .then(Commands.literal("style").then(Commands.argument("style", StringArgumentType.word())
                            .suggests(TimerCommand::suggestStyle)
                            .executes(TimerCommand::executeStyle)))
                    .then(Commands.literal("display").then(Commands.argument("display", StringArgumentType.word())
                            .suggests(TimerCommand::suggestLayout)
                            .executes(TimerCommand::executeDisplay)))
                    .then(Commands.literal("sound").then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(TimerCommand::suggestOnOff)
                            .executes(TimerCommand::executeSound)))
                    .then(Commands.literal("label").then(Commands.argument("label", StringArgumentType.greedyString())
                            .executes(TimerCommand::executeLabel)))
                    .then(Commands.literal("players").then(Commands.argument("players", EntityArgument.players())
                            .executes(TimerCommand::executePlayers)));
            dispatcher.register(root);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> timeNode(Execute handler) {
        return Commands.argument("time", StringArgumentType.word())
                .suggests(TimerCommand::suggestTime)
                .executes(ctx -> handler.run(ctx));
    }

    /** 可选尾部：greedyString 接收剩余整段（含空格与选择器原文） */
    private static RequiredArgumentBuilder<CommandSourceStack, String> optionsNode(TimerMode mode) {
        return Commands.argument("options", StringArgumentType.greedyString())
                .suggests(TimerCommand::suggestTail)
                .executes(ctx -> executeStart(ctx, mode, StringArgumentType.getString(ctx, "options")));
    }

    @FunctionalInterface
    private interface Execute {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    // ==================== 补全 ====================

    private static CompletableFuture<Suggestions> suggestTime(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (String suggestion : TIME_SUGGESTIONS) {
            if (suggestion.startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    /**
     * 尾部补全：正在输入选择器词时整体交给原版选择器补全（EntityArgument）；
     * 其余按已选类别增量建议（已选类别不再候补），带中文 tooltip。
     */
    private static CompletableFuture<Suggestions> suggestTail(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String trimmed = remaining.stripTrailing();
        String lastToken = trimmed.isEmpty() ? "" : trimmed.substring(trimmed.lastIndexOf(' ') + 1);
        if (lastToken.startsWith("@")) {
            // 词起始偏移交由原版解析（含 @a[ 前缀与括号内补全）
            SuggestionsBuilder selector = builder.createOffset(
                    builder.getStart() + remaining.length() - lastToken.length());
            return EntityArgument.players().listSuggestions(context, selector);
        }
        SuggestionsBuilder target = builder.createOffset(builder.getStart() + remaining.length());
        for (String candidate : TimerInput.suggestNext(remaining)) {
            target.suggest(candidate, tooltipFor(candidate));
        }
        return target.buildFuture();
    }

    private static Component tooltipFor(String candidate) {
        return switch (candidate) {
            case "simple", "normal", "fancy" ->
                    ServerI18n.tr("carpetprimaryuan.command.timer.tip_style");
            case "bar", "center", "actionbar", "both" ->
                    ServerI18n.tr("carpetprimaryuan.command.timer.tip_layout");
            case "silent" -> ServerI18n.tr("carpetprimaryuan.command.timer.tip_silent");
            case "@a", "@s", "@a[team=" -> ServerI18n.tr("carpetprimaryuan.command.timer.tip_selector");
            case "--" -> ServerI18n.tr("carpetprimaryuan.command.timer.tip_label");
            default -> Component.empty();
        };
    }

    private static CompletableFuture<Suggestions> suggestStyle(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (TimerStyle style : TimerStyle.values()) {
            if (style.id().startsWith(remaining)) {
                builder.suggest(style.id());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLayout(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (TimerLayout layout : TimerLayout.values()) {
            if (layout.id().startsWith(remaining)) {
                builder.suggest(layout.id());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestOnOff(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if ("on".startsWith(remaining)) {
            builder.suggest("on");
        }
        if ("off".startsWith(remaining)) {
            builder.suggest("off");
        }
        return builder.buildFuture();
    }

    // ==================== 执行体 ====================

    private static boolean hasLevel2(CommandSourceStack source) {
        //#if MC <= 12110
        //$$ return source.hasPermission(2);
        //#else
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //#endif
    }

    /** 规则门控：关闭时给出明确提示（树保持可见以便该提示可达） */
    private static boolean checkDisabled(CommandSourceStack source) {
        if (!CarpetPrimaryuanSettings.countdown) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.disabled"));
            return true;
        }
        return false;
    }

    /** @return 秒数；输入非法时已发送失败提示并返回 null */
    private static Long parseTime(CommandSourceStack source, String raw) {
        try {
            return CountdownTime.parseSeconds(raw);
        } catch (IllegalArgumentException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.bad_time", raw));
            return null;
        }
    }

    /** 从命令原始输入截取参数节点的文本（Brigadier 公开 API） */
    private static String nodeText(CommandContext<CommandSourceStack> ctx, String name) {
        String input = ctx.getInput();
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (name.equals(node.getNode().getName())) {
                StringRange range = node.getRange();
                return input.substring(Math.min(range.getStart(), input.length()),
                        Math.min(range.getEnd(), input.length()));
            }
        }
        return null;
    }

    private static int executeDown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return executeStart(ctx, TimerMode.DOWN, null);
    }

    private static int executeStart(CommandContext<CommandSourceStack> ctx, TimerMode mode,
                                    String tail) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        Long seconds = null;
        if (mode == TimerMode.DOWN) {
            seconds = parseTime(source, StringArgumentType.getString(ctx, "time"));
            if (seconds == null) {
                return 0;
            }
        }
        TimerInput.Parsed options;
        try {
            options = TimerInput.parse(tail);
        } catch (TimerInput.TimerSyntaxException e) {
            source.sendFailure(ServerI18n.tr(e.key(), e.args()));
            return 0;
        }
        // 目标解析：显式选择器走原版 EntityArgument；省略时 down=动态全服，up=玩家来源默认本人
        CountdownTargets.Resolved targets;
        if (options.selectorRaw() == null) {
            targets = mode == TimerMode.DOWN
                    ? CountdownTargets.Resolved.dynamic()
                    : CountdownTargets.resolveUpDefault(source);
        } else if (options.selectorRaw().equals("@a")) {
            targets = CountdownTargets.Resolved.dynamic();
        } else {
            EntitySelector selector = parseSelector(source, options.selectorRaw());
            if (selector == null) {
                return 0;
            }
            targets = CountdownTargets.resolve(options.selectorRaw(), selector, source);
        }
        if (!targets.dynamicAll() && targets.realCount() == 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_match"));
            return 0;
        }
        TimerStyle style = options.style() != null ? options.style() : TimerStyle.SIMPLE;
        int result = CountdownManager.get().start(source.getServer(), new CountdownManager.StartRequest(
                mode,
                mode == TimerMode.DOWN ? seconds : 0,
                style,
                options.layout(),
                options.label(),
                options.silent(),
                targets.dynamicAll(),
                targets.targets(),
                sourcePlayerId(source)));
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.conflict"));
            return 0;
        }
        String targetsText = targets.dynamicAll()
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.targets_all").getString()
                : ServerI18n.tr("carpetprimaryuan.command.countdown.targets_players", targets.realCount()).getString();
        String label = options.label() != null ? options.label()
                : ServerI18n.tr(mode == TimerMode.DOWN
                        ? "carpetprimaryuan.timer.default_down"
                        : "carpetprimaryuan.timer.default_up").getString();
        if (mode == TimerMode.DOWN) {
            long sec = seconds;
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.started_down",
                    label, CountdownTime.formatClock(sec), targetsText), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.started_up",
                    label, targetsText), false);
        }
        return 1;
    }

    /** 原版解析选择器原文（合法性、执行上下文与越界检查均交给原版） */
    private static EntitySelector parseSelector(CommandSourceStack source, String raw) {
        try {
            StringReader reader = new StringReader(raw);
            EntitySelector selector = EntityArgument.players().parse(reader);
            if (reader.canRead()) {
                throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                        Component.literal(raw)).create();
            }
            return selector;
        } catch (CommandSyntaxException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.bad_selector", raw));
            return null;
        }
    }

    private static java.util.UUID sourcePlayerId(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                && !(player instanceof carpet.patches.EntityPlayerMPFake)
                ? player.getUUID()
                : null;
    }

    private static int executeRoot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.hasActiveSession()) {
            sendBriefStatus(source, manager);
            return 1;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.examples"), false);
        if (manager.lastResult() != null) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.last_result",
                    manager.lastResult()), false);
        }
        return 1;
    }

    private static int executePause(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        return switch (manager.pause(source.getServer())) {
            case 0 -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.paused"), false);
                yield 1;
            }
            case 1 -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.already_paused"));
                yield 0;
            }
            default -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
                yield 0;
            }
        };
    }

    private static int executeResume(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        return switch (manager.resume(source.getServer())) {
            case 0 -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.resumed"), false);
                yield 1;
            }
            case 1 -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.running_now"));
                yield 0;
            }
            default -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
                yield 0;
            }
        };
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (!manager.hasActiveSession()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        boolean upResult = manager.mode() == TimerMode.UP
                && manager.state() != CountdownSession.State.RESULT;
        int remaining = manager.remainingCeilSeconds();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p
                && !(p instanceof EntityPlayerMPFake) ? p : null;
        if (!manager.stopTimer(source.getServer(), player)) {
            return 0;
        }
        if (!upResult) {
            // down 手动停止：只给操作来源一次反馈，已由管理器移除全部显示
            int sec = remaining;
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.stopped_remaining",
                    CountdownTime.formatClock(sec)), false);
        } else if (player == null) {
            // up 结算结果已广播给目标；控制台等非玩家来源不在目标内，单独回显一次
            String result = manager.lastResult();
            if (result != null) {
                source.sendSuccess(() -> Component.literal(result), false);
            }
        }
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (!manager.hasActiveSession()) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.examples"), false);
            if (manager.lastResult() != null) {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.last_result",
                        manager.lastResult()), false);
            }
            return 1;
        }
        sendBriefStatus(source, manager);
        if (manager.lastResult() != null) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.last_result",
                    manager.lastResult()), false);
        }
        return 1;
    }

    /** 简短状态：方向、名称、状态、时间、效果/布局（旧会话显示渠道组合）、对象 */
    private static void sendBriefStatus(CommandSourceStack source, CountdownManager manager) {
        String stateText = switch (manager.state()) {
            case RUNNING -> ServerI18n.tr("carpetprimaryuan.command.countdown.state_running").getString();
            case PAUSED -> ServerI18n.tr("carpetprimaryuan.command.countdown.state_paused").getString();
            default -> ServerI18n.tr("carpetprimaryuan.command.timer.state_result").getString();
        };
        String direction = ServerI18n.tr(manager.mode() == TimerMode.UP
                ? "carpetprimaryuan.command.timer.dir_up"
                : "carpetprimaryuan.command.timer.dir_down").getString();
        String time = manager.mode() == TimerMode.UP
                ? CountdownTime.formatUpTenths(manager.elapsedNanos())
                : CountdownTime.formatClock(manager.remainingCeilSeconds());
        String displayText = manager.legacy()
                ? CountdownChannel.formatCombo(manager.channels())
                : ServerI18n.tr("carpetprimaryuan.command.timer.style_display",
                        manager.style().id(), manager.layout().id()).getString();
        String targetsText = manager.dynamicAll()
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.targets_all").getString()
                : ServerI18n.tr("carpetprimaryuan.command.countdown.targets_players",
                        manager.targetCount()).getString();
        String soundText = ServerI18n.tr(manager.soundTier() == TimerSoundPlan.Tier.NONE
                ? "carpetprimaryuan.command.timer.sound_off_state"
                : "carpetprimaryuan.command.timer.sound_on_state").getString();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.status",
                direction,
                manager.label(),
                stateText,
                time,
                displayText,
                targetsText,
                soundText), false);
    }

    // ==================== 运行中调整 ====================

    private static int executeSetTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        Long seconds = parseTime(source, StringArgumentType.getString(ctx, "time"));
        if (seconds == null) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        int result = manager.setTime(source.getServer(), seconds);
        if (result == -3) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.up_no_time"));
            return 0;
        }
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.time_set",
                CountdownTime.formatClock(seconds)), false);
        return 1;
    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        Long seconds = parseTime(source, StringArgumentType.getString(ctx, "time"));
        if (seconds == null) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        int result = manager.addTime(source.getServer(), seconds);
        if (result == -3) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.up_no_time"));
            return 0;
        }
        if (result == -2) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.time_cap"));
            return 0;
        }
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.time_added",
                CountdownTime.formatClock(seconds), CountdownTime.formatClock(manager.remainingCeilSeconds())), false);
        return 1;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        Long seconds = parseTime(source, StringArgumentType.getString(ctx, "time"));
        if (seconds == null) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        int result = manager.removeTime(source.getServer(), seconds);
        if (result == -3) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.up_no_time"));
            return 0;
        }
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.time_removed",
                CountdownTime.formatClock(seconds), CountdownTime.formatClock(manager.remainingCeilSeconds())), false);
        return 1;
    }

    private static int executeStyle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "style");
        TimerStyle style = TimerStyle.parse(raw);
        if (style == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.bad_style", raw));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setStyle(source.getServer(), style) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.style_set", style.id()), false);
        return 1;
    }

    private static int executeDisplay(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "display");
        TimerLayout layout = TimerLayout.parse(raw);
        if (layout == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.bad_layout", raw));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setLayout(source.getServer(), layout) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.display_set", layout.id()), false);
        return 1;
    }

    private static int executeSound(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "mode");
        Boolean on;
        if (raw.equalsIgnoreCase("on")) {
            on = true;
        } else if (raw.equalsIgnoreCase("off")) {
            on = false;
        } else {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.bad_mode", raw));
            return 0;
        }
        if (CountdownManager.get().setSound(on) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr(on
                ? "carpetprimaryuan.command.timer.sound_on"
                : "carpetprimaryuan.command.timer.sound_off"), false);
        return 1;
    }

    private static int executeLabel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        String label = StringArgumentType.getString(ctx, "label").trim();
        if (label.isEmpty() || label.codePointCount(0, label.length()) > 128) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.bad_label", 128));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setLabel(source.getServer(), label) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.label_set", label), false);
        return 1;
    }

    private static int executePlayers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownTargets.Resolved resolved = CountdownTargets.resolve(
                nodeText(ctx, "players"),
                ctx.getArgument("players", EntitySelector.class),
                source);
        if (!resolved.dynamicAll() && resolved.realCount() == 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_match"));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setTargets(source.getServer(), resolved.dynamicAll(), resolved.targets()) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.timer.no_active"));
            return 0;
        }
        String targetsText = resolved.dynamicAll()
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.targets_all").getString()
                : ServerI18n.tr("carpetprimaryuan.command.countdown.targets_players",
                        resolved.realCount()).getString();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.timer.players_set", targetsText), false);
        return 1;
    }
}
