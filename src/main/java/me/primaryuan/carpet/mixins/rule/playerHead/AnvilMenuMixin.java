package me.primaryuan.carpet.mixins.rule.playerHead;

import me.primaryuan.carpet.handler.playerHead.HeadAnvilHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 铁砧通用头颅换肤：改名触发异步查询，结果未就绪时禁止领取。 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Inject(method = "setItemName", at = @At("RETURN"))
    private void pry$headNameChanged(String name, CallbackInfoReturnable<Boolean> cir) {
        HeadAnvilHandler.onNameChanged((AnvilMenu) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void pry$headCreateResult(CallbackInfo ci) {
        HeadAnvilHandler.afterCreateResult((AnvilMenu) (Object) this);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void pry$headBlockPickup(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (HeadAnvilHandler.blocksPickup((AnvilMenu) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void pry$headTaken(Player player, ItemStack stack, CallbackInfo ci) {
        HeadAnvilHandler.onTake((AnvilMenu) (Object) this);
    }
}
