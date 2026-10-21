package me.primaryuan.carpet.mixins.rule.playerHeadSkin;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.handler.playerHeadSkin.PlayerHeadSkinHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityPlayerMPFake.class)
public abstract class FakePlayerMixin {
    @Inject(method = "createShadow", at = @At("HEAD"), remap = false)
    private static void pry$restoreBeforeShadow(MinecraftServer server, ServerPlayer player,
                                                CallbackInfoReturnable<EntityPlayerMPFake> cir) {
        PlayerHeadSkinHandler.beforeShadow(player);
    }
}
