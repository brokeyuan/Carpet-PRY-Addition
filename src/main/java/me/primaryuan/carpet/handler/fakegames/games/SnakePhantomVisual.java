package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.replay.phantom.PryPackets;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 贪吃蛇的纯数据包幻影玩家：不进玩家列表、不创建真实实体/物品，只向本局观众
 * 发送 PlayerInfo + AddEntity + 移动/头部包；离开视野或结束发送移除包。
 *
 * <p>实体 ID 使用独立高位段；每个观众独立记录已发送状态，避免重复 AddEntity。
 * 观众离开/跨维度/退出时显式清理，不依赖客户端区块卸载。发送频率限制为每 2 tick
 * 一次，位置变化太小不发送。</p>
 */
final class SnakePhantomVisual {

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_600_000_000);
    private static final int SPAWN_RANGE = 64;
    private static final int REMOVE_RANGE = 80;
    private static final int MOVE_INTERVAL = 2;

    private static final class SentState {
        Vec3 pos;
        float yaw;
        float pitch;
    }

    private final int entityId;
    private final String uuid;
    private final String name = "Snake";
    private final PryPackets packets = PryPackets.create();
    private final Map<UUID, SentState> sent = new HashMap<>();
    private final Map<UUID, ServerPlayer> viewers = new HashMap<>();
    private Vec3 position;
    private float yaw;
    private float pitch;
    private int tickCounter;
    private boolean despawned;

    SnakePhantomVisual(Vec3 position) {
        this.entityId = NEXT_ENTITY_ID.getAndIncrement();
        this.uuid = UUID.nameUUIDFromBytes(("pry-snake-" + entityId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        this.position = position;
    }

    int entityId() {
        return entityId;
    }

    boolean isDespawned() {
        return despawned;
    }

    /** 每 tick 推进；audience 只包含本局在线成员。 */
    boolean tick(List<ServerPlayer> audience, Vec3 pos, float yaw, float pitch) {
        if (despawned) {
            return false;
        }
        this.position = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        tickCounter++;
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer viewer : audience) {
            if (viewer == null || viewer.hasDisconnected()) {
                continue;
            }
            UUID id = viewer.getUUID();
            present.add(id);
            viewers.put(id, viewer);
            SentState state = sent.get(id);
            double distanceSq = viewer.position().distanceToSqr(pos);
            if (state == null) {
                if (distanceSq <= (double) SPAWN_RANGE * SPAWN_RANGE) {
                    spawnTo(viewer);
                }
            } else if (distanceSq > (double) REMOVE_RANGE * REMOVE_RANGE) {
                removeFrom(viewer);
            } else if (tickCounter % MOVE_INTERVAL == 0) {
                sendMove(viewer, state);
            }
        }
        for (UUID id : new ArrayList<>(sent.keySet())) {
            if (!present.contains(id)) {
                ServerPlayer viewer = viewers.get(id);
                if (viewer != null && !viewer.hasDisconnected()) {
                    removeFrom(viewer);
                }
                sent.remove(id);
                viewers.remove(id);
            }
        }
        return true;
    }

    /** 玩家离开本局/掉线前调用：只清理该玩家收到的幻影。 */
    void forget(ServerPlayer viewer) {
        if (viewer != null) {
            removeFrom(viewer);
        }
    }

    /** 结束/取消：向所有仍记录的观众发送移除包。 */
    void despawn() {
        if (despawned) {
            return;
        }
        despawned = true;
        for (ServerPlayer viewer : new ArrayList<>(viewers.values())) {
            if (viewer != null && !viewer.hasDisconnected()) {
                removeFrom(viewer);
            }
        }
        sent.clear();
        viewers.clear();
    }

    private void spawnTo(ServerPlayer viewer) {
        send(viewer, packets.playerInfoAdd(uuid, name, "", null, "wide"));
        send(viewer, packets.addEntity(entityId, uuid, position.x, position.y, position.z,
                yaw, pitch, 0.0f, 0.0f, 0.0f));
        send(viewer, packets.entityMetadata(entityId, (byte) 0, "wide"));
        send(viewer, packets.rotateHead(entityId, yaw));
        SentState state = new SentState();
        state.pos = position;
        state.yaw = yaw;
        state.pitch = pitch;
        sent.put(viewer.getUUID(), state);
    }

    private void sendMove(ServerPlayer viewer, SentState state) {
        double dx = position.x - state.pos.x;
        double dy = position.y - state.pos.y;
        double dz = position.z - state.pos.z;
        if (dx * dx + dy * dy + dz * dz < 0.0004
                && Math.abs(yaw - state.yaw) < 0.5f
                && Math.abs(pitch - state.pitch) < 0.5f) {
            return;
        }
        send(viewer, packets.movePacket(entityId, dx, dy, dz,
                position.x, position.y, position.z, yaw, pitch, state.yaw, state.pitch, false));
        state.pos = position;
        state.yaw = yaw;
        state.pitch = pitch;
    }

    private void removeFrom(ServerPlayer viewer) {
        if (viewer == null) {
            return;
        }
        if (sent.remove(viewer.getUUID()) != null && !viewer.hasDisconnected()) {
            send(viewer, packets.removeEntities(entityId));
            send(viewer, packets.playerInfoRemove(uuid));
        }
        viewers.remove(viewer.getUUID());
    }

    private void send(ServerPlayer viewer, Packet<?> packet) {
        if (packet != null && viewer.connection != null && !viewer.hasDisconnected()) {
            viewer.connection.send(packet);
        }
    }
}
