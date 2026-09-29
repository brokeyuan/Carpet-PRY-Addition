package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.patPatPlayers.PatPatPlayersHandler;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /patnod 命令：按玩家开关"接不接受被摸"（规则 patPatPlayers 控制可用性）。
 *
 * 结构：
 *   patnod        → 查看自己的当前状态（接受 / 不接受）
 *   patnod on     → 接受被摸（其他玩家可以摸你）
 *   patnod off    → 不接受被摸（其他玩家无法摸你，摸头对其完全不生效）
 *
 * 状态按玩家名持久化于 config/carpet-pry-patnod.json（默认接受，仅记录拒绝者）。
 * 仅服务器玩家可用（控制台无自身状态）。
 */
public final class PatNodCommand {

    private PatNodCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("patnod")
                    // 主规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equals(CarpetPrimaryuanSettings.patPatPlayers))
                    .executes(PatNodCommand::showStatus)
                    .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                    .then(Commands.literal("off").executes(ctx -> set(ctx, false))));
        });
    }

    // ==================== 执行 ====================

    private static int showStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(ServerI18n.tr(PatPatPlayersHandler.acceptsPat(player)
                ? "carpetprimaryuan.command.patnod.status_on"
                : "carpetprimaryuan.command.patnod.status_off"));
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean accept) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PatPatPlayersHandler.setPatAccept(player, accept);
        player.sendSystemMessage(ServerI18n.tr(accept
                ? "carpetprimaryuan.command.patnod.on"
                : "carpetprimaryuan.command.patnod.off"));
        return 1;
    }
}
