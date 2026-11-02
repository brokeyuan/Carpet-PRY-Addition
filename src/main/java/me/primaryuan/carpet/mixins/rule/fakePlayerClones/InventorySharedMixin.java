package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.SharedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 共享背包只保留一个注入：组内非主实例跳过 Inventory.tick。
 *
 * <p>槽位本身由 SharedInventory 在入组时替换为同一底层容器，
 * getItem/removeItem/setItem/原地 shrink 全部天然作用于同一 ItemStack；
 * 不再需要逐个写方法转发，也不会产生可消费的 copy() 副本。</p>
 */
@Mixin(Inventory.class)
public abstract class InventorySharedMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void pry$skipDuplicateItemTick(CallbackInfo ci) {
        me.primaryuan.carpet.clones.CloneInjectProbe.hit("inventory.tick");
        if (((Inventory) (Object) this).player instanceof ServerPlayer player
                && !SharedInventoryManager.isTickOwner(player)) {
            ci.cancel();
        }
    }
}
