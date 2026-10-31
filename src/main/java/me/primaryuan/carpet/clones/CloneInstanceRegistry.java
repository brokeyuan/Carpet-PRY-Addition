package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerPlayer;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * ServerPlayer 实例 → cloneId 索引（按对象身份，非 UUID——同组成员 UUID 相同，
 * 必须用 Identity 语义区分；分身脚本任务将来定位具体分身的查询接口）。
 *
 * <p>生命周期：spawn 分身成功后 put；分身退出（kill/断开）时 remove。
 * 查询不到（-1）即非分身或已退出。</p>
 */
public final class CloneInstanceRegistry {

    private static final Map<ServerPlayer, Integer> ID_BY_PLAYER =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    private CloneInstanceRegistry() {}

    public static void put(ServerPlayer player, int cloneId) {
        ID_BY_PLAYER.put(player, cloneId);
    }

    public static int cloneIdOf(ServerPlayer player) {
        Integer id = ID_BY_PLAYER.get(player);
        return id == null ? -1 : id;
    }

    public static void remove(ServerPlayer player) {
        ID_BY_PLAYER.remove(player);
    }

    public static void clear() {
        ID_BY_PLAYER.clear();
    }
}
