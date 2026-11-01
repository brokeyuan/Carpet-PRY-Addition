package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.SharedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 共享背包注入点：
 * <ul>
 *   <li>非主实例分身的 Inventory 写方法 → 转调主实例同名方法（权威唯一），
 *       随后由主实例的 RETURN 同步把权威快照对齐全组（含调用者）。
 *       返回值取"主实例执行后调用者槽位上的权威物品"（对 removeItem 系列，
 *       即被移除的那份的拷贝）。</li>
 *   <li>主实例/普通玩家写方法走原路径，RETURN 处组同步——覆盖放置/拾取/
 *       丢弃/容器/命令等一切来源。</li>
 * </ul>
 * 同 Tick 多分身依次修改：每次写立即落到权威并对齐全组，下一分身读到最新值
 * （64 石头连续放置 63→62 不回滚，spec 6.2）。
 */
@Mixin(Inventory.class)
public abstract class InventorySharedMixin {

    private Inventory self() {
        return (Inventory) (Object) this;
    }

    /** 返回重定向目标（主实例）；null=不重定向 */
    private Inventory pry$authority() {
        ServerPlayer self = (ServerPlayer) self().player;
        if (!SharedInventoryManager.shouldRedirect(self)) {
            return null;
        }
        ServerPlayer primary = SharedInventoryManager.primaryOf(self);
        return (primary == null || primary == self) ? null : primary.getInventory();
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$setItem(int slot, ItemStack stack, CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            auth.setItem(slot, stack);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$removeItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            ItemStack removed = auth.removeItem(slot, amount);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            cir.setReturnValue(removed.isEmpty() ? ItemStack.EMPTY : removed.copy());
        }
    }

    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$removeItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            ItemStack removed = auth.removeItemNoUpdate(slot);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            cir.setReturnValue(removed.isEmpty() ? ItemStack.EMPTY : removed.copy());
        }
    }

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$removeFromSelected(boolean wholeStack, CallbackInfoReturnable<ItemStack> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            ItemStack removed = auth.removeFromSelected(wholeStack);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            cir.setReturnValue(removed.isEmpty() ? ItemStack.EMPTY : removed.copy());
        }
    }

    // ===== 漏网写路径补齐（§三审查：dropAll/pickSlot/setSelectedItem/removeItem(ItemStack) 直改 NonNullList） =====

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$dropAll(CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            auth.dropAll();
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            SharedInventoryManager.syncEquipment((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$pickSlot(int index, CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            auth.pickSlot(index);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            SharedInventoryManager.syncEquipment((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    @Inject(method = "setSelectedItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$setSelectedItem(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            cir.setReturnValue(auth.setSelectedItem(stack));
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            SharedInventoryManager.syncEquipment((ServerPlayer) auth.player);
        }
    }

    @Inject(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$removeItemStack(ItemStack stack, CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            auth.removeItem(stack);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            SharedInventoryManager.syncEquipment((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$clearContent(CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            auth.clearContent();
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$add(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            boolean added = auth.add(stack);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            cir.setReturnValue(added);
        }
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$addAt(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Inventory auth = pry$authority();
        if (auth != null) {
            boolean added = auth.add(slot, stack);
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            cir.setReturnValue(added);
        }
    }

    @Inject(method = "addAndPickItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void pry$addAndPickItem(ItemStack stack, CallbackInfo ci) {
        Inventory auth = pry$authority();
        if (auth != null) {
            //#if MC >= 12111
            auth.addAndPickItem(stack);
            //#else
            //$$ auth.add(stack);
            //#endif
            SharedInventoryManager.syncGroup((ServerPlayer) auth.player);
            ci.cancel();
        }
    }

    // ===== 权威侧同步触发（覆盖一切写来源；非主实例写已在上面的转发里同步过，
    // 这里对主实例与普通路径生效；转发路径 double-sync 幂等无害） =====

    @Inject(method = "setItem", at = @At("RETURN"), require = 0)
    private void pry$syncAfterSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        // 26.x setItem 会路由 EntityEquipment（盔甲/副手槽位）——装备也触发对齐
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
        SharedInventoryManager.syncEquipment((ServerPlayer) self().player);
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), require = 0)
    private void pry$syncAfterRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"), require = 0)
    private void pry$syncAfterRemoveItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "removeFromSelected", at = @At("RETURN"), require = 0)
    private void pry$syncAfterRemoveFromSelected(boolean wholeStack, CallbackInfoReturnable<ItemStack> cir) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "clearContent", at = @At("RETURN"), require = 0)
    private void pry$syncAfterClear(CallbackInfo ci) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "setChanged", at = @At("RETURN"), require = 0)
    private void pry$syncAfterSetChanged(CallbackInfo ci) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), require = 0)
    private void pry$syncAfterAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), require = 0)
    private void pry$syncAfterAddAt(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    }

    //#if MC >= 12111
    //$$ @Inject(method = "addAndPickItem", at = @At("RETURN"), require = 0)
    //$$ private void pry$syncAfterAddAndPickItem(ItemStack stack, CallbackInfo ci) {
    //$$     SharedInventoryManager.syncGroup((ServerPlayer) self().player);
    //$$ }
    //#endif
}
