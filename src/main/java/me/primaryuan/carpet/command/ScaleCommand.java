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

import java.util.ArrayList;
import java.util.List;
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
 *   - 规则=self：所有人都只能调自己（无论 OP）
 *   - 规则=true：OP 可以；非 OP 不可
 *   - 规则=everyone：所有人都可以
 *   - 规则=false：整个命令不可见
 *
 * Tab 补全（玩家列表）：
 *   - self 模式：所有人只补全自己名字
 *   - true 模式 + 非 OP：只补全自己名字
 *   - OP（非 self 模式） / everyone：补全所有在线玩家
 *
 * 范围限制：
 *   - 自己操作 / everyone 模式非 OP / self 模式（含 OP）：受 playerScaleMin/Max 限制
 *   - OP 调他人（非 self 模式）：不受限制
 */
public final class ScaleCommand {

    /** 默认 scale 值（reset 时使用） */
    private static final double DEFAULT_SCALE = 1.0;

    private ScaleCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 自定义玩家补全提供者：根据模式过滤可见玩家
            SuggestionProvider<CommandSourceStack> modifySuggestions = ScaleCommand::suggestPlayersForModification;
            SuggestionProvider<CommandSourceStack> infoSuggestions = ScaleCommand::suggestPlayersForInfo;

            dispatcher.register(Commands.literal("scale")
                    .requires(source -> !"false".equalsIgnoreCase(CarpetPrimaryuanSettings.playerScale))

                    // ===== set =====
                    .then(Commands.literal("set")
                            // /scale set <value>
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg())
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

    /**
     * 是否允许"调节他人"（set / reset 他人）。
     * 规则=self：所有人都不可（无论 OP，只能调自己）；
     * 规则=true：OP 允许、非 OP 不可；
     * 规则=everyone：所有人允许。
     */
    private static boolean canModifyOther(CommandSourceStack source) {
        String rule = CarpetPrimaryuanSettings.playerScale;
        if ("self".equalsIgnoreCase(rule)) return false;
        if (CommandSupport.isAdmin(source)) return true;
        return "everyone".equalsIgnoreCase(rule);
    }

    /**
     * 是否允许"查询他人 info"（比 modify 更宽松：true 模式下也允许，info 不危险）。
     * 规则=self：所有人都不可（与 modify 一致，只能看自己）。
     */
    private static boolean canViewOther(CommandSourceStack source) {
        String rule = CarpetPrimaryuanSettings.playerScale;
        if ("self".equalsIgnoreCase(rule)) return false;
        if (CommandSupport.isAdmin(source)) return true;
        return "everyone".equalsIgnoreCase(rule) || "true".equalsIgnoreCase(rule);
    }

    // ==================== set 自己 / 他人 ====================

    private static int setSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer self = source.getPlayerOrException();
        double value = DoubleArgumentType.getDouble(ctx, "value");

        if (requirePositive(source, value)) return 0;
        if (outOfRange(source, value)) return 0;
        return applyScale(self, value, source, "set", true);
    }

    private static int setTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = CommandSupport.resolvePlayer(ctx);
        if (target == null) return 0;

        boolean selfOperation = isSelfOperation(source, target);
        if (!selfOperation && !canModifyOther(source)) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.scale.no_permission_modify"));
            return 0;
        }

        double value = DoubleArgumentType.getDouble(ctx, "value");

        if (requirePositive(source, value)) return 0;
        if (outOfRange(source, value)) return 0;

        return applyScale(target, value, source, "set", selfOperation);
    }

    /**
     * 软边界校验：value 超出 playerScaleMin/Max 时向来源发送提示并返回 true。
     * 软边界约束所有玩家（含管理员调自己与调他人）——管理员需要更大范围时，
     * 通过 /carpet playerScaleMin / playerScaleMax 调整边界本身。
     * 硬边界仅要求 value > 0（见 requirePositive）。
     */
    private static boolean outOfRange(CommandSourceStack source, double value) {
        double min = CarpetPrimaryuanSettings.playerScaleMin;
        double max = CarpetPrimaryuanSettings.playerScaleMax;
        if (value < min || value > max) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.scale.out_of_range",
                    formatScale(value), formatScale(min), formatScale(max)));
            return true;
        }
        return false;
    }

    /**
     * 硬边界校验：scale 仅要求为大于 0 的有限数值（上不封顶），非法时向来源发送提示并返回 true。
     * RangedAttributeMixin 放行 SCALE 属性的任意有限正值，保证命令反馈值与实际生效值一致。
     */
    private static boolean requirePositive(CommandSourceStack source, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.scale.must_be_positive", formatScale(value)));
            return true;
        }
        return false;
    }

    // ==================== reset 自己 / 他人 ====================

    private static int resetSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        return applyScale(self, DEFAULT_SCALE, ctx.getSource(), "reset", true);
    }

    private static int resetTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = CommandSupport.resolvePlayer(ctx);
        if (target == null) return 0;

        boolean selfOperation = isSelfOperation(source, target);
        if (!selfOperation && !canModifyOther(source)) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.scale.no_permission_modify"));
            return 0;
        }

        return applyScale(target, DEFAULT_SCALE, source, "reset", selfOperation);
    }

    // ==================== info 自己 / 他人 ====================

    private static int infoSelfScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        sendInfo(self, ctx.getSource(), true);
        return 1;
    }

    private static int infoTargetScale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer target = CommandSupport.resolvePlayer(ctx);
        if (target == null) return 0;

        boolean selfOperation = isSelfOperation(source, target);
        if (!selfOperation && !canViewOther(source)) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.scale.no_permission_view"));
            return 0;
        }

        sendInfo(target, source, selfOperation);
        return 1;
    }

    /**
     * 发送 info 信息给命令执行者。
     * @param target 被查询的玩家
     * @param source 命令来源（消息接收者）
     * @param self true=查询自己，附带允许范围；false=查询他人，仅显示当前大小
     */
    private static void sendInfo(ServerPlayer target, CommandSourceStack source, boolean self) {
        double current = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).getBaseValue();
        String currentStr = formatScale(current);
        String defaultStr = formatScale(DEFAULT_SCALE);
        if (self) {
            String minStr = formatScale(CarpetPrimaryuanSettings.playerScaleMin);
            String maxStr = formatScale(CarpetPrimaryuanSettings.playerScaleMax);
            String mode = CarpetPrimaryuanSettings.playerScale;
            String modeStr = switch (mode.toLowerCase()) {
                case "self" -> ServerI18n.tr("carpetprimaryuan.command.scale.mode_self").getString();
                case "true" -> ServerI18n.tr("carpetprimaryuan.command.scale.mode_true").getString();
                case "everyone" -> ServerI18n.tr("carpetprimaryuan.command.scale.mode_everyone").getString();
                default -> mode;
            };
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.scale.info_self", currentStr, defaultStr, minStr, maxStr, modeStr), false);
        } else {
            String name = target.getName().getString();
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.scale.info_other", name, currentStr, defaultStr), false);
        }
    }

    // ==================== 核心：applyScale ====================

    /**
     * @param target  被调整的玩家
     * @param value   scale 值
     * @param source  命令来源（用于反馈）
     * @param action  动作名（set / reset，用于拼反馈 i18n key）
     * @param isSelf  是否为玩家本人执行
     */
    private static int applyScale(ServerPlayer target, double value,
                                  CommandSourceStack source, String action, boolean isSelf) {
        String valueStr = formatScale(value);

        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)
                .setBaseValue(value);
        if (isSelf) {
            target.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.command.scale." + action + "_self", valueStr));
        } else {
            String name = target.getName().getString();
            boolean adminDoing = CommandSupport.isAdmin(source);
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.scale." + action + "_other_" + (adminDoing ? "admin" : "anyone"),
                    name, valueStr), true);
            // 被修改玩家收到消息：区分管理员 vs 普通玩家（everyone 模式）
            String actorName = source.isPlayer()
                    ? source.getPlayer().getName().getString()
                    : "Console";
            target.sendSystemMessage(ServerI18n.tr(
                    adminDoing ? "carpetprimaryuan.command.scale.adjusted_by_admin"
                               : "carpetprimaryuan.command.scale.adjusted_by_player",
                    actorName, valueStr));
        }
        return 1;
    }

    // ==================== 工具方法 ====================

    private static String formatScale(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** 目标玩家是否为命令执行者本人 */
    private static boolean isSelfOperation(CommandSourceStack source, ServerPlayer target) {
        return source.isPlayer() && target.getUUID().equals(source.getPlayer().getUUID());
    }

    /**
     * 在线玩家名称补全：includeAll 为 true 时补全所有在线玩家，否则只补全自己。
     */
    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, boolean includeAll) {
        ServerPlayer self = context.getSource().getPlayer();
        String selfName = self != null ? CommandSupport.profileName(self) : null;
        List<String> candidates = new ArrayList<>();
        for (ServerPlayer p : context.getSource().getServer().getPlayerList().getPlayers()) {
            String name = CommandSupport.profileName(p);
            if (!includeAll && !name.equalsIgnoreCase(selfName)) {
                continue;
            }
            candidates.add(name);
        }
        CommandSupport.suggestMatching(builder, candidates);
        return builder.buildFuture();
    }

    /**
     * 在线玩家名称补全（用于 set <value> <player> 和 reset <player>）。
     * self 模式：所有人只补全自己名字（无论 OP）。
     * true 模式非 OP：只补全自己名字。
     * OP（非 self 模式） / everyone：补全所有在线玩家。
     */
    private static CompletableFuture<Suggestions> suggestPlayersForModification(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String rule = CarpetPrimaryuanSettings.playerScale;
        boolean selfMode = "self".equalsIgnoreCase(rule);
        boolean admin = !selfMode && CommandSupport.isAdmin(context.getSource());
        boolean everyone = "everyone".equalsIgnoreCase(rule);
        return suggestPlayers(context, builder, !selfMode && (admin || everyone));
    }

    /**
     * 在线玩家名称补全（用于 info <player>）。
     * self 模式：只补全自己（与 canViewOther 一致）。
     * 其他模式：补全所有在线玩家（执行时由 canViewOther 拦截，但补全显示更友好）。
     */
    private static CompletableFuture<Suggestions> suggestPlayersForInfo(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        boolean selfMode = "self".equalsIgnoreCase(CarpetPrimaryuanSettings.playerScale);
        return suggestPlayers(context, builder, !selfMode);
    }
}
