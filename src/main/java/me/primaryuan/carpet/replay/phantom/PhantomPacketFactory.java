package me.primaryuan.carpet.replay.phantom;

import me.primaryuan.carpet.mixins.rule.playerReplay.AnimateAccessor;
import me.primaryuan.carpet.mixins.rule.playerReplay.RotateHeadAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import sun.misc.Unsafe;

/**
 * 幻影包实例工厂：RotateHead/Animate 的公开构造器首指令即 Entity.getId() 解引用
 * （EntityType.PLAYER.create 又恒 null，R2/R3 字节码实证），因此用 Unsafe
 * 分配零值实例 + accessor 直写字段，全程不触碰任何 Entity。
 * 仅在服务器主线程调用；分配失败（Unsafe 不可用）返回 null 由调用方跳过该包。
 */
public final class PhantomPacketFactory
{
    private static final Unsafe UNSAFE = unsafe();

    private PhantomPacketFactory() {}

    private static Unsafe unsafe()
    {
        try
        {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** 头部朝向包（ entityId + yHeadRot） */
    public static Packet<?> rotateHead(int entityId, float headYaw)
    {
        if (UNSAFE == null)
        {
            return null;
        }
        try
        {
            ClientboundRotateHeadPacket packet =
                    (ClientboundRotateHeadPacket) UNSAFE.allocateInstance(ClientboundRotateHeadPacket.class);
            RotateHeadAccessor acc = (RotateHeadAccessor) packet;
            acc.pry$setEntityId(entityId);
            acc.pry$setHeadYaw((byte) (int) (headYaw * 256f / 360f));
            return packet;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** 挥动动画包（id + action）：0 主手挥动 / 3 副手挥动 */
    public static Packet<?> animate(int entityId, byte animType)
    {
        if (UNSAFE == null)
        {
            return null;
        }
        try
        {
            ClientboundAnimatePacket packet =
                    (ClientboundAnimatePacket) UNSAFE.allocateInstance(ClientboundAnimatePacket.class);
            AnimateAccessor acc = (AnimateAccessor) packet;
            acc.pry$setId(entityId);
            acc.pry$setAction(animType == 1 ? 3 : 0);
            return packet;
        }
        catch (Exception e)
        {
            org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
                    .error("[replay] 幻影包分配失败（Unsafe 不可用或字段漂移）", e);
            return null;
        }
    }

    /**
     * 1.21~1.21.1 全量位置包（id/x/y/z/yRot/xRot/onGround）：同 Unsafe 路线。
     * 该线公开构造器首指令 Entity.getId() 解引用，无法 null 构造（R4-F2）。
     */
    public static Packet<?> teleport121(int entityId, double x, double y, double z,
                                        float yaw, float pitch)
    {
        if (UNSAFE == null)
        {
            return null;
        }
        try
        {
            Class<?> cls = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            Object packet = UNSAFE.allocateInstance(cls);
            me.primaryuan.carpet.mixins.rule.playerReplay.TeleportEntityAccessors acc =
                    (me.primaryuan.carpet.mixins.rule.playerReplay.TeleportEntityAccessors) packet;
            acc.pry$setId(entityId);
            acc.pry$setX(x);
            acc.pry$setY(y);
            acc.pry$setZ(z);
            acc.pry$setYRot((byte) (int) (yaw * 256f / 360f));
            acc.pry$setXRot((byte) (int) (pitch * 256f / 360f));
            acc.pry$setOnGround(false);
            return (Packet<?>) packet;
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
