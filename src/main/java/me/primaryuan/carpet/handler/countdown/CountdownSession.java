package me.primaryuan.carpet.handler.countdown;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * 计时会话状态机（纯 Java，零 MC 依赖；时钟经参数注入便于单测）。
 *
 * <p>两种方向共用一套状态流转：IDLE→RUNNING→(PAUSED↔)→RESULT。DOWN 用截止时间
 * （deadline）推算剩余、向上取整显示整秒，自然归零或 remove 减至归零进入 RESULT；
 * UP 用"已完成段累计 + 当前段起点"推算已用纳秒、向下截断显示十分之一秒，
 * 仅手动停止进入 RESULT（无时长上限，不存在自然归零）。RESULT 为结果展示阶段，
 * 期间允许直接启动新计时替换展示。TPS 低不影响真实时长。</p>
 *
 * <p>入口差异：旧 /countdown 启动 legacy 会话（style=NORMAL、layout=null 按
 * 渠道组合渲染、旧节拍表与音效配方）；新 /timer 启动带 style/layout 的会话，
 * 渠道由布局派生。sound/milestones 的显式开关（set 覆盖值）对新旧会话统一生效，
 * 不回写各自默认值。</p>
 *
 * <p>进度分母（有效总时长）仅 DOWN 使用：初始为启动时长，set/add 使剩余超过
 * 分母时同步扩展，进度恒在 [0,1]；UP 恒满条。</p>
 */
public final class CountdownSession {

    public enum State { IDLE, RUNNING, PAUSED, RESULT }

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** 旧入口长程节拍表（秒），递减排列；10..1 的逐秒倒数由窗口 + 音效承担，不入表 */
    public static final long[] LEGACY_MILESTONE_BEATS = {1800, 900, 600, 300, 60, 30, 15};

    /** 新入口 fancy 长程节点（仅两处） */
    public static final long[] FANCY_MILESTONE_BEATS = {60, 30};

    private State state = State.IDLE;
    private TimerMode mode;
    /** 旧 /countdown 入口启动的会话（按渠道组合渲染，保留旧语义） */
    private boolean legacy;
    private TimerStyle style;
    /** null = 旧入口渠道渲染模式 */
    private TimerLayout layout;
    private long durationNanos;
    private long effectiveTotalNanos;
    /** DOWN：PAUSED 冻结的剩余值（RUNNING 下以 deadlineNanos 为准）；UP 未用 */
    private long remainingNanos;
    private long deadlineNanos;
    /** UP：已完成运行段累计；RUNNING 下叠加 now - segmentStartNanos */
    private long elapsedBeforeNanos;
    private long segmentStartNanos;
    /** 进入 RESULT 的时刻（结果展示窗口起点） */
    private long resultAtNanos;

    private EnumSet<CountdownChannel> channels;
    /** 旧入口中央窗口；新入口 null（窗口由布局与档位决定） */
    private Long lastWindowSeconds;
    private String label;
    /** silent 启动词：禁用全部声音，优先级高于 sound 覆盖 */
    private boolean silent;
    /** sound on/off 显式覆盖（/timer sound、旧 set sound）；null = 未覆盖 */
    private Boolean soundOverride;
    /** 里程碑 on/off 显式覆盖（旧 set milestones）；null = 按入口默认 */
    private Boolean milestonesOverride;
    private boolean dynamicAll;
    private final Set<UUID> snapshotTargets = new HashSet<>();

    /** 当前生效节拍表（派生自入口与档位） */
    private long[] milestoneBeats = {};
    /** 上次节拍检测的剩余整秒（跨越检测基线） */
    private long lastMilestoneSecond;
    /** 尚未触发的节拍集合 */
    private final Set<Long> armedMilestones = new HashSet<>();
    /** 本会话已触发过的节拍（档位/开关切换重新武装时不回滚重播） */
    private final Set<Long> firedMilestones = new HashSet<>();

    private CountdownSession() {}

    // ==================== 启动 ====================

    /**
     * 启动 DOWN 会话。时长经 CountdownTime 校验为 [1, 86400] 秒。
     *
     * @param style/layout 新入口参数；legacy 启动由 startLegacy 承担
     */
    public static CountdownSession startDown(long durationSeconds, TimerStyle style, TimerLayout layout,
                                             String label, boolean silent, boolean dynamicAll,
                                             Set<UUID> snapshot, long now) {
        if (durationSeconds < 1 || durationSeconds > CountdownTime.MAX_SECONDS) {
            throw new IllegalArgumentException("duration");
        }
        CountdownSession s = base(TimerMode.DOWN, false, style, layout, label, silent, dynamicAll, snapshot);
        s.state = State.RUNNING;
        s.durationNanos = durationSeconds * NANOS_PER_SECOND;
        s.effectiveTotalNanos = s.durationNanos;
        s.deadlineNanos = now + s.durationNanos;
        s.refreshBeats((int) durationSeconds);
        return s;
    }

    /** 启动 UP 会话：从零累计，无时长上限，无自然归零。 */
    public static CountdownSession startUp(TimerStyle style, TimerLayout layout, String label, boolean silent,
                                           boolean dynamicAll, Set<UUID> snapshot, long now) {
        CountdownSession s = base(TimerMode.UP, false, style, layout, label, silent, dynamicAll, snapshot);
        s.state = State.RUNNING;
        s.segmentStartNanos = now;
        return s;
    }

    /** 旧 /countdown 入口：style=NORMAL、layout=null、旧节拍表、旧窗口默认 10 秒。 */
    public static CountdownSession startLegacy(long durationSeconds, String label, EnumSet<CountdownChannel> channels,
                                               boolean dynamicAll, Set<UUID> snapshot, long now) {
        if (durationSeconds < 1 || durationSeconds > CountdownTime.MAX_SECONDS) {
            throw new IllegalArgumentException("duration");
        }
        CountdownSession s = base(TimerMode.DOWN, true, TimerStyle.NORMAL, null,
                label, false, dynamicAll, snapshot);
        if (channels != null && !channels.isEmpty()) {
            s.channels = EnumSet.copyOf(channels);
        }
        s.state = State.RUNNING;
        s.durationNanos = durationSeconds * NANOS_PER_SECOND;
        s.effectiveTotalNanos = s.durationNanos;
        s.deadlineNanos = now + s.durationNanos;
        s.lastWindowSeconds = 10L;
        s.refreshBeats((int) durationSeconds);
        return s;
    }

    private static CountdownSession base(TimerMode mode, boolean legacy, TimerStyle style, TimerLayout layout,
                                         String label, boolean silent, boolean dynamicAll, Set<UUID> snapshot) {
        CountdownSession s = new CountdownSession();
        s.mode = mode;
        s.legacy = legacy;
        s.style = style;
        s.layout = layout;
        s.label = label;
        s.silent = silent;
        s.dynamicAll = dynamicAll;
        if (snapshot != null) {
            s.snapshotTargets.addAll(snapshot);
        }
        s.channels = layout != null ? layout.channels() : EnumSet.noneOf(CountdownChannel.class);
        return s;
    }

    /** 武装节拍：小于启动时长的节拍才生效（等于或大于的按"启动即在节拍上"跳过）——由 refreshBeats(currentSec) 承担 */

    // ==================== 基础查询 ====================

    public State state() {
        return state;
    }

    public TimerMode mode() {
        return mode;
    }

    public boolean legacy() {
        return legacy;
    }

    public TimerStyle style() {
        return style;
    }

    public TimerLayout layout() {
        return layout;
    }

    public String label() {
        return label;
    }

    public boolean silent() {
        return silent;
    }

    public EnumSet<CountdownChannel> channels() {
        return EnumSet.copyOf(channels);
    }

    /** null = 旧入口中央窗口为全程或按 lastWindowSeconds */
    public Long lastWindowSeconds() {
        return lastWindowSeconds;
    }

    public boolean dynamicAll() {
        return dynamicAll;
    }

    public Set<UUID> snapshotTargets() {
        return snapshotTargets;
    }

    public boolean shouldDisplay(UUID id) {
        return dynamicAll || snapshotTargets.contains(id);
    }

    /** 启动总时长（秒），status 展示用；UP 返回 0 */
    public long durationSeconds() {
        return mode == TimerMode.DOWN ? durationNanos / NANOS_PER_SECOND : 0;
    }

    public long resultAtNanos() {
        return resultAtNanos;
    }

    // ==================== 配置修改 ====================

    public void setChannels(EnumSet<CountdownChannel> channels) {
        this.channels = EnumSet.copyOf(channels);
    }

    public void setLastWindow(Long seconds) {
        this.lastWindowSeconds = seconds;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** 新入口切换布局：渠道随布局重派生；旧入口（layout=null）走 setChannels */
    public void setLayout(TimerLayout layout) {
        this.layout = layout;
        this.channels = layout.channels();
    }

    public void setStyle(TimerStyle style, long now) {
        this.style = style;
        if (!legacy) {
            // 档位切换只影响之后的事件：按当前剩余重新武装新节拍表（已触发的不回滚重播）
            refreshBeats(mode == TimerMode.DOWN ? remainingCeilSeconds(now) : 0);
        }
    }

    public void setTargets(boolean dynamicAll, Set<UUID> snapshot) {
        this.dynamicAll = dynamicAll;
        this.snapshotTargets.clear();
        if (snapshot != null) {
            this.snapshotTargets.addAll(snapshot);
        }
    }

    // ==================== 节拍与音效 ====================

    /**
     * 按入口与档位派生节拍表并重新武装：小于 currentSec 且未触发过的节拍生效；
     * currentSec 同时作为跨越检测基线（启动时 = 总时长）。
     */
    private void refreshBeats(int currentSec) {
        if (legacy) {
            milestoneBeats = milestonesOn() ? LEGACY_MILESTONE_BEATS : new long[0];
        } else {
            milestoneBeats = style == TimerStyle.FANCY && milestonesOn() ? FANCY_MILESTONE_BEATS : new long[0];
        }
        armedMilestones.clear();
        lastMilestoneSecond = currentSec;
        for (long m : milestoneBeats) {
            if (m < currentSec && !firedMilestones.contains(m)) {
                armedMilestones.add(m);
            }
        }
    }

    public boolean milestonesEnabled() {
        return milestonesOn();
    }

    private boolean milestonesOn() {
        if (milestonesOverride != null) {
            return milestonesOverride;
        }
        return legacy || style == TimerStyle.FANCY;
    }

    public long[] milestoneBeats() {
        return milestoneBeats;
    }

    /** 开关节拍提示（旧入口 set milestones）：按当前剩余重新武装，小于当前秒的节拍才武装 */
    public void setMilestonesEnabled(boolean on, int currentSec) {
        milestonesOverride = on;
        lastMilestoneSecond = currentSec;
        refreshBeats(currentSec);
    }

    /**
     * 整秒变化时推进节拍检测（DOWN 下每次显示秒变化调用，双向都调以维护基线）。
     *
     * @return 本次触发的节拍值（被跨越的已武装节拍中最接近落点者），无则 0；
     *         一次跨越多个节拍只发一个，其余静默标记已触发
     */
    public long advanceMilestones(int newSec) {
        long fired = 0;
        if (milestonesOn() && newSec < lastMilestoneSecond) {
            Iterator<Long> it = armedMilestones.iterator();
            while (it.hasNext()) {
                long m = it.next();
                if (m >= newSec) {
                    it.remove();
                    firedMilestones.add(m);
                    if (fired == 0 || m < fired) {
                        fired = m;
                    }
                }
            }
        }
        lastMilestoneSecond = newSec;
        return fired;
    }

    /**
     * 生效音效层：silent 最高；显式覆盖其次（开 = 至少 NORMAL，simple 提升到 NORMAL）；
     * 否则按档位默认（simple 无声）。
     */
    public TimerSoundPlan.Tier soundTier() {
        if (silent) {
            return TimerSoundPlan.Tier.NONE;
        }
        if (soundOverride != null) {
            if (!soundOverride) {
                return TimerSoundPlan.Tier.NONE;
            }
            if (legacy) {
                return TimerSoundPlan.Tier.LEGACY;
            }
            return style == TimerStyle.FANCY ? TimerSoundPlan.Tier.FANCY : TimerSoundPlan.Tier.NORMAL;
        }
        if (legacy) {
            return TimerSoundPlan.Tier.LEGACY;
        }
        return switch (style) {
            case SIMPLE -> TimerSoundPlan.Tier.NONE;
            case NORMAL -> TimerSoundPlan.Tier.NORMAL;
            case FANCY -> TimerSoundPlan.Tier.FANCY;
        };
    }

    /** sound on/off 显式覆盖（null 恢复档位默认） */
    public void setSoundOverride(Boolean on) {
        soundOverride = on;
    }

    public Boolean soundOverride() {
        return soundOverride;
    }

    // ==================== 状态流转 ====================

    public void pause(long now) {
        if (state != State.RUNNING) {
            throw new IllegalStateException("not running");
        }
        if (mode == TimerMode.DOWN) {
            remainingNanos = Math.max(0, deadlineNanos - now);
        } else {
            elapsedBeforeNanos += now - segmentStartNanos;
        }
        state = State.PAUSED;
    }

    public void resume(long now) {
        if (state != State.PAUSED) {
            throw new IllegalStateException("not paused");
        }
        if (mode == TimerMode.DOWN) {
            deadlineNanos = now + remainingNanos;
        } else {
            segmentStartNanos = now;
        }
        state = State.RUNNING;
    }

    /** @return 是否原本处于活动状态（含 RESULT）；IDLE 返回 false */
    public boolean stop() {
        if (state == State.IDLE) {
            return false;
        }
        state = State.IDLE;
        return true;
    }

    /**
     * DOWN RUNNING 推进一个 tick。
     *
     * @return 当前剩余整秒（向上取整）；本次调用触发完成返回 -1；非 RUNNING 返回 -2
     */
    public int advanceRunning(long now) {
        if (mode != TimerMode.DOWN || state != State.RUNNING) {
            return -2;
        }
        long remaining = deadlineNanos - now;
        if (remaining <= 0) {
            enterResult(now);
            return -1;
        }
        long ceil = CountdownTime.ceilSeconds(remaining);
        return ceil > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ceil;
    }

    /** 自然归零 / remove 减至归零 / UP 手动停止共用的结果入口；每会话仅一次 */
    private void enterResult(long now) {
        if (state == State.RUNNING || state == State.PAUSED) {
            state = State.RESULT;
            resultAtNanos = now;
            if (mode == TimerMode.DOWN) {
                remainingNanos = 0;
            } else {
                elapsedBeforeNanos += Math.max(0, now - segmentStartNanos);
            }
        }
    }

    /** UP 手动停止：冻结最终已用时间并进入结果展示；仅 RUNNING/PAUSED 有效 */
    public void stopToResult(long now) {
        if (mode != TimerMode.UP) {
            throw new IllegalStateException("not up");
        }
        enterResult(now);
    }

    /** DOWN 手动停止：取消并回 IDLE，不进入结果展示（不冒充自然结束） */
    public boolean stopDown() {
        if (mode != TimerMode.DOWN) {
            throw new IllegalStateException("not down");
        }
        return stop();
    }

    // ==================== 时间查询 ====================

    public long remainingNanos(long now) {
        if (mode != TimerMode.DOWN) {
            return 0;
        }
        return switch (state) {
            case RUNNING -> Math.max(0, deadlineNanos - now);
            case PAUSED -> remainingNanos;
            case RESULT -> 0;
            default -> 0;
        };
    }

    /** UP 已用纳秒：RUNNING 累计当前段；PAUSED/RESULT 冻结 */
    public long elapsedNanos(long now) {
        if (mode != TimerMode.UP) {
            return 0;
        }
        return switch (state) {
            case RUNNING -> elapsedBeforeNanos + (now - segmentStartNanos);
            case PAUSED, RESULT -> elapsedBeforeNanos;
            default -> 0;
        };
    }

    /** 剩余整秒（向上取整，恒不提前为 0）；DOWN 专用 */
    public int remainingCeilSeconds(long now) {
        long ceil = CountdownTime.ceilSeconds(remainingNanos(now));
        return ceil > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ceil;
    }

    /** BossBar 进度：DOWN = 剩余/有效总时长，UP 恒满条；clamp [0,1] */
    public double progress(long now) {
        if (mode == TimerMode.UP) {
            return 1.0;
        }
        if (effectiveTotalNanos <= 0) {
            return 0;
        }
        double p = (double) remainingNanos(now) / effectiveTotalNanos;
        return Math.min(1.0, Math.max(0.0, p));
    }

    /** 旧入口中央倒计时（Title/Subtitle）窗口判定 */
    public boolean inLastWindow(int ceilSeconds) {
        return lastWindowSeconds == null || ceilSeconds <= lastWindowSeconds;
    }

    // ==================== 时间修改（仅 DOWN） ====================

    /** 剩余时间直接设为指定值（≥1 秒，≤24h 由调用方保证） */
    public void setTime(long seconds, long now) {
        requireDown();
        adjust(seconds * NANOS_PER_SECOND, now);
    }

    /** 增加剩余时间（修改后 ≤24h 由调用方保证） */
    public void addTime(long seconds, long now) {
        requireDown();
        adjust(remainingNanos(now) + seconds * NANOS_PER_SECOND, now);
    }

    /**
     * 减少剩余时间；减至 ≤0 触发结果（RUNNING 自然完成 / PAUSED 直接进入结果）。
     *
     * @return 本次调用是否触发结果
     */
    public boolean removeTime(long seconds, long now) {
        requireDown();
        long target = remainingNanos(now) - seconds * NANOS_PER_SECOND;
        if (target <= 0) {
            if (state == State.RUNNING || state == State.PAUSED) {
                enterResult(now);
                return true;
            }
            return false;
        }
        adjust(target, now);
        return false;
    }

    private void requireDown() {
        if (mode != TimerMode.DOWN) {
            throw new IllegalStateException("not down");
        }
    }

    private void adjust(long targetNanos, long now) {
        if (state != State.RUNNING && state != State.PAUSED) {
            throw new IllegalStateException("not adjustable");
        }
        targetNanos = Math.max(NANOS_PER_SECOND, targetNanos);
        if (targetNanos > CountdownTime.MAX_SECONDS * NANOS_PER_SECOND) {
            throw new IllegalArgumentException("cap");
        }
        if (targetNanos > effectiveTotalNanos) {
            effectiveTotalNanos = targetNanos;
        }
        if (state == State.RUNNING) {
            deadlineNanos = now + targetNanos;
        } else {
            remainingNanos = targetNanos;
        }
    }
}
