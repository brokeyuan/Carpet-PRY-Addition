package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/**
 * ClientboundAddEntityPacket.uuid 字段写访问器（显示 UUID 构造点回填）。
 */
@Mixin(ClientboundAddEntityPacket.class)
public interface ClientboundAddEntityPacketAccessor {

    @Mutable
    @Accessor("uuid")
    void pry$setUuid(UUID uuid);
}
