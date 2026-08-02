package me.primaryuan.carpet.mixins.rule.fakePlayerDropStackModifiers;

import carpet.helpers.EntityPlayerActionPack;
import me.primaryuan.carpet.util.DropSlotScheduler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerActionPack.class)
public class EntityPlayerActionPackMixin {

    @Shadow
    @Final
    private ServerPlayer player;

    @Inject(
            method = "stopAll",
            at = @At("HEAD"),
            remap = false
    )
    private void pry$stopAllDropSlotTasks(CallbackInfo ci) {
        DropSlotScheduler.stopAll(player.getUUID());
    }
}
