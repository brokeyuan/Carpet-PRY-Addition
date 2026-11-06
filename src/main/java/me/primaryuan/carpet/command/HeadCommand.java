package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.playerHead.HeadItems;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * /head：管理员头颅入口。
 *
 * <p>根命令在任一相关规则开启且来源为管理员时可见；各子命令分别受对应规则
 * 控制。接收玩家与纪念档案中的“颁发对象”是两个独立概念。</p>
 */
public final class HeadCommand {

    private HeadCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("head")
                        .requires(HeadCommand::rootVisible)
                        .then(Commands.literal("universal")
                                .requires(HeadCommand::universalVisible)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> giveUniversal(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> giveUniversal(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    static boolean rootVisible(CommandSourceStack source) {
        return CarpetPrimaryuanSettings.universalHead && CommandSupport.isAdmin(source);
    }

    static boolean universalVisible(CommandSourceStack source) {
        return CarpetPrimaryuanSettings.universalHead && CommandSupport.isAdmin(source);
    }

    private static int giveUniversal(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        if (!CarpetPrimaryuanSettings.universalHead || !CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_permission"));
            return 0;
        }
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.target_not_found"));
            return 0;
        }
        // 首版不做部分发放或掉地：空间不足直接拒绝。
        Inventory inventory = target.getInventory();
        ItemStack stack = HeadItems.universal(count);
        if (inventory.getFreeSlot() == -1 && inventory.getSlotWithRemainingSpace(stack) == -1) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space", CommandSupport.profileName(target)));
            return 0;
        }
        if (!inventory.add(stack)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space", CommandSupport.profileName(target)));
            return 0;
        }
        target.containerMenu.broadcastChanges();
        String name = CommandSupport.profileName(target);
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.head.universal.given", name, count), false);
        return count;
    }
}
