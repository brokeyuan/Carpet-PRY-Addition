package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
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
                    // 无参 = 翻转本人被骑许可（回执广播新状态）
                    .executes(ctx -> toggleBare(ctx, EntitiesRidingPlayersHandler.Permission.RIDE))
                    .then(Commands.literal("on").executes(ctx ->
                            toggle(ctx, true, EntitiesRidingPlayersHandler.Permission.RIDE,
                                    "carpetprimaryuan.command.ride.allow_ride")))
                    .then(Commands.literal("off").executes(ctx ->
                            toggle(ctx, false, EntitiesRidingPlayersHandler.Permission.RIDE,
                                    "carpetprimaryuan.command.ride.disallow_ride"))));

            dispatcher.register(Commands.literal("picking")
                    .requires(source -> CarpetPrimaryuanSettings.pickupPlayers)
                    // 无参 = 翻转本人被捡许可
                    .executes(ctx -> toggleBare(ctx, EntitiesRidingPlayersHandler.Permission.PICKUP))
                    .then(Commands.literal("on").executes(ctx ->
                            toggle(ctx, true, EntitiesRidingPlayersHandler.Permission.PICKUP,
                                    "carpetprimaryuan.command.ride.allow_pickup")))
                    .then(Commands.literal("off").executes(ctx ->
                            toggle(ctx, false, EntitiesRidingPlayersHandler.Permission.PICKUP,
                                    "carpetprimaryuan.command.ride.disallow_pickup"))));
        });
    }

    /** 无参入口：翻转本人对应类型的许可并按新状态广播 */
    private static int toggleBare(CommandContext<CommandSourceStack> context,
                                  EntitiesRidingPlayersHandler.Permission type) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        boolean allow = !EntitiesRidingPlayersHandler.isAllowed(type, player.getName().getString());
        toggle(context, allow, type, allow
                ? (type == EntitiesRidingPlayersHandler.Permission.RIDE
                        ? "carpetprimaryuan.command.ride.allow_ride"
                        : "carpetprimaryuan.command.ride.allow_pickup")
                : (type == EntitiesRidingPlayersHandler.Permission.RIDE
                        ? "carpetprimaryuan.command.ride.disallow_ride"
                        : "carpetprimaryuan.command.ride.disallow_pickup"));
        return 1;
    }

    /** on/off 共用入口：设置本人对应类型的许可并广播 */
    private static int toggle(CommandContext<CommandSourceStack> context, boolean allow,
                              EntitiesRidingPlayersHandler.Permission type,
                              String messageKey) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
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
