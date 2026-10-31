package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 分身 spawn/kill 生命周期管理（M2）。
 *
 * <p>spawn：以在线主实例（或组记录）的身份再创建一个同 UUID 假人。
 * placeNewPlayer 前必须在出生点注册 DistanceManager 并预设 lastSectionPos，
 * 否则 ChunkMap.move 对 (0,0,0) 旧 section removePlayer 触发 NPE（M1 实证）。</p>
 *
 * <p>kill：一次调用退出整组；每个成员走 Carpet kill（正常退出非死亡）。</p>
 */
public final class CloneSpawnManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    private CloneSpawnManager() {}

    /**
     * 为目标身份再生成一个分身。
     *
     * @return 新分身；创建失败返回 null
     */
        public static ServerPlayer spawnClone(MinecraftServer server, String name, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        UUID uuid = resolveUuid(server, name);
        if (uuid == null) {
            return null;
        }
        // 真人冲突保护：身份被真人占用时禁止生成分身
        if (CloneGroupRegistry.isOnlineRealPlayer(server, uuid)) {
            return null;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(uuid, name);
        CloneAllowlist.allow(uuid, name);
        try {
            GameProfile profile = new GameProfile(uuid, name);
            EntityPlayerMPFake instance = EntityPlayerMPFake.respawnFake(server, level, profile,
                    ClientInformation.createDefault());
            // placeNewPlayer 内部 connection.teleport -> ChunkMap.move 会对"旧 section"
            // 做 DistanceManager.removePlayer；新实例 lastSectionPos 恒为 (0,0,0)，
            // 该 chunk 无玩家集合 -> NPE。先在出生点注册 DistanceManager 并把
            // lastSectionPos 预设为出生 section，使 remove/add 成对净空（M1 实证）。
            CloneDistanceManagerHelper.preRegister(instance);
            server.getPlayerList().placeNewPlayer(
                    new FakeClientConnection(PacketFlow.SERVERBOUND),
                    instance,
                    new CommonListenerCookie(profile, 0, instance.clientInformation(), false));
            instance.stopRiding();
            instance.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
            instance.setHealth(20.0F);
            unsetRemoved(instance);
            instance.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(0.6F);
            instance.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            server.getPlayerList().broadcastAll(
                    new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256.0F / 360.0F)),
                    level.dimension());
            server.getPlayerList().broadcastAll(
                    net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.of(instance),
                    level.dimension());

            // 组登记由 placeNewPlayer RETURN 注入统一完成（唯一登记点，
            // 此处再 add 会造成同实例双 cloneId）。这里只取号回显。
            int cloneId = CloneInstanceRegistry.cloneIdOf(instance);
            // 让老成员（和其他观察者）的 tab 里能看到新实例
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, instance));
            LOGGER.info("[pry:clones] spawned clone {}#{} entityId={} at ({}, {}, {}) in {}",
                    name, cloneId, instance.getId(), pos.x, pos.y, pos.z, level.dimension().identifier());
            return instance;
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] spawn clone {} failed", name, t);
            return null;
        }
    }

    /** 整组退出（kill 语义：正常退出非死亡，无掉落） */
    public static int killGroup(MinecraftServer server, CloneGroup group) {
        List<ServerPlayer> members = group.onlineMembers();
        int n = 0;
        for (ServerPlayer p : members) {
            if (p instanceof EntityPlayerMPFake fake) {
                fake.kill(net.minecraft.network.chat.Component.literal("clone group exit"));
                n++;
            }
            CloneInstanceRegistry.remove(p);
            group.remove(p);
        }
        LOGGER.info("[pry:clones] killed group {} ({} members)", group.name(), n);
        return n;
    }

    private static void unsetRemoved(EntityPlayerMPFake instance) throws Exception {
        Method m = net.minecraft.world.entity.Entity.class.getDeclaredMethod("unsetRemoved");
        m.setAccessible(true);
        m.invoke(instance);
    }

    /** 在线实例优先；否则查 usercache；否则 offline UUID */
        private static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) {
            return p.getUUID();
        }
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g != null) {
            return g.uuid();
        }
        return net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
    }
}
