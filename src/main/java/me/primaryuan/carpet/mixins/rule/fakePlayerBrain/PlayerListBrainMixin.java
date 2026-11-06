package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import me.primaryuan.carpet.brain.BrainManager;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 假人下线的骑乘驱散：拦截 {@code PlayerList.remove} 头部。
 *
 * <p>原版 remove 的顺序（26.3/1.21.11 字节码核实）：save → 若 isPassenger 且
 * 根载具 hasExactlyOnePlayerPassenger → stopRiding + getPassengersAndSelf().forEach
 * (UNLOADED_WITH_PLAYER) → unRide。假人（唯一玩家乘客）骑鸡下线时，这条链会把
 * 鸡随玩家一并卸载——但鸡是真实世界的实体，应留在世界中。注入在 HEAD（save 与
 * RootVehicle 判断之前）让脑子先驱散自己建立的骑乘关系：鸡恢复自由实体，
 * 随后的 RootVehicle 判断 isPassenger=false 直接跳过卸载分支。</p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListBrainMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void pry$brainPlayerRemove(ServerPlayer player, CallbackInfo ci) {
        BrainManager.onFakePlayerRemoved(player);
    }
}
