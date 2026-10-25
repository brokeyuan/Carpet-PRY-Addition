package me.primaryuan.carpet.handler.countdown;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 倒计时会话状态机（纯 Java，零 MC 依赖；时钟经参数注入便于单测）。
 *
 * <p>状态流转：IDLE→RUNNING→(PAUSED↔)→COMPLETING→IDLE。完成仅能从 RUNNING
 * 自然推进（或 PAUSED 下 remove 减至归零）触发一次，COMPLETING 由管理器在
 * 2 秒后收尾。RUNNING 用截止时间（deadline）推算剩余；PAUSED 冻结剩余值，
 * resume 时按当前时刻重算截止——TPS 低不影响真实时长。</p>
 *
 * <p>进度分母（有效总时长）初始为启动时长；set/add 使剩余超过分母时同步扩展，
 * 保证进度恒在 [0,1]；remove 不收缩分母。</p>
 */
public final class CountdownSession {

    public enum State { IDLE, RUNNING, PAUSED, COMPLETING }

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private State state = State.IDLE;
    private long durationNanos;
    private long effectiveTotalNanos;
    /** PAUSED 冻结的剩余值（RUNNING 下以 deadlineNanos 为准） */
    private long remainingNanos;
    private long deadlineNanos;
    /** 进入 COMPLETING 的时刻（完成提示 2 秒窗口起点） */
    private long completionAtNanos;

    private EnumSet<CountdownChannel> channels;
    private Long lastWindowSeconds;
    private String label;
    private boolean dynamicAll;
    private final Set<UUID> snapshotTargets = new HashSet<>();

    private CountdownSession() {}

    /**
     * 创建 RUNNING 会话。时长经 CountdownTime 校验为 [1, 86400] 秒。
     *
     * @param lastWindowSeconds null = 全程显示中央倒计时
     * @param dynamicAll        true = 动态全服（快照失效）；false = 使用 snapshot
     */
    public static CountdownSession start(long durationSeconds, EnumSet<CountdownChannel> channels, String label,
                                         Long lastWindowSeconds, boolean dynamicAll, Set<UUID> snapshot, long now) {
        if (durationSeconds < 1 || durationSeconds > CountdownTime.MAX_SECONDS) {
            throw new IllegalArgumentException("duration");
        }
        CountdownSession s = new CountdownSession();
        s.state = State.RUNNING;
        s.durationNanos = durationSeconds * NANOS_PER_SECOND;
        s.effectiveTotalNanos = s.durationNanos;
        s.deadlineNanos = now + s.durationNanos;
        s.channels = EnumSet.copyOf(channels);
        s.lastWindowSeconds = lastWindowSeconds;
        s.label = label;
        s.dynamicAll = dynamicAll;
        if (snapshot != null) {
            s.snapshotTargets.addAll(snapshot);
        }
        return s;
    }

    public State state() {
        return state;
    }

    /** 启动总时长（秒），status 展示用 */
    public long durationSeconds() {
        return durationNanos / NANOS_PER_SECOND;
    }

    public EnumSet<CountdownChannel> channels() {
        return EnumSet.copyOf(channels);
    }

    public String label() {
        return label;
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

    /** null = 中央显示窗口为全程 */
    public Long lastWindowSeconds() {
        return lastWindowSeconds;
    }

    public long completionAtNanos() {
        return completionAtNanos;
    }

    public void setChannels(EnumSet<CountdownChannel> channels) {
        this.channels = EnumSet.copyOf(channels);
    }

    public void setLastWindow(Long seconds) {
        this.lastWindowSeconds = seconds;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setTargets(boolean dynamicAll, Set<UUID> snapshot) {
        this.dynamicAll = dynamicAll;
        this.snapshotTargets.clear();
        if (snapshot != null) {
            this.snapshotTargets.addAll(snapshot);
        }
    }

    // ==================== 状态流转 ====================

    public void pause(long now) {
        if (state != State.RUNNING) {
            throw new IllegalStateException("not running");
        }
        remainingNanos = Math.max(0, deadlineNanos - now);
        state = State.PAUSED;
    }

    public void resume(long now) {
        if (state != State.PAUSED) {
            throw new IllegalStateException("not paused");
        }
        deadlineNanos = now + remainingNanos;
        state = State.RUNNING;
    }

    /** @return 是否原本处于活动状态（含 COMPLETING）；IDLE 返回 false */
    public boolean stop() {
        if (state == State.IDLE) {
            return false;
        }
        state = State.IDLE;
        return true;
    }

    /**
     * RUNNING 推进一个 tick。
     *
     * @return 当前剩余整秒（向上取整）；本次调用触发完成返回 -1；非 RUNNING 返回 -2
     */
    public int advanceRunning(long now) {
        if (state != State.RUNNING) {
            return -2;
        }
        long remaining = deadlineNanos - now;
        if (remaining <= 0) {
            complete(now);
            return -1;
        }
        long ceil = CountdownTime.ceilSeconds(remaining);
        return ceil > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ceil;
    }

    private void complete(long now) {
        if (state == State.RUNNING) {
            state = State.COMPLETING;
            completionAtNanos = now;
            remainingNanos = 0;
        }
    }

    // ==================== 时间查询 ====================

    public long remainingNanos(long now) {
        return switch (state) {
            case RUNNING -> Math.max(0, deadlineNanos - now);
            case PAUSED -> remainingNanos;
            default -> 0;
        };
    }

    /** 剩余整秒（向上取整，恒不提前为 0） */
    public int remainingCeilSeconds(long now) {
        long ceil = CountdownTime.ceilSeconds(remainingNanos(now));
        return ceil > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ceil;
    }

    /** BossBar 进度 = 剩余 / 有效总时长，clamp [0,1] */
    public double progress(long now) {
        if (effectiveTotalNanos <= 0) {
            return 0;
        }
        double p = (double) remainingNanos(now) / effectiveTotalNanos;
        return Math.min(1.0, Math.max(0.0, p));
    }

    /** 中央倒计时（Title/Subtitle）窗口判定 */
    public boolean inLastWindow(int ceilSeconds) {
        return lastWindowSeconds == null || ceilSeconds <= lastWindowSeconds;
    }

    // ==================== 时间修改 ====================

    /** 剩余时间直接设为指定值（≥1 秒，≤24h 由调用方保证） */
    public void setTime(long seconds, long now) {
        adjust(seconds * NANOS_PER_SECOND, now);
    }

    /** 增加剩余时间（修改后 ≤24h 由调用方保证） */
    public void addTime(long seconds, long now) {
        adjust(remainingNanos(now) + seconds * NANOS_PER_SECOND, now);
    }

    /**
     * 减少剩余时间；减至 ≤0 触发完成（RUNNING 自然完成 / PAUSED 直接进入完成）。
     *
     * @return 本次调用是否触发完成
     */
    public boolean removeTime(long seconds, long now) {
        long target = remainingNanos(now) - seconds * NANOS_PER_SECOND;
        if (target <= 0) {
            if (state == State.RUNNING) {
                complete(now);
                return true;
            }
            if (state == State.PAUSED) {
                state = State.COMPLETING;
                completionAtNanos = now;
                remainingNanos = 0;
                return true;
            }
            return false;
        }
        adjust(target, now);
        return false;
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
