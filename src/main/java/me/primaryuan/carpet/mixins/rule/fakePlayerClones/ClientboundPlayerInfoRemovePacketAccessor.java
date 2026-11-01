package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.UUID;

/**
 * ClientboundPlayerInfoRemovePacket.profileIds 字段写访问器（显示 UUID 回填）。
 */
@Mixin(ClientboundPlayerInfoRemovePacket.class)
public interface ClientboundPlayerInfoRemovePacketAccessor {

    @Mutable
    @Accessor("profileIds")
    void pry$setProfileIds(List<UUID> profileIds);
}
