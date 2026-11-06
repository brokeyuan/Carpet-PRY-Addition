package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Animate 字段直写（同上） */
@Mixin(ClientboundAnimatePacket.class)
public interface AnimateAccessor
{
    @Mutable
    @Accessor("id")
    void pry$setId(int id);

    @Mutable
    @Accessor("action")
    void pry$setAction(int action);
}
