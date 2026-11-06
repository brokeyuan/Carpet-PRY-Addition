package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.fakegames.FakeGameManager;
import me.primaryuan.carpet.handler.fakegames.FakeGameResults;
import me.primaryuan.carpet.handler.fakegames.FakeGameSettings;
import me.primaryuan.carpet.handler.fakegames.FakeGameUi;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.GameType;
import me.primaryuan.carpet.handler.fakegames.MemberState;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /fakegame 与 /fg：假游戏统一入口。主面板、游戏列表、详情、准备、管理全部为
 * 可点击聊天面板；每个按钮携带场次 ID，旧邀请/旧按钮不会误操作下一局。
 */
public final class FgCommand {

    private static final SuggestionProvider<CommandSourceStack> GAME_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(GameType.values()).map(t -> t.id)::iterator, builder);
    private static final SuggestionProvider<CommandSourceStack> SESSION_SUGGESTIONS =
            (context, builder) -> {
                GameSession session = FakeGameManager.get().current();
                if (session != null && session.phase != SessionPhase.FINISHED) {
                    builder.suggest(session.id);
                }
                return builder.buildFuture();
            };
    private static final SuggestionProvider<CommandSourceStack> SETTING_SUGGESTIONS =
            (context, builder) -> {
                GameSession session = currentSession(context, "session");
                if (session != null) {
                    for (GameType.Setting setting : session.type.settings()) {
                        builder.suggest(setting.key());
                    }
                }
                return builder.buildFuture();
            };

    private FgCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(build("fakegame"));
            dispatcher.register(build("fg"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String literal) {
        return Commands.literal(literal)
                .requires(source -> CarpetPrimaryuanSettings.fakeGames)
                .executes(FgCommand::executePanel)
                .then(Commands.literal("list").executes(FgCommand::executeList))
                .then(Commands.literal("info")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_SUGGESTIONS)
                                .executes(FgCommand::executeInfo)))
                .then(Commands.literal("create")
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_SUGGESTIONS)
                                .executes(ctx -> executeCreate(ctx, null))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                FakeGameSettings.presetNames(), builder))
                                        .executes(ctx -> executeCreate(ctx,
                                                StringArgumentType.getString(ctx, "preset"))))))
                .then(withSession(Commands.literal("join"), FgCommand::executeJoin))
                .then(withSession(Commands.literal("leave"), FgCommand::executeLeave))
                .then(withSession(Commands.literal("start"), FgCommand::executeStart))
                .then(withSession(Commands.literal("cancel"), FgCommand::executeCancel))
                .then(withSession(Commands.literal("stop"), FgCommand::executeCancel))
                .then(withSession(Commands.literal("status"), FgCommand::executeStatus))
                .then(Commands.literal("invite")
                        .executes(FgCommand::executeInvite)
                        .then(Commands.argument("session", StringArgumentType.word())
                                .suggests(SESSION_SUGGESTIONS)
                                .executes(FgCommand::executeInvite)))
                .then(Commands.literal("set")
                        .then(Commands.argument("session", StringArgumentType.word())
                                .suggests(SESSION_SUGGESTIONS)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(SETTING_SUGGESTIONS)
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(FgCommand::executeSet)))))
                .then(Commands.literal("guess")
                        .then(Commands.argument("word", StringArgumentType.greedyString())
                                .executes(FgCommand::executeGuess)))
                .then(Commands.literal("goal").executes(FgCommand::executeGoal))
                .then(Commands.literal("top")
                        .executes(FgCommand::executeTopAll)
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_SUGGESTIONS)
                                .executes(FgCommand::executeTop)))
                .then(Commands.literal("me")
                        .executes(FgCommand::executeMeAll)
                        .then(Commands.argument("game", StringArgumentType.word())
                                .suggests(GAME_SUGGESTIONS)
                                .executes(FgCommand::executeMe)))
                .then(Commands.literal("admin")
                        .requires(FgCommand::hasLevel2)
                        .executes(FgCommand::executeAdmin)
                        .then(Commands.literal("tempRules")
                                .then(Commands.literal("on").executes(ctx -> setTempRules(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setTempRules(ctx, false))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("value", IntegerArgumentType.integer(8, 128))
                                        .executes(FgCommand::executeAdminRadius)))
                        .then(Commands.literal("boundary")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(4, 40))
                                        .executes(FgCommand::executeAdminBoundary)))
                        .then(Commands.literal("preset")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 128))
                                                .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 0))
                                                        .then(Commands.argument("maxY", IntegerArgumentType.integer(1, 128))
                                                                .executes(FgCommand::executeAdminPreset)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withSession(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            com.mojang.brigadier.Command<CommandSourceStack> command) {
        return builder.executes(command)
                .then(Commands.argument("session", StringArgumentType.word())
                        .suggests(SESSION_SUGGESTIONS)
                        .executes(command));
    }

    // ==================== 面板 ====================

    private static int executePanel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(FakeGameUi.separator());
        source.sendSystemMessage(Component.literal("§6§l" +
                ServerI18n.tr("carpetprimaryuan.fakegames.panel.title").getString()));
        source.sendSystemMessage(FakeGameUi.line(
                FakeGameUi.button(tr("panel.games"), "/fakegame list", tr("panel.games_hover")),
                Component.literal("  "),
                FakeGameUi.button(tr("panel.current"), "/fakegame status", tr("panel.current_hover")),
                Component.literal("  "),
                FakeGameUi.button(tr("panel.top"), "/fakegame top", tr("panel.top_hover"))));
        source.sendSystemMessage(FakeGameUi.line(
                FakeGameUi.button(tr("panel.me"), "/fakegame me", tr("panel.me_hover")),
                Component.literal("  "),
                FakeGameUi.button(tr("panel.help"), "/fakegame list", tr("panel.help_hover"))));
        if (hasLevel2(source)) {
            source.sendSystemMessage(FakeGameUi.line(
                    FakeGameUi.button(tr("panel.admin"), "/fakegame admin", tr("panel.admin_hover"),
                            ChatFormatting.GOLD)));
        }
        source.sendSystemMessage(FakeGameUi.separator());
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(Component.literal("§6§l" + tr("list.title")));
        for (GameType type : GameType.values()) {
            String unavailable = type.unavailableKey();
            MutableComponent line = Component.literal("§e" + ServerI18n.tr(type.nameKey()).getString())
                    .append(Component.literal(" §7(" + type.minPlayers + "-" + type.maxPlayers + "人) "));
            if (unavailable != null) {
                line.append(Component.literal("§c[" + tr("list.unavailable") + "] "));
            }
            line.append(ServerI18n.tr(type.descKey()));
            line.append(Component.literal(" "));
            line.append(FakeGameUi.button(tr("list.detail"), "/fakegame info " + type.id,
                    tr("list.detail_hover")));
            source.sendSystemMessage(line);
        }
        return GameType.values().length;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        GameType type = GameType.byId(StringArgumentType.getString(context, "game").toLowerCase(Locale.ROOT));
        CommandSourceStack source = context.getSource();
        if (type == null) {
            source.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.unknown_game",
                    StringArgumentType.getString(context, "game")));
            return 0;
        }
        source.sendSystemMessage(FakeGameUi.separator());
        source.sendSystemMessage(Component.literal("§6§l" + ServerI18n.tr(type.nameKey()).getString()));
        source.sendSystemMessage(Component.literal("§7" + ServerI18n.tr(type.descKey()).getString()));
        source.sendSystemMessage(Component.literal("§7" + tr("info.players") + " §f"
                + type.minPlayers + "-" + type.maxPlayers));
        source.sendSystemMessage(Component.literal("§7" + tr("info.win") + " §f"
                + ServerI18n.tr(type.winKey()).getString()));
        if (type.unavailableKey() != null) {
            source.sendSystemMessage(Component.literal("§c" + tr("info.unavailable") + "："
                    + ServerI18n.tr(type.unavailableKey()).getString()));
        }
        source.sendSystemMessage(Component.literal("§7" + tr("info.settings") + "："));
        for (GameType.Setting setting : type.settings()) {
            source.sendSystemMessage(Component.literal("  §8- §f" + ServerI18n.tr(setting.labelKey()).getString()
                    + " §7= §e" + setting.def()));
        }
        if (type.unavailableKey() == null) {
            source.sendSystemMessage(FakeGameUi.line(
                    FakeGameUi.button(tr("info.create"), "/fakegame create " + type.id,
                            tr("info.create_hover")),
                    Component.literal("  "),
                    FakeGameUi.suggest(tr("info.create_preset"), "/fakegame create " + type.id + " ",
                            tr("info.create_preset_hover"), ChatFormatting.AQUA)));
        }
        return 1;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context, String preset) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameType type = GameType.byId(StringArgumentType.getString(context, "game").toLowerCase(Locale.ROOT));
        if (type == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.unknown_game",
                    StringArgumentType.getString(context, "game")));
            return 0;
        }
        if (type.unavailableKey() != null) {
            player.sendSystemMessage(ServerI18n.tr(type.unavailableKey()));
            return 0;
        }
        GameSession existing = FakeGameManager.get().current();
        if (existing != null && existing.phase != SessionPhase.FINISHED) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.already_running",
                    ServerI18n.tr(existing.type.nameKey()).getString()));
            return 0;
        }
        GameSession session = FakeGameManager.get().start(player, type, preset, null);
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.start_denied"));
            return 0;
        }
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.started_self",
                ServerI18n.tr(type.nameKey()).getString(), session.id));
        inviteNearby(session, player);
        sendPrepare(session, player, hasLevel2(context.getSource()));
        return 1;
    }

    private static void inviteNearby(GameSession session, ServerPlayer host) {
        for (ServerPlayer target : host.level().getServer().getPlayerList().getPlayers()) {
            if (target.getUUID().equals(host.getUUID()) || target.level() != session.level) {
                continue;
            }
            if (target.distanceToSqr(host) > (double) session.radius * session.radius) {
                continue;
            }
            target.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.invite_message",
                    host.getName().getString(),
                    ServerI18n.tr(session.type.nameKey()).getString(),
                    ServerI18n.tr(session.type.descKey()).getString())
                    .copy().append(Component.literal(" "))
                    .append(FakeGameUi.button(tr("join"), "/fakegame join " + session.id, tr("join_hover"))));
        }
    }

    // ==================== 加入 / 开始 / 退出 ====================

    private static int executeJoin(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = currentSession(context, "session");
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        GameMember member = session.member(player.getUUID());
        if (member != null && member.state != MemberState.LEFT) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.already_in_game"));
            return 0;
        }
        String rejection = session.joinRejection(player);
        if (rejection != null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames." + rejection));
            return 0;
        }
        if (!FakeGameManager.get().join(session.id, player)) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.already_in_game"));
            return 0;
        }
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.joined",
                ServerI18n.tr(session.type.nameKey()).getString()));
        sendPrepare(session, player, hasLevel2(context.getSource()));
        return 1;
    }

    private static int executeStart(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = currentSession(context, "session");
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        String error = FakeGameManager.get().begin(session.id, player, hasLevel2(context.getSource()));
        if (error != null) {
            player.sendSystemMessage(errorMessage(error));
            return 0;
        }
        return 1;
    }

    private static int executeCancel(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = currentSession(context, "session");
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        if (!FakeGameManager.get().cancel(session.id, player,
                hasLevel2(context.getSource()), "cancel_stop")) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stop_denied"));
            return 0;
        }
        return 1;
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = currentSession(context, "session");
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        GameMember member = session.member(player.getUUID());
        if (member == null || member.state == MemberState.LEFT) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.not_member"));
            return 0;
        }
        FakeGameManager.get().leave(session.id, player);
        return 1;
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = currentSession(context, "session");
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        if (session.phase != SessionPhase.WAITING) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.join_too_late"));
            return 0;
        }
        inviteNearby(session, player);
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.invite_sent"));
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GameSession session = currentSession(context, "session");
        if (session == null) {
            source.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.no_game"));
            return 0;
        }
        sendStatus(session, source);
        return 1;
    }

    private static void sendStatus(GameSession session, CommandSourceStack source) {
        source.sendSystemMessage(FakeGameUi.separator());
        source.sendSystemMessage(Component.literal("§6§l" + ServerI18n.tr(session.type.nameKey()).getString()
                + " §8#" + session.id));
        source.sendSystemMessage(Component.literal("§7" + tr("status.phase") + " §f"
                + ServerI18n.tr("carpetprimaryuan.fakegames.phase."
                + session.phase.name().toLowerCase(Locale.ROOT)).getString()
                + " §7| " + tr("status.alive") + " §f" + session.activeCount()));
        source.sendSystemMessage(Component.literal("§7" + tr("status.region") + " ")
                .append(session.regionText()));
        for (GameMember member : session.allMembers()) {
            MutableComponent line = Component.literal("§8- ")
                    .append(Component.literal(session.nameOf(member.id))
                            .withStyle(member.state == MemberState.ACTIVE
                                    ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (member.state == MemberState.ELIMINATED && member.eliminationReason != null) {
                line.append(Component.literal(" §8(").append(member.eliminationReason)
                        .append(Component.literal("§8)")));
            }
            if (member.score != 0) {
                line.append(Component.literal(" §e" + member.score));
            }
            source.sendSystemMessage(line);
        }
        if (session.phase == SessionPhase.WAITING || session.phase == SessionPhase.COUNTDOWN) {
            sendChecks(session, source::sendSystemMessage);
            sendSettings(session, source::sendSystemMessage);
        }
        if (session.phase == SessionPhase.WAITING) {
            source.sendSystemMessage(FakeGameUi.line(
                    FakeGameUi.button(tr("prepare.start"), "/fakegame start " + session.id,
                            tr("prepare.start_hover")),
                    Component.literal("  "),
                    FakeGameUi.button(tr("prepare.leave"), "/fakegame leave " + session.id,
                            tr("prepare.leave_hover"), ChatFormatting.YELLOW),
                    Component.literal("  "),
                    FakeGameUi.button(tr("prepare.cancel"), "/fakegame cancel " + session.id,
                            tr("prepare.cancel_hover"), ChatFormatting.RED)));
        } else if (session.phase == SessionPhase.RUNNING) {
            source.sendSystemMessage(FakeGameUi.line(
                    FakeGameUi.button(tr("prepare.leave"), "/fakegame leave " + session.id,
                            tr("prepare.leave_hover"), ChatFormatting.YELLOW),
                    Component.literal("  "),
                    FakeGameUi.button(tr("prepare.cancel"), "/fakegame cancel " + session.id,
                            tr("prepare.cancel_hover"), ChatFormatting.RED)));
        }
    }

    private static void sendPrepare(GameSession session, ServerPlayer player, boolean admin) {
        player.sendSystemMessage(FakeGameUi.separator());
        player.sendSystemMessage(Component.literal("§6§l" + tr("prepare.title") + " §8#" + session.id));
        player.sendSystemMessage(Component.literal("§7" + tr("prepare.game") + " §e"
                + ServerI18n.tr(session.type.nameKey()).getString()
                + " §7| " + tr("prepare.phase") + " §f"
                + ServerI18n.tr("carpetprimaryuan.fakegames.phase."
                + session.phase.name().toLowerCase(Locale.ROOT)).getString()));
        player.sendSystemMessage(Component.literal("§7")
                .append(ServerI18n.tr(session.type.descKey())));
        player.sendSystemMessage(Component.literal("§7" + tr("prepare.region") + " ")
                .append(session.regionText()));
        player.sendSystemMessage(Component.literal("§7" + tr("prepare.members") + "："));
        for (GameMember member : session.allMembers()) {
            player.sendSystemMessage(Component.literal("  §8- ")
                    .append(Component.literal(session.nameOf(member.id))
                            .withStyle(member.state == MemberState.ACTIVE
                                    ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(member.id.equals(session.host)
                            ? " §6[" + tr("prepare.host") + "]" : "")));
        }
        sendChecks(session, player::sendSystemMessage);
        sendSettings(session, player::sendSystemMessage);
        player.sendSystemMessage(FakeGameUi.line(
                FakeGameUi.button(tr("prepare.start"), "/fakegame start " + session.id,
                        tr("prepare.start_hover")),
                Component.literal("  "),
                FakeGameUi.button(tr("prepare.invite"), "/fakegame invite " + session.id,
                        tr("prepare.invite_hover"), ChatFormatting.AQUA),
                Component.literal("  "),
                FakeGameUi.button(tr("prepare.leave"), "/fakegame leave " + session.id,
                        tr("prepare.leave_hover"), ChatFormatting.YELLOW),
                Component.literal("  "),
                FakeGameUi.button(tr("prepare.cancel"), "/fakegame cancel " + session.id,
                        tr("prepare.cancel_hover"), ChatFormatting.RED)));
    }

    private static void sendChecks(GameSession session, java.util.function.Consumer<Component> sink) {
        sink.accept(Component.literal("§7" + tr("prepare.checks") + "："));
        for (GameSession.CheckItem check : session.checklist()) {
            sink.accept(Component.literal((check.ok() ? "  §a✓ " : "  §c✗ ")
                    + tr("check." + check.key())
                    + (check.detail().isEmpty() ? "" : " §8(" + check.detail() + ")")));
        }
    }

    private static void sendSettings(GameSession session, java.util.function.Consumer<Component> sink) {
        if (session.type.settings().isEmpty()) {
            return;
        }
        sink.accept(Component.literal("§7" + tr("prepare.settings") + "："));
        for (GameType.Setting setting : session.type.settings()) {
            String value = session.options.get(setting.key());
            MutableComponent line = Component.literal("  §8- §f" + ServerI18n.tr(setting.labelKey()).getString()
                    + " §7= §e" + value + " ");
            String next = nextChoice(setting, value);
            line.append(FakeGameUi.suggest(tr("prepare.change"),
                    "/fakegame set " + session.id + " " + setting.key() + " " + next,
                    tr("prepare.change_hover") + " " + setting.choices(), ChatFormatting.AQUA));
            sink.accept(line);
        }
    }

    private static String nextChoice(GameType.Setting setting, String current) {
        if (setting.choices().isEmpty()) {
            return current;
        }
        int index = setting.choices().indexOf(current);
        return setting.choices().get((index + 1) % setting.choices().size());
    }

    // ==================== 设置 / 猜词 / 终点 ====================

    private static int executeSet(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        String sessionId = StringArgumentType.getString(context, "session");
        GameSession session = FakeGameManager.get().byId(sessionId);
        if (session == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.stale_session"));
            return 0;
        }
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        if (!FakeGameManager.get().setOption(sessionId, player,
                hasLevel2(context.getSource()), key, value)) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.setting_rejected"));
            return 0;
        }
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.setting_changed",
                tr("setting." + key), value));
        return 1;
    }

    private static int executeGuess(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        String word = StringArgumentType.getString(context, "word").trim();
        if (word.isEmpty()) {
            return 0;
        }
        if (!FakeGameManager.get().submitGuess(player, word)) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.guess_rejected"));
        }
        return 1;
    }

    private static int executeGoal(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameSession session = FakeGameManager.get().current();
        if (session == null || session.phase != SessionPhase.WAITING
                || session.type != GameType.RED_LIGHT_GREEN_LIGHT) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.goal_unavailable"));
            return 0;
        }
        if (!canConfigureGoal(session.host.equals(player.getUUID()),
                hasLevel2(context.getSource()), session.inRegion(player))) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.goal_denied"));
            return 0;
        }
        session.goalPos = player.blockPosition();
        session.setOption("goal", "manual");
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.goal_set",
                player.getName().getString(),
                session.goalPos.getX(), session.goalPos.getY(), session.goalPos.getZ()));
        return 1;
    }

    /** 终点配置权：发起者或管理员，且必须在会话维度与区域内。 */
    static boolean canConfigureGoal(boolean host, boolean admin, boolean inRegion) {
        return (host || admin) && inRegion;
    }

    // ==================== 排行榜 / 我的战绩 ====================

    private static int executeTopAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean any = false;
        for (GameType type : GameType.values()) {
            for (String key : keysOf(type)) {
                if (FakeGameResults.hasBoard(key)) {
                    sendTop(source, key, 3);
                    any = true;
                }
            }
        }
        if (!any) {
            source.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.top_empty"));
        }
        return 1;
    }

    private static int executeTop(CommandContext<CommandSourceStack> context) {
        GameType type = GameType.byId(StringArgumentType.getString(context, "game").toLowerCase(Locale.ROOT));
        if (type == null) {
            context.getSource().sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.unknown_game",
                    StringArgumentType.getString(context, "game")));
            return 0;
        }
        boolean any = false;
        for (String key : keysOf(type)) {
            if (FakeGameResults.hasBoard(key)) {
                sendTop(context.getSource(), key, 10);
                any = true;
            }
        }
        if (!any) {
            context.getSource().sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.top_empty"));
        }
        return 1;
    }

    private static List<String> keysOf(GameType type) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String key : FakeGameResults.keys()) {
            if (key.equals(type.id) || key.startsWith(type.id + "|")) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            keys.add(type.id);
        }
        return keys;
    }

    private static void sendTop(CommandSourceStack source, String key, int limit) {
        source.sendSystemMessage(Component.literal("§6§l" + tr("top.title") + " §8" + key));
        List<FakeGameResults.TopEntry> entries = FakeGameResults.top(key, limit);
        if (entries.isEmpty()) {
            source.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.top_empty"));
            return;
        }
        int rank = 1;
        for (FakeGameResults.TopEntry entry : entries) {
            source.sendSystemMessage(Component.literal("§e" + rank++ + ". §f" + entry.name()
                    + " §7- " + tr("top.wins") + " §a" + entry.wins()
                    + " §7/ " + tr("top.plays") + " §f" + entry.plays()
                    + " §7/ " + tr("top.best") + " §e" + entry.best()));
        }
    }

    private static int executeMeAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        boolean any = false;
        for (GameType type : GameType.values()) {
            for (String key : keysOf(type)) {
                FakeGameResults.TopEntry entry = FakeGameResults.personal(player.getUUID(), key);
                if (entry != null) {
                    player.sendSystemMessage(Component.literal("§e" + key + " §7- "
                            + tr("me.wins") + " §a" + entry.wins()
                            + " §7/ " + tr("me.plays") + " §f" + entry.plays()
                            + " §7/ " + tr("me.best") + " §e" + entry.best()));
                    any = true;
                }
            }
        }
        if (!any) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.me_empty"));
        }
        return 1;
    }

    private static int executeMe(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) {
            return 0;
        }
        GameType type = GameType.byId(StringArgumentType.getString(context, "game").toLowerCase(Locale.ROOT));
        if (type == null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.unknown_game",
                    StringArgumentType.getString(context, "game")));
            return 0;
        }
        boolean any = false;
        for (String key : keysOf(type)) {
            FakeGameResults.TopEntry entry = FakeGameResults.personal(player.getUUID(), key);
            if (entry != null) {
                player.sendSystemMessage(Component.literal("§e" + key + " §7- "
                        + tr("me.wins") + " §a" + entry.wins()
                        + " §7/ " + tr("me.plays") + " §f" + entry.plays()
                        + " §7/ " + tr("me.best") + " §e" + entry.best()));
                any = true;
            }
        }
        if (!any) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.me_empty"));
        }
        return 1;
    }

    // ==================== 管理 ====================

    private static int executeAdmin(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSystemMessage(FakeGameUi.separator());
        source.sendSystemMessage(Component.literal("§6§l" + tr("admin.title")));
        source.sendSystemMessage(Component.literal("§7" + tr("admin.tempRules") + "："
                + (FakeGameSettings.temporaryRulesAllowed() ? "§aON" : "§cOFF")));
        source.sendSystemMessage(Component.literal("§7" + tr("admin.maxRadius") + "：§f"
                + FakeGameSettings.maxRadius()));
        source.sendSystemMessage(Component.literal("§7" + tr("admin.boundary") + "：§f"
                + FakeGameSettings.boundaryIntervalTicks() + " tick"));
        source.sendSystemMessage(FakeGameUi.line(
                FakeGameUi.button(tr("admin.tempOn"), "/fakegame admin tempRules on",
                        tr("admin.tempOn_hover")),
                Component.literal("  "),
                FakeGameUi.button(tr("admin.tempOff"), "/fakegame admin tempRules off",
                        tr("admin.tempOff_hover"), ChatFormatting.RED)));
        source.sendSystemMessage(FakeGameUi.suggest(tr("admin.setRadius"), "/fakegame admin radius ",
                tr("admin.setRadius_hover"), ChatFormatting.AQUA));
        source.sendSystemMessage(FakeGameUi.suggest(tr("admin.setBoundary"), "/fakegame admin boundary ",
                tr("admin.setBoundary_hover"), ChatFormatting.AQUA));
        return 1;
    }

    private static int setTempRules(CommandContext<CommandSourceStack> context, boolean allowed) {
        FakeGameSettings.setTemporaryRules(allowed);
        context.getSource().sendSystemMessage(ServerI18n.tr(
                allowed ? "carpetprimaryuan.fakegames.admin.temp_on"
                        : "carpetprimaryuan.fakegames.admin.temp_off"));
        return 1;
    }

    private static int executeAdminRadius(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "value");
        if (!FakeGameSettings.setMaxRadius(value)) {
            context.getSource().sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.admin.invalid"));
            return 0;
        }
        context.getSource().sendSystemMessage(ServerI18n.tr(
                "carpetprimaryuan.fakegames.admin.radius_set", value));
        return 1;
    }

    private static int executeAdminBoundary(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "ticks");
        if (!FakeGameSettings.setBoundaryInterval(value)) {
            context.getSource().sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.admin.invalid"));
            return 0;
        }
        context.getSource().sendSystemMessage(ServerI18n.tr(
                "carpetprimaryuan.fakegames.admin.boundary_set", value));
        return 1;
    }

    private static int executeAdminPreset(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int minY = IntegerArgumentType.getInteger(context, "minY");
        int maxY = IntegerArgumentType.getInteger(context, "maxY");
        if (!FakeGameSettings.addPreset(name, radius, minY, maxY)) {
            context.getSource().sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.admin.invalid"));
            return 0;
        }
        context.getSource().sendSystemMessage(ServerI18n.tr(
                "carpetprimaryuan.fakegames.admin.preset_set", name, radius));
        return 1;
    }

    // ==================== 工具 ====================

    private static GameSession currentSession(CommandContext<CommandSourceStack> context, String argument) {
        GameSession active = FakeGameManager.get().current();
        if (active == null || active.phase == SessionPhase.FINISHED) {
            return null;
        }
        String id;
        try {
            id = StringArgumentType.getString(context, argument);
        } catch (IllegalArgumentException e) {
            return active;
        }
        return active.id.equals(id) ? active : null;
    }

    private static Component errorMessage(String code) {
        String key = switch (code) {
            case "no_game" -> "carpetprimaryuan.fakegames.no_game";
            case "stale_session" -> "carpetprimaryuan.fakegames.stale_session";
            case "not_waiting" -> "carpetprimaryuan.fakegames.error.not_waiting";
            case "not_host" -> "carpetprimaryuan.fakegames.error.not_host";
            case "min_players" -> "carpetprimaryuan.fakegames.error.min_players";
            case "max_players" -> "carpetprimaryuan.fakegames.error.max_players";
            case "invalid_member" -> "carpetprimaryuan.fakegames.error.invalid_member";
            case "unavailable" -> "carpetprimaryuan.fakegames.error.unavailable";
            default -> "carpetprimaryuan.fakegames.error.internal";
        };
        return ServerI18n.tr(key);
    }

    static String tr(String key) {
        return ServerI18n.tr("carpetprimaryuan.fakegames." + key).getString();
    }

    /** 管理员判定（跨版本分界：1.21.11 起 CommandSourceStack 不再暴露 hasPermission） */
    private static boolean hasLevel2(CommandSourceStack source) {
        //#if MC < 12111
        //$$ return source.hasPermission(2);
        //#else
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //#endif
    }
}
