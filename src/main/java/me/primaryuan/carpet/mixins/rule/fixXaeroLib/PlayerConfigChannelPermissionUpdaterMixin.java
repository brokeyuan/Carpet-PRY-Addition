package me.primaryuan.carpet.mixins.rule.fixXaeroLib;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "xaero.lib.common.player.config.permission.PlayerConfigChannelPermissionUpdater", remap = false)
public class PlayerConfigChannelPermissionUpdaterMixin {

    @Inject(
        method = "handle",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private static void onHandleHead(@Coerce Object player, boolean isOp, CallbackInfo ci) {
        if (CarpetPrimaryuanSettings.FixXaeroLib) {
            String className = player.getClass().getName();
            if (className.contains("EntityPlayerMPFake") || className.contains("FakePlayer")) {
                ci.cancel();
            }
        }
    }
}
