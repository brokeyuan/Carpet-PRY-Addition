package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.IdentityHashMap;

/**
 * 分身显示 UUID 注册表（纯服务端客户端显示方案）。
 *
 * <p>服务端真实身份 UUID 不变（存档/库存/权限归属）；每个分身派生一个
 * 稳定的"显示 UUID"仅用于客户端区分身体：nameUUIDFromBytes(真实UUID + "#" + cloneId)。
 * cloneId 稳定 → 显示 UUID 重启后可复算，无需持久化。</p>
 *
 * <p>客户端核实结论（26.3 客户端 jar javap，★★★）：
 * ClientLevel.addEntity → TransientEntitySectionManager.addEntity →
 * EntityLookup.add 按 byUuid 去重——显示 UUID 不同即可同屏多实体；
 * handleAddEntity 的 player 分支要求先收到对应 UUID 的 PlayerInfo，
 * 否则 "Server attempted to add player prior to sending player info" 直接丢弃；
 * RemotePlayer 皮肤取自 PlayerInfo 包内 profile.properties（textures），
 * 不回查 sessionserver——显示 UUID 携带复制 textures 即皮肤正确。</p>
 */
public final class CloneDisplayIds {

    /** ServerPlayer 实例（Identity 语义）→ 显示 UUID */
    private static final Map<ServerPlayer, UUID> BY_PLAYER =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    /** 显示 UUID → 真实 UUID（包改写回查用） */
    private static final Map<UUID, UUID> REAL_BY_DISPLAY = new ConcurrentHashMap<>();

    /** 退出中实例的真实 UUID → 显示 UUID（Remove 包改写用；注销即缓存，包发送在其后） */
    private static final Map<UUID, UUID> EXITING = new ConcurrentHashMap<>();

    private CloneDisplayIds() {}

    /** spawn 流程在 placeNewPlayer 之前调用（必须早于任何 PlayerInfo/AddEntity 下发） */
    public static void register(ServerPlayer player, UUID realUuid, int cloneId) {
        UUID display = UUID.nameUUIDFromBytes((realUuid + "#clone#" + cloneId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BY_PLAYER.put(player, display);
        REAL_BY_DISPLAY.put(display, realUuid);
    }

    /** 实例注销（PlayerList.remove 钩子调用） */
    public static void unregister(ServerPlayer player) {
        UUID display = BY_PLAYER.remove(player);
        if (display != null) {
            REAL_BY_DISPLAY.remove(display);
        }
    }

    /** PlayerList.remove 钩子：实例注销前缓存显示 UUID（供其 Remove 包改写） */
    public static void markExiting(ServerPlayer player) {
        UUID display = BY_PLAYER.remove(player);
        if (display != null) {
            EXITING.put(player.getUUID(), display);
        }
    }

    /** 真实 UUID → 退出中实例的显示 UUID；无则 null */
    public static UUID exitingDisplay(UUID realUuid) {
        return EXITING.get(realUuid);
    }

    public static void clearExiting(UUID realUuid) {
        EXITING.remove(realUuid);
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

    /** 由真实 UUID 反查主实例的显示 UUID（无实例上下文的包路径用） */
    public static UUID displayOfPrimary(UUID realUuid, Iterable<ServerPlayer> candidates) {
        ServerPlayer primary = null;
        for (ServerPlayer p : candidates) {
            if (p.getUUID().equals(realUuid) && BY_PLAYER.containsKey(p)) {
                if (primary == null || CloneInstanceRegistry.cloneIdOf(p) < CloneInstanceRegistry.cloneIdOf(primary)) {
                    primary = p;
                }
            }
        }
        return primary == null ? null : BY_PLAYER.get(primary);
    }

    /** 显示 UUID → 真实 UUID；非分身显示 UUID 返回 null */
    public static UUID realOf(UUID display) {
        return REAL_BY_DISPLAY.get(display);
    }

    public static void clear() {
        BY_PLAYER.clear();
        REAL_BY_DISPLAY.clear();
    }
}
