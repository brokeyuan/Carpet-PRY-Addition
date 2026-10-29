package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountdownSessionTest {

    private static final long SEC = 1_000_000_000L;

    private static CountdownSession start60(long now) {
        return CountdownSession.startLegacy(60, "倒计时", EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE),
                true, null, now);
    }

    @Test
    void ticksDownAndCompletesOnce() {
        long t = 0;
        CountdownSession s = start60(t);
        assertEquals(CountdownSession.State.RUNNING, s.state());
        assertEquals(60, s.advanceRunning(t));
        assertEquals(59, s.advanceRunning(t + SEC));
        // 剩余 0.5 秒仍显示 1，不提前出现 00:00
        assertEquals(1, s.advanceRunning(t + 59 * SEC + 500_000_000L));
        assertEquals(-1, s.advanceRunning(t + 60 * SEC));
        assertEquals(CountdownSession.State.RESULT, s.state());
        // 结果阶段重复推进不再触发
        assertEquals(-2, s.advanceRunning(t + 61 * SEC));
        assertEquals(0, s.remainingCeilSeconds(t + 61 * SEC));
    }

    @Test
    void pauseFreezesAndResumeContinues() {
        long t = 0;
        CountdownSession s = start60(t);
        s.pause(t + 28_300_000_000L);
        assertEquals(CountdownSession.State.PAUSED, s.state());
        assertEquals(32, s.remainingCeilSeconds(t + 28_300_000_000L));
        // 暂停期间时钟继续走，剩余不变
        assertEquals(32, s.remainingCeilSeconds(t + 40 * SEC));
        // 暂停中不能再次暂停
        assertThrows(IllegalStateException.class, () -> s.pause(t + 41 * SEC));
        s.resume(t + 100 * SEC);
        assertEquals(CountdownSession.State.RUNNING, s.state());
        // 从冻结点继续，而非重新计时
        assertEquals(32, s.remainingCeilSeconds(t + 100 * SEC));
        assertEquals(31, s.advanceRunning(t + 100 * SEC + 700_000_000L));
        // 运行中不能恢复
        assertThrows(IllegalStateException.class, () -> s.resume(t + 101 * SEC));
    }

    @Test
    void stopWorksFromAllActiveStates() {
        long t = 0;
        CountdownSession s = start60(t);
        assertTrue(s.stop());
        assertEquals(CountdownSession.State.IDLE, s.state());
        assertFalse(s.stop());
    }

    @Test
    void setTimeKeepsStateAndExtendsDenominator() {
        long t = 0;
        CountdownSession s = start60(t);
        s.setTime(30, t + 10 * SEC);
        assertEquals(CountdownSession.State.RUNNING, s.state());
        assertEquals(30, s.remainingCeilSeconds(t + 10 * SEC));
        assertEquals(0.5, s.progress(t + 10 * SEC), 1e-9); // 30/60
        // 设为超过原总时长：分母同步扩展
        s.setTime(100, t + 11 * SEC);
        assertEquals(100, s.remainingCeilSeconds(t + 11 * SEC));
        assertEquals(1.0, s.progress(t + 11 * SEC), 1e-9);
        // 分母不收缩：set 回小值
        s.setTime(50, t + 12 * SEC);
        assertEquals(0.5, s.progress(t + 12 * SEC), 1e-9);
    }

    @Test
    void addExtendsAndRemoveCompletesOnce() {
        long t = 0;
        CountdownSession s = start60(t);
        s.addTime(30, t);
        assertEquals(90, s.remainingCeilSeconds(t));
        assertEquals(1.0, s.progress(t), 1e-9); // 剩余 90 / 分母扩展至 90
        assertEquals(60, s.durationSeconds(), "启动总时长展示不变");
        // 一秒后剩余 89 / 分母 90，进度不越界
        assertEquals(89.0 / 90.0, s.progress(t + SEC), 1e-9);
        // 减到归零：完成一次
        assertTrue(s.removeTime(1000, t + 2 * SEC));
        assertEquals(CountdownSession.State.RESULT, s.state());
        assertFalse(s.removeTime(10, t + 3 * SEC), "RESULT 下不再触发完成");
        // IDLE 下修改被拒
        s.stop();
        assertThrows(IllegalStateException.class, () -> s.setTime(30, t + 4 * SEC));
    }

    @Test
    void removeWhilePausedCompletes() {
        long t = 0;
        CountdownSession s = start60(t);
        s.pause(t + 10 * SEC);
        assertTrue(s.removeTime(100, t + 11 * SEC));
        assertEquals(CountdownSession.State.RESULT, s.state());
    }

    @Test
    void adjustWhilePausedKeepsPaused() {
        long t = 0;
        CountdownSession s = start60(t);
        s.pause(t + 5 * SEC);
        s.setTime(30, t + 6 * SEC);
        assertEquals(CountdownSession.State.PAUSED, s.state());
        assertEquals(30, s.remainingCeilSeconds(t + 60 * SEC), "暂停期时钟漂移不影响冻结值");
        s.resume(t + 60 * SEC);
        // 剩余 29.2 秒向上取整显示 30，走过整点后显示 29
        assertEquals(30, s.advanceRunning(t + 60 * SEC + 800_000_000L));
        assertEquals(29, s.advanceRunning(t + 60 * SEC + 1_200_000_000L));
    }

    @Test
    void lastWindowEvaluation() {
        long t = 0;
        CountdownSession s = CountdownSession.startLegacy(60, "l", EnumSet.of(CountdownChannel.TITLE), true, null, t);
        // 旧入口默认窗口最后 10 秒
        assertTrue(s.inLastWindow(10));
        assertFalse(s.inLastWindow(11));
        s.setLastWindow(5L);
        assertFalse(s.inLastWindow(6));
        assertTrue(s.inLastWindow(5));
        s.setLastWindow(null);
        assertTrue(s.inLastWindow(60));
        s.setLastWindow(3L);
        assertTrue(s.inLastWindow(3));
        assertFalse(s.inLastWindow(4));
    }

    @Test
    void targetsSnapshotAndDynamic() {
        long t = 0;
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CountdownSession s = CountdownSession.startLegacy(60, "l", EnumSet.of(CountdownChannel.BOSSBAR),
                false, Set.of(a), t);
        assertTrue(s.shouldDisplay(a));
        assertFalse(s.shouldDisplay(b));
        assertTrue(s.snapshotTargets().contains(a));
        s.setTargets(true, Set.of(b));
        assertTrue(s.shouldDisplay(a));
        assertTrue(s.shouldDisplay(b));
    }

    @Test
    void rejectsInvalidDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> CountdownSession.startLegacy(0, "l", EnumSet.of(CountdownChannel.TITLE), true, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CountdownSession.startLegacy(86401, "l", EnumSet.of(CountdownChannel.TITLE), true, null, 0));
    }

    @Test
    void milestonesArmBelowDurationAndFireOnCrossing() {
        // 5 分钟启动：1800/900/600 大于等于总时长被跳过，armed = {60, 30, 15}
        CountdownSession s = startLegacySeconds(300);
        assertEquals(0, s.advanceMilestones(300), "启动点本身不触发节拍");
        assertEquals(0, s.advanceMilestones(240));
        assertEquals(0, s.advanceMilestones(61));
        assertEquals(60, s.advanceMilestones(60));
        assertEquals(0, s.advanceMilestones(59));
        assertEquals(30, s.advanceMilestones(30));
        assertEquals(0, s.advanceMilestones(16));
        assertEquals(15, s.advanceMilestones(15));
        assertEquals(0, s.advanceMilestones(14));
    }

    @Test
    void jumpCrossingFiresClosestBeatOnly() {
        // 1 小时启动后 set time 跳到 29 秒：跨过 1800..30 六个节拍，只发最接近落点的 30
        CountdownSession s = startLegacySeconds(3600);
        assertEquals(0, s.advanceMilestones(3600));
        assertEquals(30, s.advanceMilestones(29));
        assertEquals(15, s.advanceMilestones(15));
        assertEquals(0, s.advanceMilestones(14), "被跨越节拍已静默清除，不补发");
    }

    @Test
    void addUpwardDoesNotRefireBeats() {
        CountdownSession s = startLegacySeconds(60);
        assertEquals(0, s.advanceMilestones(60));
        assertEquals(30, s.advanceMilestones(30));
        // add 后时间变长：基线上调，已触发的 30 不回滚
        assertEquals(0, s.advanceMilestones(130));
        assertEquals(15, s.advanceMilestones(15));
        assertEquals(0, s.advanceMilestones(130));
        assertEquals(0, s.advanceMilestones(29), "30 已触发过，二次下探不再响");
    }

    @Test
    void milestoneAndSoundSwitches() {
        CountdownSession s = startLegacySeconds(3600);
        assertTrue(s.milestonesEnabled());
        assertEquals(TimerSoundPlan.Tier.LEGACY, s.soundTier());
        assertEquals(0, s.advanceMilestones(2000));
        s.setMilestonesEnabled(false, 2000);
        assertFalse(s.milestonesEnabled());
        assertEquals(0, s.advanceMilestones(1500), "禁用期间不触发");
        s.setMilestonesEnabled(true, 1500);
        // 重新武装：小于当前秒的节拍生效，已流逝的 1800 不再响
        assertEquals(900, s.advanceMilestones(900));
    }

    @Test
    void styleSwitchDoesNotRefireBeats() {
        long t = 0;
        CountdownSession s = CountdownSession.startDown(300, TimerStyle.FANCY, TimerLayout.BOTH, "l", false,
                true, null, t);
        assertEquals(0, s.advanceMilestones(300));
        assertEquals(60, s.advanceMilestones(60));
        // 档位切换后重新武装：已触发的 60 不回滚重播，未触发的 30 正常经过触发
        s.setStyle(TimerStyle.NORMAL, t + SEC);
        s.setStyle(TimerStyle.FANCY, t + 2 * SEC);
        assertEquals(0, s.advanceMilestones(31));
        assertEquals(30, s.advanceMilestones(30));
        assertEquals(0, s.advanceMilestones(29));
    }

    // ==================== 正计时（UP） ====================

    @Test
    void upAccumulatesAndPausesWithoutDrift() {
        long t = 0;
        CountdownSession s = CountdownSession.startUp(TimerStyle.SIMPLE, TimerLayout.ACTIONBAR, "计时",
                false, true, null, t);
        assertEquals(TimerMode.UP, s.mode());
        assertEquals(0, s.elapsedNanos(t));
        assertEquals(1_500_000_000L, s.elapsedNanos(t + 1_500_000_000L));
        // 暂停 10 秒不增加
        s.pause(t + 2 * SEC);
        assertEquals(2_000_000_000L, s.elapsedNanos(t + 12 * SEC));
        assertEquals(2_000_000_000L, s.elapsedNanos(t + 30 * SEC), "暂停期间时钟继续走不累计");
        // 恢复后从冻结点继续累计
        s.resume(t + 40 * SEC);
        assertEquals(2_000_000_000L, s.elapsedNanos(t + 40 * SEC));
        assertEquals(2_500_000_000L, s.elapsedNanos(t + 40 * SEC + 500_000_000L));
        assertEquals(TimerSoundPlan.Tier.NONE, s.soundTier(), "simple 默认无声");
        // 运行中不能恢复
        assertThrows(IllegalStateException.class, () -> s.resume(t + 41 * SEC));
    }

    @Test
    void upStopFreezesFinalElapsedOnce() {
        long t = 0;
        CountdownSession s = CountdownSession.startUp(TimerStyle.NORMAL, TimerLayout.BAR, "计时",
                false, true, null, t);
        s.pause(t + 3_200_000_000L);
        s.resume(t + 10 * SEC);
        s.stopToResult(t + 12_345_678_912L);
        assertEquals(CountdownSession.State.RESULT, s.state());
        // 最终用时 = 3.2s + 2.345678912s，冻结且只结算一次
        assertEquals(5_545_678_912L, s.elapsedNanos(t + 100 * SEC));
        assertEquals(5_545_678_912L, s.elapsedNanos(t + 200 * SEC));
        assertEquals(1.0, s.progress(t + 100 * SEC), 1e-9, "正计时恒满条");
    }

    @Test
    void upHasNoDurationCapAndNoMilestones() {
        long t = 0;
        CountdownSession s = CountdownSession.startUp(TimerStyle.FANCY, TimerLayout.BOTH, "计时",
                false, true, null, t);
        // 超过 24 小时不回卷
        assertEquals(25L * 3600 * SEC, s.elapsedNanos(t + 25L * 3600 * SEC));
        assertEquals(0, s.advanceMilestones(12345), "正计时不触发倒数节拍");
    }

    @Test
    void upRejectsTimeAdjustment() {
        long t = 0;
        CountdownSession s = CountdownSession.startUp(TimerStyle.SIMPLE, TimerLayout.ACTIONBAR, "计时",
                false, true, null, t);
        assertThrows(IllegalStateException.class, () -> s.setTime(30, t));
        assertThrows(IllegalStateException.class, () -> s.addTime(30, t));
        assertThrows(IllegalStateException.class, () -> s.removeTime(30, t));
    }

    @Test
    void soundTierOverrides() {
        long t = 0;
        CountdownSession simple = CountdownSession.startDown(60, TimerStyle.SIMPLE, TimerLayout.BAR, "l",
                false, true, null, t);
        assertEquals(TimerSoundPlan.Tier.NONE, simple.soundTier());
        // silent 覆盖一切
        CountdownSession fancy = CountdownSession.startDown(60, TimerStyle.FANCY, TimerLayout.BOTH, "l",
                true, true, null, t);
        assertEquals(TimerSoundPlan.Tier.NONE, fancy.soundTier());
        // 显式 sound on 将 simple 提升到 NORMAL；显式 off 压过 fancy
        simple.setSoundOverride(true);
        assertEquals(TimerSoundPlan.Tier.NORMAL, simple.soundTier());
        fancy.setSoundOverride(false);
        assertEquals(TimerSoundPlan.Tier.NONE, fancy.soundTier());
    }

    @Test
    void layoutChannelsDerivation() {
        long t = 0;
        CountdownSession s = CountdownSession.startDown(60, TimerStyle.NORMAL, TimerLayout.BOTH, "l",
                false, true, null, t);
        assertEquals(EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE, CountdownChannel.SUBTITLE),
                s.channels());
        s.setLayout(TimerLayout.BAR);
        assertEquals(EnumSet.of(CountdownChannel.BOSSBAR), s.channels());
        assertTrue(s.layout().hasBar());
        assertFalse(s.layout().hasCentral());
    }

    private static CountdownSession startLegacySeconds(int seconds) {
        return CountdownSession.startLegacy(seconds, "l", EnumSet.of(CountdownChannel.BOSSBAR), true, null, 0);
    }
}
