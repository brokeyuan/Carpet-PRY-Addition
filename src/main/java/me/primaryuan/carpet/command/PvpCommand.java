package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.peacefulPlayers.PvpManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /pvp 命令：按玩家开关 PVP（规则 peacefulPlayers 控制可用性与权限）。
 *
 * 结构（动作在前，目标可选；无目标 = 自己）：
 *   pvp
 *     (无参数)           → 查看自己的 PVP 状态 + 全服模式（控制台提示子命令）
 *     list               → 列出所有 PVP 关闭的玩家
 *     on                 → 开启自己的 PVP
 *     off                → 关闭自己的 PVP（进入和平状态）
 *     on <player|@a>     → 开启指定玩家 / 全服 PVP
 *     off <player|@a>    → 关闭指定玩家 / 全服 PVP
 *
 * PVP 语义（双向保护）：关闭 PVP 的玩家不能攻击玩家，也不会受到任何玩家造成的伤害；
 * 自伤不受限制。被拦截的攻击会播放提示音（提醒信号）。
 *
 * 权限规则（规则 peacefulPlayers）：
 *   - false：整个命令不可见（隐藏）
 *   - 自己（无目标 / 目标是自己）：所有开启模式下所有人可用
 *   - 他人（具体玩家名）：self=所有人都不可（无论 OP）；true=仅管理员；everyone=所有人
 *   - 全局（@a，类似群聊全员禁言）：仅管理员，self 模式除外
 *   - 全局关闭期间：所有个人开关操作（on/off，含管理员）均被锁定，仅 /pvp on @a 可解除；
 *     解除时强制覆盖——所有玩家恢复 PVP 开启，之后才能再调整个人开关
 *   - list 与状态查询在所有开启模式下对所有人可用
 */
public final class PvpCommand {

    /** /pvp on|off 的全服选择器：切换全服 PVP 模式（新加入玩家跟随） */
    private static final String SELECTOR_ALL = "@a";

    private PvpCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pvp")
                    // 规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equalsIgnoreCase(CarpetPrimaryuanSettings.peacefulPlayers))

                    // /pvp —— 查看自己的状态 + 全服模式
                    .executes(PvpCommand::showSelfStatus)

                    // /pvp list —— 所有 PVP 关闭的玩家
                    .then(Commands.literal("list")
                            .executes(PvpCommand::listPvpOff))

                    // /pvp on [player|@a]
                    // 注意：@a 必须用字面量节点而非 word() 参数——StringArgumentType.word()
                    // 不允许 '@' 字符，作为参数值会直接解析失败（"参数后应有空格分隔"）
                    .then(Commands.literal("on")
                            .executes(ctx -> setSelf(ctx, PvpManager.STATE_ON))
                            .then(Commands.literal(SELECTOR_ALL)
                                    .requires(PvpCommand::canModifyGlobal)
                                    .executes(ctx -> setGlobal(ctx, PvpManager.STATE_ON)))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(PvpCommand::suggestTargets)
                                    .executes(ctx -> setTarget(ctx, PvpManager.STATE_ON))))

                    // /pvp off [player|@a]
                    .then(Commands.literal("off")
                            .executes(ctx -> setSelf(ctx, PvpManager.STATE_OFF))
                            .then(Commands.literal(SELECTOR_ALL)
                                    .requires(PvpCommand::canModifyGlobal)
                                    .executes(ctx -> setGlobal(ctx, PvpManager.STATE_OFF)))
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(PvpCommand::suggestTargets)
                                    .executes(ctx -> setTarget(ctx, PvpManager.STATE_OFF)))));
        });
    }

    // ==================== 权限检查 ====================

    /**
     * 是否允许"调整他人"（具体玩家名）。
     * 规则=self：所有人都不可（无论 OP，只能调自己）；
     * 规则=true：OP 允许、非 OP 不可；
     * 规则=everyone：所有人允许。
     */
    private static boolean canModifyOther(CommandSourceStack source) {
        String mode = CarpetPrimaryuanSettings.peacefulPlayers;
        if ("self".equalsIgnoreCase(mode)) return false;
        if (CommandSupport.isAdmin(source)) return true;
        return "everyone".equalsIgnoreCase(mode);
    }

    /**
     * 是否允许"切换全服 PVP 模式"（@a，类似群聊全员禁言的群主权限）。
     * 所有模式下仅管理员可用；self 模式下无人可用（含 OP）。
     */
    private static boolean canModifyGlobal(CommandSourceStack source) {
        if ("self".equalsIgnoreCase(CarpetPrimaryuanSettings.peacefulPlayers)) return false;
        return CommandSupport.isAdmin(source);
    }

    // ==================== Tab 补全 ====================

    /**
     * /pvp on|off 目标补全：自己始终可见；其他玩家按权限过滤。
     * @a 无需在此补全——它是字面量节点（literal），Brigadier 自动按前缀补全，
     * 且其 requires 已按权限过滤。
     */
    private static CompletableFuture<Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        List<String> candidates = new ArrayList<>();
        if (source.isPlayer()) {
            candidates.add(CommandSupport.profileName(source.getPlayer()));
        }
        if (canModifyOther(source)) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                String name = CommandSupport.profileName(player);
                if (!candidates.contains(name)) {
                    candidates.add(name);
                }
            }
        }
        CommandSupport.suggestMatching(builder, candidates);
        return builder.buildFuture();
    }

    // ==================== 开关自己 ====================

    private static int setSelf(CommandContext<CommandSourceStack> ctx, String state) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer self;
        try {
            self = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.pvp.player_only"));
            return 0;
        }
        if (requireNotGlobalLocked(source)) return 0;

        PvpManager.setPlayerState(CommandSupport.profileName(self), state);
        source.sendSuccess(() -> ServerI18n.tr(PvpManager.STATE_ON.equals(state)
                ? "carpetprimaryuan.command.pvp.status_self_on"
                : "carpetprimaryuan.command.pvp.status_self_off"), false);
        return 1;
    }

    // ==================== 开关全服 ====================

    /**
     * /pvp on|off @a：切换全服 PVP 模式（强制覆盖：清空所有个人设置，
     * 新加入玩家跟随默认状态）。权限由字面量节点的 requires 保证（仅管理员、self 模式除外）。
     */
    private static int setGlobal(CommandContext<CommandSourceStack> ctx, String state) {
        CommandSourceStack source = ctx.getSource();
        boolean on = PvpManager.STATE_ON.equals(state);
        PvpManager.setGlobalState(state);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(ServerI18n.tr(on
                    ? "carpetprimaryuan.command.pvp.notify_on"
                    : "carpetprimaryuan.command.pvp.notify_off"));
        }
        source.sendSuccess(() -> ServerI18n.tr(on
                ? "carpetprimaryuan.command.pvp.set_all_on"
                : "carpetprimaryuan.command.pvp.set_all_off"), false);
        return 1;
    }

    // ==================== 开关目标玩家 ====================

    private static int setTarget(CommandContext<CommandSourceStack> ctx, String state) {
        CommandSourceStack source = ctx.getSource();
        boolean on = PvpManager.STATE_ON.equals(state);
        String setKey = on
                ? "carpetprimaryuan.command.pvp.set_target_on"
                : "carpetprimaryuan.command.pvp.set_target_off";
        String notifyKey = on
                ? "carpetprimaryuan.command.pvp.notify_on"
                : "carpetprimaryuan.command.pvp.notify_off";

        // 全局禁言期间，所有个人开关操作（on/off）均被锁定，仅 @a 可解除全局
        if (requireNotGlobalLocked(source)) return 0;

        ServerPlayer targetPlayer = CommandSupport.resolvePlayer(ctx);
        if (targetPlayer == null) {
            return 0;
        }
        ServerPlayer self = source.isPlayer() ? source.getPlayer() : null;
        if (self != targetPlayer && !canModifyOther(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.pvp.no_permission"));
            return 0;
        }
        PvpManager.setPlayerState(CommandSupport.profileName(targetPlayer), state);
        targetPlayer.sendSystemMessage(ServerI18n.tr(notifyKey));
        source.sendSuccess(() -> ServerI18n.tr(setKey, CommandSupport.profileName(targetPlayer)), false);
        return 1;
    }

    /**
     * 全局禁言（全服 PVP 关闭）期间的个人操作门禁：所有个人开关操作（on/off，含管理员）
     * 一律拒绝，仅 /pvp on @a 可解除全局（解除时所有玩家强制恢复 PVP 开启）。
     *
     * @return true 表示已拦截（已发送失败提示）
     */
    private static boolean requireNotGlobalLocked(CommandSourceStack source) {
        if (PvpManager.isGlobalOff()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.pvp.global_lock"));
            return true;
        }
        return false;
    }

    // ==================== list ====================

    private static int listPvpOff(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        // 显示全服模式，便于理解全局禁言期间为何列表几乎包含所有人
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.pvp.status_global",
                ServerI18n.tr(PvpManager.isGlobalOff()
                        ? "carpetprimaryuan.command.pvp.global_state_off"
                        : "carpetprimaryuan.command.pvp.global_state_on").getString()), false);

        List<String> offPlayers = PvpManager.listPvpOffPlayers(source.getServer());
        if (offPlayers.isEmpty()) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.pvp.list_empty"), false);
        } else {
            String joined = String.join(", ", offPlayers);
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.pvp.list_header", offPlayers.size(), joined), false);
        }
        return 1;
    }

    // ==================== 自身状态 ====================

    private static int showSelfStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ServerPlayer self = source.getPlayerOrException();
            boolean pvpOn = PvpManager.isPvpOn(self);
            source.sendSuccess(() -> ServerI18n.tr(pvpOn
                    ? "carpetprimaryuan.command.pvp.status_self_on"
                    : "carpetprimaryuan.command.pvp.status_self_off"), false);
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.pvp.status_global",
                    ServerI18n.tr(PvpManager.isGlobalOff()
                            ? "carpetprimaryuan.command.pvp.global_state_off"
                            : "carpetprimaryuan.command.pvp.global_state_on").getString()), false);
        } catch (CommandSyntaxException e) {
            // 控制台等非玩家来源：无"自身状态"，提示可用子命令
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.pvp.console_hint"), false);
        }
        return 1;
    }
}
