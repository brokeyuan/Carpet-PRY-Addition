package me.primaryuan.carpet.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
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
 * 实现方式：重建整个 dropStack builder，复制 Carpet 原有子节点的 executes，
 * 并为每个子节点追加修饰子节点。不依赖 Brigadier then() 的同名节点合并行为。
 */
public final class PlayerCommandExtension {

    private PlayerCommandExtension() {}

    /**
     * 重建 dropStack builder：复制 Carpet 原有子节点（all/mainhand/offhand/<slot>）的 executes，
     * 并为每个子节点追加 once/continuous/interval/after/perTick/randomly/stop 修饰子节点。
     *
     * 调用方在 Mixin 中用 cir.setReturnValue(rebuildDropStackBuilder(original)) 替换返回值。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static LiteralArgumentBuilder<CommandSourceStack> rebuildDropStackBuilder(
            LiteralArgumentBuilder<CommandSourceStack> original) {
        LiteralArgumentBuilder<CommandSourceStack> rebuilder = Commands.literal(original.getLiteral());
        // 原版 makeDropCommand 的顶层无 executes，无需复制 command
        // 遍历 original 已有的子节点，重建并追加修饰
        for (CommandNode<CommandSourceStack> child : original.getChildren()) {
            if (child instanceof LiteralCommandNode<CommandSourceStack> litChild) {
                String lit = litChild.getLiteral();
                int slot;
                String slotKey;
                switch (lit) {
                    case "all" -> { slot = DropSlotScheduler.SLOT_ALL; slotKey = "all"; }
                    case "mainhand" -> { slot = DropSlotScheduler.SLOT_MAINHAND; slotKey = "mainhand"; }
                    case "offhand" -> { slot = DropSlotScheduler.SLOT_OFFHAND; slotKey = "offhand"; }
                    default -> { rebuilder.then(child); continue; }
                }
                LiteralArgumentBuilder<CommandSourceStack> newChild = Commands.literal(lit);
                if (litChild.getCommand() != null) {
                    newChild.executes(litChild.getCommand());
                }
                addModifiers(newChild, slot, slotKey);
                rebuilder.then(newChild);
            } else if (child instanceof ArgumentCommandNode) {
                ArgumentCommandNode<CommandSourceStack, ?> argChild = (ArgumentCommandNode<CommandSourceStack, ?>) child;
                ArgumentType type = argChild.getType();
                RequiredArgumentBuilder newArg = Commands.argument(argChild.getName(), type);
                if (argChild.getCommand() != null) {
                    newArg.executes(argChild.getCommand());
                }
                addModifiersDynamic(newArg);
                rebuilder.then(newArg);
            } else {
                rebuilder.then(child);
            }
        }
        return rebuilder;
    }

    // ===== 修饰子节点挂载 =====

    /**
     * 给固定 slot（all/mainhand/offhand）的 LiteralArgumentBuilder 追加修饰子节点。
     */
    private static void addModifiers(LiteralArgumentBuilder<CommandSourceStack> slotNode, int slot, String slotKey) {
        slotNode.then(Commands.literal("once").executes(ctx -> runOnce(ctx, slot)));
        slotNode.then(Commands.literal("continuous").executes(ctx -> startContinuous(ctx, slot, slotKey)));
        slotNode.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> startInterval(ctx, slot, slotKey))));
        slotNode.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> startAfter(ctx, slot, slotKey))));
        slotNode.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> startPerTick(ctx, slot, slotKey))));
        slotNode.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(ctx -> startRandomly(ctx, slot, slotKey)))));
        slotNode.then(Commands.literal("stop")
                .executes(ctx -> stopTask(ctx, slotKey)));
    }

    /**
     * 给动态 slot（argument("slot", ...)）的 RequiredArgumentBuilder 追加修饰子节点。
     * slot 从 context 动态读取，slotKey = "slot_" + slot。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addModifiersDynamic(RequiredArgumentBuilder slotNode) {
        slotNode.then(Commands.literal("once").executes(PlayerCommandExtension::runOnceDynamic));
        slotNode.then(Commands.literal("continuous").executes(PlayerCommandExtension::startContinuousDynamic));
        slotNode.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startIntervalDynamic)));
        slotNode.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(PlayerCommandExtension::startAfterDynamic)));
        slotNode.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(PlayerCommandExtension::startPerTickDynamic)));
        slotNode.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(PlayerCommandExtension::startRandomlyDynamic))));
        slotNode.then(Commands.literal("stop")
                .executes(PlayerCommandExtension::stopTaskDynamic));
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
