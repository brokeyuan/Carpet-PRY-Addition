package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class HatCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 规则关闭时整棵命令不可见（不显示、不可执行）
            dispatcher.register(Commands.literal("hat")
                    .requires(source -> CarpetPrimaryuanSettings.playerhat)
                    .executes(HatCommand::execute));
        });
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        ItemStack mainHandItem = player.getMainHandItem();
        ItemStack headSlotItem = player.getItemBySlot(EquipmentSlot.HEAD);

        if (mainHandItem.isEmpty()) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.command.hat.empty_hand"));
            return 0;
        }

        player.setItemSlot(EquipmentSlot.HEAD, mainHandItem);
        player.setItemInHand(InteractionHand.MAIN_HAND, headSlotItem);

        String itemName = mainHandItem.getDisplayName().getString();
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.command.hat.equipped", itemName));

        return 1;
    }
}