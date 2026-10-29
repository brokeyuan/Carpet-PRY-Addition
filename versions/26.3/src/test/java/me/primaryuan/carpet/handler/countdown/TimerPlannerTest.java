package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerPlannerTest {

    private static final long SEC = 1_000_000_000L;

    private static final TimerPlanner.TimerTexts T = new TimerPlanner.TimerTexts() {
        @Override
        public String barDown(String label, String time) {
            return label + " 剩余 " + time;
        }

        @Override
        public String barUp(String label, String time) {
            return label + " 已用 " + time;
        }

        @Override
        public String paused(String label, String time) {
            return label + " 已暂停 " + time;
        }

        @Override
        public String finishedLine(String label) {
            return label + " 计时结束";
        }

        @Override
        public String upResult(String label, String time) {
            return label + " 已用 " + time;
        }

        @Override
        public String subtitlePaused(String label) {
            return label + " 已暂停";
        }

        @Override
        public String subtitleResumed(String label) {
            return label + " 已继续";
        }

        @Override
        public String subtitleFinished(String label) {
            return label + " 已结束";
        }

        @Override
        public String subtitleStopped(String label) {
            return label + " 已停止";
        }
    };

    private static CountdownSession down(long seconds, TimerStyle style, TimerLayout layout, long now) {
        return CountdownSession.startDown(seconds, style, layout, "倒计时", false, true, null, now);
    }

    // ==================== 布局渠道约束 ====================

    @Test
    void barAndActionbarLayoutsNeverEmitCentral() {
        for (TimerLayout layout : EnumSet.of(TimerLayout.BAR, TimerLayout.ACTIONBAR)) {
            CountdownSession s = down(300, TimerStyle.FANCY, layout, 0);
            TimerFrame run = TimerPlanner.running(s, 0, T);
            assertNull(run.titleText(), layout + " 常态不发中央");
            // 开场/节点/暂停闪烁：中央不允许的布局 flash 帧也不该出现（由管理器跳过，
            // central() 决策恒为 null）
            assertNull(TimerPlanner.central(s, 0, false, T), layout + " 无中央决策");
            // 进入最后倒数窗口同样不发中央
            assertNull(TimerPlanner.central(s, 299_500_000_000L, false, T), layout + " 窗口内仍无中央");
        }
    }

    @Test
    void barLayoutCarriesBarAndActionContent() {
        CountdownSession bar = down(60, TimerStyle.NORMAL, TimerLayout.BAR, 0);
        TimerFrame f = TimerPlanner.running(bar, 0, T);
        assertNotNull(f.barText());
        assertNull(f.actionText(), "bar 布局无动作栏");
        assertEquals("倒计时 剩余 01:00", f.barText());
        assertEquals(TimerColor.CYAN, f.barColor());

        CountdownSession action = down(60, TimerStyle.NORMAL, TimerLayout.ACTIONBAR, 0);
        TimerFrame fa = TimerPlanner.running(action, 0, T);
        assertNull(fa.barText());
        assertEquals("倒计时 剩余 01:00", fa.actionText());
    }

    @Test
    void upBarUsesElapsedTextAndFullBar() {
        CountdownSession s = CountdownSession.startUp(TimerStyle.NORMAL, TimerLayout.BAR, "计时", false, true, null, 0);
        TimerFrame f = TimerPlanner.running(s, 1_234_000_000L, T);
        assertEquals("计时 已用 00:01.2", f.barText());
        assertEquals(1.0F, f.barProgress(), "正计时恒满条");
        assertEquals(TimerColor.CYAN, f.barColor());
    }

    // ==================== 中央窗口 ====================

    @Test
    void centerShowsClockThenSingleSeconds() {
        CountdownSession s = down(300, TimerStyle.SIMPLE, TimerLayout.CENTER, 0);
        TimerFrame c = TimerPlanner.central(s, 0, false, T);
        assertNotNull(c);
        assertEquals("05:00", c.titleText());
        assertEquals("倒计时", c.subtitleText());

        TimerFrame last = TimerPlanner.central(s, 293_000_000_000L, false, T);
        assertEquals("7", last.titleText(), "最后 10 秒改为单个剩余秒数");
        assertEquals(TimerFrame.Anim.CONTINUOUS, last.anim());
    }

    @Test
    void bothWindowByStyle() {
        CountdownSession simple = down(60, TimerStyle.SIMPLE, TimerLayout.BOTH, 0);
        assertNull(TimerPlanner.central(simple, 0, false, T), "窗口外不占屏");
        assertNotNull(TimerPlanner.central(simple, 56_000_000_000L, false, T), "simple 最后 5 秒");
        assertNull(TimerPlanner.central(simple, 54_500_000_000L, false, T), "剩余 5.5s 向上取整为 6 → 窗口外");

        CountdownSession fancy = down(60, TimerStyle.FANCY, TimerLayout.BOTH, 0);
        assertNotNull(TimerPlanner.central(fancy, 51_000_000_000L, false, T), "fancy 最后 10 秒");
        assertEquals("10", TimerPlanner.central(fancy, 50_000_000_000L, false, T).titleText());
        assertNull(TimerPlanner.central(fancy, 0, false, T));
    }

    @Test
    void upBothRunningHasNoCentralButDownDownDoesNotLeak() {
        CountdownSession up = CountdownSession.startUp(TimerStyle.FANCY, TimerLayout.BOTH, "计时", false, true, null, 0);
        assertNull(TimerPlanner.central(up, 10 * SEC, false, T), "up 平时只显示顶部，不伪造最后几秒");
    }

    @Test
    void flashSuppressesContinuousButNotLastNumber() {
        CountdownSession center = down(300, TimerStyle.FANCY, TimerLayout.CENTER, 0);
        assertNull(TimerPlanner.central(center, 0, true, T), "节点闪烁期间抑制常态中央");
        // 最后数字优先于闪烁
        assertEquals("9", TimerPlanner.central(center, 291_000_000_000L, true, T).titleText());

        CountdownSession up = CountdownSession.startUp(TimerStyle.NORMAL, TimerLayout.CENTER, "计时", false, true, null, 0);
        assertNull(TimerPlanner.central(up, 0, true, T));
        assertEquals("00:00.0", TimerPlanner.central(up, 0, false, T).titleText());
    }

    // ==================== 暂停 ====================

    @Test
    void pausedFramesFreezeAndMarkStatus() {
        CountdownSession both = down(60, TimerStyle.NORMAL, TimerLayout.BOTH, 0);
        both.pause(28_300_000_000L);
        // both 暂停平时不占屏（暂停提示为一次性闪烁）
        assertNull(TimerPlanner.central(both, 40 * SEC, false, T));
        TimerFrame flash = TimerPlanner.flash(both, 28_300_000_000L, TimerPlanner.FlashKind.PAUSE, 0, T);
        assertEquals("00:32", flash.titleText(), "暂停闪烁冻结时间");
        assertEquals("倒计时 已暂停", flash.subtitleText());
        assertEquals(TimerFrame.Anim.FLASH, flash.anim());
        // 顶栏与动作栏（如启用）显示已暂停
        CountdownSession bar = down(60, TimerStyle.NORMAL, TimerLayout.BAR, 0);
        bar.pause(28_300_000_000L);
        assertEquals("倒计时 已暂停 00:32", TimerPlanner.paused(bar, 100 * SEC, T).barText(), "暂停期时钟漂移不改变文本");

        CountdownSession center = down(60, TimerStyle.NORMAL, TimerLayout.CENTER, 0);
        center.pause(28_300_000_000L);
        assertEquals("倒计时 已暂停", TimerPlanner.central(center, 40 * SEC, false, T).subtitleText());
    }

    @Test
    void upPauseFreezesElapsedText() {
        CountdownSession s = CountdownSession.startUp(TimerStyle.SIMPLE, TimerLayout.ACTIONBAR, "计时", false, true, null, 0);
        s.pause(2_000_000_000L);
        TimerFrame f = TimerPlanner.paused(s, 100 * SEC, T);
        assertEquals("计时 已暂停 00:02.0", f.actionText());
    }

    // ==================== 闪烁与结果 ====================

    @Test
    void startAndResumeFlashContent() {
        CountdownSession s = down(60, TimerStyle.FANCY, TimerLayout.BOTH, 0);
        TimerFrame start = TimerPlanner.flash(s, 0, TimerPlanner.FlashKind.START, 0, T);
        assertEquals("01:00", start.titleText());
        assertEquals("倒计时", start.subtitleText());

        s.pause(10 * SEC);
        s.resume(20 * SEC);
        TimerFrame resume = TimerPlanner.flash(s, 20 * SEC, TimerPlanner.FlashKind.RESUME, 0, T);
        assertEquals("00:50", resume.titleText(), "恢复闪烁显示当前剩余");
        assertEquals("倒计时 已继续", resume.subtitleText());
    }

    @Test
    void milestoneFlashShowsBeat() {
        CountdownSession s = down(300, TimerStyle.FANCY, TimerLayout.BOTH, 0);
        TimerFrame f = TimerPlanner.flash(s, 240 * SEC, TimerPlanner.FlashKind.MILESTONE, 60, T);
        assertEquals("01:00", f.titleText(), "节拍大字沿用旧格式：60 秒显示 01:00，30 秒显示 30");
        assertEquals(TimerFrame.Anim.FLASH, f.anim());
        TimerFrame f30 = TimerPlanner.flash(s, 240 * SEC, TimerPlanner.FlashKind.MILESTONE, 30, T);
        assertEquals("30", f30.titleText());
        assertEquals(TimerFrame.Anim.FLASH, f.anim());
    }

    @Test
    void downResultFrame() {
        CountdownSession simple = down(60, TimerStyle.SIMPLE, TimerLayout.BAR, 0);
        simple.advanceRunning(60 * SEC); // 归零
        TimerFrame f = TimerPlanner.result(simple, 60 * SEC, T);
        assertEquals("倒计时 计时结束", f.barText());
        assertEquals(0.0F, f.barProgress());
        assertNull(f.titleText(), "bar 布局结果不发中央");
        assertEquals(TimerColor.WHITE, f.barColor(), "simple 结果保持白");
        assertEquals(TimerFrame.Anim.RESULT, f.anim());

        CountdownSession both = down(60, TimerStyle.NORMAL, TimerLayout.BOTH, 0);
        both.advanceRunning(60 * SEC);
        TimerFrame fb = TimerPlanner.result(both, 60 * SEC, T);
        assertEquals("0", fb.titleText(), "真正结束才显示 0");
        assertEquals("倒计时 已结束", fb.subtitleText());
        assertEquals(TimerColor.GREEN, fb.barColor());
    }

    @Test
    void upResultFrameShowsMillis() {
        CountdownSession s = CountdownSession.startUp(TimerStyle.FANCY, TimerLayout.BAR, "计时", false, true, null, 0);
        s.stopToResult(5_123_456_789L);
        TimerFrame f = TimerPlanner.result(s, 100 * SEC, T);
        assertEquals("计时 已用 00:05.123", f.barText());
        assertEquals(1.0F, f.barProgress());
        assertEquals(TimerColor.GREEN, f.barColor());
        assertNull(f.titleText(), "bar 布局不发中央");
        CountdownSession center = CountdownSession.startUp(TimerStyle.FANCY, TimerLayout.CENTER, "计时", false, true, null, 0);
        center.stopToResult(5_123_456_789L);
        assertEquals("00:05.123", TimerPlanner.result(center, 100 * SEC, T).titleText());
        assertEquals("计时 已停止", TimerPlanner.result(center, 100 * SEC, T).subtitleText());
    }

    // ==================== 配色分档 ====================

    @Test
    void colorThresholds() {
        CountdownSession normal = down(3600, TimerStyle.NORMAL, TimerLayout.BAR, 0);
        assertEquals(TimerColor.CYAN, TimerPlanner.colorFor(normal, 11));
        assertEquals(TimerColor.YELLOW, TimerPlanner.colorFor(normal, 10));
        assertEquals(TimerColor.RED, TimerPlanner.colorFor(normal, 3));

        CountdownSession fancy = down(3600, TimerStyle.FANCY, TimerLayout.BAR, 0);
        assertEquals(TimerColor.CYAN, TimerPlanner.colorFor(fancy, 61));
        assertEquals(TimerColor.GOLD, TimerPlanner.colorFor(fancy, 60));
        assertEquals(TimerColor.YELLOW, TimerPlanner.colorFor(fancy, 10));
        assertEquals(TimerColor.RED, TimerPlanner.colorFor(fancy, 3));

        CountdownSession simple = down(3600, TimerStyle.SIMPLE, TimerLayout.BAR, 0);
        assertEquals(TimerColor.WHITE, TimerPlanner.colorFor(simple, 3));

        CountdownSession legacy = CountdownSession.startLegacy(60, "l", EnumSet.of(CountdownChannel.BOSSBAR), true, null, 0);
        assertEquals(TimerColor.PURPLE, TimerPlanner.colorFor(legacy, 30), "旧入口固定紫色");
    }

    // ==================== 结果内容（聊天侧由管理器承担，此处只验帧） ====================

    @Test
    void resultOnlyFromTerminalStates() {
        CountdownSession s = down(60, TimerStyle.NORMAL, TimerLayout.BAR, 0);
        // RUNNING 时 central() 不给结果内容
        assertTrue(TimerPlanner.central(s, 0, false, T) == null);
        assertFalse(TimerPlanner.running(s, 0, T).hasCentral());
    }
}
