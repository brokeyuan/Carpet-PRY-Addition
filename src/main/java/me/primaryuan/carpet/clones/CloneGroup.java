package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分身组：一个 UUID/GameProfile 对应一个组，组内成员共享身份与背包（M3）。
 *
 * <p>主实例语义：在线成员中 cloneId 最小的成员是原版单值查询
 * （getPlayer(uuid) / getPlayerByName）的返回对象。原版单值接口的行为
 * 不因分身改变；组内精确定位一律走 {@link #instance(int)}。</p>
 */
public final class CloneGroup {

    /** 组身份：与成员共享的 UUID */
    private final UUID uuid;
    /** 组内游戏名（=GameProfile.name，全部成员一致） */
    private final String name;
    /** cloneId（1 起）→ 在线成员；成员退出时移除，cloneId 不回收复用直到整组退出 */
    private final Map<Integer, ServerPlayer> members = new ConcurrentHashMap<>();
    /** 下一个 cloneId */
    private int nextId = 1;

    public CloneGroup(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** 登记一个新上线的分身，返回分配的 cloneId */
    public int add(ServerPlayer player) {
        int id = nextId++;
        members.put(id, player);
        return id;
    }

    /** 成员退出注销 */
    public void remove(ServerPlayer player) {
        members.values().remove(player);
    }

    public ServerPlayer instance(int cloneId) {
        return members.get(cloneId);
    }

    /** 在线成员（cloneId 升序）；1 号为主实例 */
    public List<ServerPlayer> onlineMembers() {
        List<Integer> ids = new ArrayList<>(members.keySet());
        ids.sort(Integer::compareTo);
        List<ServerPlayer> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            out.add(members.get(id));
        }
        return out;
    }

    /** 主实例：在线成员中 cloneId 最小者；组内无人在线返回 null */
    public ServerPlayer primary() {
        List<ServerPlayer> all = onlineMembers();
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 该玩家是否为组内非主实例（存档写盘跳过、kill 语义判断用）。
     * 不是本组成员或它就是主实例时返回 false。
     */
    public boolean isNonPrimary(ServerPlayer player) {
        ServerPlayer primary = primary();
        return primary != null && primary != player && members.containsValue(player);
    }

    /** 该玩家所属 cloneId；不属于返回 -1 */
    public int cloneIdOf(ServerPlayer player) {
        for (Map.Entry<Integer, ServerPlayer> e : members.entrySet()) {
            if (e.getValue() == player) {
                return e.getKey();
            }
        }
        return -1;
    }

    public int onlineCount() {
        return members.size();
    }

    /** 组是否全部离线（组记录保留供 rejoin，成员表已清空） */
    public boolean allOffline() {
        return members.isEmpty();
    }

    /** 成员里是否全是 Carpet 假人（回放幻影等非 EntityPlayerMPFake 一律不得入组） */
    public boolean allFake(MinecraftServer server) {
        for (ServerPlayer p : onlineMembers()) {
            if (!(p instanceof EntityPlayerMPFake)) {
                return false;
            }
        }
        return true;
    }
}
