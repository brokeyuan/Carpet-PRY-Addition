package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.CloneDisplayIds;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PlayerInfoRemove 包改写：退出实例的 PlayerInfo 在客户端按显示 UUID 登记，
 * Remove 包由真实 UUID 组成。用当前 remove 实例上下文精确改写，不再依赖
 * 真实 UUID→单个显示 UUID 的全局缓存（同组多实例同时退出时不会串包）。
 */
@Mixin(ClientboundPlayerInfoRemovePacket.class)
public abstract class ClientboundPlayerInfoRemovePacketMixin {

    @Inject(
            method = "<init>(Ljava/util/List;)V",
            at = @At("RETURN"),
            require = 1
    )
    private void pry$rewriteRemove(List<UUID> profileIds, CallbackInfo ci) {
        boolean changed = false;
        List<UUID> mapped = new ArrayList<>(profileIds.size());
        for (UUID id : profileIds) {
            UUID display = CloneDisplayIds.displayForRemoval(id);
            if (display != null) {
                mapped.add(display);
                changed = true;
            } else {
                mapped.add(id);
            }
        }
        if (changed) {
            ((ClientboundPlayerInfoRemovePacketAccessor) (Object) this).pry$setProfileIds(mapped);
        }
    }
}
