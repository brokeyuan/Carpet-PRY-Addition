package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * 假游戏管理器：全局唯一活动会话（默认同服一场）的生命周期编排。
 *
 * <p>tick 任务仅在会话存在期间注册（规则关闭不跑任何逻辑），会话结束自动注销。
 * 清理路径全覆盖：自然结束、取消、规则关闭、服务器停止、玩家掉线。会话只存内存，
 * 重启后一切未完成游戏视为结束，不做持久化。</p>
 */
public final class FakeGameManager implements ServerTickScheduler.TickTask {
    private static final FakeGameManager INSTANCE = new FakeGameManager();
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    private GameSession session;
    private Runnable tickHandle;
    private boolean initialized;

    private FakeGameManager() {}

    public static FakeGameManager get() {
        return INSTANCE;
    }

    /** CarpetPrimaryuanServer.onGameStarted 调用（一次性）：停服清理 + 掉线/攻击监听 */
    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown("cancel_server_stop"));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onQuit(handler.player));
        // 有效攻击命中：仅用于热土豆等以攻击为交互的玩法；不取消任何原版伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer victim)
                    || !(source.getEntity() instanceof ServerPlayer attacker)
                    || attacker == victim) {
                return true;
            }
            GameSession active = INSTANCE.session;
            if (active != null && active.phase == SessionPhase.RUNNING
                    && attacker.distanceToSqr(victim) <= 36.0
                    && attacker.hasLineOfSight(victim)) {
                try {
                    active.onAttack(attacker, victim);
                } catch (Throwable t) {
                    LOGGER.error("[FakeGames] onAttack failed", t);
                }
            }
            return true;
        });
    }

    /** 规则观察者调用：规则关闭时安全结束进行中的游戏并清空状态 */
    public static void onRuleChanged() {
        if (!CarpetPrimaryuanSettings.fakeGames) {
            INSTANCE.endIfActive("cancel_rule");
        }
    }

    /** 局内攻击是否应绕过和平玩家 PVP 拦截：同一局、双方都是存活成员。 */
    public static boolean allowsPvp(ServerPlayer attacker, ServerPlayer victim) {
        GameSession active = INSTANCE.session;
        if (active == null || active.phase != SessionPhase.RUNNING) {
            return false;
        }
        GameMember a = active.member(attacker.getUUID());
        GameMember v = active.member(victim.getUUID());
        return a != null && v != null && a.state == MemberState.ACTIVE && v.state == MemberState.ACTIVE;
    }

    public GameSession current() {
        return session;
    }

    /** 发起游戏：以发起者为圆心建立等待中的会话；发起者资格不合格时不建会话 */
    public GameSession start(ServerPlayer host, GameType type) {
        return start(host, type, null, null);
    }

    public GameSession start(ServerPlayer host, GameType type, String presetName,
                             Map<String, String> initialOptions) {
        if (session != null && session.phase != SessionPhase.FINISHED) {
            return null;
        }
        if (type.unavailableKey() != null) {
            return null;
        }
        GameSession candidate = new GameSession(type, host, GameLogic.create(type),
                id -> {
                    MinecraftServer server = host.level().getServer();
                    return server == null ? null : server.getPlayerList().getPlayer(id);
                }, presetName, null, initialOptions);
        if (!candidate.addPlayer(host)) {
            return null;
        }
        session = candidate;
        if (tickHandle == null) {
            tickHandle = ServerTickScheduler.register(this);
        }
        return session;
    }

    public boolean join(ServerPlayer player) {
        if (session == null) {
            return false;
        }
        return join(session.id, player);
    }

    /** 按场次 ID 加入；ID 不匹配视为旧邀请。 */
    public boolean join(String sessionId, ServerPlayer player) {
        GameSession active = session;
        if (active == null || active.phase == SessionPhase.FINISHED || !active.id.equals(sessionId)) {
            return false;
        }
        return active.addPlayer(player);
    }

    /** 主动退出 */
    public boolean leave(ServerPlayer player) {
        GameSession active = session;
        return active != null && active.phase != SessionPhase.FINISHED
                && active.removePlayer(player, "carpetprimaryuan.fakegames.left");
    }

    public boolean leave(String sessionId, ServerPlayer player) {
        GameSession active = session;
        if (active == null || active.phase == SessionPhase.FINISHED || !active.id.equals(sessionId)) {
            return false;
        }
        return active.removePlayer(player, "carpetprimaryuan.fakegames.left");
    }

    /** 发起者/管理员点击开始；返回错误代码，null 表示成功进入倒计时。 */
    public String begin(String sessionId, ServerPlayer actor, boolean admin) {
        GameSession active = session;
        if (active == null || active.phase == SessionPhase.FINISHED) {
            return "no_game";
        }
        if (!active.id.equals(sessionId)) {
            return "stale_session";
        }
        return active.begin(actor, admin);
    }

    public boolean setOption(String sessionId, ServerPlayer actor, boolean admin,
                             String key, String value) {
        GameSession active = session;
        if (active == null || active.phase != SessionPhase.WAITING || !active.id.equals(sessionId)) {
            return false;
        }
        if (!admin && !active.isHost(actor.getUUID())) {
            return false;
        }
        return active.setOption(key, value);
    }

    /** 结束当前游戏（发起者/管理员），返回是否确有游戏被结束 */
    public boolean stop(String reasonKey) {
        return endIfActive(reasonKey);
    }

    /** 按场次 ID 结束；非发起者且非管理员拒绝。 */
    public boolean cancel(String sessionId, ServerPlayer actor, boolean admin, String reasonKey) {
        GameSession active = session;
        if (active == null || active.phase == SessionPhase.FINISHED || !active.id.equals(sessionId)) {
            return false;
        }
        if (!admin && !active.isHost(actor.getUUID())) {
            return false;
        }
        active.cancel(reasonKey);
        return true;
    }

    public GameSession byId(String sessionId) {
        GameSession active = session;
        return active != null && active.id.equals(sessionId) ? active : null;
    }

    public boolean submitGuess(ServerPlayer player, String message) {
        if (session == null || session.phase != SessionPhase.RUNNING) {
            return false;
        }
        return session.tryGuess(player, message);
    }

    private boolean endIfActive(String reasonKey) {
        if (session == null || session.phase == SessionPhase.FINISHED) {
            return false;
        }
        session.cancel(reasonKey);
        return true;
    }

    /** 玩家掉线：退出本局；最后一人都离开时游戏自然终止 */
    private void onQuit(ServerPlayer player) {
        if (session == null || session.phase == SessionPhase.FINISHED) {
            return;
        }
        session.removePlayer(player, null);
        if (session.members.values().stream().noneMatch(m -> m.state != MemberState.LEFT)) {
            session.cancel("cancel_all_left");
        }
    }

    /** 停服：先走玩法结束/清理，再断开 tick。 */
    private void shutdown(String reasonKey) {
        GameSession active = session;
        if (active != null) {
            try {
                active.shutdown();
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] shutdown failed", t);
            }
        }
        release();
    }

    private void clear() {
        shutdown("cancel_server_stop");
    }

    @Override
    public boolean tick(MinecraftServer server) {
        GameSession active = session;
        if (active != null && CarpetPrimaryuanSettings.fakeGames) {
            try {
                active.tick(server);
            } catch (Throwable e) {
                LOGGER.error("[FakeGameManager] Session tick crashed; cancelling", e);
                try {
                    active.cancel("cancel_error");
                } catch (Throwable nested) {
                    LOGGER.error("[FakeGameManager] Session cancel also failed", nested);
                }
                try {
                    active.shutdown();
                } catch (Throwable nested) {
                    LOGGER.error("[FakeGameManager] Session shutdown also failed", nested);
                }
                release();
                return false;
            }
        }
        if (session == null || session.phase == SessionPhase.FINISHED) {
            release();
            return false;
        }
        return true;
    }

    private void release() {
        session = null;
        if (tickHandle != null) {
            tickHandle.run();
            tickHandle = null;
        }
    }
}
