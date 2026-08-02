package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
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
 * 给 Carpet 的 /player <name> dropStack [all|mainhand|offhand|<slot>] 命令节点
 * 追加 once/continuous/interval <ticks>/after <ticks>/perTick <times>/randomly <min> <max>/stop
 * 动作修饰子节点。
 *
 * 仅当 CarpetPrimaryuanSettings.fakePlayerDropStackModifiers 为 true 时挂载。
 *
 * Brigadier 的 then() 会自动合并同名子节点，所以新加的 literal("all").then(literal("continuous").executes(...))
 * 会与原有的 literal("all").executes(dropAllOnce) 合并，保留原 executes 并追加 children。
 */
public final class PlayerCommandExtension {

    private PlayerCommandExtension() {}

    /**
     * 给 dropStack 命令节点追加动作修饰子节点。
     * 调用方需保证传入的是 makeDropCommand("dropStack", true) 返回的 builder。
     */
    public static void extendDropStackNode(LiteralArgumentBuilder<CommandSourceStack> dropStackBuilder) {
        if (!CarpetPrimaryuanSettings.fakePlayerDropStackModifiers) {
            return;
        }

        // all: slot=-2, slotKey="all"
        dropStackBuilder.then(buildSlotNode("all", DropSlotScheduler.SLOT_ALL, "all"));
        // mainhand: slot=-1, slotKey="mainhand"
        dropStackBuilder.then(buildSlotNode("mainhand", DropSlotScheduler.SLOT_MAINHAND, "mainhand"));
        // offhand: slot=40, slotKey="offhand"
        dropStackBuilder.then(buildSlotNode("offhand", DropSlotScheduler.SLOT_OFFHAND, "offhand"));
        // <slot>: RequiredArgumentBuilder，slotKey 动态 "slot_<n>"
        // buildModifierNodesForDynamicSlot 已返回 argument("slot", ...)，
        // Brigadier 的 then() 会与 Carpet 原有的 argument("slot").executes(dropSlot) 合并：
        // 保留原 executes，并追加 once/continuous/interval/after/perTick/randomly/stop 子节点。
        dropStackBuilder.then(buildModifierNodesForDynamicSlot());
    }

    /**
     * 为固定的 literal slot（all/mainhand/offhand）构建带修饰子节点的 builder。
     * Brigadier 会自动与原 builder 合并同名 literal。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildSlotNode(String literal, int slot, String slotKey) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal);
        addModifiers(node, slot, slotKey);
        return node;
    }

    /**
     * 给一个 slot 节点追加 once/continuous/interval/after/perTick/randomly/stop 子节点。
     * slot 与 slotKey 固定（用于 all/mainhand/offhand）。
     */
    private static void addModifiers(LiteralArgumentBuilder<CommandSourceStack> slotNode, int slot, String slotKey) {
        // once：立即丢一次
        slotNode.then(Commands.literal("once")
                .executes(ctx -> runOnce(ctx, slot)));

        // continuous：每 tick 丢一次
        slotNode.then(Commands.literal("continuous")
                .executes(ctx -> startContinuous(ctx, slot, slotKey)));

        // interval <ticks>：每隔 ticks 丢一次
        slotNode.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> startInterval(ctx, slot, slotKey))));

        // after <ticks>：ticks 后丢一次
        slotNode.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> startAfter(ctx, slot, slotKey))));

        // perTick <times>：每秒 times 次
        slotNode.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> startPerTick(ctx, slot, slotKey))));

        // randomly <min> <max>：每次随机间隔
        slotNode.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(ctx -> startRandomly(ctx, slot, slotKey)))));

        // stop：停止该 slotKey 任务
        slotNode.then(Commands.literal("stop")
                .executes(ctx -> stopTask(ctx, slotKey)));
    }

    /**
     * 给动态 slot 参数节点追加修饰子节点。slot 从 context 读取，slotKey = "slot_" + slot。
     */
    private static ArgumentBuilder<CommandSourceStack, ?> buildModifierNodesForDynamicSlot() {
        // 用一个空的 literal 作为容器是不行的——我们需要直接把修饰子节点加到 argument("slot", ...) 上。
        // 但 Brigadier 的 argument 节点本身不能再用 literal(...) 直接合并到原 argument（同名 argument 会冲突）。
        // 因此这里返回一个不带 literal 的 builder 链：通过 then() 给 argument("slot") 加子节点。
        // 由于 ArgumentBuilder.then() 返回自身，我们用一个 dummy literal 链接到 argument，
        // 然后在 executes 时从 context 取 slot。
        //
        // 实际上更简单的做法：直接返回 argument("slot", ...).then(literal("once"))... 链。
        // 但因为我们要复用 addModifiers 的逻辑，而 addModifiers 接受 LiteralArgumentBuilder，
        // 这里我们创建一个新的 LiteralArgumentBuilder 是不合适的。
        //
        // 解决方案：直接对 argument("slot") 节点追加子节点。
        // 但 buildSlotNode 返回的是 LiteralArgumentBuilder，不能直接复用。
        // 这里手动构造，slotKey 在 executes 时动态生成。
        //
        // 注：Brigadier 允许 argument 节点下挂 literal 子节点，反之亦然。
        ArgumentBuilder<CommandSourceStack, ?> slotArg = Commands.argument("slot", IntegerArgumentType.integer(0, 40));

        slotArg.then(Commands.literal("once")
                .executes(PlayerCommandExtension::runOnceDynamic));
        slotArg.then(Commands.literal("continuous")
                .executes(PlayerCommandExtension::startContinuousDynamic));
        slotArg.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startIntervalDynamic)));
        slotArg.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startAfterDynamic)));
        slotArg.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(PlayerCommandExtension::startPerTickDynamic)));
        slotArg.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(PlayerCommandExtension::startRandomlyDynamic))));
        slotArg.then(Commands.literal("stop")
                .executes(PlayerCommandExtension::stopTaskDynamic));

        return slotArg;
    }

    // ===== 固定 slot（all/mainhand/offhand）的命令回调 =====

    private static int runOnce(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        DropSlotScheduler.dropOnce(player, slot);
        return 1;
    }

    private static int startContinuous(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        CommandSourceStack source = ctx.getSource();
        boolean ok = DropSlotScheduler.startContinuous(player, slot, slotKey, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.started_continuous",
                    player.getName().getString(), slotKey), true);
        }
        return 1;
    }

    private static int startInterval(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        CommandSourceStack source = ctx.getSource();
        boolean ok = DropSlotScheduler.startInterval(player, slot, slotKey, ticks, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.started_interval",
                    player.getName().getString(), ticks, slotKey), true);
        }
        return 1;
    }

    private static int startAfter(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int delay = IntegerArgumentType.getInteger(ctx, "ticks");
        CommandSourceStack source = ctx.getSource();
        boolean ok = DropSlotScheduler.startAfter(player, slot, slotKey, delay, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.started_after",
                    player.getName().getString(), delay, slotKey), true);
        }
        return 1;
    }

    private static int startPerTick(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int times = IntegerArgumentType.getInteger(ctx, "times");
        CommandSourceStack source = ctx.getSource();
        boolean ok = DropSlotScheduler.startPerTick(player, slot, slotKey, times, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.started_perTick",
                    player.getName().getString(), times, slotKey), true);
        }
        return 1;
    }

    private static int startRandomly(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        int minVal = IntegerArgumentType.getInteger(ctx, "min");
        int maxVal = IntegerArgumentType.getInteger(ctx, "max");
        if (maxVal < minVal) {
            // 交换，避免IllegalArgumentException
            int t = minVal; minVal = maxVal; maxVal = t;
        }
        final int min = minVal;
        final int max = maxVal;
        CommandSourceStack source = ctx.getSource();
        boolean ok = DropSlotScheduler.startRandomly(player, slot, slotKey, min, max, source);
        if (!ok) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.started_randomly",
                    player.getName().getString(), min, max, slotKey), true);
        }
        return 1;
    }

    private static int stopTask(CommandContext<CommandSourceStack> ctx, String slotKey) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        DropSlotScheduler.stop(player, slotKey, ctx.getSource());
        return 1;
    }

    // ===== 动态 slot（<slot> 参数）的命令回调 =====

    private static int runOnceDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return runOnce(ctx, slot);
    }

    private static int startContinuousDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return startContinuous(ctx, slot, "slot_" + slot);
    }

    private static int startIntervalDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return startInterval(ctx, slot, "slot_" + slot);
    }

    private static int startAfterDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return startAfter(ctx, slot, "slot_" + slot);
    }

    private static int startPerTickDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return startPerTick(ctx, slot, "slot_" + slot);
    }

    private static int startRandomlyDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return startRandomly(ctx, slot, "slot_" + slot);
    }

    private static int stopTaskDynamic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return stopTask(ctx, "slot_" + slot);
    }

    // ===== 工具方法 =====

    /**
     * 从 context 拿到目标玩家（/player <player> ... 中的 player 参数）。
     * 不抛异常，找不到返回 null（并已发反馈）。
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
