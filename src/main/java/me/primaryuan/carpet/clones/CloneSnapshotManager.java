package me.primaryuan.carpet.clones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

/**
 * 分身组快照持久化（M4）：kill 时把每成员 dim/pos/rot/gamemode 与一份共享
 * 背包写入 world/playerdata/<name>-clones.json（独立文件，不写原版
 * per-UUID 存档——多实例写同一文件会互覆，M0 实证）；rejoin 时读快照
 * 逐成员恢复。服务器重启后不自动生成，仅 /fakeclone rejoin 手动恢复。
 */
public final class CloneSnapshotManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CloneSnapshotManager() {}

    public static final class MemberSnapshot {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
        public String gameMode;
    }

    public static final class GroupSnapshot {
        public String name;
        public String uuid;
        public List<MemberSnapshot> members = new ArrayList<>();
    }

    /** kill 全组前采集快照 */
    public static boolean save(MinecraftServer server, CloneGroup group) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return false;
        }
        try {
            GroupSnapshot snap = new GroupSnapshot();
            snap.name = group.name();
            snap.uuid = group.uuid().toString();
            ServerPlayer primary = group.primary();
            List<ServerPlayer> members = group.onlineMembers();
            for (ServerPlayer p : members) {
                MemberSnapshot m = new MemberSnapshot();
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
            Files.writeString(file, GSON.toJson(snap), StandardCharsets.UTF_8);
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
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GroupSnapshot.class);
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] load snapshot {} failed", name, t);
            return null;
        }
    }

    /** rejoin：按快照逐成员恢复；返回恢复的成员数 */
    public static int rejoin(MinecraftServer server, GroupSnapshot snap) {
        UUID uuid = UUID.fromString(snap.uuid);
        // 真人冲突保护
        if (CloneGroupRegistry.isOnlineRealPlayer(server, uuid)) {
            LOGGER.warn("[pry:clones] rejoin {} blocked: real player online", snap.name);
            return -1;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(uuid, snap.name);
        int restored = 0;
        for (MemberSnapshot m : snap.members) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    Identifier.parse(m.dimension)));
            if (level == null) {
                LOGGER.warn("[pry:clones] rejoin {} member dim {} missing, skip", snap.name, m.dimension);
                continue;
            }
            ServerPlayer p = CloneSpawnManager.spawnClone(server, snap.name, level,
                    new Vec3(m.x, m.y, m.z), m.yaw, m.pitch);
            if (p != null) {
                applyGameMode(p, m.gameMode);
                restored++;
            }
        }
        if (restored > 0) {
            // 背包持久化走主实例原版 playerdata（kill 时 PlayerList.remove->save
            // 写入全部组件；首个成员 spawn 时 loadPlayerData 读回），随后组内分发
            ServerPlayer primary = group.primary();
            if (primary != null) {
                SharedInventoryManager.syncGroup(primary);
                SharedInventoryManager.syncEquipment(primary);
            }
        }
        LOGGER.info("[pry:clones] rejoined {} ({}/{})", snap.name, restored, snap.members.size());
        return restored;
    }

    private static void applyGameMode(ServerPlayer p, String gameMode) {
        try {
            GameType gt = GameType.byName(gameMode);
            p.gameMode.changeGameModeForPlayer(gt);
        } catch (Throwable ignored) {
        }
    }

    private static Path fileOf(MinecraftServer server, String name) {
        // world/playerdata/<name>-clones.json
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldDir.resolve("playerdata").resolve(sanitize(name) + "-clones.json");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

}
