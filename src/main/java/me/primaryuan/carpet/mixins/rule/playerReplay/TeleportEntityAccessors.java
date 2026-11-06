package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21~1.21.1 TeleportEntity 字段直写（该线公开构造需要 Entity，
 * PLAYER.create 恒 null，故默认构造 + accessor；x/y/z 为 double 绝对坐标）。
 */
@Mixin(ClientboundTeleportEntityPacket.class)
public interface TeleportEntityAccessors
{
    @Mutable
    @Accessor("id")
    void pry$setId(int id);

    @Mutable
    @Accessor("x")
    void pry$setX(double x);

    @Mutable
    @Accessor("y")
    void pry$setY(double y);

    @Mutable
    @Accessor("z")
    void pry$setZ(double z);

    @Mutable
    @Accessor("yRot")
    void pry$setYRot(byte yaw);

    @Mutable
    @Accessor("xRot")
    void pry$setXRot(byte pitch);

    @Mutable
    @Accessor("onGround")
    void pry$setOnGround(boolean onGround);
}
