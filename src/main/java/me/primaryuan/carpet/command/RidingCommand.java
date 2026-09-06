package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class RidingCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 规则关闭时整棵命令不可见（不显示、不可执行）
            dispatcher.register(Commands.literal("riding")
                    .requires(source -> CarpetPrimaryuanSettings.ridingPlayers)
                    .then(Commands.literal("on").executes(ctx ->
                            toggle(ctx, true, EntitiesRidingPlayersHandler.Permission.RIDE,
                                    "carpetprimaryuan.command.ride.allow_ride")))
                    .then(Commands.literal("off").executes(ctx ->
                            toggle(ctx, false, EntitiesRidingPlayersHandler.Permission.RIDE,
                                    "carpetprimaryuan.command.ride.disallow_ride"))));

            dispatcher.register(Commands.literal("picking")
                    .requires(source -> CarpetPrimaryuanSettings.pickupPlayers)
                    .then(Commands.literal("on").executes(ctx ->
                            toggle(ctx, true, EntitiesRidingPlayersHandler.Permission.PICKUP,
                                    "carpetprimaryuan.command.ride.allow_pickup")))
                    .then(Commands.literal("off").executes(ctx ->
                            toggle(ctx, false, EntitiesRidingPlayersHandler.Permission.PICKUP,
                                    "carpetprimaryuan.command.ride.disallow_pickup"))));
        });
    }

    /** on/off 共用入口：设置本人对应类型的许可并广播 */
    private static int toggle(CommandContext<CommandSourceStack> context, boolean allow,
                              EntitiesRidingPlayersHandler.Permission type,
                              String messageKey) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EntitiesRidingPlayersHandler.setPermission(type, player.getName().getString(), allow);
        broadcast(context.getSource(), messageKey, player.getName().getString());
        return 1;
    }

    /**
     * 向全服玩家广播消息（按 /carpet language 设置的全局语言翻译，不区分玩家语言）
     */
    private static void broadcast(CommandSourceStack source, String key, Object... args) {
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(ServerI18n.tr(key, args));
        }
    }
}
