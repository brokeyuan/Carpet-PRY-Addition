package me.primaryuan.carpet.mixins.rule.entitiesRidingPlayers;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

//#if MC >= 12103
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import java.util.Set;
//#else
//$$ import net.minecraft.world.level.portal.DimensionTransition;
//#endif

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    // 注入点已在全部 10 个构建版本上用字节码核实（setGameMode(GameType) 唯一重载，
    // 体内恰一处 ServerGamePacketListenerImpl.send(Packet)，紧邻 ClientboundGameEventPacket）。
    // require=0 仍保留：产物声明的版本范围比构建版本宽（1.21.8 jar 允许 1.21.6/1.21.7、
    // 26.x jar 开放上界），未构建版本无法核实，静默跳过比启动崩溃安全。
    // 该钩子是旁观者禁令"骑乘中切旁观立即下车"的关键路径，见
    // EntitiesRidingPlayersHandler.onGameModeChange。
    @Inject(method = "setGameMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER), require = 0)
    private void ridingPlayers$onGameModeChange(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.onGameModeChange((Player) (Object) this, gameType);
        }
    }

    // ==== 玩家塔跨维度跟随 ====
    // 原版玩家作载具跨维度传送必甩客：ServerPlayer.teleport（1.21.3+）/changeDimension
    // （1.21/1.21.1）的跨维度分支没有 Entity.teleportCrossDimension 的乘客处理（快照乘客→
    // ejectPassengers→calculatePassengerTransition 逐个传送→载具落位后 startRiding(force)
    // 重组），乘客在 removePlayerImmediately→setRemoved 的 forEach(stopRiding) 处被留在
    // 原维度；同维度分支只移包不摘除实体，乘客随骑乘粘滞照常跟随，故只有跨维度散塔。
    // ridingPlayers/pickupPlayers 建塔后按原版非玩家载具同一模式补齐：传送入口 HEAD 弹下
    // 直系乘客并各自传送到"落点+塔内相对偏移"（乘客自身的传送会递归触发本钩子，子塔
    // 自动随行），载具落位（addDuringTeleport，11 个构建版本上两方法体内各恰一处调用，
    // 字节码核实）后 force 重组骑乘链。
    //#if MC >= 12103
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), require = 0)
    private void ridingPlayers$carryTowerBeforeCrossDimensionTeleport(TeleportTransition transition,
                                                                      CallbackInfoReturnable<ServerPlayer> cir,
                                                                      @Share("pryCrossDimRiders") LocalRef<List<Entity>> riders) {
        riders.set(List.of());
        if (!CarpetPrimaryuanSettings.ridingPlayers && !CarpetPrimaryuanSettings.pickupPlayers) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isRemoved() || !(self.level() instanceof ServerLevel fromLevel)
                || fromLevel.dimension() == transition.newLevel().dimension()) {
            return;
        }
        List<Entity> passengers = List.copyOf(self.getPassengers());
        if (passengers.isEmpty()) {
            return;
        }
        self.ejectPassengers();
        List<Entity> remount = new ArrayList<>();
        for (Entity passenger : passengers) {
            if (!(passenger instanceof ServerPlayer rider)) {
                continue;
            }
            Entity teleported = rider.teleport(pry$riderTransition(transition, self, rider));
            if (teleported != null) {
                remount.add(teleported);
            }
        }
        riders.set(remount);
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/server/level/ServerLevel;addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V",
                     shift = At.Shift.AFTER),
            require = 0)
    private void ridingPlayers$remountTowerAfterCrossDimensionTeleport(TeleportTransition transition,
                                                                       CallbackInfoReturnable<ServerPlayer> cir,
                                                                       @Share("pryCrossDimRiders") LocalRef<List<Entity>> riders) {
        List<Entity> remount = riders.get();
        if (remount == null) {
            return;
        }
        for (Entity rider : remount) {
            //#if MC >= 12110
            rider.startRiding((Entity) (Object) this, true, false);
            //#else
            //$$ rider.startRiding((Entity) (Object) this, true);
            //#endif
        }
    }

    /** 原版 Entity.calculatePassengerTransition 同式：落点+塔内相对偏移，relatives 声明为相对的分量不加偏移 */
    private static TeleportTransition pry$riderTransition(TeleportTransition transition, ServerPlayer vehicle, ServerPlayer rider) {
        Set<Relative> relatives = transition.relatives();
        float yRot = transition.yRot() + (relatives.contains(Relative.Y_ROT) ? 0.0F : rider.getYRot() - vehicle.getYRot());
        float xRot = transition.xRot() + (relatives.contains(Relative.X_ROT) ? 0.0F : rider.getXRot() - vehicle.getXRot());
        Vec3 offset = rider.position().subtract(vehicle.position());
        Vec3 pos = transition.position().add(
                relatives.contains(Relative.X) ? 0.0 : offset.x,
                relatives.contains(Relative.Y) ? 0.0 : offset.y,
                relatives.contains(Relative.Z) ? 0.0 : offset.z);
        return transition.withPosition(pos).withRotation(yRot, xRot).transitionAsPassenger();
    }
    //#else
    //$$ @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
    //$$         at = @At("HEAD"), require = 0)
    //$$ private void ridingPlayers$carryTowerBeforeCrossDimensionTeleport(DimensionTransition transition,
    //$$                                                                    CallbackInfoReturnable<Entity> cir,
    //$$                                                                    @Share("pryCrossDimRiders") LocalRef<List<Entity>> riders) {
    //$$     riders.set(List.of());
    //$$     if (!CarpetPrimaryuanSettings.ridingPlayers && !CarpetPrimaryuanSettings.pickupPlayers) {
    //$$         return;
    //$$     }
    //$$     ServerPlayer self = (ServerPlayer) (Object) this;
    //$$     if (self.isRemoved() || !(self.level() instanceof ServerLevel fromLevel)
    //$$             || fromLevel.dimension() == transition.newLevel().dimension()) {
    //$$         return;
    //$$     }
    //$$     List<Entity> passengers = List.copyOf(self.getPassengers());
    //$$     if (passengers.isEmpty()) {
    //$$         return;
    //$$     }
    //$$     self.ejectPassengers();
    //$$     List<Entity> remount = new ArrayList<>();
    //$$     for (Entity passenger : passengers) {
    //$$         if (!(passenger instanceof ServerPlayer rider)) {
    //$$             continue;
    //$$         }
    //$$         Entity teleported = rider.changeDimension(pry$riderTransition(transition, self, rider));
    //$$         if (teleported != null) {
    //$$             remount.add(teleported);
    //$$         }
    //$$     }
    //$$     riders.set(remount);
    //$$ }
    //$$
    //$$ @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
    //$$         at = @At(value = "INVOKE",
    //$$                  target = "Lnet/minecraft/server/level/ServerLevel;addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V",
    //$$                  shift = At.Shift.AFTER),
    //$$         require = 0)
    //$$ private void ridingPlayers$remountTowerAfterCrossDimensionTeleport(DimensionTransition transition,
    //$$                                                                    CallbackInfoReturnable<Entity> cir,
    //$$                                                                    @Share("pryCrossDimRiders") LocalRef<List<Entity>> riders) {
    //$$     List<Entity> remount = riders.get();
    //$$     if (remount == null) {
    //$$         return;
    //$$     }
    //$$     for (Entity rider : remount) {
    //$$         rider.startRiding((Entity) (Object) this, true);
    //$$     }
    //$$ }
    //$$
    //$$ /** 原版 Entity.changeDimension 乘客同式：落点+塔内相对偏移+朝向差（旧线无 relatives 概念，恒加） */
    //$$ private static DimensionTransition pry$riderTransition(DimensionTransition transition, ServerPlayer vehicle, ServerPlayer rider) {
    //$$     Vec3 offset = rider.position().subtract(vehicle.position());
    //$$     Vec3 pos = transition.pos().add(offset.x, offset.y, offset.z);
    //$$     float yRot = rider.getYRot() + (transition.yRot() - vehicle.getYRot());
    //$$     float xRot = rider.getXRot() + (transition.xRot() - vehicle.getXRot());
    //$$     return new DimensionTransition(transition.newLevel(), pos, Vec3.ZERO, yRot, xRot, DimensionTransition.DO_NOTHING);
    //$$ }
    //#endif
}
