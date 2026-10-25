package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.countdown.CountdownChannel;
import me.primaryuan.carpet.handler.countdown.CountdownManager;
import me.primaryuan.carpet.handler.countdown.CountdownSelectorType;
import me.primaryuan.carpet.handler.countdown.CountdownTargets;
import me.primaryuan.carpet.handler.countdown.CountdownTime;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /countdown 命令：服务端全局单实例倒计时（规则 countdown 控制可用性）。
 *
 * <p>树形结构（时间参数自带格式校验与补全；display 用 + 组合、增量补全；
 * players 为原版玩家选择器，raw 文本用于判定独立 @a 的动态全服模式）：</p>
 * <pre>
 * /countdown start &lt;time&gt; [display] [players]
 * /countdown pause | resume | stop | status
 * /countdown add &lt;time&gt; | remove &lt;time&gt;
 * /countdown set time|display|players|last|label ...
 * </pre>
 *
 * <p>权限等级 2（命令方块可用）。规则关闭时命令树保持可见以便给出明确提示
 * "功能已关闭"（任务书要求，区别于其他 PRY 命令的静默隐藏）；管理器拒绝
 * 重复 start，stop 任意状态立即清理。</p>
 */
public final class CountdownCommand {

    /** label 最大长度（Unicode 码点） */
    private static final int MAX_LABEL_CODE_POINTS = 128;

    /** 省略 display 参数时的默认组合 */
    private static final String DEFAULT_DISPLAY = "bossbar+title";

    private static final List<String> TIME_SUGGESTIONS = List.of("10s", "30s", "60s", "5m", "1m30s", "2h");

    private CountdownCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("countdown")
                    // 权限等级 2（LEVEL_GAMEMASTERS）；规则门控在各执行体内做，保证关闭时有明确提示
                    .requires(CountdownCommand::hasLevel2)
                    .then(startBranch())
                    .then(Commands.literal("pause").executes(CountdownCommand::executePause))
                    .then(Commands.literal("resume").executes(CountdownCommand::executeResume))
                    .then(Commands.literal("stop").executes(CountdownCommand::executeStop))
                    .then(Commands.literal("status").executes(CountdownCommand::executeStatus))
                    .then(Commands.literal("add").then(timeNode(CountdownCommand::executeAdd)))
                    .then(Commands.literal("remove").then(timeNode(CountdownCommand::executeRemove)))
                    .then(Commands.literal("set")
                            .then(Commands.literal("time").then(timeNode(CountdownCommand::executeSetTime)))
                            .then(Commands.literal("display").then(Commands.argument("display", StringArgumentType.word())
                                    .suggests(CountdownCommand::suggestDisplay)
                                    .executes(CountdownCommand::executeSetDisplay)))
                            .then(Commands.literal("players").then(Commands.argument("players", CountdownSelectorType.players())
                                    .executes(CountdownCommand::executeSetPlayers)))
                            .then(Commands.literal("last").then(Commands.argument("last", TimeArgumentType.timeOrAll())
                                    .suggests(CountdownCommand::suggestTimeOrAll)
                                    .executes(CountdownCommand::executeSetLast)))
                            .then(Commands.literal("label").then(Commands.argument("label", StringArgumentType.greedyString())
                                    .executes(CountdownCommand::executeSetLabel))));
            dispatcher.register(root);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> startBranch() {
        RequiredArgumentBuilder<CommandSourceStack, ?> players = Commands.argument("players", CountdownSelectorType.players())
                .executes(ctx -> executeStart(ctx, StringArgumentType.getString(ctx, "display"),
                        CountdownSelectorType.get(ctx, "players")));
        RequiredArgumentBuilder<CommandSourceStack, ?> display = Commands.argument("display", StringArgumentType.word())
                .suggests(CountdownCommand::suggestDisplay)
                .executes(ctx -> executeStart(ctx, StringArgumentType.getString(ctx, "display"), null))
                .then(players);
        RequiredArgumentBuilder<CommandSourceStack, ?> time = Commands.argument("time", TimeArgumentType.time())
                .executes(ctx -> executeStart(ctx, null, null))
                .then(display);
        return Commands.literal("start").then(time);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> timeNode(Execute handler) {
        return Commands.argument("time", TimeArgumentType.time()).executes(ctx -> handler.run(ctx));
    }

    @FunctionalInterface
    private interface Execute {
        int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    // ==================== 参数类型 ====================

    /**
     * 时间参数：解析共用 CountdownTime（非法输入报"合法格式"提示）；
     * allowAll 变体额外接受 all（以 -1 哨兵返回，调用方映射为"全程"）。
     */
    private static final class TimeArgumentType implements ArgumentType<Long> {
        private static final long ALL_SENTINEL = -1L;

        private final boolean allowAll;

        private TimeArgumentType(boolean allowAll) {
            this.allowAll = allowAll;
        }

        static TimeArgumentType time() {
            return new TimeArgumentType(false);
        }

        static TimeArgumentType timeOrAll() {
            return new TimeArgumentType(true);
        }

        @Override
        public Long parse(StringReader reader) throws CommandSyntaxException {
            String raw = reader.readUnquotedString();
            if (allowAll && raw.equalsIgnoreCase("all")) {
                return ALL_SENTINEL;
            }
            try {
                return CountdownTime.parseSeconds(raw);
            } catch (IllegalArgumentException e) {
                throw new SimpleCommandExceptionType(
                        ServerI18n.tr("carpetprimaryuan.command.countdown.bad_time", raw)).create();
            }
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            suggestTimeCandidates(builder, allowAll);
            return builder.buildFuture();
        }

        @Override
        public Collection<String> getExamples() {
            return List.of("60", "1m30s", "2h");
        }
    }

    private static void suggestTimeCandidates(SuggestionsBuilder builder, boolean allowAll) {
        String remaining = builder.getRemainingLowerCase();
        if (allowAll && "all".startsWith(remaining)) {
            builder.suggest("all");
        }
        for (String suggestion : TIME_SUGGESTIONS) {
            if (suggestion.startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
    }

    /** display 渠道组合增量补全：+ 后仅建议未用渠道 */
    private static CompletableFuture<Suggestions> suggestDisplay(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int plus = remaining.lastIndexOf('+');
        EnumSet<CountdownChannel> used = EnumSet.noneOf(CountdownChannel.class);
        String typed;
        if (plus >= 0) {
            try {
                used.addAll(CountdownChannel.parseCombo(remaining.substring(0, plus)));
            } catch (IllegalArgumentException ignored) {
                // 前缀含未完成段时忽略，按全量建议
            }
            typed = remaining.substring(plus + 1).toLowerCase(Locale.ROOT);
        } else {
            typed = remaining.toLowerCase(Locale.ROOT);
        }
        SuggestionsBuilder target = plus >= 0 ? builder.createOffset(builder.getStart() + plus + 1) : builder;
        EnumSet<CountdownChannel> candidates = EnumSet.noneOf(CountdownChannel.class);
        CountdownChannel.suggestNext(typed, used, candidates);
        for (CountdownChannel channel : candidates) {
            target.suggest(channel.id());
        }
        return target.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTimeOrAll(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        suggestTimeCandidates(builder, true);
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
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.disabled"));
            return true;
        }
        return false;
    }

    private static long getSeconds(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, Long.class);
    }

    private static int executeStart(CommandContext<CommandSourceStack> ctx, String displayRaw,
                                    CountdownSelectorType.SelectorInput targets) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        long seconds = getSeconds(ctx, "time");
        EnumSet<CountdownChannel> channels = parseChannels(source, displayRaw, DEFAULT_DISPLAY);
        if (channels == null) {
            return 0;
        }
        CountdownTargets.Resolved resolved = CountdownTargets.resolve(targets, source);
        if (!resolved.dynamicAll() && resolved.realCount() == 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_match"));
            return 0;
        }
        int result = CountdownManager.get().start(source.getServer(), seconds, channels,
                resolved.dynamicAll(), resolved.targets());
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.already_running"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.started",
                CountdownTime.formatClock(seconds)), false);
        return 1;
    }

    private static int executePause(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        return switch (manager.pause()) {
            case 0 -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.paused",
                        CountdownTime.formatClock(manager.remainingCeilSeconds())), false);
                yield 1;
            }
            case 1 -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.already_paused"));
                yield 0;
            }
            default -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
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
        return switch (manager.resume()) {
            case 0 -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.resumed",
                        CountdownTime.formatClock(manager.remainingCeilSeconds())), false);
                yield 1;
            }
            case 1 -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.running_now"));
                yield 0;
            }
            default -> {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
                yield 0;
            }
        };
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        if (!CountdownManager.get().stop()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.stopped"), false);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (!manager.hasActiveSession()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        String stateText = switch (manager.state()) {
            case RUNNING -> ServerI18n.tr("carpetprimaryuan.command.countdown.state_running").getString();
            case PAUSED -> ServerI18n.tr("carpetprimaryuan.command.countdown.state_paused").getString();
            default -> ServerI18n.tr("carpetprimaryuan.command.countdown.state_completing").getString();
        };
        String targetsText = manager.dynamicAll()
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.targets_all").getString()
                : ServerI18n.tr("carpetprimaryuan.command.countdown.targets_players", manager.targetCount()).getString();
        String state = stateText;
        String targets = targetsText;
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.status",
                state,
                CountdownTime.formatClock(manager.remainingCeilSeconds()),
                CountdownTime.formatClock(manager.durationSeconds()),
                CountdownChannel.formatCombo(manager.channels()),
                targets), false);
        return 1;
    }

    private static int executeAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        long seconds = getSeconds(ctx, "time");
        CountdownManager manager = CountdownManager.get();
        int result = manager.addTime(source.getServer(), seconds);
        if (result == -2) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.time_cap"));
            return 0;
        }
        if (result != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        int remaining = manager.remainingCeilSeconds();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.time_added",
                CountdownTime.formatClock(seconds), CountdownTime.formatClock(remaining)), false);
        return 1;
    }

    private static int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        long seconds = getSeconds(ctx, "time");
        CountdownManager manager = CountdownManager.get();
        if (manager.removeTime(source.getServer(), seconds) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        int remaining = manager.remainingCeilSeconds();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.time_removed",
                CountdownTime.formatClock(seconds), CountdownTime.formatClock(remaining)), false);
        return 1;
    }

    private static int executeSetTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        long seconds = getSeconds(ctx, "time");
        CountdownManager manager = CountdownManager.get();
        if (manager.setTime(source.getServer(), seconds) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.time_set",
                CountdownTime.formatClock(seconds)), false);
        return 1;
    }

    private static EnumSet<CountdownChannel> parseChannels(CommandSourceStack source, String raw, String fallback) {
        String input = raw == null ? fallback : raw;
        try {
            return CountdownChannel.parseCombo(input);
        } catch (IllegalArgumentException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.bad_display", e.getMessage()));
            return null;
        }
    }

    private static int executeSetDisplay(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        EnumSet<CountdownChannel> channels = parseChannels(source, StringArgumentType.getString(ctx, "display"), null);
        if (channels == null) {
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setDisplay(source.getServer(), channels) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.display_set",
                CountdownChannel.formatCombo(channels)), false);
        return 1;
    }

    private static int executeSetPlayers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        CountdownTargets.Resolved resolved = CountdownTargets.resolve(
                CountdownSelectorType.get(ctx, "players"), source);
        if (!resolved.dynamicAll() && resolved.realCount() == 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_match"));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setTargets(source.getServer(), resolved.dynamicAll(), resolved.targets()) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        String targetsText = resolved.dynamicAll()
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.targets_all").getString()
                : ServerI18n.tr("carpetprimaryuan.command.countdown.targets_players", resolved.realCount()).getString();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.players_set", targetsText), false);
        return 1;
    }

    private static int executeSetLast(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        long value = getSeconds(ctx, "last");
        Long seconds = value == TimeArgumentType.ALL_SENTINEL ? null : value;
        CountdownManager manager = CountdownManager.get();
        if (manager.setLastWindow(source.getServer(), seconds) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        String windowText = seconds == null
                ? ServerI18n.tr("carpetprimaryuan.command.countdown.last_all").getString()
                : CountdownTime.formatClock(seconds);
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.last_set", windowText), false);
        return 1;
    }

    private static int executeSetLabel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (checkDisabled(source)) {
            return 0;
        }
        String label = StringArgumentType.getString(ctx, "label").trim();
        if (label.isEmpty() || label.codePointCount(0, label.length()) > MAX_LABEL_CODE_POINTS) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.bad_label", MAX_LABEL_CODE_POINTS));
            return 0;
        }
        CountdownManager manager = CountdownManager.get();
        if (manager.setLabel(source.getServer(), label) != 0) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.countdown.no_active"));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.countdown.label_set", label), false);
        return 1;
    }
}
