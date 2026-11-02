package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

//#if MC >= 12105
import me.primaryuan.carpet.clones.SharedInventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.5+ 装备 map 共享后，盔甲/副手的 inventoryTick 也只由主实例执行一次。
 */
@Mixin(EntityEquipment.class)
public abstract class EntityEquipmentTickMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void pry$skipDuplicateEquipmentTick(Entity entity, CallbackInfo ci) {
        me.primaryuan.carpet.clones.CloneInjectProbe.hit("equipment.tick");
        if (entity instanceof ServerPlayer player && !SharedInventoryManager.isTickOwner(player)) {
            ci.cancel();
        }
    }
}
//#endif
