package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.DropSlotScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 独立的 /player <name> dropall 命令节点（v3 spec）。
 *
 * 结构：
 *   dropall
 *     - executes（顶层无参数等价 once）
 *     - once
 *     - continuous
 *     - interval <ticks>
 *     - after <ticks>
 *     - perTick <times>
 *     - randomly <min> <max>
 *     - stop
 *
 * 行为：
 * - 整个 dropall 命令树（含 once）通过根节点 requires 谓词受
 *   {@link CarpetPrimaryuanSettings#fakePlayerDropStackModifiers} 控制可见性；
 *   规则关闭时整棵命令不可见（tab 补全不到、无法执行）。
 * - 规则变更时由 RuleObserver 触发命令树重新下发，可见性立即生效。
 *
 * 调度统一委托 {@link DropSlotScheduler}，slotKey 固定为 "dropall"。
 */
public final class PlayerCommandExtension {

    /** 与 DropSlotScheduler.SLOT_ALL 一致：-2 表示全背包 */
    private static final int SLOT_ALL = -2;

    /** 任务槽位 key：标识 dropall 任务，用于 DropSlotScheduler 内部去重 */
    private static final String SLOT_KEY = "dropall";

    private PlayerCommandExtension() {}

    /**
     * 构建独立的 dropall 命令 builder。
     *
     * @return dropall 命令的 LiteralArgumentBuilder
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildDropAllNode() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("dropall")
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerDropStackModifiers);

        // 顶层无参数等价 once
        builder.executes(PlayerCommandExtension::runOnce);

        // once：立即丢一次全背包
        builder.then(Commands.literal("once").executes(PlayerCommandExtension::runOnce));

        // continuous：每 tick 丢一次全背包
        builder.then(Commands.literal("continuous").executes(PlayerCommandExtension::startContinuous));

        // interval <ticks>：每 ticks 丢一次全背包
        builder.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startInterval)));

        // after <ticks>：延迟 ticks 后丢一次全背包
        builder.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startAfter)));

        // perTick <times>：每秒 times 次丢全背包
        builder.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(PlayerCommandExtension::startPerTick)));

        // randomly <min> <max>：随机间隔 min-max tick 丢一次全背包
        builder.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(PlayerCommandExtension::startRandomly))));

        // stop：停止 dropall 任务
        builder.then(Commands.literal("stop").executes(PlayerCommandExtension::stopTask));

        return builder;
    }

    // ===== 命令回调 =====

    /**
     * once / 顶层：立即丢一次全背包。
     */
    private static int runOnce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        DropSlotScheduler.dropOnce(player, SLOT_ALL);
        return 1;
    }

    /**
     * continuous：每 tick 丢一次全背包。
     */
    private static int startContinuous(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        boolean ok = DropSlotScheduler.startContinuous(player, SLOT_ALL, SLOT_KEY, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.already_running", SLOT_KEY), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.started_continuous",
                    player.getName().getString(), SLOT_KEY), true);
        }
        return 1;
    }

    /**
     * interval <ticks>：每 ticks 丢一次全背包。
     */
    private static int startInterval(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        boolean ok = DropSlotScheduler.startInterval(player, SLOT_ALL, SLOT_KEY, ticks, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.already_running", SLOT_KEY), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.started_interval",
                    player.getName().getString(), ticks, SLOT_KEY), true);
        }
        return 1;
    }

    /**
     * after <ticks>：延迟 ticks 后丢一次全背包。
     */
    private static int startAfter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int delay = IntegerArgumentType.getInteger(ctx, "ticks");
        boolean ok = DropSlotScheduler.startAfter(player, SLOT_ALL, SLOT_KEY, delay, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.already_running", SLOT_KEY), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.started_after",
                    player.getName().getString(), delay, SLOT_KEY), true);
        }
        return 1;
    }

    /**
     * perTick <times>：每秒 times 次丢全背包。
     */
    private static int startPerTick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int times = IntegerArgumentType.getInteger(ctx, "times");
        boolean ok = DropSlotScheduler.startPerTick(player, SLOT_ALL, SLOT_KEY, times, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.already_running", SLOT_KEY), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.started_perTick",
                    player.getName().getString(), times, SLOT_KEY), true);
        }
        return 1;
    }

    /**
     * randomly <min> <max>：随机间隔 min-max tick 丢一次全背包。
     */
    private static int startRandomly(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int minVal = IntegerArgumentType.getInteger(ctx, "min");
        int maxVal = IntegerArgumentType.getInteger(ctx, "max");
        if (maxVal < minVal) {
            int t = minVal; minVal = maxVal; maxVal = t;
        }
        final int min = minVal;
        final int max = maxVal;
        boolean ok = DropSlotScheduler.startRandomly(player, SLOT_ALL, SLOT_KEY, min, max, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.already_running", SLOT_KEY), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropall.started_randomly",
                    player.getName().getString(), min, max, SLOT_KEY), true);
        }
        return 1;
    }

    /**
     * stop：停止 dropall 任务。
     */
    private static int stopTask(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        DropSlotScheduler.stop(player, SLOT_KEY, source);
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
}
