package me.primaryuan.carpet.replay.phantom;

import me.primaryuan.carpet.replay.common.PlaybackClock;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 幻影回放实例表：一份录像的一个观看实例可以同时展示多名参与者幻影。
 * 每个观看者拥有独立实例、时钟、暂停/结束与装备缓存，未观看者不会收到幻影包；
 * 多人轨道按各自在线片段显示/消失，离线空档不显示也不插值。
 */
public final class PhantomEntityTracker
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Map<Integer, PhantomInstance> ACTIVE = new ConcurrentHashMap<>();
    private static final AtomicInteger PLAYBACK_IDS = new AtomicInteger(1);
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_700_000_000);

    private PhantomEntityTracker() {}

    /** 单个参与者的幻影同步状态 */
    private static final class ParticipantPhantom
    {
        final ReplayParticipant participant;
        final PhantomPlayer phantom;
        final PhantomSync sync;
        boolean visible;

        ParticipantPhantom(ReplayParticipant participant, PhantomPlayer phantom, PhantomSync sync)
        {
            this.participant = participant;
            this.phantom = phantom;
            this.sync = sync;
        }

        void hide()
        {
            if (visible)
            {
                sync.despawn();
                visible = false;
            }
        }
    }

    public static final class PhantomInstance
    {
        public final int playbackId;
        public final String name;
        public final String viewerUuid;
        public final String viewerName;
        public final ReplayData replay;
        public final PlaybackClock clock;
        private final List<ParticipantPhantom> tracks = new ArrayList<>();
        /** here 模式的坐标平移量与旋转角 */
        public final double offsetX;
        public final double offsetY;
        public final double offsetZ;
        public final float yawRotation;
        volatile boolean ended;

        PhantomInstance(int playbackId, String name, String viewerUuid, String viewerName,
                        ReplayData replay, PlaybackClock clock,
                        double ox, double oy, double oz, float yawRot)
        {
            this.playbackId = playbackId;
            this.name = name;
            this.viewerUuid = viewerUuid;
            this.viewerName = viewerName;
            this.replay = replay;
            this.clock = clock;
            this.offsetX = ox;
            this.offsetY = oy;
            this.offsetZ = oz;
            this.yawRotation = yawRot;
        }

        public int participantCount()
        {
            return tracks.size();
        }
    }

    /**
     * 启动回放：只为 viewer 本人创建观看实例。
     * @param anchor null=原始位置；否则以执行者位置与朝向做 here 平移/旋转
     */
    public static PhantomInstance start(MinecraftServer server, ReplayData replay, String name,
                                        ServerPlayer viewer, ServerPlayer anchor, boolean loop)
    {
        if (ACTIVE.size() >= ReplayLimits.MAX_ACTIVE_PLAYBACKS)
        {
            return null;
        }
        int forViewer = 0;
        for (PhantomInstance i : ACTIVE.values())
        {
            if (i.viewerUuid.equals(viewer.getStringUUID()))
            {
                forViewer++;
            }
        }
        if (forViewer >= ReplayLimits.MAX_PLAYBACKS_PER_VIEWER)
        {
            return null;
        }
        int playbackId = PLAYBACK_IDS.getAndIncrement();
        PlaybackClock clock = new PlaybackClock(replay.durationTicks(), loop ? -1 : 1);
        double ox = 0;
        double oy = 0;
        double oz = 0;
        float yawRot = 0;
        if (anchor != null)
        {
            ox = anchor.getX() - replay.metadata.initialX;
            oy = anchor.getY() - replay.metadata.initialY;
            oz = anchor.getZ() - replay.metadata.initialZ;
            yawRot = anchor.getYRot() - replay.metadata.initialYaw;
        }
        PhantomInstance inst = new PhantomInstance(playbackId, name, viewer.getStringUUID(),
                viewer.getName().getString(), replay, clock, ox, oy, oz, yawRot);
        PacketSink sink = new ViewerPacketSink(server, viewer.getStringUUID());
        for (ReplayParticipant participant : replay.participants)
        {
            int entityId = ENTITY_IDS.getAndIncrement();
            String uuid = UUID.nameUUIDFromBytes(("pry-phantom-" + entityId).getBytes()).toString();
            String textures = participant.skinTextures;
            String signature = participant.skinSignature;
            ServerPlayer live = server.getPlayerList().getPlayerByName(participant.name);
            if (live != null)
            {
                for (com.mojang.authlib.properties.Property p : pry$textureProps(live))
                {
                    textures = p.value();
                    signature = p.signature();
                    break;
                }
            }
            PhantomPlayer phantom = new PhantomPlayer(entityId, uuid,
                    participant.skinName == null || participant.skinName.isEmpty()
                            ? participant.name : participant.skinName,
                    textures, signature, participant.model);
            PhantomSync sync = new PhantomSync(phantom, sink, PryPackets.create());
            inst.tracks.add(new ParticipantPhantom(participant, phantom, sync));
        }
        ACTIVE.put(playbackId, inst);
        return inst;
    }

    public static void tick(MinecraftServer server)
    {
        for (PhantomInstance inst : List.copyOf(ACTIVE.values()))
        {
            try
            {
                tickInstance(server, inst);
            }
            catch (Throwable e)
            {
                LOGGER.error("[replay] 幻影回放异常，终止实例 {}", inst.playbackId, e);
                end(server, inst.playbackId);
            }
        }
    }

    private static void tickInstance(MinecraftServer server, PhantomInstance inst)
    {
        if (inst.ended)
        {
            return;
        }
        ServerPlayer viewer = server.getPlayerList().getPlayer(UUID.fromString(inst.viewerUuid));
        if (viewer == null || viewer.hasDisconnected())
        {
            end(server, inst.playbackId);
            return;
        }
        if (!me.primaryuan.carpet.replay.common.ReplayPermissions.canView(
                inst.replay.metadata.access, inst.replay.metadata.ownerUuid,
                viewer.getStringUUID(), me.primaryuan.carpet.replay.common.ReplayPermissions.isAdmin(viewer)))
        {
            // 访问范围收紧后，旧观看实例立即停止，不靠旧按钮绕过
            end(server, inst.playbackId);
            return;
        }
        int t = inst.clock.advance();
        if (t < 0)
        {
            if (inst.clock.finished)
            {
                end(server, inst.playbackId);
            }
            return;
        }
        String viewerDim = dimensionOf(viewer);
        for (ParticipantPhantom track : inst.tracks)
        {
            ReplayParticipant.Segment seg = track.participant.segmentAt(t);
            if (seg == null)
            {
                track.hide();
                continue;
            }
            int rel = t - seg.startTick;
            VisualFrame raw = seg.visual.frameAt(rel);
            if (raw == null || !seg.visual.dimensionAt(rel).equals(viewerDim))
            {
                track.hide();
                continue;
            }
            VisualFrame frame = applyTransform(inst, raw);
            track.sync.setFrameIndex(rel);
            track.sync.tick(frame, seg.visual, bytes -> ItemStackBytes.deserialize(server, bytes),
                    rel == 0 || seg.visual.isSegmentStart(rel));
            track.visible = true;
        }
        if (inst.clock.finished)
        {
            end(server, inst.playbackId);
        }
    }

    private static VisualFrame applyTransform(PhantomInstance inst, VisualFrame raw)
    {
        if (inst.yawRotation == 0 && inst.offsetX == 0 && inst.offsetY == 0 && inst.offsetZ == 0)
        {
            return raw;
        }
        VisualFrame f = raw.copy();
        double rx = raw.x - inst.replay.metadata.initialX;
        double rz = raw.z - inst.replay.metadata.initialZ;
        float rad = (float) Math.toRadians(inst.yawRotation);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double nx = rx * cos - rz * sin;
        double nz = rx * sin + rz * cos;
        f.x = inst.replay.metadata.initialX + nx + inst.offsetX;
        f.z = inst.replay.metadata.initialZ + nz + inst.offsetZ;
        f.y = raw.y + inst.offsetY;
        f.bodyYaw = raw.bodyYaw + inst.yawRotation;
        f.headYaw = raw.headYaw + inst.yawRotation;
        f.vx = (float) ((raw.vx * cos - raw.vz * sin));
        f.vz = (float) ((raw.vx * sin + raw.vz * cos));
        return f;
    }

    private static String dimensionOf(ServerPlayer p)
    {
        //#if MC >= 12111
        return p.level().dimension().identifier().toString();
        //#else
        //$$ return p.level().dimension().location().toString();
        //#endif
    }

    public static PhantomInstance get(int playbackId)
    {
        return ACTIVE.get(playbackId);
    }

    private static java.util.Collection<com.mojang.authlib.properties.Property> pry$textureProps(ServerPlayer player)
    {
        //#if MC >= 12110
        return player.getGameProfile().properties().get("textures");
        //#else
        //$$ return player.getGameProfile().getProperties().get("textures");
        //#endif
    }

    public static List<PhantomInstance> listActive()
    {
        return List.copyOf(ACTIVE.values());
    }

    public static List<PhantomInstance> listVisibleTo(ServerPlayer player)
    {
        List<PhantomInstance> out = new ArrayList<>();
        for (PhantomInstance i : ACTIVE.values())
        {
            if (i.viewerUuid.equals(player.getStringUUID()))
            {
                out.add(i);
            }
        }
        out.sort((a, b) -> Integer.compare(a.playbackId, b.playbackId));
        return out;
    }

    public static boolean pause(int playbackId)
    {
        PhantomInstance i = ACTIVE.get(playbackId);
        if (i == null || i.ended)
        {
            return false;
        }
        i.clock.paused = true;
        return true;
    }

    public static boolean resume(int playbackId)
    {
        PhantomInstance i = ACTIVE.get(playbackId);
        if (i == null || i.ended || !i.clock.paused)
        {
            return false;
        }
        i.clock.paused = false;
        return true;
    }

    public static boolean end(MinecraftServer server, int playbackId)
    {
        PhantomInstance i = ACTIVE.remove(playbackId);
        if (i == null)
        {
            return false;
        }
        i.ended = true;
        for (ParticipantPhantom track : i.tracks)
        {
            try
            {
                track.hide();
            }
            catch (Throwable e)
            {
                LOGGER.error("[replay] 幻影销毁包失败 {}", playbackId, e);
            }
        }
        return true;
    }

    public static void endForViewer(MinecraftServer server, String viewerUuid)
    {
        for (PhantomInstance i : List.copyOf(ACTIVE.values()))
        {
            if (i.viewerUuid.equals(viewerUuid))
            {
                end(server, i.playbackId);
            }
        }
    }

    public static void shutdownAll()
    {
        for (PhantomInstance i : List.copyOf(ACTIVE.values()))
        {
            i.ended = true;
            for (ParticipantPhantom track : i.tracks)
            {
                try
                {
                    track.hide();
                }
                catch (Exception ignored)
                {
                    // 服务器关闭路径尽力而为
                }
            }
        }
        ACTIVE.clear();
    }

    public static int activeCount()
    {
        return ACTIVE.size();
    }

    /** 只向实例观看者发送，不向其他在线玩家广播 */
    private static final class ViewerPacketSink implements PacketSink
    {
        private final MinecraftServer server;
        private final String viewerUuid;

        ViewerPacketSink(MinecraftServer server, String viewerUuid)
        {
            this.server = server;
            this.viewerUuid = viewerUuid;
        }

        @Override
        public void send(ServerPlayer observer, net.minecraft.network.protocol.Packet<?> packet)
        {
            if (observer.connection != null)
            {
                observer.connection.send(packet);
            }
        }

        @Override
        public List<ServerPlayer> observers()
        {
            ServerPlayer viewer = server.getPlayerList().getPlayer(UUID.fromString(viewerUuid));
            if (viewer == null || viewer.hasDisconnected())
            {
                return List.of();
            }
            return List.of(viewer);
        }

        @Override
        public void forget(ServerPlayer observer)
        {
            // PhantomSync 内部按 uuid 管理
        }
    }
}
