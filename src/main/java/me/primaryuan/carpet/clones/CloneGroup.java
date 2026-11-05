package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分身组：一个 UUID/GameProfile 对应一个组，组内成员共享身份与背包。
 *
 * <p>主实例语义：在线成员中 cloneId 最小的成员是原版单值查询
 * （getPlayer(uuid) / getPlayerByName）的返回对象。组内精确定位一律走
 * {@link #instance(int)}。</p>
 *
 * <p>状态机：ACTIVE → EXITING（整组退出事务已启动）→ ACTIVE（成员清空）；
 * RESTORING 期间拒绝其他 spawn/rejoin。</p>
 */
public final class CloneGroup {

    public enum State {
        ACTIVE,
        EXITING,
        RESTORING
    }

    /** 组身份：与成员共享的 UUID */
    private final UUID uuid;
    /** 组内游戏名（=GameProfile.name，全部成员一致） */
    private final String name;
    /** cloneId（1 起）→ 在线成员；成员退出时移除，cloneId 不回收复用直到整组退出 */
    private final Map<Integer, ServerPlayer> members = new ConcurrentHashMap<>();
    /** placeNewPlayer 前预留编号（显示 UUID 必须在首个包之前确定） */
    private final Map<ServerPlayer, Integer> reservations =
            Collections.synchronizedMap(new IdentityHashMap<>());
    /** 本次恢复失败待撤销的实例：跳过存档写盘 */
    private final Set<ServerPlayer> rollingBack =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private int nextId = 1;
    private State state = State.ACTIVE;
    private SharedInventory inventory;
    private GameProfile profile;
    /** 整组退出前已显式保存一次权威 playerdata，退出过程不再逐实例写盘 */
    private boolean exitSaved;

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

    // ===== 编号预留 / 入组 =====

    /** 预留一个 cloneId（不消费重复调用；placeNewPlayer HEAD 使用） */
    public int reserve(ServerPlayer player) {
        Integer existing = reservations.get(player);
        if (existing != null) {
            return existing;
        }
        int id = nextId++;
        reservations.put(player, id);
        return id;
    }

    /** 指定编号预留（rejoin 恢复旧 cloneId/显示 UUID） */
    public int reserveExact(ServerPlayer player, int cloneId) {
        reservations.put(player, cloneId);
        nextId = Math.max(nextId, cloneId + 1);
        return cloneId;
    }

    /** 预留转正：登记成员并返回 cloneId */
    public int commit(ServerPlayer player) {
        Integer id = reservations.remove(player);
        if (id == null) {
            id = reserve(player);
            reservations.remove(player);
        }
        members.put(id, player);
        return id;
    }

    /** 放弃预留（placeNewPlayer 失败） */
    public void release(ServerPlayer player) {
        reservations.remove(player);
        rollingBack.remove(player);
    }

    /** 登记一个新上线的分身，返回分配的 cloneId */
    public int add(ServerPlayer player) {
        reserve(player);
        return commit(player);
    }

    /** 成员退出注销 */
    public void remove(ServerPlayer player) {
        members.values().remove(player);
        reservations.remove(player);
        rollingBack.remove(player);
    }

    /** 成员表只读视图（remove 钩子判定用） */
    public Map<Integer, ServerPlayer> members() {
        return Collections.unmodifiableMap(members);
    }

    public ServerPlayer instance(int cloneId) {
        return members.get(cloneId);
    }

    /** 在线成员（cloneId 升序）；1 号为主实例。isRemoved 成员（已退出未注销）被过滤 */
    public List<ServerPlayer> onlineMembers() {
        List<Integer> ids = new ArrayList<>(members.keySet());
        ids.sort(Integer::compareTo);
        List<ServerPlayer> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            ServerPlayer p = members.get(id);
            if (!p.isRemoved()) {
                out.add(p);
            }
        }
        return out;
    }

    /** 主实例：在线成员中 cloneId 最小者；组内无人在线返回 null（热路径无分配） */
    public ServerPlayer primary() {
        ServerPlayer best = null;
        int bestId = Integer.MAX_VALUE;
        for (Map.Entry<Integer, ServerPlayer> e : members.entrySet()) {
            if (e.getKey() < bestId && !e.getValue().isRemoved()) {
                best = e.getValue();
                bestId = e.getKey();
            }
        }
        return best;
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
        Integer reserved = reservations.get(player);
        return reserved == null ? -1 : reserved;
    }

    /** 预览下一个 cloneId（不消费） */
    public int peekNextId() {
        return nextId;
    }

    public void setNextId(int nextId) {
        this.nextId = Math.max(1, nextId);
    }

    public int onlineCount() {
        int n = 0;
        for (ServerPlayer p : members.values()) {
            if (!p.isRemoved()) {
                n++;
            }
        }
        return n;
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

    // ===== 状态机 =====

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /** 开始整组退出；非 ACTIVE 返回 false（重复 kill/恢复中一律拒绝） */
    public boolean beginExit() {
        if (state != State.ACTIVE) {
            return false;
        }
        state = State.EXITING;
        return true;
    }

    /** 成员全部离开后回到 ACTIVE（组记录保留供 rejoin） */
    public void finishExitIfEmpty() {
        if (members.isEmpty()) {
            state = State.ACTIVE;
            exitSaved = false;
        }
    }

    // ===== 共享库存与身份 =====

    public SharedInventory sharedInventory() {
        return inventory;
    }

    public void setSharedInventory(SharedInventory inventory) {
        this.inventory = inventory;
    }

    public GameProfile profile() {
        return profile;
    }

    /** 只记录首个成员解析出的完整 profile（含 textures），后续成员复用 */
    public void captureProfile(GameProfile profile) {
        if (this.profile == null && profile != null) {
            this.profile = profile;
        }
    }

    public boolean isExitSaved() {
        return exitSaved;
    }

    public void setExitSaved(boolean exitSaved) {
        this.exitSaved = exitSaved;
    }

    public void markRollingBack(ServerPlayer player) {
        rollingBack.add(player);
    }

    public boolean isRollingBack(ServerPlayer player) {
        return rollingBack.contains(player);
    }
}
