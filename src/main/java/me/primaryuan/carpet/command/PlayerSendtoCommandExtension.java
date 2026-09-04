package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.SendtoLinkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 独立的 /player <name> sendto 命令节点（假人背包链接 spec）。
 *
 * 结构（频率子命令与 dropall 一致）：
 *   sendto
 *     - <target>（建立链接并立即开始转移，默认 continuous）
 *     - once（立即对已有链接转移一组）
 *     - continuous（每 tick 转一组）
 *     - interval <ticks>（每 ticks 转一组）
 *     - after <ticks>（延迟 ticks 后转一组）
 *     - perTick <times>（每秒 times 次转一组）
 *     - randomly <min> <max>（随机间隔转一组）
 *     - stop（停止转移并移除全部链接）
 *
 * 行为：
 * - 整个 sendto 命令树通过根节点 requires 谓词受
 *   {@link CarpetPrimaryuanSettings#fakePlayerSendto} 控制可见性；
 *   规则关闭时整棵命令不可见（tab 补全不到、无法执行），
 *   已有链接保留在内存中但暂停转移，重新开启后恢复。
 * - 规则变更时由 CarpetPrimaryuanServer 的 RuleObserver 触发命令树重新下发，可见性立即生效。
 *
 * 链接的校验、存储、调度与物品转移统一委托 {@link SendtoLinkManager}。
 * 接入方式与 dropall（{@link PlayerCommandExtension}）一致：由
 * mixins.rule.fakePlayerSendto.PlayerCommandMixin 注入 Carpet 的 /player 命令树。
 */
public final class PlayerSendtoCommandExtension {

    private PlayerSendtoCommandExtension() {}

    /**
     * 构建独立的 sendto 命令 builder。
     *
     * @return sendto 命令的 LiteralArgumentBuilder
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildSendtoNode() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("sendto")
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerSendto);

        // <target>：建立 源假人 → 目标假人 的链接并立即开始转移（默认 continuous）
        builder.then(Commands.argument("target", StringArgumentType.word())
                .suggests(PlayerSendtoCommandExtension::suggestOnlinePlayers)
                .executes(PlayerSendtoCommandExtension::addLink));

        // once：立即对已有链接转移一组（不影响既有调度节奏）
        builder.then(Commands.literal("once").executes(PlayerSendtoCommandExtension::runOnce));

        // continuous：每 tick 转一组
        builder.then(Commands.literal("continuous").executes(PlayerSendtoCommandExtension::startContinuous));

        // interval <ticks>：每 ticks 转一组
        builder.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerSendtoCommandExtension::startInterval)));

        // after <ticks>：延迟 ticks 后转一组
        builder.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerSendtoCommandExtension::startAfter)));

        // perTick <times>：每秒 times 次转一组
        builder.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(PlayerSendtoCommandExtension::startPerTick)));

        // randomly <min> <max>：随机间隔 min-max tick 转一组
        builder.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(PlayerSendtoCommandExtension::startRandomly))));

        // stop：停止转移并移除该源的全部链接
        builder.then(Commands.literal("stop").executes(PlayerSendtoCommandExtension::stopAll));

        return builder;
    }

    // ===== 命令回调 =====

    /**
     * sendto <target>：建立链接并立即开始转移（新源默认 continuous），
     * 反馈该源当前的完整链接列表。
     */
    private static int addLink(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        String playerName = player.getName().getString();
        String targetName = StringArgumentType.getString(ctx, "target");

        SendtoLinkManager.LinkResult result = SendtoLinkManager.addLink(source.getServer(), player, targetName);
        if (result == SendtoLinkManager.LinkResult.SUCCESS) {
            List<String> links = SendtoLinkManager.getLinks(player);
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.linked", playerName, targetName), true);
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.current_links", playerName, String.join(", ", links)), false);
            return 1;
        }

        switch (result) {
            case SOURCE_NOT_FAKE:
                source.sendFailure(ServerI18n.tr(source,
                        "carpetprimaryuan.command.sendto.source_not_fake", playerName));
                break;
            case TARGET_OFFLINE:
                source.sendFailure(ServerI18n.tr(source,
                        "carpetprimaryuan.command.sendto.target_offline", targetName));
                break;
            case TARGET_NOT_FAKE:
                source.sendFailure(ServerI18n.tr(source,
                        "carpetprimaryuan.command.sendto.target_not_fake", targetName));
                break;
            case SELF_LINK:
                source.sendFailure(ServerI18n.tr(source,
                        "carpetprimaryuan.command.sendto.self_link", playerName, targetName));
                break;
            case DUPLICATE:
            default:
                source.sendFailure(ServerI18n.tr(source,
                        "carpetprimaryuan.command.sendto.duplicate", playerName, targetName));
                break;
        }
        return 0;
    }

    /**
     * once：立即对已有链接转移一组。
     */
    private static int runOnce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        String playerName = player.getName().getString();

        int moved = SendtoLinkManager.transferOnce(source.getServer(), player);
        if (moved < 0) {
            source.sendFailure(ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.no_links", playerName));
            return 0;
        }
        if (moved == 0) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.transferred_zero", playerName), false);
            return 0;
        }
        final int count = moved;
        source.sendSuccess(() -> ServerI18n.tr(source,
                "carpetprimaryuan.command.sendto.transferred_once", count), true);
        return 1;
    }

    /**
     * continuous：每 tick 转一组。
     */
    private static int startContinuous(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return startMode(ctx, SendtoLinkManager.Mode.CONTINUOUS, 1, 0, 0,
                "carpetprimaryuan.command.sendto.started_continuous");
    }

    /**
     * interval <ticks>：每 ticks 转一组。
     */
    private static int startInterval(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        return startMode(ctx, SendtoLinkManager.Mode.INTERVAL, ticks, 0, 0,
                "carpetprimaryuan.command.sendto.started_interval", ticks);
    }

    /**
     * after <ticks>：延迟 ticks 后转一组。
     */
    private static int startAfter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int delay = IntegerArgumentType.getInteger(ctx, "ticks");
        return startMode(ctx, SendtoLinkManager.Mode.AFTER, delay, 0, 0,
                "carpetprimaryuan.command.sendto.started_after", delay);
    }

    /**
     * perTick <times>：每秒 times 次转一组（每 max(1, 20/times) tick 一次）。
     */
    private static int startPerTick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int times = IntegerArgumentType.getInteger(ctx, "times");
        int interval = Math.max(1, 20 / Math.max(1, times));
        return startMode(ctx, SendtoLinkManager.Mode.PERTICK, interval, 0, 0,
                "carpetprimaryuan.command.sendto.started_perTick", times);
    }

    /**
     * randomly <min> <max>：随机间隔 min-max tick 转一组。
     */
    private static int startRandomly(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int minVal = IntegerArgumentType.getInteger(ctx, "min");
        int maxVal = IntegerArgumentType.getInteger(ctx, "max");
        if (maxVal < minVal) {
            int t = minVal; minVal = maxVal; maxVal = t;
        }
        final int min = minVal;
        final int max = maxVal;
        return startMode(ctx, SendtoLinkManager.Mode.RANDOMLY, min, min, max,
                "carpetprimaryuan.command.sendto.started_randomly", min, max);
    }

    /**
     * 频率子命令统一入口：设置调度模式，无链接时提示先建立链接。
     * feedbackArgs 为成功反馈消息的格式化参数（首参数固定为源假人名）。
     */
    private static int startMode(CommandContext<CommandSourceStack> ctx, SendtoLinkManager.Mode mode,
                                 int interval, int min, int max, String key, Object... feedbackArgs)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        String playerName = player.getName().getString();

        if (!SendtoLinkManager.setMode(player, mode, interval, min, max)) {
            source.sendFailure(ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.no_links", playerName, playerName));
            return 0;
        }

        // 组装反馈参数：源假人名 + 各子命令的附加参数
        Object[] args = new Object[feedbackArgs.length + 1];
        args[0] = playerName;
        System.arraycopy(feedbackArgs, 0, args, 1, feedbackArgs.length);
        source.sendSuccess(() -> ServerI18n.tr(source, key, args), true);
        return 1;
    }

    /**
     * stop：停止转移并移除该源的全部链接，反馈累计统计。
     */
    private static int stopAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        String playerName = player.getName().getString();

        int[] stats = SendtoLinkManager.stopAndRemove(player);
        if (stats == null) {
            source.sendFailure(ServerI18n.tr(source,
                    "carpetprimaryuan.command.sendto.no_links", playerName, playerName));
            return 0;
        }
        source.sendSuccess(() -> ServerI18n.tr(source,
                "carpetprimaryuan.command.sendto.stopped_all", playerName, stats[0], stats[1]), true);
        return 1;
    }

    // ===== 工具方法 =====

    /**
     * 从 CommandContext 中解析目标 ServerPlayer。
     * 依赖外层命令树注入的 "player" 字符串参数（Carpet /player <name> 风格）。
     */
    private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx) {
        String playerName;
        try {
            playerName = StringArgumentType.getString(ctx, "player");
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Missing player argument"));
            return null;
        }
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
        }
        return player;
    }

    /**
     * 自动补全：从在线玩家列表中获取所有玩家名。
     */
    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var players = context.getSource().getServer().getPlayerList().getPlayers();
            String remaining = builder.getRemainingLowerCase();
            for (ServerPlayer p : players) {
                String name = p.getName().getString();
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }
}
