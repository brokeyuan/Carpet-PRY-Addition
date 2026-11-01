package me.primaryuan.carpet.clones;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分身组注册表：UUID → 组。按名称查询走小写索引。
 *
 * <p>真人冲突保护：{@link #isOnlineRealPlayer} 判定该身份当前是否由真人
 * 占用（在线非假人玩家）。分身创建/登录保护都以此为准。</p>
 */
public final class CloneGroupRegistry {

    private static final Map<UUID, CloneGroup> BY_UUID = new ConcurrentHashMap<>();
    private static final Map<String, CloneGroup> BY_NAME = new ConcurrentHashMap<>();

    private CloneGroupRegistry() {}

    /** 取或建组；组存在时校验名称一致性 */
    public static CloneGroup getOrCreate(UUID uuid, String name) {
        return BY_UUID.computeIfAbsent(uuid, u -> {
            CloneGroup g = new CloneGroup(u, name);
            BY_NAME.put(name.toLowerCase(), g);
            return g;
        });
    }

        public static CloneGroup byUuid(UUID uuid) {
        return uuid == null ? null : BY_UUID.get(uuid);
    }

        public static CloneGroup byName(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase());
    }

    /** 组名反查（未建组返回 name 兜底） */
    public static String nameOf(UUID uuid) {
        CloneGroup g = BY_UUID.get(uuid);
        return g == null ? "unknown" : g.name();
    }

    public static boolean exists(UUID uuid) {
        return BY_UUID.containsKey(uuid);
    }

    /** 整组销毁（kill 后可选清理；组记录保留供 rejoin 时只清成员表） */
    public static void removeGroup(UUID uuid) {
        CloneGroup g = BY_UUID.remove(uuid);
        if (g != null) {
            BY_NAME.remove(g.name().toLowerCase());
        }
    }

    /** 全部组（/fakeclone list 用） */
    public static List<CloneGroup> all() {
        return new ArrayList<>(BY_UUID.values());
    }

    /** 该 UUID 当前在线的玩家里有没有真人（非 Carpet 假人） */
    public static boolean isOnlineRealPlayer(MinecraftServer server, UUID uuid) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(uuid) && !(p instanceof carpet.patches.EntityPlayerMPFake)) {
                return true;
            }
        }
        return false;
    }

    /** 停服清理 */
    public static void clear() {
        BY_UUID.clear();
        BY_NAME.clear();
    }
}
