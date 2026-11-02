package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.FakeClientConnection;
import com.mojang.authlib.GameProfile;
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

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * 分身 spawn/kill 生命周期管理（M2）。
 *
 * <p>spawn：以组权威 profile（含 textures）再创建一个同 UUID 假人；首个成员
 * 读一次 playerdata 建立共享存储，后续成员只接入存储，不读盘覆盖。</p>
 *
 * <p>kill：所有整组退出统一走 {@link #killGroup}：先快照、再权威 playerdata
 * 保存、最后请求 Carpet 正常退出。Carpet kill 经 MinecraftServer.schedule
 * 调度断连，因此这里不删除成员登记，由 PlayerList.remove 的 finally 清理。</p>
 */
public final class CloneSpawnManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    private CloneSpawnManager() {}

    public static ServerPlayer spawnClone(MinecraftServer server, String name, ServerLevel level,
                                          Vec3 pos, float yaw, float pitch) {
        return spawnClone(server, name, level, pos, yaw, pitch, null);
    }

    /**
     * @param forcedCloneId rejoin 时恢复旧编号；null 表示顺序分配
     * @return 新分身；创建失败返回 null（失败不会留下半组）
     */
    public static ServerPlayer spawnClone(MinecraftServer server, String name, ServerLevel level,
                                          Vec3 pos, float yaw, float pitch, Integer forcedCloneId) {
        UUID uuid = resolveUuid(server, name);
        if (uuid == null) {
            return null;
        }
        if (CloneGroupRegistry.isOnlineRealPlayer(server, uuid)) {
            return null;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(uuid, name);
        boolean restoring = forcedCloneId != null && group.state() == CloneGroup.State.RESTORING;
        if (!group.isActive() && !restoring) {
            LOGGER.warn("[pry:clones] spawn {} rejected: group state {}", name, group.state());
            return null;
        }
        CloneAllowlist.allow(uuid, name);
        EntityPlayerMPFake instance = null;
        try {
            GameProfile profile = buildProfile(group, uuid, name);
            instance = EntityPlayerMPFake.respawnFake(server, level, profile,
                    ClientInformation.createDefault());
            if (forcedCloneId != null) {
                group.reserveExact(instance, forcedCloneId);
            }
            CloneDistanceManagerHelper.preRegister(instance);
            server.getPlayerList().placeNewPlayer(
                    new FakeClientConnection(PacketFlow.SERVERBOUND),
                    instance,
                    new CommonListenerCookie(profile, 0, instance.clientInformation(), false));

            // 入组由 placeNewPlayer RETURN 完成；未登记说明共享存储接管失败
            int cloneId = CloneInstanceRegistry.cloneIdOf(instance);
            if (cloneId < 0) {
                abortSpawn(server, group, instance);
                return null;
            }
            // 仅组首次上线读盘；后续成员读盘会用磁盘旧背包覆盖运行中的权威状态
            if (SharedInventoryManager.needsInitialLoad(group)) {
                if (!CloneVersionAdapter.loadPlayerData(server, instance)) {
                    abortSpawn(server, group, instance);
                    return null;
                }
                SharedInventoryManager.markLoaded(group);
            }
            instance.stopRiding();
            CloneVersionAdapter.teleport(instance, level, pos, yaw, pitch);
            instance.setHealth(20.0F);
            unsetRemoved(instance);
            instance.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(0.6F);
            GameType mode = GameType.SURVIVAL;
            ServerPlayer primaryNow = group.primary();
            if (primaryNow != null && primaryNow != instance) {
                mode = primaryNow.gameMode.getGameModeForPlayer();
            }
            instance.gameMode.changeGameModeForPlayer(mode);
            server.getPlayerList().broadcastAll(
                    new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(instance,
                            (byte) (instance.yHeadRot * 256.0F / 360.0F)),
                    level.dimension());
            CloneVersionAdapter.broadcastPosition(server, instance, level);
            server.getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, instance));
            group.setExitSaved(false);
            LOGGER.info("[pry:clones] spawned clone {}#{} entityId={} at ({}, {}, {}) in {}",
                    name, cloneId, instance.getId(), pos.x, pos.y, pos.z, level.dimension().identifier());
            return instance;
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] spawn clone {} failed", name, t);
            if (instance != null) {
                abortSpawn(server, group, instance);
            }
            return null;
        }
    }

    /**
     * 整组退出入口（/player name kill 与 /fakeclone kill 共用）。
     *
     * @return 成员数；负数表示失败（-1 状态不允许 / -2 快照保存失败 / -3 存档保存失败）
     */
    public static int killGroup(MinecraftServer server, CloneGroup group) {
        if (!group.isActive()) {
            return -1;
        }
        List<ServerPlayer> members = group.onlineMembers();
        if (members.isEmpty()) {
            group.finishExitIfEmpty();
            return 0;
        }
        if (!CloneSnapshotManager.save(server, group)) {
            return -2;
        }
        ServerPlayer primary = group.primary();
        if (primary == null) {
            return -2;
        }
        try {
            // 权威 playerdata 只写一次；退出过程中各实例不再重复写盘
            ((me.primaryuan.carpet.mixins.rule.fakePlayerClones.PlayerListAccessor)
                    server.getPlayerList()).pry$save(primary);
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] save playerdata for group {} failed", group.name(), t);
            return -3;
        }
        group.setExitSaved(true);
        if (!group.beginExit()) {
            return -1;
        }
        int n = 0;
        for (ServerPlayer p : members) {
            try {
                if (p instanceof EntityPlayerMPFake fake) {
                    fake.kill(net.minecraft.network.chat.Component.literal("clone group exit"));
                } else {
                    p.connection.disconnect(net.minecraft.network.chat.Component.literal("clone group exit"));
                }
                n++;
            } catch (Throwable t) {
                LOGGER.error("[pry:clones] request exit for {} failed", group.name(), t);
            }
        }
        LOGGER.info("[pry:clones] requested exit for group {} ({} members)", group.name(), n);
        return n;
    }

    /**
     * 单体正常退出：只请求指定成员断连，不改组状态，其他成员和共享库存继续运行。
     *
     * @return 1 已请求；-1 组不在 ACTIVE 或成员不在线
     */
    public static int exitMember(MinecraftServer server, CloneGroup group, ServerPlayer member) {
        if (!group.isActive() || !group.members().containsValue(member)) {
            return -1;
        }
        if (!(member instanceof EntityPlayerMPFake fake)) {
            return -1;
        }
        try {
            fake.kill(net.minecraft.network.chat.Component.literal("clone member exit"));
            return 1;
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] request exit for {}#{} failed", group.name(),
                    CloneInstanceRegistry.cloneIdOf(member), t);
            return -1;
        }
    }

    /**
     * 撤销本次创建的半成品实例：直接断连（走完整 remove），跳过存档写盘。
     * 不用普通 kill 回滚，避免把失败状态写进权威存档。
     */
    public static void abortSpawn(MinecraftServer server, CloneGroup group, ServerPlayer player) {
        group.markRollingBack(player);
        try {
            if (player.connection != null) {
                player.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(
                        net.minecraft.network.chat.Component.literal("clone restore rollback")));
            } else {
                server.getPlayerList().remove(player);
            }
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] abort spawn disconnect failed", t);
            try {
                server.getPlayerList().remove(player);
            } catch (Throwable ignored) {
            }
        } finally {
            group.remove(player);
            CloneInstanceRegistry.remove(player);
            CloneDisplayIds.unregister(player);
            group.finishExitIfEmpty();
        }
    }

    private static void unsetRemoved(EntityPlayerMPFake instance) throws Exception {
        Method m = net.minecraft.world.entity.Entity.class.getDeclaredMethod("unsetRemoved");
        m.setAccessible(true);
        m.invoke(instance);
    }

    /** 组已有权威 profile 时复制完整属性；否则先用空属性，由首个 Carpet 成员补齐 */
    private static GameProfile buildProfile(CloneGroup group, UUID uuid, String name) {
        GameProfile source = group.profile();
        if (source == null) {
            return CloneVersionAdapter.newProfile(uuid, name);
        }
        // 统一组内显示名：后续成员沿用首个成员的权威 profile 名字（Carpet 可能改写大小写）
        String canonical = CloneVersionAdapter.profileName(source);
        return CloneVersionAdapter.copyProfile(source, uuid,
                canonical == null || canonical.isEmpty() ? name : canonical);
    }

    /** 在线实例 → 已有组 → 旧存档映射 → offline UUID（与 Carpet 身份解析一致） */
    public static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) {
            return p.getUUID();
        }
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g != null) {
            return g.uuid();
        }
        UUID mapped = net.minecraft.server.players.OldUsersConverter
                .convertMobOwnerIfNecessary(server, name);
        if (mapped != null) {
            return mapped;
        }
        return net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
    }
}
