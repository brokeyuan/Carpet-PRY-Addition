package me.primaryuan.carpet.mixins.rule.playerHat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.handler.playerHat.PlayerHatHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    // 旁观与冷却检查由原版入口完成；只包裹唯一一次 use 调用。
    //#if MC >= 12103
    @WrapOperation(method = "useItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), require = 1)
    private InteractionResult playerHat$afterUse(ItemStack stack, Level level, Player player,
            InteractionHand hand, Operation<InteractionResult> original) {
        var before = stack == player.getMainHandItem() ? PlayerHatHandler.beforeUse((ServerPlayer) player, hand) : null;
        InteractionResult result = original.call(stack, level, player, hand);
        if (result == InteractionResult.PASS && PlayerHatHandler.tryEquip((ServerPlayer) player, hand, before)) {
            return InteractionResult.SUCCESS.heldItemTransformedTo(player.getMainHandItem());
        }
        return result;
    }
    //#else
    //$$ @WrapOperation(method = "useItem", at = @At(value = "INVOKE", target =
    //$$         "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"), require = 1)
    //$$ private net.minecraft.world.InteractionResultHolder<ItemStack> playerHat$afterUse(ItemStack stack, Level level, Player player,
    //$$         InteractionHand hand, Operation<net.minecraft.world.InteractionResultHolder<ItemStack>> original) {
    //$$     var before = stack == player.getMainHandItem() ? PlayerHatHandler.beforeUse((ServerPlayer) player, hand) : null;
    //$$     var result = original.call(stack, level, player, hand);
    //$$     if (result.getResult() == InteractionResult.PASS && PlayerHatHandler.tryEquip((ServerPlayer) player, hand, before)) {
    //$$         return net.minecraft.world.InteractionResultHolder.success(player.getMainHandItem());
    //$$     }
    //$$     return result;
    //$$ }
    //#endif
}
