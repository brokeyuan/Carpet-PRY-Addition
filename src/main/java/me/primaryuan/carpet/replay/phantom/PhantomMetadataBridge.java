package me.primaryuan.carpet.replay.phantom;

import me.primaryuan.carpet.mixins.rule.playerReplay.EntityDataAccessors;
import me.primaryuan.carpet.mixins.rule.playerReplay.LivingEntityDataAccessors;
import me.primaryuan.carpet.mixins.rule.playerReplay.PlayerDataAccessors;
import me.primaryuan.carpet.replay.common.VisualFlags;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Pose;

import java.util.ArrayList;
import java.util.List;

/**
 * 幻影实体元数据构造：皮肤层全开 + 主手右 + 姿态位。
 * accessor 经 mixin 静态桥拿 protected 常量；值直接按协议语义构造。
 */
final class PhantomMetadataBridge
{
    private PhantomMetadataBridge() {}

    static Packet<?> build(int entityId, byte flags, String model)
    {
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(4);
        try
        {
            // 皮肤层：全部开启（披风|上衣|左臂|左腿|右臂|右腿|帽子 = 0x7F）
            EntityDataAccessor<Byte> skin = PlayerDataAccessors.pry$skinLayers();
            values.add(SynchedEntityData.DataValue.create(skin, (byte) 0x7F));
            // 主手：1.21~1.21.10 字节 0=右/1=左；1.21.11+ 为 HumanoidArm 枚举（序列化同为 0/1）
            EntityDataAccessor<Object> hand = PlayerDataAccessors.pry$mainHand();
            //#if MC >= 12111
            values.add(SynchedEntityData.DataValue.create(hand, net.minecraft.world.entity.HumanoidArm.RIGHT));
            //#else
            //$$ values.add(SynchedEntityData.DataValue.create(hand, (byte) 0));
            //#endif
            // Entity shared flags 位（javap setShiftKeyDown=setSharedFlag(1)/setSprinting=(3)）：
            // 潜行=0x02、疾跑=0x08、隐身=0x20（DATA_SHARED_FLAGS_ID 字节）
            EntityDataAccessor<Byte> shared = LivingEntityDataAccessors.pry$sharedFlags();
            byte sharedBits = 0;
            if ((flags & VisualFlags.SNEAKING) != 0)
            {
                sharedBits |= 0x02;
            }
            if ((flags & VisualFlags.SPRINTING) != 0)
            {
                sharedBits |= 0x08;
            }
            values.add(SynchedEntityData.DataValue.create(shared, sharedBits));
            // 姿态
            EntityDataAccessor<Pose> pose = EntityDataAccessors.pry$pose();
            values.add(SynchedEntityData.DataValue.create(pose, poseOf(flags)));
        }
        catch (Throwable t)
        {
            // accessor 漂移时跳过元数据（幻影仍可见，仅皮肤层/姿态默认）——记日志由调用方处理
            return null;
        }
        return new ClientboundSetEntityDataPacket(entityId, values);
    }

    static Pose poseOf(byte flags)
    {
        if ((flags & VisualFlags.SWIMMING) != 0)
        {
            return Pose.SWIMMING;
        }
        if ((flags & VisualFlags.FALL_FLYING) != 0)
        {
            return Pose.FALL_FLYING;
        }
        if ((flags & VisualFlags.SNEAKING) != 0)
        {
            return Pose.CROUCHING;
        }
        return Pose.STANDING;
    }
}
