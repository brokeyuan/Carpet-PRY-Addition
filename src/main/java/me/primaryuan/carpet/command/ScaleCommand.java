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
 * /scale 命令：玩家调节自身 scale，管理员/everyone 模式调节任意玩家。
 *
 * 结构（统一三层：动作 + 参数 + 可选目标）：
 *   scale
 *     set
 *       <value>              → 给自己设置（受 playerScaleMin/Max 限制）
 *       <value> <player>     → 给指定玩家设置（canModifyOther 权限）
 *     reset
 *       (无参数)             → 自己恢复 1.0
 *       <player>             → 恢复指定玩家（canModifyOther 权限）
 *     info
 *       (无参数)             → 查看自己当前大小 + 允许范围
 *       <player>             → 查看指定玩家当前大小（canModifyOther 权限）
 *
 * 权限规则（canModifyOther）：
 *   - OP：总是可以
 *   - 规则=everyone：所有人都可以
 *   - 规则=true：非 OP 不可
 *   - 规则=false：整个命令不可见
 *
 * Tab 补全（玩家列表）：
 *   - true 模式 + 非 OP：只补全自己名字
 *   - OP / everyone：补全所有在线玩家
 *
 * 范围限制：
 *   - 自己/everyone 调别人：受 playerScaleMin/Max 限制
 *   - OP 调别人：不受限制
 */
public final class ScaleCommand {

    /** 默认 scale 值（reset 时使用） */
    private static final double DEFAULT_SCALE = 1.0;

    /** value 参数的硬上下限（OP 路径），避免极端值导致崩溃 */
    private static final double VALUE_MIN = 0.0;
    private static final double VALUE_MAX = 100.0;

    private ScaleCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 自定义玩家补全提供者：根据模式过滤可见玩家
            SuggestionProvider<CommandSourceStack> modifySuggestions = ScaleCommand::suggestPlayersForModification;
            SuggestionProvider<CommandSourceStack> infoSuggestions = ScaleCommand::suggestPlayersForInfo;

            dispatcher.register(Commands.literal("scale")
                    .requires(source -> !"false".equalsIgnoreCase(CarpetPrimaryuanSettings.playerScaleModifiers))

                    // ===== set =====
                    .then(Commands.literal("set")
                            // /scale set <value>
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(VALUE_MIN, VALUE_MAX))
                                    .executes(ScaleCommand::setSelfScale)
                                    // /scale set <value> <player>
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .suggests(modifySuggestions)
                                            .executes(ScaleCommand::setTargetScale))))

                    // ===== reset =====
                    .then(Commands.literal("reset")
                            .executes(ScaleCommand::resetSelfScale)
                            // /scale reset <player>
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(modifySuggestions)
                                    .executes(ScaleCommand::resetTargetScale)))

                    // ===== info =====
                    .then(Commands.literal("info")
                            .executes(ScaleCommand::infoSelfScale)
                            // /scale info <player>
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(infoSuggestions)
                                    .executes(ScaleCommand::infoTargetScale))));
        });
    }

    // ==================== 权限检查 ====================

    private static boolean isAdmin(CommandSourceStack source) {
        if (!source.isPlayer()) return true;
        //#if MC <= 12110
        //$$ return source.hasPermission(4);
        //#else
        return Commands.LEVEL_OWNERS.check(source.permissions());
        //#endif
    }

    /**
     * 是否允许"调节他人"（set / reset 他人）。
     * 规则：OP 总是允许；规则=everyone 所有人允许；规则=true 非 OP 不允许。
     */
    private static boolean canModifyOther(CommandSourceStack source) {
        if (isAdmin(source)) return true;
        String rule = CarpetPrimaryuanSettings.playerScaleModifiers;
        return "everyone".equalsIgnoreCase(rule);
    }

    /**
     * 是否允许"查询他人 info"（比 modify 更宽松：true 模式下也允许，info 不危险）。
     */
    private static boolean canViewOther(CommandSourceStack source) {
        if (isAdmin(source)) return true;
        String rule = CarpetPrimaryuanSettings.playerScaleModifiers;
        return "everyone".equalsIgnoreCase(rule) || "true".equalsIgnoreCase(rule);
    }

    // ==================== set 自己 / 他人 ====================

    private static int setSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        double min = CarpetPrimaryuanSettings.playerScaleMin;
        double max = CarpetPrimaryuanSettings.playerScaleMax;
        if (value < min || value > max) {
            self.sendSystemMessage(ServerI18n.tr(self,
                    "carpetprimaryuan.command.scale.out_of_range",
                    formatScale(value), formatScale(min), formatScale(max)));
            return 0;
        }
        return applyScale(self, value, ctx.getSource(), "set_self", null);
    }

    private static int setTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.player_offline", playerName), false);
            return 0;
        }

        // 权限检查
        boolean selfOperation = source.isPlayer() && target.getUUID().equals(source.getPlayer().getUUID());
        if (!selfOperation && !canModifyOther(source)) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.no_permission_modify"), false);
            return 0;
        }

        double value = DoubleArgumentType.getDouble(ctx, "value");

        // 范围限制：自己操作 或 everyone 模式下的非 OP → 受范围限制；OP 调他人 → 不受限
        boolean adminActingOnOther = isAdmin(source) && !selfOperation;
        if (!adminActingOnOther) {
            double min = CarpetPrimaryuanSettings.playerScaleMin;
            double max = CarpetPrimaryuanSettings.playerScaleMax;
            if (value < min || value > max) {
                source.sendSuccess(() -> ServerI18n.tr(source,
                        "carpetprimaryuan.command.scale.out_of_range",
                        formatScale(value), formatScale(min), formatScale(max)), false);
                return 0;
            }
        }

        if (selfOperation) {
            return applyScale(target, value, source, "set_self", null);
        } else {
            return applyScale(target, value, source, "set_other", playerName);
        }
    }

    // ==================== reset 自己 / 他人 ====================

    private static int resetSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        return applyScale(self, DEFAULT_SCALE, ctx.getSource(), "reset_self", null);
    }

    private static int resetTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.player_offline", playerName), false);
            return 0;
        }

        boolean selfOperation = source.isPlayer() && target.getUUID().equals(source.getPlayer().getUUID());
        if (!selfOperation && !canModifyOther(source)) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.no_permission_modify"), false);
            return 0;
        }

        if (selfOperation) {
            return applyScale(target, DEFAULT_SCALE, source, "reset_self", null);
        } else {
            return applyScale(target, DEFAULT_SCALE, source, "reset_other", playerName);
        }
    }

    // ==================== info 自己 / 他人 ====================

    private static int infoSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        sendInfo(self, ctx.getSource(), true);
        return 1;
    }

    private static int infoTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.player_offline", playerName), false);
            return 0;
        }

        boolean selfOperation = source.isPlayer() && target.getUUID().equals(source.getPlayer().getUUID());
        if (!selfOperation && !canViewOther(source)) {
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.no_permission_view"), false);
            return 0;
        }

        if (selfOperation) {
            sendInfo(target, source, true);
        } else {
            sendInfo(target, source, false);
        }
        return 1;
    }

    /**
     * 发送 info 信息给命令执行者。
     * @param target 被查询的玩家
     * @param source 命令来源（消息接收者）
     * @param self true=查询自己，附带允许范围；false=查询他人，仅显示当前大小
     */
    private static void sendInfo(ServerPlayer target, CommandSourceStack source, boolean self) {
        //#if MC >= 12105
        double current = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).getBaseValue();
        String currentStr = formatScale(current);
        String defaultStr = formatScale(DEFAULT_SCALE);
        if (self) {
            String minStr = formatScale(CarpetPrimaryuanSettings.playerScaleMin);
            String maxStr = formatScale(CarpetPrimaryuanSettings.playerScaleMax);
            String mode = CarpetPrimaryuanSettings.playerScaleModifiers;
            String modeStr;
            if ("true".equalsIgnoreCase(mode)) {
                modeStr = ServerI18n.tr(source, "carpetprimaryuan.command.scale.mode_true").getString();
            } else if ("everyone".equalsIgnoreCase(mode)) {
                modeStr = ServerI18n.tr(source, "carpetprimaryuan.command.scale.mode_everyone").getString();
            } else {
                modeStr = mode;
            }
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.info_self", currentStr, defaultStr, minStr, maxStr, modeStr), false);
        } else {
            String name = target.getName().getString();
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale.info_other", name, currentStr, defaultStr), false);
        }
        //#else
        //$$ source.sendSuccess(() -> ServerI18n.tr(source,
        //$$         "carpetprimaryuan.command.scale.unsupported_version"), false);
        //#endif
    }

    // ==================== 核心：applyScale ====================

    /**
     * @param target     被调整的玩家
     * @param value      scale 值
     * @param source     命令来源（用于反馈）
     * @param selfKey    自己执行的反馈 i18n key（set_self / reset_self）
     * @param targetName 被调整玩家名（他人执行路径传值；自己执行路径传 null）
     */
    private static int applyScale(ServerPlayer target, double value,
                                  CommandSourceStack source, String selfKey, String targetName) {
        boolean isSelf = targetName == null;
        String valueStr = formatScale(value);

        //#if MC >= 12105
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)
                .setBaseValue(value);
        if (isSelf) {
            target.sendSystemMessage(ServerI18n.tr(target,
                    "carpetprimaryuan.command.scale." + selfKey, valueStr));
        } else {
            String name = target.getName().getString();
            boolean adminDoing = isAdmin(source);
            String otherKey;
            if ("reset_other".equals(selfKey)) {
                otherKey = adminDoing ? "reset_other_admin" : "reset_other_anyone";
            } else {
                otherKey = adminDoing ? "set_other_admin" : "set_other_anyone";
            }
            source.sendSuccess(() -> ServerI18n.tr(source,
                    "carpetprimaryuan.command.scale." + otherKey, name, valueStr), true);
            // 被修改玩家收到消息：区分管理员 vs 普通玩家（everyone 模式）
            // 用 try-catch 包裹 getPlayerOrException() 避免 CommandSyntaxException 向上传播
            String actorName;
            try {
                actorName = source.isPlayer()
                        ? source.getPlayerOrException().getName().getString()
                        : "Console";
            } catch (Exception e) {
                actorName = "Console";
            }
            target.sendSystemMessage(ServerI18n.tr(target,
                    adminDoing ? "carpetprimaryuan.command.scale.adjusted_by_admin"
                               : "carpetprimaryuan.command.scale.adjusted_by_player",
                    actorName, valueStr));
        }
        return 1;
        //#else
        //$$ if (isSelf) {
        //$$     target.sendSystemMessage(ServerI18n.tr(target,
        //$$             "carpetprimaryuan.command.scale.unsupported_version"));
        //$$ } else {
        //$$     source.sendSuccess(() -> ServerI18n.tr(source,
        //$$             "carpetprimaryuan.command.scale.unsupported_version"), false);
        //$$ }
        //$$ return 0;
        //#endif
    }

    // ==================== 工具方法 ====================

    private static String formatScale(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String playerName(ServerPlayer p) {
        //#if MC >= 12110
        return p.getGameProfile().name();
        //#else
        //$$ return p.getGameProfile().getName();
        //#endif
    }

    /**
     * 在线玩家名称补全（用于 set <value> <player> 和 reset <player>）。
     * true 模式非 OP：只补全自己名字。
     * OP / everyone：补全所有在线玩家。
     */
    private static CompletableFuture<Suggestions> suggestPlayersForModification(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var players = context.getSource().getServer().getPlayerList().getPlayers();
            String remaining = builder.getRemainingLowerCase();
            boolean admin = isAdmin(context.getSource());
            boolean everyone = "everyone".equalsIgnoreCase(CarpetPrimaryuanSettings.playerScaleModifiers);
            boolean allowAll = admin || everyone;
            String selfName = context.getSource().isPlayer()
                    ? playerName(context.getSource().getPlayerOrException()) : null;
            for (ServerPlayer p : players) {
                String name = playerName(p);
                if (!allowAll && !name.equalsIgnoreCase(selfName)) {
                    continue;
                }
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    /**
     * 在线玩家名称补全（用于 info <player>）。
     * 比 modify 稍宽：true 模式下非 OP 也能查别人 info（info 无破坏性）。
     * 但如果规则=false 整个命令不可见，不会到这里。
     */
    private static CompletableFuture<Suggestions> suggestPlayersForInfo(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            var players = context.getSource().getServer().getPlayerList().getPlayers();
            String remaining = builder.getRemainingLowerCase();
            for (ServerPlayer p : players) {
                String name = playerName(p);
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }
}
