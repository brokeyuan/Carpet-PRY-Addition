package me.primaryuan.carpet.replay.phantom;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
//#if MC >= 12111
//$$ import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
//#endif
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
//#if MC < 12111
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
//#endif
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

import com.mojang.datafixers.util.Pair;

import java.util.List;
import java.util.UUID;

/**
 * 幻影包构造（javap 核实：m1-playback-api.md）。
 * 版本分支集中在这一层：1.21~1.21.10 用 TeleportEntity/Move$PosRot；1.21.11+ / 26.x 用 PositionSync。
 * 手工构造全部参数直传，不持有真实 Entity。
 */
public interface PryPackets
{
    /** PlayerInfo 添加（Entry.listed=false 不进 Tab）；无 textures 也可（延迟皮肤） */
    Packet<?> playerInfoAdd(String uuid, String name, String textures, String signature, String model);

    Packet<?> playerInfoRemove(String uuid);

    Packet<?> addEntity(int entityId, String uuid, double x, double y, double z,
                        float yaw, float pitch, float vx, float vy, float vz);

    Packet<?> removeEntities(int entityId);

    /** 移动包：大位移/强制时返回全量（Teleport/PositionSync），小位移返回 Move$PosRot，无变化返回 null */
    Packet<?> movePacket(int entityId, double dx, double dy, double dz,
                         double absX, double absY, double absZ,
                         float yaw, float pitch, float lastYaw, float lastPitch, boolean force);

    Packet<?> rotateOnly(int entityId, float yaw, float pitch, float lastYaw, float lastPitch);

    Packet<?> rotateHead(int entityId, float headYaw);

    Packet<?> animate(int entityId, byte animType, byte hand);

    Packet<?> equipment(int entityId, List<Pair<EquipmentSlot, ItemStack>> slots);

    /** 实体元数据：皮肤层开关、主手、姿态（潜行/游泳/滑翔从 flags 推） */
    Packet<?> entityMetadata(int entityId, byte flags, String model);

    static PryPackets create()
    {
        return new PryPackets()
        {
            @Override
            public Packet<?> playerInfoAdd(String uuid, String name, String textures, String signature, String model)
            {
                return PryPacketHooks.playerInfoAdd(uuid, name, textures, signature, model);
            }

            @Override
            public Packet<?> playerInfoRemove(String uuid)
            {
                return PryPacketHooks.playerInfoRemove(uuid);
            }

            @Override
            public Packet<?> addEntity(int entityId, String uuid, double x, double y, double z,
                                       float yaw, float pitch, float vx, float vy, float vz)
            {
                //#if MC >= 260200
                //$$ return new ClientboundAddEntityPacket(entityId, UUID.fromString(uuid), x, y, z,
                //$$         yaw, pitch, net.minecraft.world.entity.EntityTypes.PLAYER, 0,
                //$$         new Vec3(vx, vy, vz), 0.0);
                //#else
                return new ClientboundAddEntityPacket(entityId, UUID.fromString(uuid), x, y, z,
                        yaw, pitch, EntityType.PLAYER, 0, new Vec3(vx, vy, vz), 0.0);
                //#endif
            }

            @Override
            public Packet<?> removeEntities(int entityId)
            {
                return new ClientboundRemoveEntitiesPacket(entityId);
            }

            @Override
            public Packet<?> movePacket(int entityId, double dx, double dy, double dz,
                                        double absX, double absY, double absZ,
                                        float yaw, float pitch, float lastYaw, float lastPitch, boolean force)
            {
                if (force || Math.abs(dx) > 8 || Math.abs(dy) > 8 || Math.abs(dz) > 8)
                {
                    return fullPosition(entityId, absX, absY, absZ, yaw, pitch);
                }
                short sx = (short) Math.round(dx * 4096);
                short sy = (short) Math.round(dy * 4096);
                short sz = (short) Math.round(dz * 4096);
                if (sx == 0 && sy == 0 && sz == 0)
                {
                    return null;
                }
                byte yb = (byte) (int) (yaw * 256f / 360f);
                byte pb = (byte) (int) (pitch * 256f / 360f);
                boolean rotChanged = yb != (byte) (int) (lastYaw * 256f / 360f)
                        || pb != (byte) (int) (lastPitch * 256f / 360f);
                //#if MC >= 260300
                //$$ net.minecraft.network.protocol.game.VecDelta delta =
                //$$         new net.minecraft.network.protocol.game.VecDelta.Linear(sx, sy, sz);
                //$$ if (rotChanged)
                //$$ {
                //$$     return new ClientboundMoveEntityPacket.PosRot(entityId, delta, yb, pb, false);
                //$$ }
                //$$ return new ClientboundMoveEntityPacket.Pos(entityId, delta, false);
                //#else
                if (rotChanged)
                {
                    return new ClientboundMoveEntityPacket.PosRot(entityId, sx, sy, sz, yb, pb, false);
                }
                return new ClientboundMoveEntityPacket.Pos(entityId, sx, sy, sz, false);
                //#endif
            }

            private Packet<?> fullPosition(int entityId, double x, double y, double z,
                                           float yaw, float pitch)
            {
                //#if MC >= 12111
                return PryPacketHooks.positionSync(entityId, new Vec3(x, y, z), yaw, pitch);
                //#else
                //$$ return PryPacketBuilders.fullPosition121(entityId, x, y, z, yaw, pitch);
                //#endif
            }

            @Override
            public Packet<?> rotateOnly(int entityId, float yaw, float pitch, float lastYaw, float lastPitch)
            {
                byte yb = (byte) (int) (yaw * 256f / 360f);
                byte pb = (byte) (int) (pitch * 256f / 360f);
                byte lyb = (byte) (int) (lastYaw * 256f / 360f);
                byte lpb = (byte) (int) (lastPitch * 256f / 360f);
                if (yb == lyb && pb == lpb)
                {
                    return null;
                }
                return new ClientboundMoveEntityPacket.Rot(entityId, yb, pb, false);
            }

            @Override
            public Packet<?> rotateHead(int entityId, float headYaw)
            {
                return PryPacketHooks.rotateHeadBridge(entityId, headYaw);
            }

            @Override
            public Packet<?> animate(int entityId, byte animType, byte hand)
            {
                if (animType == 0 || animType == 1)
                {
                    return PryPacketHooks.animateBridge(entityId, animType, hand);
                }
                return null; // 受击/进食等由客户端姿态推断或后续版本扩展
            }

            @Override
            public Packet<?> equipment(int entityId, List<Pair<EquipmentSlot, ItemStack>> slots)
            {
                return new ClientboundSetEquipmentPacket(entityId, slots);
            }

            @Override
            public Packet<?> entityMetadata(int entityId, byte flags, String model)
            {
                return PryPacketHooks.entityMetadata(entityId, flags, model);
            }
        };
    }
}
