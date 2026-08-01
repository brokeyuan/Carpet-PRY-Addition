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
            dispatcher.register(Commands.literal("riding")
                    .requires(source -> {
                        if (!source.isPlayer()) return true;
                        if (isAdmin(source)) return true;
                        return CarpetPrimaryuanSettings.ridingPlayers;
                    })
                    .then(Commands.literal("on")
                            .executes(RidingCommand::rideOn))
                    .then(Commands.literal("off")
                            .executes(RidingCommand::rideOff)));

            dispatcher.register(Commands.literal("pickup")
                    .requires(source -> {
                        if (!source.isPlayer()) return true;
                        if (isAdmin(source)) return true;
                        return CarpetPrimaryuanSettings.pickupPlayers;
                    })
                    .then(Commands.literal("on")
                            .executes(RidingCommand::pickupOn))
                    .then(Commands.literal("off")
                            .executes(RidingCommand::pickupOff)));
        });
    }

    private static boolean isAdmin(CommandSourceStack source) {
        if (!source.isPlayer()) return true;
        //#if MC <= 12110
        //$$ return source.hasPermission(4);
        //#else
        return Commands.LEVEL_OWNERS.check(source.permissions());
        //#endif
    }

    private static int rideOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntitiesRidingPlayersHandler.setRidePermission(player.getName().getString(), true);
        broadcast(context.getSource(), "carpetprimaryuan.command.ride.allow_ride", player.getName().getString());
        return 1;
    }

    private static int rideOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntitiesRidingPlayersHandler.setRidePermission(player.getName().getString(), false);
        broadcast(context.getSource(), "carpetprimaryuan.command.ride.disallow_ride", player.getName().getString());
        return 1;
    }

    private static int pickupOn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntitiesRidingPlayersHandler.setPickupPermission(player.getName().getString(), true);
        broadcast(context.getSource(), "carpetprimaryuan.command.ride.allow_pickup", player.getName().getString());
        return 1;
    }

    private static int pickupOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        EntitiesRidingPlayersHandler.setPickupPermission(player.getName().getString(), false);
        broadcast(context.getSource(), "carpetprimaryuan.command.ride.disallow_pickup", player.getName().getString());
        return 1;
    }

    /**
     * 向全服玩家广播消息，按每个玩家自身语言翻译
     */
    private static void broadcast(CommandSourceStack source, String key, Object... args) {
        for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(ServerI18n.tr(p, key, args));
        }
    }
}
