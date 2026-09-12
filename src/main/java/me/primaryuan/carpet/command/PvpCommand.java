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
 * 结构：
 *   pvp
 *     (无参数)                → 查看自己的 PVP 状态（控制台提示子命令）
 *     list                    → 列出所有 PVP 关闭的玩家
 *     set <player|@a> on|off  → 设置指定玩家（@a = 全服所有玩家）的 PVP
 *
 * PVP 语义（双向保护）：关闭 PVP 的玩家不能攻击玩家，也不会受到任何玩家造成的伤害；
 * 自伤不受限制。全服玩家默认 PVP 开启，由指令按需关闭。
 *
 * 权限规则（规则 peacefulPlayers）：
 *   - false：整个命令不可见（隐藏）
 *   - self：所有人都只能调自己（无论 OP）
 *   - true：玩家仅可调自己，管理员可调任意玩家（含 @a）
 *   - everyone：所有人可调任意玩家（含 @a）
 *   - list 与自身状态查询在所有开启模式下对所有人可用
 */
public final class PvpCommand {

    /** /pvp set 的全服选择器：作用于所有玩家（在线 + 已登记） */
    private static final String SELECTOR_ALL = "@a";

    private PvpCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pvp")
                    // 规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equalsIgnoreCase(CarpetPrimaryuanSettings.peacefulPlayers))

                    // /pvp —— 查看自己的状态
                    .executes(PvpCommand::showSelfStatus)

                    // /pvp list —— 所有 PVP 关闭的玩家
                    .then(Commands.literal("list")
                            .executes(PvpCommand::listPvpOff))

                    // /pvp set <player|@a> <on|off> —— 权限随规则模式在执行时判定
                    .then(Commands.literal("set")
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .suggests(PvpCommand::suggestTargets)
                                    .then(Commands.literal("on")
                                            .executes(ctx -> setState(ctx, PvpManager.STATE_ON)))
                                    .then(Commands.literal("off")
                                            .executes(ctx -> setState(ctx, PvpManager.STATE_OFF))))));
        });
    }

    // ==================== 权限检查 ====================

    /**
     * 是否允许"调整他人"（set 他人 / @a）。
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

    // ==================== Tab 补全 ====================

    /**
     * /pvp set 目标补全：按权限过滤——自己始终可调；@a 与其他玩家仅 canModifyOther 时可见。
     */
    private static CompletableFuture<Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        List<String> candidates = new ArrayList<>();
        if (source.isPlayer()) {
            candidates.add(CommandSupport.profileName(source.getPlayer()));
        }
        if (canModifyOther(source)) {
            candidates.add(SELECTOR_ALL);
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

    // ==================== set ====================

    private static int setState(CommandContext<CommandSourceStack> ctx, String state) {
        CommandSourceStack source = ctx.getSource();
        String target = StringArgumentType.getString(ctx, "player");
        boolean on = PvpManager.STATE_ON.equals(state);
        String setKey = on
                ? "carpetprimaryuan.command.pvp.set_target_on"
                : "carpetprimaryuan.command.pvp.set_target_off";
        String setAllKey = on
                ? "carpetprimaryuan.command.pvp.set_all_on"
                : "carpetprimaryuan.command.pvp.set_all_off";
        String notifyKey = on
                ? "carpetprimaryuan.command.pvp.notify_on"
                : "carpetprimaryuan.command.pvp.notify_off";

        if (SELECTOR_ALL.equals(target)) {
            if (!canModifyOther(source)) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.pvp.no_permission"));
                return 0;
            }
            PvpManager.setAllStates(state);
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(ServerI18n.tr(notifyKey));
            }
            source.sendSuccess(() -> ServerI18n.tr(setAllKey), false);
            return 1;
        }

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

    // ==================== list ====================

    private static int listPvpOff(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
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
        } catch (CommandSyntaxException e) {
            // 控制台等非玩家来源：无"自身状态"，提示可用子命令
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.pvp.console_hint"), false);
        }
        return 1;
    }
}
