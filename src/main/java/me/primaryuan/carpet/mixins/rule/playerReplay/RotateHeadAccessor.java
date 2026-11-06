package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RotateHead 字段直写（构造器需要 Entity 载体，PLAYER.create 恒 null） */
@Mixin(ClientboundRotateHeadPacket.class)
public interface RotateHeadAccessor
{
    @Mutable
    @Accessor("entityId")
    void pry$setEntityId(int id);

    @Mutable
    @Accessor("yHeadRot")
    void pry$setHeadYaw(byte yaw);
}
