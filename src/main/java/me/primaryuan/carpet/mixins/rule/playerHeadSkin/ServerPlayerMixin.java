package me.primaryuan.carpet.mixins.rule.playerHeadSkin;

import me.primaryuan.carpet.handler.playerHeadSkin.PlayerHeadSkinHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void pry$inheritOriginalHeadSkin(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        PlayerHeadSkinHandler.onRespawn(oldPlayer, (ServerPlayer) (Object) this);
    }
}
