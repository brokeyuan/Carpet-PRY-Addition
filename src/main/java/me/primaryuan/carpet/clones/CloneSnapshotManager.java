package me.primaryuan.carpet.clones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 分身组快照持久化（M4）：kill 时把每成员 cloneId/dim/pos/rot/gamemode 写入
 * world/playerdata/&lt;name&gt;-clones.json；背包本身由权威 playerdata 承载，
 * 只保存一次。rejoin 先全量校验再逐成员恢复，失败只撤销本次创建。
 */
public final class CloneSnapshotManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_VERSION = 2;
    private static boolean shutdownHook;

    private CloneSnapshotManager() {}

    /** 停服前为仍在线组落一次快照（此时世界仍可读，晚于此仅清内存） */
    public static void init() {
        if (shutdownHook) {
            return;
        }
        shutdownHook = true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (CloneGroup group : CloneGroupRegistry.all()) {
                if (group.onlineCount() > 0) {
                    save(server, group);
                }
            }
        });
    }

    public static final class MemberSnapshot {
        public int cloneId;
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
        public String gameMode;
    }

    public static final class GroupSnapshot {
        public int schemaVersion = SCHEMA_VERSION;
        public String name;
        public String uuid;
        public int nextCloneId = 1;
        public String skinValue;
        public String skinSignature;
        public List<MemberSnapshot> members = new ArrayList<>();
    }

    /** kill 全组前采集快照；返回 false 时调用方必须中止退出。 */
    public static boolean save(MinecraftServer server, CloneGroup group) {
        try {
            GroupSnapshot snap = new GroupSnapshot();
            snap.name = group.name();
            snap.uuid = group.uuid().toString();
            snap.nextCloneId = group.peekNextId();
            String[] texture = CloneVersionAdapter.profileTexture(group.profile());
            if (texture != null) {
                snap.skinValue = texture[0];
                snap.skinSignature = texture[1];
            }
            for (ServerPlayer p : group.onlineMembers()) {
                MemberSnapshot m = new MemberSnapshot();
                m.cloneId = CloneInstanceRegistry.cloneIdOf(p);
                m.dimension = p.level().dimension().identifier().toString();
                m.x = p.getX();
                m.y = p.getY();
                m.z = p.getZ();
                m.yaw = p.getYRot();
                m.pitch = p.getXRot();
                m.gameMode = p.gameMode.getGameModeForPlayer().getName();
                snap.members.add(m);
            }
            Path file = fileOf(server, group.name());
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(snap), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("[pry:clones] saved snapshot {} ({} members)", group.name(), snap.members.size());
            return true;
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] save snapshot {} failed", group.name(), t);
            return false;
        }
    }

    public static GroupSnapshot load(MinecraftServer server, String name) {
        try {
            Path file = fileOf(server, name);
            if (!Files.exists(file)) {
                return null;
            }
            GroupSnapshot snap = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    GroupSnapshot.class);
            if (snap == null || snap.members == null) {
                return null;
            }
            normalize(snap);
            return snap;
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] load snapshot {} failed", name, t);
            return null;
        }
    }

    /** 旧快照迁移：缺少 cloneId 时按原顺序分配，保证显示 UUID 可复算 */
    private static void normalize(GroupSnapshot snap) {
        int next = Math.max(1, snap.nextCloneId);
        Set<Integer> used = new HashSet<>();
        for (MemberSnapshot m : snap.members) {
            if (m.cloneId <= 0 || !used.add(m.cloneId)) {
                do {
                    m.cloneId = next++;
                } while (!used.add(m.cloneId));
            } else {
                next = Math.max(next, m.cloneId + 1);
            }
        }
        snap.nextCloneId = next;
        snap.schemaVersion = SCHEMA_VERSION;
    }

    /**
     * rejoin：返回恢复成员数，负数为拒绝原因：
     * -1 真人占用，-2 组在线/退出中，-3 某个成员创建失败（已撤销本次创建），
     * -4 快照数据/维度非法。
     */
    public static int rejoin(MinecraftServer server, GroupSnapshot snap) {
        if (snap == null || snap.name == null || snap.uuid == null || snap.members.isEmpty()) {
            return -4;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(snap.uuid);
        } catch (IllegalArgumentException e) {
            return -4;
        }
        if (CloneGroupRegistry.isOnlineRealPlayer(server, uuid)) {
            LOGGER.warn("[pry:clones] rejoin {} blocked: real player online", snap.name);
            return -1;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(uuid, snap.name);
        if (!group.isActive() || group.onlineCount() > 0) {
            LOGGER.warn("[pry:clones] rejoin {} blocked: group online/state={}", snap.name, group.state());
            return -2;
        }

        // 全量预检：任一维度/数据非法时不创建任何实体
        List<ServerLevel> levels = new ArrayList<>(snap.members.size());
        for (MemberSnapshot m : snap.members) {
            if (m == null || m.dimension == null || m.gameMode == null || !Double.isFinite(m.x)
                    || !Double.isFinite(m.y) || !Double.isFinite(m.z)
                    || !Double.isFinite(m.yaw) || !Double.isFinite(m.pitch)
                    || m.cloneId <= 0) {
                LOGGER.warn("[pry:clones] rejoin {} rejected: invalid member data", snap.name);
                return -4;
            }
            if (GameType.byName(m.gameMode) == null) {
                LOGGER.warn("[pry:clones] rejoin {} rejected: invalid gamemode {}", snap.name, m.gameMode);
                return -4;
            }
            ServerLevel level;
            try {
                level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                        Identifier.parse(m.dimension)));
            } catch (Throwable t) {
                level = null;
            }
            if (level == null) {
                LOGGER.warn("[pry:clones] rejoin {} rejected: dimension {} missing",
                        snap.name, m.dimension);
                return -4;
            }
            levels.add(level);
        }

        if (group.profile() == null && snap.skinValue != null) {
            group.captureProfile(CloneVersionAdapter.profileWithTexture(uuid, snap.name,
                    snap.skinValue, snap.skinSignature));
        }
        group.setState(CloneGroup.State.RESTORING);
        List<ServerPlayer> created = new ArrayList<>(snap.members.size());
        try {
            for (int i = 0; i < snap.members.size(); i++) {
                MemberSnapshot m = snap.members.get(i);
                ServerPlayer p = CloneSpawnManager.spawnClone(server, snap.name, levels.get(i),
                        new Vec3(m.x, m.y, m.z), m.yaw, m.pitch, m.cloneId);
                if (p == null) {
                    LOGGER.error("[pry:clones] rejoin {} failed at member #{}; rolling back {} created",
                            snap.name, m.cloneId, created.size());
                    rollback(server, group, created);
                    return -3;
                }
                applyGameMode(p, m.gameMode);
                created.add(p);
            }
            group.setNextId(Math.max(snap.nextCloneId, maxCloneId(snap) + 1));
            LOGGER.info("[pry:clones] rejoined {} ({}/{})", snap.name, created.size(), snap.members.size());
            return created.size();
        } finally {
            group.setState(CloneGroup.State.ACTIVE);
        }
    }

    private static int maxCloneId(GroupSnapshot snap) {
        int max = 0;
        for (MemberSnapshot m : snap.members) {
            max = Math.max(max, m.cloneId);
        }
        return max;
    }

    /** 只撤销本次创建的实例，不触碰原有组与权威存储 */
    private static void rollback(MinecraftServer server, CloneGroup group, List<ServerPlayer> created) {
        for (int i = created.size() - 1; i >= 0; i--) {
            CloneSpawnManager.abortSpawn(server, group, created.get(i));
        }
    }

    private static void applyGameMode(ServerPlayer p, String gameMode) {
        try {
            p.gameMode.changeGameModeForPlayer(GameType.byName(gameMode));
        } catch (Throwable ignored) {
        }
    }

    private static Path fileOf(MinecraftServer server, String name) {
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldDir.resolve("playerdata").resolve(sanitize(name) + "-clones.json");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
