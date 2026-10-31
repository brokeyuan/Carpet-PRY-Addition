package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.SharedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装备同步触发点：ServerPlayer.setItemSlot 是盔甲/主副手穿戴、脱卸与
 * /item replace 的通用入口（Review R1 F3：26.3 装备槽独立于 Inventory，
 * 会话中期装备变更此前无组同步）。组内任一成员装备变更后全组对齐。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSetItemSlotMixin {

    @Inject(
            method = "setItemSlot",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$syncEquipmentAfterSet(
            net.minecraft.world.entity.EquipmentSlot slot,
            net.minecraft.world.item.ItemStack stack, CallbackInfo ci
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        CloneGroup group = CloneGroupRegistry.byUuid(self.getUUID());
        if (group == null || group.onlineMembers().size() <= 1) {
            return;
        }
        SharedInventoryManager.syncEquipment(self);
    }
}
