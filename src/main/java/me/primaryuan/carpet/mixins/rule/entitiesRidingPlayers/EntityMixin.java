package me.primaryuan.carpet.mixins.rule.entitiesRidingPlayers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void ridingPlayers$removePassenger(Entity passenger, CallbackInfo ci) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.onDismount((Entity) (Object) this);
        }
    }

    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void ridingPlayers$onAddPassenger(Entity passenger, CallbackInfo ci) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.onMount((Entity) (Object) this, passenger);
        }
    }

    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"),
            require = 0
    )
    private boolean ridingPlayers$allowRidingPlayers(EntityType instance, Operation<Boolean> original) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            return instance == EntityType.PLAYER || original.call(instance);
        }
        return original.call(instance);
    }
}
