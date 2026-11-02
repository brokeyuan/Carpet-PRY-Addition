package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分身显示 UUID 注册表（纯服务端客户端显示方案）。
 *
 * <p>服务端真实身份 UUID 不变（存档/库存/权限归属）；#1 使用真实 UUID
 * （Carpet 首个假人在入组前已按真实 UUID 发包），其余分身派生稳定显示 UUID：
 * nameUUIDFromBytes(真实UUID + "#clone#" + cloneId)。cloneId 持久化在快照中，
 * 重启/恢复后显示 UUID 不变。</p>
 */
public final class CloneDisplayIds {

    /** ServerPlayer 实例（Identity 语义）→ 显示 UUID */
    private static final Map<ServerPlayer, UUID> BY_PLAYER =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** 显示 UUID → 真实 UUID（包改写回查用） */
    private static final Map<UUID, UUID> REAL_BY_DISPLAY = new ConcurrentHashMap<>();

    /** 当前正在 PlayerList.remove 中的分身（Remove 包构造上下文；服务端线程） */
    private static ServerPlayer removing;

    private CloneDisplayIds() {}

    /** spawn 流程在 placeNewPlayer 之前调用（必须早于任何 PlayerInfo/AddEntity 下发） */
    public static void register(ServerPlayer player, UUID realUuid, int cloneId) {
        UUID display = cloneId <= 1 ? realUuid : derive(realUuid, cloneId);
        BY_PLAYER.put(player, display);
        if (!display.equals(realUuid)) {
            REAL_BY_DISPLAY.put(display, realUuid);
        }
    }

    /** 实例注销（PlayerList.remove 完成后调用） */
    public static void unregister(ServerPlayer player) {
        UUID display = BY_PLAYER.remove(player);
        if (display != null) {
            REAL_BY_DISPLAY.remove(display);
        }
    }

    /** PlayerList.remove 期间建立精确的实例上下文（同一 UUID 多实例不会串包） */
    public static void beginRemoval(ServerPlayer player) {
        removing = player;
    }

    public static void endRemoval() {
        removing = null;
    }

    /** 当前 remove 实例的显示 UUID；真实 UUID 不匹配或无上下文返回 null */
    public static UUID displayForRemoval(UUID realUuid) {
        ServerPlayer player = removing;
        if (player == null || !player.getUUID().equals(realUuid)) {
            return null;
        }
        UUID display = BY_PLAYER.get(player);
        return display != null && !display.equals(realUuid) ? display : null;
    }

    public static UUID displayOf(ServerPlayer player) {
        return BY_PLAYER.get(player);
    }

    /** 该实例是否登记为分身 */
    public static boolean isClone(ServerPlayer player) {
        return BY_PLAYER.containsKey(player);
    }

    /** 稳定派生：cloneId 不变则显示 UUID 重启后一致 */
    public static UUID derive(UUID realUuid, int cloneId) {
        return UUID.nameUUIDFromBytes(
                (realUuid + "#clone#" + cloneId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 显示 UUID → 真实 UUID；非分身显示 UUID 返回 null */
    public static UUID realOf(UUID display) {
        return REAL_BY_DISPLAY.get(display);
    }

    /** 停服/换世界清理全部静态映射 */
    public static void clear() {
        BY_PLAYER.clear();
        REAL_BY_DISPLAY.clear();
        removing = null;
    }
}
