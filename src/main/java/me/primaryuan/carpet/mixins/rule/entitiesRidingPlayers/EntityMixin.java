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

    // 1.21.2 起，原版 startRiding 才用 EntityType.canSerialize() 拒绝"不可保存"的
    // 载具（PLAYER 注册时调用 noSave()，canSerialize() 恒 false），须在此放行玩家
    // 作载具。1.21/1.21.1 的 startRiding 没有这道门禁（canSerialize 调用只在
    // getEncodeId()，字节码核实），vanilla 本就不拒绝玩家载具，无需注入——
    // 对其强挂 target 会 require=1 响亮崩溃（boot check 实测）。
    // 三参重载 1.21.10 起才出现，1.21.3~1.21.9 只有二参重载。按版本保留唯一
    // 存在的 target 并去掉 require=0：门禁失配时让 mixin 响亮地失败。
    //#if MC >= 12110
    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    )
    private boolean ridingPlayers$allowRidingPlayers(EntityType instance, Operation<Boolean> original) {
        return pry$allowPlayerVehicles(instance, original);
    }
    //#else
    //#if MC >= 12103
    //$$ @WrapOperation(
    //$$         method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
    //$$         at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    //$$ )
    //$$ private boolean ridingPlayers$allowRidingPlayers(EntityType instance, Operation<Boolean> original) {
    //$$     return pry$allowPlayerVehicles(instance, original);
    //$$ }
    //#else
    //$$ // 1.21/1.21.1：无 canSerialize 门禁，本注入不需要
    //#endif
    //#endif

    private boolean pry$allowPlayerVehicles(EntityType instance, Operation<Boolean> original) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            return instance == EntityType.PLAYER || original.call(instance);
        }
        return original.call(instance);
    }

    // 旁观者禁令的"门口拦截"：无论发起方是谁（模组交互 / 原版 /ride 的 force=true /
    // 其他模组），想让旁观者与玩家建立骑乘关系都在此拒绝。注入在内层重载上以覆盖
    // 全部调用方（一参重载只是 force=false 的委托）。
    //#if MC >= 12110
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void ridingPlayers$blockSpectatorMount(Entity vehicle, boolean force, boolean flag, CallbackInfoReturnable<Boolean> cir) {
        ridingPlayers$checkSpectatorMount(vehicle, cir);
    }
    //#else
    //$$ @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    //$$ private void ridingPlayers$blockSpectatorMount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
    //$$     ridingPlayers$checkSpectatorMount(vehicle, cir);
    //$$ }
    //#endif

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
