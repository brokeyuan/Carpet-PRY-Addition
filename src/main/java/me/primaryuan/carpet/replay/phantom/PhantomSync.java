package me.primaryuan.carpet.replay.phantom;

import me.primaryuan.carpet.replay.common.VisualFlags;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualTrack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.mojang.datafixers.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 幻影逐帧同步：把第 tick 帧状态与上帧差异变换成包序列。
 * 包构造的版本差异集中在 {@link PryPackets}；本类维护"上帧状态"做差量（移动/转头/装备）。
 * 纯发包：不触碰世界、不创建实体。
 */
public final class PhantomSync
{
    /** 首次可见范围（方块） */
    public static final double SPAWN_RANGE = 128;
    /** 每多少 Tick 强制全量位置同步（防漂移） */
    private static final int FORCED_SYNC_INTERVAL = 20;

    private final PhantomPlayer phantom;
    private final PacketSink sink;
    private final PryPackets packets;
    private EquipmentResolver resolver;
    /** 观察者 uuid → 已同步（收过 spawn 序列） */
    private final Map<String, Boolean> spawned = new HashMap<>();
    private VisualFrame last;
    private int lastEquipmentTick = -1;
    private int framesSinceForce;
    private final Map<Byte, byte[]> lastEquipment = new HashMap<>();
    private byte lastSentFlags = -1;
    private boolean segmentStart;

    public PhantomSync(PhantomPlayer phantom, PacketSink sink, PryPackets packets)
    {
        this.phantom = phantom;
        this.sink = sink;
        this.packets = packets;
    }

    /**
     * 每 Tick 推进：frame 为当前帧，track 提供装备/动画事件。
     * @param track 装备与动画事件源
     * @param equipmentAt 解析第 tick 的装备快照（槽 → 序列化字节；播放端反序列化为 ItemStack）
     */
    public void tick(VisualFrame frame, VisualTrack track, EquipmentResolver equipmentAt, boolean segmentStart)
    {
        resolver = equipmentAt;
        this.segmentStart = segmentStart;
        // 新观察者发 spawn 序列；已退出观察者剔除（重进视为新观察者重发）
        // 键 = uuid + 维度：维度切换（键变化）自然触发重发 spawn 序列
        java.util.Set<String> present = new java.util.HashSet<>();
        for (ServerPlayer obs : sink.observers())
        {
            if (obs.hasDisconnected())
            {
                continue;
            }
            String key = obs.getStringUUID() + "@" + obs.level().dimension().identifier();
            present.add(key);
            if (!spawned.containsKey(key))
            {
                spawnTo(obs, frame);
            }
        }
        spawned.keySet().retainAll(present);
        // 首 Tick 只做 spawn（位置已含在 AddEntity），装备/动画/姿态照常处理——
        // 否则 Tick 0 的装备快照事件会被跳过（R5 实测盔甲丢失的根因）
        boolean first = last == null;
        if (!first)
        {
            framesSinceForce++;
            // 段边界（暂停恢复/维度切换）强制全量位置，不插值出不存在的移动路线
            boolean forced = framesSinceForce >= FORCED_SYNC_INTERVAL || segmentStart;
            // 移动 + 转向
            double dx = frame.x - last.x;
            double dy = frame.y - last.y;
            double dz = frame.z - last.z;
            Packet<?> move = packets.movePacket(phantom.entityId, dx, dy, dz,
                    frame.x, frame.y, frame.z,
                    frame.bodyYaw, frame.pitch, last.bodyYaw, last.pitch, forced);
            if (move != null)
            {
                broadcast(move);
                Packet<?> head = packets.rotateHead(phantom.entityId, frame.headYaw);
                if (head != null)
                {
                    broadcast(head);
                }
            }
            else if (yawDelta(last.bodyYaw, frame.bodyYaw) != 0 || yawDelta(last.headYaw, frame.headYaw) != 0
                    || pitchDelta(last.pitch, frame.pitch) != 0)
            {
                Packet<?> rot = packets.rotateOnly(phantom.entityId, frame.bodyYaw, frame.pitch,
                        last.bodyYaw, last.pitch);
                if (rot != null)
                {
                    broadcast(rot);
                }
                Packet<?> head = packets.rotateHead(phantom.entityId, frame.headYaw);
                if (head != null)
                {
                    broadcast(head);
                }
            }
            if (forced)
            {
                framesSinceForce = 0;
            }
        }
        // 姿态/潜行/游泳等元数据：flags 变化即重发（初始已在 spawnTo 发送）
        if (frame.flags != lastSentFlags)
        {
            lastSentFlags = frame.flags;
            Packet<?> meta = packets.entityMetadata(phantom.entityId, frame.flags, phantom.model);
            if (meta != null)
            {
                broadcast(meta);
            }
        }
        // 装备
        applyEquipment(frame, track, equipmentAt);
        // 动画事件（本 tick）
        for (VisualTrack.AnimEvent e : track.animEvents)
        {
            if (e.tick() == currentTickFrameIndex && e.seq() >= 0)
            {
                Packet<?> anim = packets.animate(phantom.entityId, e.type(), e.hand());
                if (anim != null)
                {
                    broadcast(anim);
                }
            }
        }
        last = frame.copy();
    }

    private int currentTickFrameIndex;

    /** 由引擎每帧调用设置绝对帧序号 */
    public void setFrameIndex(int idx)
    {
        this.currentTickFrameIndex = idx;
    }

    private void applyEquipment(VisualFrame frame, VisualTrack track, EquipmentResolver equipmentAt)
    {
        for (VisualTrack.EquipmentEvent e : track.equipmentEvents)
        {
            if (e.tick() != currentTickFrameIndex)
            {
                continue;
            }
            byte slot = e.slot();
            if (java.util.Arrays.equals(lastEquipment.get(slot), e.itemBytes()))
            {
                continue;
            }
            lastEquipment.put(slot, e.itemBytes());
            ItemStack stack = equipmentAt.resolve(e.itemBytes());
            if (stack == null)
            {
                continue;
            }
            EquipmentSlot es = slotOf(slot);
            if (es == null)
            {
                continue;
            }
            List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
            list.add(Pair.of(es, stack));
            Packet<?> p = packets.equipment(phantom.entityId, list);
            if (p != null)
            {
                broadcast(p);
            }
        }
    }

    static EquipmentSlot slotOf(byte slot)
    {
        return switch (slot)
        {
            case 0 -> EquipmentSlot.MAINHAND;
            case 1 -> EquipmentSlot.OFFHAND;
            case 2 -> EquipmentSlot.FEET;
            case 3 -> EquipmentSlot.LEGS;
            case 4 -> EquipmentSlot.CHEST;
            case 5 -> EquipmentSlot.HEAD;
            default -> null;
        };
    }

    /** 新观察者进入：PlayerInfo（不入列表）→ AddEntity → 当前帧姿态/装备 */
    private void spawnTo(ServerPlayer obs, VisualFrame frame)
    {
        Packet<?> info = packets.playerInfoAdd(phantom.uuid, phantom.profileName, phantom.profileTextures,
                phantom.profileSignature, phantom.model);
        if (info != null)
        {
            sink.send(obs, info);
        }
        Packet<?> add = packets.addEntity(phantom.entityId, phantom.uuid, frame.x, frame.y, frame.z,
                frame.bodyYaw, frame.pitch, frame.vx, frame.vy, frame.vz);
        if (add != null)
        {
            sink.send(obs, add);
        }
        Packet<?> meta = packets.entityMetadata(phantom.entityId, frame.flags, phantom.model);
        if (meta != null)
        {
            sink.send(obs, meta);
        }
        lastSentFlags = frame.flags;
        Packet<?> head = packets.rotateHead(phantom.entityId, frame.headYaw);
        if (head != null)
        {
            sink.send(obs, head);
        }
        // 装备快照：重进视野的观察者补收当前装备（含空槽清空）
        if (resolver != null)
        {
            for (byte s = 0; s < 6; s++)
            {
                byte[] item = lastEquipment.get(s);
                if (item == null)
                {
                    continue;
                }
                net.minecraft.world.entity.EquipmentSlot es = slotOf(s);
                ItemStack stack = resolver.resolve(item);
                if (es == null || stack == null)
                {
                    continue;
                }
                List<Pair<net.minecraft.world.entity.EquipmentSlot, ItemStack>> one =
                        new ArrayList<>();
                one.add(Pair.of(es, stack));
                Packet<?> eq = packets.equipment(phantom.entityId, one);
                if (eq != null)
                {
                    sink.send(obs, eq);
                }
            }
        }
        spawned.put(obs.getStringUUID() + "@" + obs.level().dimension().identifier(), true);
    }

    /** 观察者离开（退出/换维度） */
    public void forget(ServerPlayer obs)
    {
        spawned.keySet().removeIf(k -> k.startsWith(obs.getStringUUID() + "@"));
    }

    /** 结束播放：移除实体 + 移除 PlayerInfo */
    public void despawn()
    {
        Packet<?> remove = packets.removeEntities(phantom.entityId);
        if (remove != null)
        {
            broadcast(remove);
        }
        Packet<?> info = packets.playerInfoRemove(phantom.uuid);
        if (info != null)
        {
            broadcast(info);
        }
        spawned.clear();
        last = null;
    }

    private void broadcast(Packet<?> p)
    {
        for (ServerPlayer obs : sink.observers())
        {
            sink.send(obs, p);
        }
    }

    static float yawDelta(float a, float b)
    {
        float d = b - a;
        while (d >= 180f)
        {
            d -= 360f;
        }
        while (d < -180f)
        {
            d += 360f;
        }
        return d;
    }

    static float pitchDelta(float a, float b)
    {
        return b - a;
    }

    /** 装备字节 → ItemStack 解析器（播放端实现） */
    public interface EquipmentResolver
    {
        ItemStack resolve(byte[] itemBytes);
    }
}
