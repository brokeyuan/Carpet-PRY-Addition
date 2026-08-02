package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
 * 实现方式：完全手动构建 dropStack 命令树，
 * 为每个 slot 节点同时保留 Carpet 原版 executes（单次丢出）和修饰子节点。
 */
public final class PlayerCommandExtension {

    private PlayerCommandExtension() {}

    /**
     * 完全手动构建 dropStack builder。
     * 不依赖原 builder 的 getArguments()，直接创建 4 个 slot 子节点，
     * 每个节点包含：Carpet 原版 executes（单次丢出）+ 修饰子节点。
     *
     * Carpet 原版 dropStack 命令结构（来自 makeDropCommand）：
     *   dropStack -> all | mainhand | offhand | <slot>
     *   每个 slot 节点有 executes（单次丢出该槽位物品）
     *
     * 扩展后：
     *   dropStack -> all | mainhand | offhand | <slot>
     *   每个 slot 节点：
     *     - executes（原版行为，单次丢出）
     *     - once / continuous / interval / after / perTick / randomly / stop 子节点
     *       （修饰子节点的 executes 调用 DropSlotScheduler 调度持续丢出）
     */
    public static LiteralArgumentBuilder<CommandSourceStack> rebuildDropStackBuilder() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("dropStack");

        // all: slot=-2, slotKey="all"
        LiteralArgumentBuilder<CommandSourceStack> allNode = Commands.literal("all");
        allNode.executes(ctx -> dropOnceCarpet(ctx, DropSlotScheduler.SLOT_ALL));
        addModifiers(allNode, DropSlotScheduler.SLOT_ALL, "all");
        builder.then(allNode);

        // mainhand: slot=-1, slotKey="mainhand"
        LiteralArgumentBuilder<CommandSourceStack> mainhandNode = Commands.literal("mainhand");
        mainhandNode.executes(ctx -> dropOnceCarpet(ctx, DropSlotScheduler.SLOT_MAINHAND));
        addModifiers(mainhandNode, DropSlotScheduler.SLOT_MAINHAND, "mainhand");
        builder.then(mainhandNode);

        // offhand: slot=40, slotKey="offhand"
        LiteralArgumentBuilder<CommandSourceStack> offhandNode = Commands.literal("offhand");
        offhandNode.executes(ctx -> dropOnceCarpet(ctx, DropSlotScheduler.SLOT_OFFHAND));
        addModifiers(offhandNode, DropSlotScheduler.SLOT_OFFHAND, "offhand");
        builder.then(offhandNode);

        // <slot>: argument 0-40, slotKey="slot_<n>"
        RequiredArgumentBuilder<CommandSourceStack, Integer> slotArg =
                Commands.argument("slot", IntegerArgumentType.integer(0, 40));
        slotArg.executes(PlayerCommandExtension::dropOnceDynamicCarpet);
        addModifiersDynamic(slotArg);
        builder.then(slotArg);

        return builder;
    }

    // ===== Carpet 原版行为复刻 =====

    /**
     * 复刻 Carpet 原版 makeDropCommand 中 manipulator 的行为：
     * 调用 EntityPlayerActionPack.drop(slot, true) 丢出指定槽位一次。
     */
    private static int dropOnceCarpet(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        // 复刻 Carpet 原版 ap.drop(slot, true) 行为：丢出该槽位整组物品
        carpet.helpers.EntityPlayerActionPack actionPack = ((carpet.fakes.ServerPlayerInterface) player).getActionPack();
        actionPack.drop(slot, true);
        return 1;
    }

    private static int dropOnceDynamicCarpet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        return dropOnceCarpet(ctx, slot);
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
    private static void addModifiersDynamic(RequiredArgumentBuilder<CommandSourceStack, Integer> slotNode) {
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
        // once 等价于原版单次丢出，不受规则开关影响
        return dropOnceCarpet(ctx, slot);
    }

    /**
     * 调度类回调的统一入口：规则关闭时回退到原版单次丢出行为。
     * 原因：命令树在服务器启动时一次性注册，运行时切换规则不会重建命令树，
     * 因此规则检查必须在 executes 回调内部运行时判断。
     */
    private static int runScheduled(CommandContext<CommandSourceStack> ctx, int slot, String slotKey,
                                     java.util.function.Function<CommandSourceStack, Boolean> scheduler) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!CarpetPrimaryuanSettings.fakePlayerDropStackModifiers) {
            // 规则关闭：回退到原版单次丢出
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.rule_disabled_fallback"), false);
            return dropOnceCarpet(ctx, slot);
        }
        return scheduler.apply(source) ? 1 : 0;
    }

    private static int startContinuous(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        return runScheduled(ctx, slot, slotKey, source -> {
            ServerPlayer player = resolvePlayer(ctx);
            if (player == null) return false;
            boolean ok = DropSlotScheduler.startContinuous(player, slot, slotKey, source);
            if (!ok) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
            } else {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.started_continuous",
                        player.getName().getString(), slotKey), true);
            }
            return true;
        });
    }

    private static int startInterval(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        return runScheduled(ctx, slot, slotKey, source -> {
            ServerPlayer player = resolvePlayer(ctx);
            if (player == null) return false;
            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
            boolean ok = DropSlotScheduler.startInterval(player, slot, slotKey, ticks, source);
            if (!ok) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
            } else {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.started_interval",
                        player.getName().getString(), ticks, slotKey), true);
            }
            return true;
        });
    }

    private static int startAfter(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        return runScheduled(ctx, slot, slotKey, source -> {
            ServerPlayer player = resolvePlayer(ctx);
            if (player == null) return false;
            int delay = IntegerArgumentType.getInteger(ctx, "ticks");
            boolean ok = DropSlotScheduler.startAfter(player, slot, slotKey, delay, source);
            if (!ok) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
            } else {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.started_after",
                        player.getName().getString(), delay, slotKey), true);
            }
            return true;
        });
    }

    private static int startPerTick(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        return runScheduled(ctx, slot, slotKey, source -> {
            ServerPlayer player = resolvePlayer(ctx);
            if (player == null) return false;
            int times = IntegerArgumentType.getInteger(ctx, "times");
            boolean ok = DropSlotScheduler.startPerTick(player, slot, slotKey, times, source);
            if (!ok) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
            } else {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.started_perTick",
                        player.getName().getString(), times, slotKey), true);
            }
            return true;
        });
    }

    private static int startRandomly(CommandContext<CommandSourceStack> ctx, int slot, String slotKey) throws CommandSyntaxException {
        return runScheduled(ctx, slot, slotKey, source -> {
            ServerPlayer player = resolvePlayer(ctx);
            if (player == null) return false;
            int minVal = IntegerArgumentType.getInteger(ctx, "min");
            int maxVal = IntegerArgumentType.getInteger(ctx, "max");
            if (maxVal < minVal) {
                int t = minVal; minVal = maxVal; maxVal = t;
            }
            final int min = minVal;
            final int max = maxVal;
            boolean ok = DropSlotScheduler.startRandomly(player, slot, slotKey, min, max, source);
            if (!ok) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.already_running", slotKey), false);
            } else {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.dropstack.started_randomly",
                        player.getName().getString(), min, max, slotKey), true);
            }
            return true;
        });
    }

    private static int stopTask(CommandContext<CommandSourceStack> ctx, String slotKey) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!CarpetPrimaryuanSettings.fakePlayerDropStackModifiers) {
            // 规则关闭：无任务可停止
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.dropstack.rule_disabled_no_task"), false);
            return 0;
        }
        ServerPlayer player = resolvePlayer(ctx);
        if (player == null) return 0;
        DropSlotScheduler.stop(player, slotKey, source);
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
