package me.primaryuan.carpet.handler.countdown;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 计时管理器：全局唯一活动会话的状态编排与生命周期挂接（正/倒计时共用）。
 *
 * <p>状态变更全部收口于此（命令处理器只做参数校验与消息反馈，tick 处理器只推进），
 * 避免多处修改彼此不一致。计时用 System.nanoTime 单调时钟，服务端主线程每 tick
 * 推进（经 ServerTickScheduler，会话结束后任务自动注销）。DOWN 文本每整秒刷新、
 * BossBar 进度 10 Hz；UP 文本最多 10 Hz（十分之一秒粒度）；暂停与结果展示每秒
 * 保活。显示事件（开始/暂停/恢复/节点/结果）带 2~3 秒一次性窗口，中央阶段按
 * 结果 &gt; 暂停 &gt; 最后数字 &gt; 闪烁 &gt; 常态的优先级单一呈现。</p>
 *
 * <p>清理路径全覆盖：自然归零、手动停止、规则关闭、服务器停止、布局/渠道修改、
 * 对象修改、玩家退出。玩家加入/退出事件会因 fixBluemap mixin 对假人同样触发，
 * 处理时显式过滤。最后一份结束结果仅存内存（/timer status 查看），重启清除。</p>
 */
public final class CountdownManager implements ServerTickScheduler.TickTask {

    /** 新入口结果展示窗口（纳秒） */
    private static final long RESULT_WINDOW_NANOS = 3_000_000_000L;
    /** 旧入口完成提示窗口（纳秒） */
    private static final long LEGACY_COMPLETION_NANOS = 2_000_000_000L;
    /** 开始/暂停/恢复/节点一次性闪烁窗口（纳秒，约 2 秒） */
    private static final long FLASH_WINDOW_NANOS = 2_000_000_000L;
    /** 暂停/结果保活重发间隔（tick，约 1 秒） */
    private static final int PAUSE_KEEPALIVE_TICKS = 20;
    /** BossBar 进度刷新间隔（tick，10 Hz） */
    private static final int PROGRESS_REFRESH_TICKS = 2;

    private static final CountdownManager INSTANCE = new CountdownManager();

    private CountdownSession session;
    private final CountdownDisplay display = new CountdownDisplay();
    private final TimerPlanner.TimerTexts texts = new PlannerTexts();
    /** DOWN 秒基线（-1 = 强制下次刷新） */
    private int lastShownSecond = -1;
    /** UP 十分之一秒基线 */
    private long lastShownTenth = -1;
    private int progressCounter;
    private int pauseKeepaliveCounter;
    private TimerPlanner.FlashKind flashKind;
    private long flashUntilNanos;
    private long flashBeat;
    /** 启动来源玩家（结果消息去重；控制台为 null） */
    private UUID sourceId;
    /** 最后一份成功结束结果（内存，供 /timer status 与无参查看） */
    private String lastResult;
    private Runnable tickHandle;
    private boolean registered;

    private CountdownManager() {}

    public static CountdownManager get() {
        return INSTANCE;
    }

    /** 启动请求（命令层解析目标后组装；管理器一次性回填默认值并创建会话） */
    public record StartRequest(TimerMode mode, long durationSeconds, TimerStyle style, TimerLayout layout,
                               String label, boolean silent, boolean dynamicAll, Set<UUID> targets, UUID sourceId) {
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

    /** CarpetPrimaryuanServer 规则观察者调用：规则关闭时无条件清理（无完成音、无结果） */
    public static void onRuleChanged() {
        if (!CarpetPrimaryuanSettings.countdown) {
            forceStop();
        }
    }

    // ==================== 启动 ====================

    /**
     * 新入口启动（/timer up|down）。回填布局与名称默认值后创建会话并发开场效果。
     *
     * @return 0 成功；-1 已有活动计时（结果展示期除外）
     */
    public int start(MinecraftServer server, StartRequest req) {
        if (hasConflictingSession()) {
            return -1;
        }
        long now = System.nanoTime();
        TimerLayout layout = TimerLayout.resolve(req.mode(), req.style(), req.layout());
        String label = req.label() != null ? req.label()
                : ServerI18n.tr(req.mode() == TimerMode.DOWN
                        ? "carpetprimaryuan.timer.default_down"
                        : "carpetprimaryuan.timer.default_up").getString();
        CountdownSession s = req.mode() == TimerMode.DOWN
                ? CountdownSession.startDown(req.durationSeconds(), req.style(), layout, label, req.silent(),
                        req.dynamicAll(), req.targets(), now)
                : CountdownSession.startUp(req.style(), layout, label, req.silent(), req.dynamicAll(),
                        req.targets(), now);
        beginSession(server, s, req.sourceId());

        List<ServerPlayer> audience = audience(server);
        long now2 = System.nanoTime();
        int startSec = s.mode() == TimerMode.DOWN ? s.remainingCeilSeconds(now2) : 0;
        boolean inTickWindow = s.layout().hasCentral() && s.mode() == TimerMode.DOWN && inCentralWindow(s, startSec);
        // 开场闪烁：最后数字窗口优先，进入窗口时不再叠开场闪烁
        if (s.layout().hasCentral() && !inTickWindow) {
            flashKind = TimerPlanner.FlashKind.START;
            flashUntilNanos = now2 + FLASH_WINDOW_NANOS;
            display.renderFrame(TimerPlanner.flash(s, now2, TimerPlanner.FlashKind.START, 0, texts),
                    s.channels(), audience);
        }
        // 开场音：仅 fancy；已进入倒数窗口时改发一次倒数音，不叠加开场音
        TimerSoundPlan.opening(s.soundTier(), startSec, inTickWindow)
                .ifPresent(cue -> display.playCue(cue, audience));
        return 0;
    }

    /** 旧 /countdown 启动：旧渠道组合、旧窗口与节拍表、旧音效配方。 */
    public int startLegacy(MinecraftServer server, long seconds, EnumSet<CountdownChannel> channels,
                           boolean dynamicAll, Set<UUID> snapshotTargets) {
        if (hasConflictingSession()) {
            return -1;
        }
        String label = ServerI18n.tr("carpetprimaryuan.countdown.default_label").getString();
        CountdownSession s = CountdownSession.startLegacy(seconds, label, channels, dynamicAll, snapshotTargets,
                System.nanoTime());
        beginSession(server, s, null);
        return 0;
    }

    private void beginSession(MinecraftServer server, CountdownSession s, UUID source) {
        session = s;
        sourceId = source;
        lastShownSecond = -1;
        lastShownTenth = -1;
        progressCounter = 0;
        pauseKeepaliveCounter = 0;
        flashKind = null;
        flashUntilNanos = 0;
        display.resetDiff();
        display.applyChannels(s.channels());
        renderCycle(server, System.nanoTime());
        ensureTickTask();
    }

    /** 是否存在阻碍新启动的会话（结果展示期允许直接启动新计时替换） */
    private boolean hasConflictingSession() {
        return session != null && session.state() != CountdownSession.State.RESULT;
    }

    /** both/simple+normal 5 秒、both/fancy 与 center 10 秒的最后中央窗口判定 */
    private static boolean inCentralWindow(CountdownSession s, int sec) {
        if (s.layout() == TimerLayout.BOTH) {
            return sec <= (s.style() == TimerStyle.FANCY ? 10 : 5);
        }
        return sec <= 10;
    }

    // ==================== 会话操作（命令层入口） ====================

    /** @return 0 成功；-1 无活动计时或处于结果展示；1 已处于暂停 */
    public int pause(MinecraftServer server) {
        if (!isActive()) {
            return -1;
        }
        if (session.state() != CountdownSession.State.RUNNING) {
            return 1;
        }
        long now = System.nanoTime();
        session.pause(now);
        pauseKeepaliveCounter = 0;
        flash(session, server, now, TimerPlanner.FlashKind.PAUSE, 0);
        return 0;
    }

    /** @return 0 成功；-1 无活动计时或处于结果展示；1 未暂停（正在运行） */
    public int resume(MinecraftServer server) {
        if (!isActive()) {
            return -1;
        }
        if (session.state() != CountdownSession.State.PAUSED) {
            return 1;
        }
        long now = System.nanoTime();
        session.resume(now);
        flash(session, server, now, TimerPlanner.FlashKind.RESUME, 0);
        return 0;
    }

    /** 旧入口 stop：任意状态立即取消清理，无完成提示、无结果 */
    public boolean stopImmediate() {
        if (session == null) {
            return false;
        }
        cleanup();
        return true;
    }

    /**
     * 新入口 stop：UP 冻结最终用时并进入 3 秒结果展示（含一次结果消息与结算音）；
     * DOWN 立即取消（由调用方按停止前剩余值给来源反馈），不播放完成音、不冒充自然结束。
     *
     * @return 是否原本有活动计时
     */
    public boolean stopTimer(MinecraftServer server, ServerPlayer source) {
        CountdownSession s = session;
        if (s == null) {
            return false;
        }
        long now = System.nanoTime();
        if (s.mode() == TimerMode.UP && s.state() != CountdownSession.State.RESULT) {
            s.stopToResult(now);
            flashKind = null;
            pauseKeepaliveCounter = 0;
            lastShownSecond = -1;
            lastShownTenth = -1;
            List<ServerPlayer> audience = audience(server);
            display.renderFrame(TimerPlanner.result(s, now, texts), s.channels(), audience);
            String result = ServerI18n.tr("carpetprimaryuan.command.timer.result_up",
                    s.label(), CountdownTime.formatUpMillis(s.elapsedNanos(now))).getString();
            lastResult = result;
            sendResultChat(server, result, source);
            TimerSoundPlan.upStop(s.soundTier()).ifPresent(cue -> display.playCue(cue, audience));
            return true;
        }
        cleanup();
        return true;
    }

    /** DOWN 剩余时间直接设置（@return 0 成功；-1 无活动计时或处于结果展示；-3 正计时不可修改） */
    public int setTime(MinecraftServer server, long seconds) {
        if (!requireDown()) {
            return -3;
        }
        session.setTime(seconds, System.nanoTime());
        renderCycle(server, System.nanoTime());
        return 0;
    }

    /** @return 0 成功；-1 无活动计时或处于结果展示；-2 修改后超过 24 小时；-3 正计时不可修改 */
    public int addTime(MinecraftServer server, long seconds) {
        if (!requireDown()) {
            return -3;
        }
        long now = System.nanoTime();
        long capNanos = CountdownTime.MAX_SECONDS * 1_000_000_000L;
        if (session.remainingNanos(now) + seconds * 1_000_000_000L > capNanos) {
            return -2;
        }
        session.addTime(seconds, now);
        renderCycle(server, now);
        return 0;
    }

    /** @return 0 成功；-1 无活动计时或处于结果展示；-3 正计时不可修改 */
    public int removeTime(MinecraftServer server, long seconds) {
        if (!requireDown()) {
            return -3;
        }
        boolean completed = session.removeTime(seconds, System.nanoTime());
        if (completed) {
            enterResultDown(server);
        } else {
            renderCycle(server, System.nanoTime());
        }
        return 0;
    }

    private boolean requireDown() {
        return isActive() && session.mode() == TimerMode.DOWN;
    }

    // ==================== 运行中调整 ====================

    /** 旧入口渠道组合：切回渠道渲染模式（layout=null），移除渠道做一次性清理 */
    public int setDisplay(MinecraftServer server, EnumSet<CountdownChannel> channels) {
        if (!isActive()) {
            return -1;
        }
        EnumSet<CountdownChannel> old = session.channels();
        EnumSet<CountdownChannel> removed = EnumSet.copyOf(old);
        removed.removeAll(channels);
        session.setChannels(channels);
        display.clearChannels(removed, audience(server));
        display.applyChannels(channels);
        display.resetDiff();
        renderCycle(server, System.nanoTime());
        return 0;
    }

    /** 新入口布局：渠道随布局重派生，关闭渠道的 BossBar 移除；不发清除包，旧帧自然消失 */
    public int setLayout(MinecraftServer server, TimerLayout layout) {
        if (!isActive()) {
            return -1;
        }
        session.setLayout(layout);
        display.applyChannels(session.channels());
        display.resetDiff();
        renderCycle(server, System.nanoTime());
        return 0;
    }

    /** @return 0 成功；-1 无活动计时或处于结果展示 */
    public int setStyle(MinecraftServer server, TimerStyle style) {
        if (!isActive()) {
            return -1;
        }
        session.setStyle(style, System.nanoTime());
        renderCycle(server, System.nanoTime());
        return 0;
    }

    /** sound on/off 为当前会话显式覆盖，后续 style 切换不重置；新会话恢复其默认值 */
    public int setSound(boolean on) {
        if (!isActive()) {
            return -1;
        }
        session.setSoundOverride(on);
        return 0;
    }

    public int setTargets(MinecraftServer server, boolean dynamicAll, Set<UUID> snapshot) {
        if (!isActive()) {
            return -1;
        }
        session.setTargets(dynamicAll, snapshot);
        renderCycle(server, System.nanoTime());
        return 0;
    }

    public int setLastWindow(MinecraftServer server, Long seconds) {
        if (!isActive()) {
            return -1;
        }
        session.setLastWindow(seconds);
        renderCycle(server, System.nanoTime());
        return 0;
    }

    public int setLabel(MinecraftServer server, String label) {
        if (!isActive()) {
            return -1;
        }
        session.setLabel(label);
        renderCycle(server, System.nanoTime());
        return 0;
    }

    /** @return 0 成功；-1 无活动计时或处于结果展示 */
    public int setMilestones(MinecraftServer server, boolean on) {
        if (!isActive()) {
            return -1;
        }
        session.setMilestonesEnabled(on, session.remainingCeilSeconds(System.nanoTime()));
        return 0;
    }

    // ==================== 状态查询（命令层反馈用） ====================

    public boolean hasActiveSession() {
        return session != null;
    }

    public CountdownSession.State state() {
        return session == null ? CountdownSession.State.IDLE : session.state();
    }

    public TimerMode mode() {
        return session == null ? null : session.mode();
    }

    public boolean legacy() {
        return session != null && session.legacy();
    }

    public TimerStyle style() {
        return session == null ? null : session.style();
    }

    public TimerLayout layout() {
        return session == null ? null : session.layout();
    }

    public String label() {
        return session == null ? null : session.label();
    }

    /** 剩余整秒（无会话返回 0；UP 返回 0） */
    public int remainingCeilSeconds() {
        return session == null ? 0 : session.remainingCeilSeconds(System.nanoTime());
    }

    /** UP 已用纳秒（无会话返回 0） */
    public long elapsedNanos() {
        return session == null ? 0 : session.elapsedNanos(System.nanoTime());
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

    public TimerSoundPlan.Tier soundTier() {
        return session == null ? TimerSoundPlan.Tier.NONE : session.soundTier();
    }

    public boolean milestonesEnabled() {
        return session != null && session.milestonesEnabled();
    }

    public String lastResult() {
        return lastResult;
    }

    // ==================== 事件处理 ====================

    private void onJoin(ServerPlayer player) {
        // fixBluemap mixin 会让假人触发本事件；假人永不为显示对象
        if (player instanceof EntityPlayerMPFake) {
            return;
        }
        CountdownSession s = session;
        if (s == null || s.state() == CountdownSession.State.RESULT) {
            return;
        }
        if (!s.shouldDisplay(player.getUUID())) {
            return;
        }
        if (s.legacy()) {
            display.viewerJoinedLegacy(player, s, System.nanoTime());
            return;
        }
        display.addBarViewer(player, s.channels().contains(CountdownChannel.BOSSBAR));
        // 只补当前帧，不补开场/节点/历史声音
        display.renderFrame(currentBaseFrame(s, System.nanoTime()), s.channels(), List.of(player));
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
                if (s.mode() == TimerMode.UP) {
                    tickUp(server, s, now);
                } else if (s.legacy()) {
                    tickDownLegacy(server, s, now);
                } else {
                    tickDown(server, s, now);
                }
            }
            case PAUSED -> {
                // 保活重发冻结文本对抗 actionbar 淡出与 title 停留到期（BossBar 服务端持久，无需重发）
                if (++pauseKeepaliveCounter >= PAUSE_KEEPALIVE_TICKS) {
                    pauseKeepaliveCounter = 0;
                    renderCycle(server, now);
                }
            }
            case RESULT -> {
                // 结果展示保活：仅顶部/动作栏（中央 5/50/5 一次发满 3 秒，不重发重淡入）
                if (++pauseKeepaliveCounter >= PAUSE_KEEPALIVE_TICKS) {
                    pauseKeepaliveCounter = 0;
                    display.renderKeepalive(TimerPlanner.result(s, now, texts), s.channels(), audience(server));
                }
                if (now - s.resultAtNanos() >= (s.legacy() ? LEGACY_COMPLETION_NANOS : RESULT_WINDOW_NANOS)) {
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

    private void tickDown(MinecraftServer server, CountdownSession s, long now) {
        int sec = s.advanceRunning(now);
        if (sec == -1) {
            enterResultDown(server);
            return;
        }
        if (sec != lastShownSecond) {
            int prev = lastShownSecond;
            lastShownSecond = sec;
            renderCycle(server, now);
            if (prev >= 0 && sec < prev) {
                // 剩余真实变小：节拍跨越与逐秒倒数音（跳时/加时不补发）
                long beat = s.advanceMilestones(sec);
                if (beat > 0) {
                    showMilestone(server, s, now, beat);
                }
                TimerSoundPlan.Tier tier = s.soundTier();
                if (tier == TimerSoundPlan.Tier.LEGACY) {
                    if (sec <= 10 && sec > 0) {
                        display.playTickSoundLegacy(audience(server), sec);
                    }
                } else {
                    TimerSoundPlan.tick(tier, sec).ifPresent(cue -> display.playCue(cue, audience(server)));
                }
            } else {
                // 时间变长（add/set time）：仅同步节拍基线，不回滚已触发
                s.advanceMilestones(sec);
            }
        } else if (++progressCounter >= PROGRESS_REFRESH_TICKS) {
            progressCounter = 0;
            display.updateProgressOnly((float) s.progress(now), audience(server));
        }
    }

    private void tickDownLegacy(MinecraftServer server, CountdownSession s, long now) {
        int sec = s.advanceRunning(now);
        if (sec == -1) {
            enterLegacyCompletion(server);
            return;
        }
        if (sec != lastShownSecond) {
            int prev = lastShownSecond;
            lastShownSecond = sec;
            List<ServerPlayer> audience = audience(server);
            display.sendCountdownLegacy(s, now, audience);
            if (prev >= 0 && sec < prev) {
                long beat = s.advanceMilestones(sec);
                if (beat > 0) {
                    // 旧入口节拍遵守已选显示渠道：中央提示仅 TITLE 渠道允许时发送
                    if (s.channels().contains(CountdownChannel.TITLE)) {
                        display.showMilestoneLegacy(beat, audience);
                    }
                    if (s.soundTier() == TimerSoundPlan.Tier.LEGACY) {
                        display.playMilestoneSoundLegacy(audience);
                    }
                }
                if (s.soundTier() == TimerSoundPlan.Tier.LEGACY && sec <= 10 && sec > 0) {
                    display.playTickSoundLegacy(audience, sec);
                }
            } else {
                s.advanceMilestones(sec);
            }
        } else if (++progressCounter >= PROGRESS_REFRESH_TICKS) {
            progressCounter = 0;
            display.updateProgressOnly((float) s.progress(now), audience(server));
        }
    }

    private void tickUp(MinecraftServer server, CountdownSession s, long now) {
        long tenth = s.elapsedNanos(now) / 100_000_000L;
        if (tenth != lastShownTenth) {
            lastShownTenth = tenth;
            renderCycle(server, now);
        }
    }

    // ==================== 内部 ====================

    private boolean isActive() {
        return session != null && session.state() != CountdownSession.State.RESULT;
    }

    /** 当前基础帧（运行/暂停），观众加入补帧用 */
    private TimerFrame currentBaseFrame(CountdownSession s, long now) {
        return s.state() == CountdownSession.State.PAUSED
                ? TimerPlanner.paused(s, now, texts)
                : TimerPlanner.running(s, now, texts);
    }

    /** 一次性中央闪烁（仅中央允许的布局）：同一时刻只显示一个中央阶段 */
    private void flash(CountdownSession s, MinecraftServer server, long now,
                       TimerPlanner.FlashKind kind, long beat) {
        if (s.legacy() || !s.layout().hasCentral()) {
            return;
        }
        flashKind = kind;
        flashUntilNanos = now + FLASH_WINDOW_NANOS;
        flashBeat = beat;
        display.renderFrame(TimerPlanner.flash(s, now, kind, beat, texts), s.channels(), audience(server));
    }

    /** 长程节点：中央闪烁（允许的布局）+ 一声节点音 */
    private void showMilestone(MinecraftServer server, CountdownSession s, long now, long beat) {
        List<ServerPlayer> audience = audience(server);
        if (s.layout().hasCentral()) {
            flashKind = TimerPlanner.FlashKind.MILESTONE;
            flashUntilNanos = now + FLASH_WINDOW_NANOS;
            flashBeat = beat;
            display.renderFrame(TimerPlanner.flash(s, now, TimerPlanner.FlashKind.MILESTONE, beat, texts),
                    s.channels(), audience);
        }
        TimerSoundPlan.milestone(s.soundTier()).ifPresent(cue -> display.playCue(cue, audience));
    }

    private void ensureTickTask() {
        if (tickHandle == null) {
            tickHandle = ServerTickScheduler.register(this);
        }
    }

    /** 立即刷新一帧（启动与所有配置变更后调用），并同步刷新基线 */
    private void renderCycle(MinecraftServer server, long now) {
        CountdownSession s = session;
        if (s == null) {
            return;
        }
        if (s.legacy()) {
            display.sendCountdownLegacy(s, now, audience(server));
            lastShownSecond = s.remainingCeilSeconds(now);
            return;
        }
        List<ServerPlayer> audience = audience(server);
        boolean flashActive = flashKind != null && now < flashUntilNanos;
        display.renderFrame(currentBaseFrame(s, now), s.channels(), audience);
        TimerFrame central = TimerPlanner.central(s, now, flashActive, texts);
        if (central != null) {
            display.renderFrame(central, s.channels(), audience);
        }
        if (s.mode() == TimerMode.DOWN) {
            lastShownSecond = s.remainingCeilSeconds(now);
        } else {
            lastShownTenth = s.elapsedNanos(now) / 100_000_000L;
        }
    }

    /** down 自然归零：结果帧 + 一次结果消息 + 一次所选结束音 */
    private void enterResultDown(MinecraftServer server) {
        CountdownSession s = session;
        if (s == null) {
            return;
        }
        long now = System.nanoTime();
        flashKind = null;
        lastShownSecond = -1;
        List<ServerPlayer> audience = audience(server);
        display.renderFrame(TimerPlanner.result(s, now, texts), s.channels(), audience);
        String result = ServerI18n.tr("carpetprimaryuan.command.timer.result_down", s.label()).getString();
        lastResult = result;
        sendResultChat(server, result, sourcePlayer(server));
        TimerSoundPlan.finish(s.soundTier()).ifPresent(cue -> display.playCue(cue, audience));
    }

    /** 旧入口完成提示：原"时间到！"节奏（2 秒窗口）与原音效，另记结果 */
    private void enterLegacyCompletion(MinecraftServer server) {
        CountdownSession s = session;
        if (s == null) {
            return;
        }
        lastShownSecond = -1;
        List<ServerPlayer> audience = audience(server);
        display.showCompletionLegacy(s, audience, ServerI18n.tr("carpetprimaryuan.countdown.finished").getString());
        if (s.soundTier() == TimerSoundPlan.Tier.LEGACY) {
            display.playFinishSoundLegacy(audience);
        }
        lastResult = ServerI18n.tr("carpetprimaryuan.command.timer.result_down", s.label()).getString();
    }

    /** 结果消息：在线目标 ∪ 操作来源（来源已在目标中时不重复发送） */
    private void sendResultChat(MinecraftServer server, String text, ServerPlayer source) {
        CountdownSession s = session;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake) {
                continue;
            }
            boolean isSource = source != null && player.getUUID().equals(source.getUUID());
            if (s.shouldDisplay(player.getUUID()) || isSource) {
                player.sendSystemMessage(Component.literal(text));
            }
        }
    }

    private ServerPlayer sourcePlayer(MinecraftServer server) {
        return sourceId == null ? null : server.getPlayerList().getPlayer(sourceId);
    }

    /** 终局清理：回 IDLE 并移除本系统全部显示资源；下一 tick 任务自注销 */
    private void cleanup() {
        session = null;
        sourceId = null;
        lastShownSecond = -1;
        lastShownTenth = -1;
        progressCounter = 0;
        pauseKeepaliveCounter = 0;
        flashKind = null;
        display.hide();
        if (tickHandle != null) {
            tickHandle.run();
            tickHandle = null;
        }
    }

    /** 强制清理（规则关闭/停服）：任意状态回到 IDLE，不产生完成结果 */
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

    /** 生产文案：走 ServerI18n（Carpet 全局语言） */
    private static final class PlannerTexts implements TimerPlanner.TimerTexts {
        @Override
        public String barDown(String label, String time) {
            return ServerI18n.tr("carpetprimaryuan.timer.bar_down", label, time).getString();
        }

        @Override
        public String barUp(String label, String time) {
            return ServerI18n.tr("carpetprimaryuan.timer.bar_up", label, time).getString();
        }

        @Override
        public String paused(String label, String time) {
            return ServerI18n.tr("carpetprimaryuan.timer.paused", label, time).getString();
        }

        @Override
        public String finishedLine(String label) {
            return ServerI18n.tr("carpetprimaryuan.timer.finished_line", label).getString();
        }

        @Override
        public String upResult(String label, String time) {
            return ServerI18n.tr("carpetprimaryuan.timer.bar_up", label, time).getString();
        }

        @Override
        public String subtitlePaused(String label) {
            return ServerI18n.tr("carpetprimaryuan.timer.subtitle_paused", label).getString();
        }

        @Override
        public String subtitleResumed(String label) {
            return ServerI18n.tr("carpetprimaryuan.timer.subtitle_resumed", label).getString();
        }

        @Override
        public String subtitleFinished(String label) {
            return ServerI18n.tr("carpetprimaryuan.timer.subtitle_finished", label).getString();
        }

        @Override
        public String subtitleStopped(String label) {
            return ServerI18n.tr("carpetprimaryuan.timer.subtitle_stopped", label).getString();
        }
    }
}
