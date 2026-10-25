package me.primaryuan.carpet.handler.countdown;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 倒计时管理器：全局唯一活动会话的状态编排与生命周期挂接。
 *
 * <p>状态变更全部收口于此（命令处理器只做参数校验与消息反馈，tick 处理器只推进），
 * 避免多处修改彼此不一致。计时用 System.nanoTime 单调时钟，服务端主线程每 tick
 * 推进（经 ServerTickScheduler，会话回到 IDLE 后任务自动注销）；显示更新每秒一次，
 * 暂停期每 20 tick 重发冻结文本保持可见。完成提示阶段（COMPLETING）维持 2 秒后清理。</p>
 *
 * <p>清理路径全覆盖：自然完成、stop、规则关闭、服务器停止、渠道修改、对象修改、
 * 玩家退出。玩家加入/退出事件会因 fixBluemap mixin 对假人同样触发，处理时显式过滤。</p>
 */
public final class CountdownManager implements ServerTickScheduler.TickTask {

    /** 完成提示阶段时长（纳秒） */
    private static final long COMPLETION_WINDOW_NANOS = 2_000_000_000L;
    /** 暂停保活重发间隔（tick） */
    private static final int PAUSE_KEEPALIVE_TICKS = 20;

    private static final CountdownManager INSTANCE = new CountdownManager();

    private CountdownSession session;
    private final CountdownDisplay display = new CountdownDisplay();
    /** 上次刷新显示的整秒值（-1 = 强制下次刷新） */
    private int lastShownSecond = -1;
    private int pauseKeepaliveCounter;
    private Runnable tickHandle;
    private boolean registered;

    private CountdownManager() {}

    public static CountdownManager get() {
        return INSTANCE;
    }

    // ==================== 生命周期 ====================

    /** CarpetPrimaryuanServer.onGameStarted 调用（一次性）：停服清理 + 玩家加入/退出监听 */
    public void init() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> forceStop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onQuit(handler.player));
    }

    /** CarpetPrimaryuanServer 规则观察者调用：规则关闭时无条件清理 */
    public static void onRuleChanged() {
        if (!CarpetPrimaryuanSettings.countdown) {
            forceStop();
        }
    }

    // ==================== 会话操作（命令层入口） ====================

    /**
     * 启动倒计时。
     *
     * @return 0 成功；-1 已有活动倒计时
     */
    public int start(MinecraftServer server, long seconds, EnumSet<CountdownChannel> channels,
                     boolean dynamicAll, Set<UUID> snapshotTargets) {
        if (session != null) {
            return -1;
        }
        String label = ServerI18n.tr("carpetprimaryuan.countdown.default_label").getString();
        session = CountdownSession.start(seconds, channels, label, 5L, dynamicAll, snapshotTargets,
                System.nanoTime());
        pauseKeepaliveCounter = 0;
        display.applyChannels(channels);
        refreshNow(server);
        ensureTickTask();
        return 0;
    }

    /** @return 0 成功；-1 无活动倒计时或处于完成阶段；1 已处于暂停 */
    public int pause() {
        if (session == null || session.state() == CountdownSession.State.COMPLETING) {
            return -1;
        }
        if (session.state() != CountdownSession.State.RUNNING) {
            return 1;
        }
        session.pause(System.nanoTime());
        pauseKeepaliveCounter = 0;
        return 0;
    }

    /** @return 0 成功；-1 无活动倒计时或处于完成阶段；1 未暂停（正在运行） */
    public int resume() {
        if (session == null || session.state() == CountdownSession.State.COMPLETING) {
            return -1;
        }
        if (session.state() != CountdownSession.State.PAUSED) {
            return 1;
        }
        session.resume(System.nanoTime());
        return 0;
    }

    /** @return 是否原本有活动倒计时（已取消并清理） */
    public boolean stop() {
        if (session == null) {
            return false;
        }
        cleanup();
        return true;
    }

    /** @return 0 成功；-1 无活动倒计时或处于完成阶段 */
    public int setTime(MinecraftServer server, long seconds) {
        if (!isActiveCountdown()) {
            return -1;
        }
        session.setTime(seconds, System.nanoTime());
        refreshNow(server);
        return 0;
    }

    /** @return 0 成功；-1 无活动倒计时或处于完成阶段；-2 修改后超过 24 小时 */
    public int addTime(MinecraftServer server, long seconds) {
        if (!isActiveCountdown()) {
            return -1;
        }
        long now = System.nanoTime();
        long capNanos = CountdownTime.MAX_SECONDS * 1_000_000_000L;
        if (session.remainingNanos(now) + seconds * 1_000_000_000L > capNanos) {
            return -2;
        }
        session.addTime(seconds, now);
        refreshNow(server);
        return 0;
    }

    public int removeTime(MinecraftServer server, long seconds) {
        if (!isActiveCountdown()) {
            return -1;
        }
        boolean completed = session.removeTime(seconds, System.nanoTime());
        if (completed) {
            enterCompletion(server);
        } else {
            refreshNow(server);
        }
        return 0;
    }

    /** @return 0 成功；-1 无活动倒计时或处于完成阶段 */
    public int setDisplay(MinecraftServer server, EnumSet<CountdownChannel> channels) {
        if (!isActiveCountdown()) {
            return -1;
        }
        EnumSet<CountdownChannel> old = session.channels();
        EnumSet<CountdownChannel> removed = EnumSet.copyOf(old);
        removed.removeAll(channels);
        session.setChannels(channels);
        display.clearChannels(removed, audience(server));
        display.applyChannels(channels);
        refreshNow(server);
        return 0;
    }

    public int setTargets(MinecraftServer server, boolean dynamicAll, Set<UUID> snapshot) {
        if (!isActiveCountdown()) {
            return -1;
        }
        session.setTargets(dynamicAll, snapshot);
        refreshNow(server);
        return 0;
    }

    public int setLastWindow(MinecraftServer server, Long seconds) {
        if (!isActiveCountdown()) {
            return -1;
        }
        session.setLastWindow(seconds);
        refreshNow(server);
        return 0;
    }

    public int setLabel(MinecraftServer server, String label) {
        if (!isActiveCountdown()) {
            return -1;
        }
        session.setLabel(label);
        refreshNow(server);
        return 0;
    }

    // ==================== 状态查询（命令层反馈用） ====================

    public boolean hasActiveSession() {
        return session != null;
    }

    public CountdownSession.State state() {
        return session == null ? CountdownSession.State.IDLE : session.state();
    }

    /** 剩余整秒（无会话返回 0） */
    public int remainingCeilSeconds() {
        return session == null ? 0 : session.remainingCeilSeconds(System.nanoTime());
    }

    public long durationSeconds() {
        return session == null ? 0 : session.durationSeconds();
    }

    public EnumSet<CountdownChannel> channels() {
        return session == null ? EnumSet.noneOf(CountdownChannel.class) : session.channels();
    }

    public boolean dynamicAll() {
        return session != null && session.dynamicAll();
    }

    public int targetCount() {
        return session == null ? 0 : session.snapshotTargets().size();
    }

    // ==================== 事件处理 ====================

    private void onJoin(ServerPlayer player) {
        // fixBluemap mixin 会让假人触发本事件；假人永不为显示对象
        if (player instanceof EntityPlayerMPFake) {
            return;
        }
        if (session == null || session.state() == CountdownSession.State.COMPLETING) {
            return;
        }
        if (!session.shouldDisplay(player.getUUID())) {
            return;
        }
        display.viewerJoined(player, session, System.nanoTime());
    }

    private void onQuit(ServerPlayer player) {
        // 无会话时 BossBar 已清空，移除为 no-op；假人本就无观众身份
        display.viewerLeft(player);
    }

    // ==================== tick 推进 ====================

    @Override
    public boolean tick(MinecraftServer server) {
        CountdownSession s = session;
        if (s == null) {
            return false;
        }
        long now = System.nanoTime();
        switch (s.state()) {
            case RUNNING -> {
                int sec = s.advanceRunning(now);
                if (sec == -1) {
                    enterCompletion(server);
                } else if (sec != lastShownSecond) {
                    lastShownSecond = sec;
                    display.sendCountdown(s, now, audience(server));
                }
            }
            case PAUSED -> {
                // 保活重发冻结文本对抗 actionbar 淡出与 title 停留到期（BossBar 服务端持久，无需重发）
                if (++pauseKeepaliveCounter >= PAUSE_KEEPALIVE_TICKS) {
                    pauseKeepaliveCounter = 0;
                    display.sendCountdown(s, now, audience(server));
                }
            }
            case COMPLETING -> {
                if (now - s.completionAtNanos() >= COMPLETION_WINDOW_NANOS) {
                    cleanup();
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ==================== 内部 ====================

    private boolean isActiveCountdown() {
        return session != null && session.state() != CountdownSession.State.COMPLETING;
    }

    private void ensureTickTask() {
        if (tickHandle == null) {
            tickHandle = ServerTickScheduler.register(this);
        }
    }

    /** 立即刷新一次显示（启动与所有配置变更后调用） */
    private void refreshNow(MinecraftServer server) {
        if (session == null) {
            return;
        }
        long now = System.nanoTime();
        lastShownSecond = session.remainingCeilSeconds(now);
        display.sendCountdown(session, now, audience(server));
    }

    private void enterCompletion(MinecraftServer server) {
        if (session == null) {
            return;
        }
        lastShownSecond = -1;
        String doneText = ServerI18n.tr("carpetprimaryuan.countdown.finished").getString();
        display.showCompletion(session, audience(server), doneText);
    }

    /** 终局清理：回 IDLE 并移除本系统全部显示资源；下一 tick 任务自注销 */
    private void cleanup() {
        session = null;
        lastShownSecond = -1;
        pauseKeepaliveCounter = 0;
        display.hide();
        if (tickHandle != null) {
            tickHandle.run();
            tickHandle = null;
        }
    }

    /** 强制清理（规则关闭/停服）：任意状态回到 IDLE */
    public static void forceStop() {
        if (INSTANCE.session != null) {
            INSTANCE.cleanup();
        }
    }

    /** 当前观众：在线真人 ∩ 会话目标（动态全服 = 全部真人） */
    private List<ServerPlayer> audience(MinecraftServer server) {
        CountdownSession s = session;
        if (s == null || server == null) {
            return List.of();
        }
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake) {
                continue;
            }
            if (s.shouldDisplay(player.getUUID())) {
                out.add(player);
            }
        }
        return out;
    }
}
