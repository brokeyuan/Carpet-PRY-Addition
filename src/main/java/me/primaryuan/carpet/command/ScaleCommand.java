package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * /scale 命令：玩家调节自身 scale，管理员调节任意玩家 scale。
 *
 * 结构：
 *   scale
 *     - <value>            玩家调节自己（受 playerScaleMin/Max 范围限制）
 *     - reset              玩家重置自己（恢复 1.0）
 *     - <player> <value>  管理员调节指定玩家（不受范围限制）
 *     - <player> reset    管理员重置指定玩家
 *
 * 行为：
 * - 整个 /scale 命令通过根节点 requires 谓词受 playerScaleModifiers 规则控制可见性。
 * - 管理员路径（带 player 参数）需要 OP 4 级权限。
 * - 玩家路径的 value 必须在 playerScaleMin ~ playerScaleMax 之间。
 */
public final class ScaleCommand {

    /** 默认 scale 值（reset 时使用） */
    private static final double DEFAULT_SCALE = 1.0;

    /** 玩家路径 value 参数的硬上限，避免极端值导致崩溃 */
    private static final double VALUE_MIN = 0.0;
    private static final double VALUE_MAX = 100.0;

    /** 在线玩家名称补全 */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS =
            ScaleCommand::suggestOnlinePlayers;

    private ScaleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("scale")
                    .requires(source -> CarpetPrimaryuanSettings.playerScaleModifiers)
                    // /scale <value>  玩家调节自己
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(VALUE_MIN, VALUE_MAX))
                            .executes(ScaleCommand::setSelfScale))
                    // /scale reset  玩家重置自己
                    .then(Commands.literal("reset")
                            .executes(ScaleCommand::resetSelfScale))
                    // /scale <player> <value>  管理员调节指定玩家
                    .then(Commands.argument("player", StringArgumentType.word())
                            .requires(ScaleCommand::isAdmin)
                            .suggests(ONLINE_PLAYER_SUGGESTIONS)
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(VALUE_MIN, VALUE_MAX))
                                    .executes(ScaleCommand::setTargetScale))
                            // /scale <player> reset  管理员重置指定玩家
                            .then(Commands.literal("reset")
                                    .executes(ScaleCommand::resetTargetScale))));
        });
    }

    // ===== 权限检查 =====

    private static boolean isAdmin(CommandSourceStack source) {
        if (!source.isPlayer()) return true;
        //#if MC <= 12110
        //$$ return source.hasPermission(4);
        //#else
        return Commands.LEVEL_OWNERS.check(source.permissions());
        //#endif
    }

    // ===== 命令回调 =====

    /**
     * /scale <value>：玩家调节自己，受 playerScaleMin/Max 范围限制。
     */
    private static int setSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        double min = CarpetPrimaryuanSettings.playerScaleMin;
        double max = CarpetPrimaryuanSettings.playerScaleMax;
        if (value < min || value > max) {
            player.sendSystemMessage(ServerI18n.tr(player,
                    "carpetprimaryuan.command.scale.out_of_range",
                    formatScale(value), formatScale(min), formatScale(max)));
            return 0;
        }

        return applyScale(player, value, ctx.getSource(), "set_self", null);
    }

    /**
     * /scale reset：玩家重置自己。
     */
    private static int resetSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return applyScale(player, DEFAULT_SCALE, ctx.getSource(), "reset_self", null);
    }

    /**
     * /scale <player> <value>：管理员调节指定玩家，不受范围限制。
     */
    private static int setTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            ctx.getSource().sendSuccess(() -> ServerI18n.tr(ctx.getSource(),
                    "carpetprimaryuan.command.scale.player_offline", playerName), false);
            return 0;
        }

        double value = DoubleArgumentType.getDouble(ctx, "value");
        return applyScale(target, value, ctx.getSource(), "set_other", playerName);
    }

    /**
     * /scale <player> reset：管理员重置指定玩家。
     */
    private static int resetTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            ctx.getSource().sendSuccess(() -> ServerI18n.tr(ctx.getSource(),
                    "carpetprimaryuan.command.scale.player_offline", playerName), false);
            return 0;
        }

        return applyScale(target, DEFAULT_SCALE, ctx.getSource(), "reset_other", playerName);
    }

    // ===== 核心逻辑 =====

    /**
     * 应用 scale 到目标玩家，并发送相应提示。
     *
     * @param target     被调整的玩家
     * @param value      scale 值
     * @param source     命令来源（用于反馈）
     * @param selfKey    自己执行的反馈 i18n key
     * @param targetName 被调整玩家名（管理员路径传值；自己执行路径传 null）
     * @return 命令结果（1 = 成功）
     */
    private static int applyScale(ServerPlayer target, double value,
                                  CommandSourceStack source, String selfKey, String targetName) {
        boolean isSelf = targetName == null;
        String valueStr = formatScale(value);

        //#if MC >= 12105
        //$$ target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)
        //$$         .setBaseValue(value);
        //$$ if (isSelf) {
        //$$     target.sendSystemMessage(ServerI18n.tr(target,
        //$$             "carpetprimaryuan.command.scale." + selfKey, valueStr));
        //$$ } else {
        //$$     String name = target.getName().getString();
        //$$     source.sendSuccess(() -> ServerI18n.tr(source,
        //$$             "carpetprimaryuan.command.scale." + selfKey, name, valueStr), true);
        //$$     target.sendSystemMessage(ServerI18n.tr(target,
        //$$             "carpetprimaryuan.command.scale.adjusted_by_admin", valueStr));
        //$$ }
        //$$ return 1;
        //#else
        // 1.21~1.21.4 没有 Attributes.SCALE 字段，提示不支持
        if (isSelf) {
            target.sendSystemMessage(ServerI18n.tr(target,
                    "carpetprimaryuan.command.scale.unsupported_version"));
        } else {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.unsupported_version"), false);
        }
        return 0;
        //#endif
    }

    // ===== 工具方法 =====

    /**
     * 格式化 scale 值：整数去 .0，小数保留原样。
     */
    private static String formatScale(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /**
     * 在线玩家名称补全。
     */
    private static CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var players = context.getSource().getServer().getPlayerList().getPlayers();
            String remaining = builder.getRemainingLowerCase();
            for (ServerPlayer p : players) {
                //#if MC >= 12110
                //$$ String name = p.getGameProfile().name();
                //#else
                String name = p.getGameProfile().getName();
                //#endif
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }
}
