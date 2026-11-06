package me.primaryuan.carpet.replay.phantom;

import net.minecraft.network.protocol.Packet;
//#if MC >= 12103
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import me.primaryuan.carpet.mixins.rule.playerReplay.TeleportEntityAccessors;
//#endif

/**
 * 全量位置包。1.21.3+ record 直构；1.21~1.21.1 TeleportEntity(Entity) 无法直构
 * （PLAYER.create 恒 null），同样经字段 accessor 直写。
 */
final class PryPacketBuilders
{
    private PryPacketBuilders() {}

    static Packet<?> fullPosition121(int entityId, double x, double y, double z, float yaw, float pitch)
    {
        //#if MC >= 12103
        return new ClientboundTeleportEntityPacket(
                entityId,
                new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yaw, pitch),
                java.util.Set.of(), false);
        //#else
        //$$ return PhantomPacketFactory.teleport121(entityId, x, y, z, yaw, pitch);
        //#endif
    }
}
