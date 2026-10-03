package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.redPacket.RedPacketManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /redpacket 命令：发红包（规则 redPacket 控制可用性）。
 *
 * 结构：
 *   redpacket <份数> <祝福语>   打开类型选择 GUI，份数 1-100，祝福语 1-32 字
 *   redpacket claim <id>        聊天框点击领取（内部命令，随广播消息下发）
 */
public final class RedPacketCommand {

    private RedPacketCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("redpacket")
                    // 主规则 = false 时整棵命令树不可见
                    .requires(source -> !"false".equals(CarpetPrimaryuanSettings.redPacket))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(RedPacketCommand::openTypeMenu)))
                    .then(Commands.literal("claim")
                            .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                    .executes(RedPacketCommand::claim))));
        });
    }

    // ==================== 执行 ====================

    private static int openTypeMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int count = IntegerArgumentType.getInteger(context, "count");
        String message = RedPacketManager.sanitizeMessage(StringArgumentType.getString(context, "message"));
        if (message.isEmpty() || message.length() > 32) {
            context.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.message_invalid"));
            return 0;
        }
        RedPacketManager.openTypeMenu(player, count, message);
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int id = IntegerArgumentType.getInteger(context, "id");
        RedPacketManager.claim(player, id);
        return 1;
    }
}
