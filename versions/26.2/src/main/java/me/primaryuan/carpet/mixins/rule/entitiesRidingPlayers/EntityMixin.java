package me.primaryuan.carpet.mixins.rule.entitiesRidingPlayers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 版本覆盖：26.2 中玩家类型常量从 EntityType.PLAYER 迁至并存的 EntityTypes.PLAYER，
 * 根模板无法直接编译，故保留本覆盖。结构与根模板保持一致（严格 WrapOperation + 共享
 * helper + startRiding 入口旁观者拦截），后续修改根模板 EntityMixin 时需手动同步本文件。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void ridingPlayers$removePassenger(Entity passenger, CallbackInfo ci) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.syncPassengers((Entity) (Object) this);
        }
    }

    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void ridingPlayers$onAddPassenger(Entity passenger, CallbackInfo ci) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.syncPassengers((Entity) (Object) this);
        }
    }

    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    )
    private boolean ridingPlayers$allowRidingPlayers(EntityType instance, Operation<Boolean> original) {
        return pry$allowPlayerVehicles(instance, original);
    }

    private boolean pry$allowPlayerVehicles(EntityType instance, Operation<Boolean> original) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            return instance == net.minecraft.world.entity.EntityTypes.PLAYER || original.call(instance);
        }
        return original.call(instance);
    }

    // 旁观者禁令的"门口拦截"：与根模板同构，注入三参内层重载覆盖全部调用方
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void ridingPlayers$blockSpectatorMount(Entity vehicle, boolean force, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        ridingPlayers$checkSpectatorMount(vehicle, cir);
    }

    private void ridingPlayers$checkSpectatorMount(Entity vehicle, CallbackInfoReturnable<Boolean> cir) {
        if (!CarpetPrimaryuanSettings.ridingPlayers && !CarpetPrimaryuanSettings.pickupPlayers) {
            return;
        }
        Entity rider = (Entity) (Object) this;
        if ((rider.isSpectator() && vehicle instanceof Player)
                || (vehicle.isSpectator() && rider instanceof Player)) {
            cir.setReturnValue(false);
        }
    }
}
