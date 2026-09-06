package me.primaryuan.carpet.util;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 假人操作会话管理器（/tpp 传送流程与 /tppset spawn 延迟下线共用）：
 * tick 状态机替代裸线程，避免线程泄漏与跨线程引用。
 *
 * 流程（tick 状态机）：
 * - 传送：rejoin → 等待上线 → use×N → 等待传送完成 → kill
 * - spawn：spawn → 等待停留时间 → kill
 *
 * 同一发起者同时只允许一个进行中的会话；所有状态仅在服务器主线程访问；
 * 服务器停止时放弃全部会话（假人随服务器一起断开，无需 kill）。
 */
public final class FakePlayerSessionManager {

    /** 等待假人上线的超时（tick）：10 秒 */
    private static final int JOIN_TIMEOUT_TICKS = 200;
    /** 两次 use 的间隔（tick）：0.5 秒 */
    private static final int USE_INTERVAL_TICKS = 10;
    /** use 全部完成后等待传送完成的时间（tick）：3 秒 */
    private static final int TELEPORT_SETTLE_TICKS = 60;
    /** tppset spawn 后假人停留时间（tick）：3 秒 */
    private static final int SPAWN_LINGER_TICKS = 60;

    /** 会话阶段 */
    private enum Phase {
        /** /tpp：已 rejoin，等待假人上线 */
        WAIT_JOIN,
        /** /tpp：逐次 use 中 */
        USE,
        /** /tpp：use 完成，等待传送完成后 kill */
        WAIT_TELEPORT,
        /** /tppset spawn：等待停留时间结束后 kill */
        WAIT_KILL
    }

    /** 单次假人操作会话：/tpp 传送流程或 /tppset spawn 的延迟下线 */
    private static final class Session {
        final ServerPlayer player;        // 发起者（接收反馈消息）
        final String fakePlayerName;
        final String stationDisplayName;  // /tpp 使用；spawn 会话为 null
        final int totalUses;              // /tpp 使用；spawn 会话为 0
        Phase phase;
        int ticksInPhase = 0;
        int usesDone = 0;

        Session(ServerPlayer player, String fakePlayerName, String stationDisplayName,
                int totalUses, Phase phase) {
            this.player = player;
            this.fakePlayerName = fakePlayerName;
            this.stationDisplayName = stationDisplayName;
            this.totalUses = totalUses;
            this.phase = phase;
        }
    }

    /** 发起者 UUID → 进行中的会话 */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static boolean registered = false;

    private FakePlayerSessionManager() {}

    /** 同一玩家是否已有进行中的假人操作 */
    public static boolean hasActiveSession(ServerPlayer initiator) {
        return SESSIONS.containsKey(initiator.getUUID());
    }

    /** 启动 /tpp 传送会话：立即以发起者身份 rejoin 假人，随后按状态机 use×N 后 kill */
    public static void startTeleport(MinecraftServer server, ServerPlayer initiator,
                                     String fakePlayerName, String stationDisplayName, int totalUses) {
        ensureRegistered();
        performPlayerCommand(server, initiator, fakePlayerName, "rejoin");
        SESSIONS.put(initiator.getUUID(), new Session(initiator, fakePlayerName, stationDisplayName, totalUses, Phase.WAIT_JOIN));
    }

    /** 启动 /tppset spawn 会话：立即以发起者身份生成假人，停留 SPAWN_LINGER_TICKS 后自动下线 */
    public static void startSpawn(MinecraftServer server, ServerPlayer initiator, String fakePlayerName) {
        ensureRegistered();
        performPlayerCommand(server, initiator, fakePlayerName, "spawn");
        SESSIONS.put(initiator.getUUID(), new Session(initiator, fakePlayerName, null, 0, Phase.WAIT_KILL));
    }

    /** 惰性注册 tick 状态机与服务器停止清理 */
    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        ServerTickScheduler.register(server -> {
            if (SESSIONS.isEmpty()) return true;
            Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
            while (it.hasNext()) {
                if (!tickSession(server, it.next().getValue())) {
                    it.remove();
                }
            }
            return true;
        });
        // 服务器停止时放弃所有会话（假人随服务器一起断开，无需 kill）
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.clear());
    }

    /**
     * 推进单个会话状态机（每 tick 调用一次，位于服务器主线程）。
     *
     * @return false 表示会话已结束（从表中移除）
     */
    private static boolean tickSession(MinecraftServer server, Session session) {
        session.ticksInPhase++;

        switch (session.phase) {
            case WAIT_JOIN -> {
                if (isPlayerOnline(server, session.fakePlayerName)) {
                    // 假人已上线：立即执行第一次 use
                    performPlayerCommand(server, session.player, session.fakePlayerName, "use");
                    session.usesDone = 1;
                    session.ticksInPhase = 0;
                    session.phase = session.usesDone >= session.totalUses
                            ? Phase.WAIT_TELEPORT : Phase.USE;
                } else if (session.ticksInPhase >= JOIN_TIMEOUT_TICKS) {
                    sendFeedback(session.player,
                            "carpetprimaryuan.command.tpp.teleport_failed", session.fakePlayerName);
                    return false;
                }
            }
            case USE -> {
                if (session.ticksInPhase >= USE_INTERVAL_TICKS) {
                    performPlayerCommand(server, session.player, session.fakePlayerName, "use");
                    session.usesDone++;
                    session.ticksInPhase = 0;
                    if (session.usesDone >= session.totalUses) {
                        session.phase = Phase.WAIT_TELEPORT;
                    }
                }
            }
            case WAIT_TELEPORT -> {
                if (session.ticksInPhase >= TELEPORT_SETTLE_TICKS) {
                    performConsoleKill(server, session.fakePlayerName);
                    sendFeedback(session.player,
                            "carpetprimaryuan.command.tpp.teleport_complete", session.stationDisplayName);
                    return false;
                }
            }
            case WAIT_KILL -> {
                if (session.ticksInPhase >= SPAWN_LINGER_TICKS) {
                    performConsoleKill(server, session.fakePlayerName);
                    return false;
                }
            }
        }
        return true;
    }

    /** 以发起玩家身份执行 /player <name> <action> */
    private static void performPlayerCommand(MinecraftServer server, ServerPlayer player,
                                             String fakePlayerName, String action) {
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack(),
                "/player " + fakePlayerName + " " + action
        );
    }

    /** 以控制台身份执行 /player <name> kill（避免权限问题，让 Carpet 自然处理断开流程） */
    private static void performConsoleKill(MinecraftServer server, String fakePlayerName) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "/player " + fakePlayerName + " kill"
        );
    }

    /** 向发起者发送反馈（已断线/移除时静默跳过） */
    private static void sendFeedback(ServerPlayer player, String key, Object... args) {
        if (!player.hasDisconnected() && !player.isRemoved()) {
            player.sendSystemMessage(ServerI18n.tr(key, args));
        }
    }

    /** 检查指定名称的玩家是否在线（封装跨版本 PlayerList#getPlayer 差异） */
    private static boolean isPlayerOnline(MinecraftServer server, String name) {
        //#if MC < 12110
        //$$ // 早期版本 getPlayer(String) 返回 UUID，需遍历在线列表按名称匹配
        //$$ return server.getPlayerList().getPlayers().stream()
        //$$         .anyMatch(p -> p.getGameProfile().getName().equals(name));
        //#else
        return server.getPlayerList().getPlayer(name) != null;
        //#endif
    }
}
