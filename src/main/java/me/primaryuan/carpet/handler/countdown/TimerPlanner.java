package me.primaryuan.carpet.handler.countdown;

/**
 * 计时显示规划（纯 Java，零 MC 依赖；输入会话 + 注入时钟，输出不可变帧）。
 *
 * <p>布局约束先于效果：bar/actionbar 布局绝不产出中央内容，选择顶部或动作栏时
 * 开场、节点、结束均不会弹中央。中央阶段优先级：结果 &gt; 暂停 &gt; 最后数字 &gt;
 * 一次性闪烁 &gt; 常态连续；同一时刻只显示一个中央阶段。</p>
 *
 * <p>文案经 {@link TimerTexts} 注入（生产实现走 ServerI18n，测试用纯文本桩），
 * 规划逻辑本身不做翻译查找。</p>
 */
public final class TimerPlanner {

    /** 一次性中央闪烁种类 */
    public enum FlashKind { START, PAUSE, RESUME, MILESTONE }

    /** 文案提供者：manager 生产实现 / 测试桩各自注入 */
    public interface TimerTexts {
        String barDown(String label, String time);
        String barUp(String label, String time);
        String paused(String label, String time);
        String finishedLine(String label);
        String upResult(String label, String time);
        String subtitlePaused(String label);
        String subtitleResumed(String label);
        String subtitleFinished(String label);
        String subtitleStopped(String label);
    }

    private TimerPlanner() {}

    // ==================== 常规帧 ====================

    /** RUNNING 常规帧：顶部/动作栏常态内容 + 按布局的中央决策 */
    public static TimerFrame running(CountdownSession s, long now, TimerTexts t) {
        String label = s.label();
        String time;
        if (s.mode() == TimerMode.DOWN) {
            time = CountdownTime.formatClock(s.remainingCeilSeconds(now));
        } else {
            time = CountdownTime.formatUpTenths(s.elapsedNanos(now));
        }
        String line = s.mode() == TimerMode.DOWN ? t.barDown(label, time) : t.barUp(label, time);
        return new TimerFrame(
                s.layout().hasBar() ? line : null,
                (float) s.progress(now),
                colorFor(s, s.mode() == TimerMode.DOWN ? s.remainingCeilSeconds(now) : -1),
                null, null,
                s.layout().hasActionbar() ? line : null,
                TimerFrame.Anim.CONTINUOUS);
    }

    /** PAUSED 帧：所有布局显示"已暂停"，时间冻结 */
    public static TimerFrame paused(CountdownSession s, long now, TimerTexts t) {
        String label = s.label();
        String time;
        if (s.mode() == TimerMode.DOWN) {
            time = CountdownTime.formatClock(s.remainingCeilSeconds(now));
        } else {
            time = CountdownTime.formatUpTenths(s.elapsedNanos(now));
        }
        String line = t.paused(label, time);
        return new TimerFrame(
                s.layout().hasBar() ? line : null,
                (float) s.progress(now),
                colorFor(s, s.mode() == TimerMode.DOWN ? s.remainingCeilSeconds(now) : -1),
                null, null,
                s.layout().hasActionbar() ? line : null,
                TimerFrame.Anim.CONTINUOUS);
    }

    /** 结果帧（3 秒窗口）：down 归零 / up 停止结算，颜色转结束色 */
    public static TimerFrame result(CountdownSession s, long now, TimerTexts t) {
        String label = s.label();
        if (s.mode() == TimerMode.DOWN) {
            String line = t.finishedLine(label);
            return new TimerFrame(
                    s.layout().hasBar() ? line : null,
                    0.0F,
                    colorFor(s, 0),
                    s.layout().hasCentral() ? CountdownTime.formatTitleNumber(0) : null,
                    s.layout().hasCentral() ? t.subtitleFinished(label) : null,
                    s.layout().hasActionbar() ? line : null,
                    TimerFrame.Anim.RESULT);
        }
        String finalText = CountdownTime.formatUpMillis(s.elapsedNanos(now));
        String line = t.upResult(label, finalText);
        return new TimerFrame(
                s.layout().hasBar() ? line : null,
                1.0F,
                colorFor(s, -1),
                s.layout().hasCentral() ? finalText : null,
                s.layout().hasCentral() ? t.subtitleStopped(label) : null,
                s.layout().hasActionbar() ? line : null,
                TimerFrame.Anim.RESULT);
    }

    /**
     * 中央阶段决策（bar/actionbar 内容由 running/paused 帧承担，本方法只管中央）。
     *
     * @param flashActive 是否处于一次性闪烁展示窗口（期间抑制低优先级常态中央）
     * @return 本帧中央内容；null = 不发中央
     */
    public static TimerFrame central(CountdownSession s, long now, boolean flashActive, TimerTexts t) {
        if (s.layout() == null || !s.layout().hasCentral()) {
            return null;
        }
        String label = s.label();
        if (s.state() == CountdownSession.State.PAUSED) {
            if (s.layout() == TimerLayout.BOTH) {
                // both 平时不占屏：暂停提示由一次性闪烁帧承担，过期后中央关闭
                return null;
            }
            // 暂停闪烁帧已带动画发出，期间不再重复发连续帧
            return flashActive ? null : pausedCentral(s, now, t, label);
        }
        if (s.state() != CountdownSession.State.RUNNING) {
            return null;
        }
        if (s.mode() == TimerMode.DOWN) {
            int sec = s.remainingCeilSeconds(now);
            if (s.layout() == TimerLayout.BOTH) {
                // both 仅在最后倒数窗口显示中央：simple/normal 5 秒，fancy 10 秒
                int window = s.style() == TimerStyle.FANCY ? 10 : 5;
                return sec <= window ? lastNumber(sec, label) : null;
            }
            // center 全程可见，最后 10 秒内改成单个剩余秒数
            if (sec <= 10) {
                return lastNumber(sec, label);
            }
        } else if (s.layout() == TimerLayout.BOTH) {
            // up 平时只显示顶部，不伪造"最后几秒"
            return null;
        }
        if (flashActive) {
            return null;
        }
        return continuousCentral(s, now, t, label);
    }

    /** CENTER 布局常态连续中央（大字时间 + 名称副标题） */
    private static TimerFrame continuousCentral(CountdownSession s, long now, TimerTexts t, String label) {
        String time = s.mode() == TimerMode.DOWN
                ? CountdownTime.formatClock(s.remainingCeilSeconds(now))
                : CountdownTime.formatUpTenths(s.elapsedNanos(now));
        return new TimerFrame(null, null, null, time, label, null, TimerFrame.Anim.CONTINUOUS);
    }

    /** 暂停连续中央：时间仍是主体，副标题显示已暂停 */
    private static TimerFrame pausedCentral(CountdownSession s, long now, TimerTexts t, String label) {
        String time = s.mode() == TimerMode.DOWN
                ? CountdownTime.formatClock(s.remainingCeilSeconds(now))
                : CountdownTime.formatUpTenths(s.elapsedNanos(now));
        return new TimerFrame(null, null, null, time, t.subtitlePaused(label), null, TimerFrame.Anim.CONTINUOUS);
    }

    /** both 最后倒数与 center 最后 10 秒：大字单个剩余秒数 */
    private static TimerFrame lastNumber(int sec, String label) {
        return new TimerFrame(null, null, null, CountdownTime.formatTitleNumber(sec), label, null, TimerFrame.Anim.CONTINUOUS);
    }

    // ==================== 一次性闪烁帧 ====================

    /**
     * 开始/暂停/恢复/节点的一次性中央帧（2 秒存在感，动画由 animate 控制）。
     * 仅中央允许的布局调用（bar/actionbar 布局绝不弹中央）。
     */
    public static TimerFrame flash(CountdownSession s, long now, FlashKind kind, long beatSeconds, TimerTexts t) {
        String label = s.label();
        return switch (kind) {
            case START -> new TimerFrame(null, null, null, currentTime(s, now), label, null, TimerFrame.Anim.FLASH);
            case PAUSE -> new TimerFrame(null, null, null, currentTime(s, now), t.subtitlePaused(label), null, TimerFrame.Anim.FLASH);
            case RESUME -> new TimerFrame(null, null, null, currentTime(s, now), t.subtitleResumed(label), null, TimerFrame.Anim.FLASH);
            case MILESTONE -> new TimerFrame(null, null, null,
                    CountdownTime.formatTitleNumber(beatSeconds), label, null, TimerFrame.Anim.FLASH);
        };
    }

    private static String currentTime(CountdownSession s, long now) {
        return s.mode() == TimerMode.DOWN
                ? CountdownTime.formatClock(s.remainingCeilSeconds(now))
                : CountdownTime.formatUpTenths(s.elapsedNanos(now));
    }

    // ==================== 配色 ====================

    /**
     * 进度条配色：simple 白；normal 青色、最后 10 秒黄、3 秒红；
     * fancy 60 秒内金、10 秒内黄、3 秒内红；结果转绿（simple 保持白）。up 无阈值分档。
     */
    public static TimerColor colorFor(CountdownSession s, int downSec) {
        if (s.legacy()) {
            return TimerColor.PURPLE;
        }
        if (s.state() == CountdownSession.State.RESULT) {
            return s.style() == TimerStyle.SIMPLE ? TimerColor.WHITE : TimerColor.GREEN;
        }
        if (s.mode() == TimerMode.UP) {
            return s.style() == TimerStyle.SIMPLE ? TimerColor.WHITE : TimerColor.CYAN;
        }
        return switch (s.style()) {
            case SIMPLE -> TimerColor.WHITE;
            case NORMAL -> downSec <= 3 ? TimerColor.RED : downSec <= 10 ? TimerColor.YELLOW : TimerColor.CYAN;
            case FANCY -> downSec <= 3 ? TimerColor.RED
                    : downSec <= 10 ? TimerColor.YELLOW
                    : downSec <= 60 ? TimerColor.GOLD
                    : TimerColor.CYAN;
        };
    }
}
