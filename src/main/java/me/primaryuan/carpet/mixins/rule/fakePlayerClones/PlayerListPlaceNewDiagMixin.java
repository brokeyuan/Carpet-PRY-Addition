package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.CloneAllowlist;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M1 临时诊断：placeNewPlayer 对分身实体的执行进度打点。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListPlaceNewDiagMixin {

    private static final Logger DIAG = LogManager.getLogger("CarpetPrimaryuan");

    @Inject(
            method = "placeNewPlayer",
            at = @At("HEAD"),
            require = 0
    )
    private void pry$diagHead(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (CloneAllowlist.isAllowed(player.getUUID())) {
            DIAG.warn("[pry:clones][DIAG] placeNewPlayer HEAD id={}", player.getId());
        }
    }

    @Inject(
            method = "placeNewPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
                    remap = true
            ),
            require = 0
    )
    private void pry$diagBeforeAddToWorld(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (CloneAllowlist.isAllowed(player.getUUID())) {
            DIAG.warn("[pry:clones][DIAG] placeNewPlayer before addNewPlayer id={}", player.getId());
        }
    }

    @Inject(
            method = "placeNewPlayer",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$diagReturn(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (CloneAllowlist.isAllowed(player.getUUID())) {
            DIAG.warn("[pry:clones][DIAG] placeNewPlayer RETURN id={}", player.getId());
        }
    }
}
