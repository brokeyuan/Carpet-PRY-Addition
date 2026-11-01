package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
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
 * PlayerInfoRemove 包改写：退出实例的 PlayerInfo 在客户端是按显示 UUID 登记
 * 的（PlayerInfoUpdate 构造点已换），Remove 包由 ServerPlayer.getUUID()（真实
 * UUID）组成——不改写则客户端删不掉对应 PlayerInfo，分身退残留幽灵 tab 项。
 *
 * <p>实例上下文在 remove 钩子已缓存（CloneDisplayIds.markExiting）——真实
 * UUID → 退出中实例的显示 UUID。非分身退出无缓存，零改动。</p>
 */
@Mixin(ClientboundPlayerInfoRemovePacket.class)
public abstract class ClientboundPlayerInfoRemovePacketMixin {

    @Inject(
            method = "<init>(Ljava/util/List;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$rewriteRemove(List<UUID> profileIds, CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        boolean changed = false;
        List<UUID> mapped = new ArrayList<>(profileIds.size());
        for (UUID id : profileIds) {
            UUID display = CloneDisplayIds.exitingDisplay(id);
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
