package me.primaryuan.carpet.replay.phantom;

import net.minecraft.network.protocol.Packet;
//#if MC >= 12111
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
//#if MC >= 260300
//$$ import net.minecraft.world.entity.PositionPath;
//#else
import net.minecraft.world.entity.PositionMoveRotation;
//#endif
//#endif
import net.minecraft.world.phys.Vec3;

/**
 * 幻影包构造桥。RotateHead/Animate 经字段 accessor 直写（构造器需要 Entity 载体，
 * 而 EntityType.PLAYER.create 恒 null——R2 字节码实证），无任何 Entity 实例依赖。
 */
final class PryPacketHooks
{
    private PryPacketHooks() {}

    static Packet<?> playerInfoAdd(String uuid, String name, String textures, String signature, String model)
    {
        return PhantomPacketBridge.playerInfoAdd(uuid, name, textures, signature);
    }

    static Packet<?> playerInfoRemove(String uuid)
    {
        return PhantomPacketBridge.playerInfoRemove(uuid);
    }

    /** 头部朝向包：Unsafe 零值实例 + 字段直写（构造器会解引用 Entity，R3-F1） */
    static Packet<?> rotateHeadBridge(int entityId, float headYaw)
    {
        return PhantomPacketFactory.rotateHead(entityId, headYaw);
    }

    /** 挥动动画包：同上 */
    static Packet<?> animateBridge(int entityId, byte animType, byte hand)
    {
        return PhantomPacketFactory.animate(entityId, animType);
    }

    /** 全量位置：1.21.11+ PositionSync（record 直构）；1.21~1.21.1 走 TeleportEntity 分支 */
    static Packet<?> positionSync(int entityId, Vec3 pos, float yaw, float pitch)
    {
        //#if MC >= 260300
        //$$ return new ClientboundEntityPositionSyncPacket(
        //$$         entityId, net.minecraft.world.entity.PositionPath.of(pos), yaw, pitch, false);
        //#elseif MC >= 12111
        return new ClientboundEntityPositionSyncPacket(
                entityId, new PositionMoveRotation(pos, Vec3.ZERO, yaw, pitch), false);
        //#else
        //$$ return PryPacketBuilders.fullPosition121(entityId, pos.x, pos.y, pos.z, yaw, pitch);
        //#endif
    }

    static Packet<?> entityMetadata(int entityId, byte flags, String model)
    {
        return PhantomMetadataBridge.build(entityId, flags, model);
    }
}
