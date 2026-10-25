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
        return CountdownSession.start(60, EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE),
                "倒计时", 5L, true, null, now);
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
        assertEquals(CountdownSession.State.COMPLETING, s.state());
        // 完成阶段重复推进不再触发
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
        assertEquals(CountdownSession.State.COMPLETING, s.state());
        assertFalse(s.removeTime(10, t + 3 * SEC), "COMPLETING 下不再触发完成");
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
        assertEquals(CountdownSession.State.COMPLETING, s.state());
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
        CountdownSession s = CountdownSession.start(60, EnumSet.of(CountdownChannel.TITLE), "l", 5L, true, null, t);
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
        CountdownSession s = CountdownSession.start(60, EnumSet.of(CountdownChannel.BOSSBAR), "l", 5L,
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
                () -> CountdownSession.start(0, EnumSet.of(CountdownChannel.TITLE), "l", 5L, true, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CountdownSession.start(86401, EnumSet.of(CountdownChannel.TITLE), "l", 5L, true, null, 0));
    }

    @Test
    void milestonesArmBelowDurationAndFireOnCrossing() {
        // 5 分钟启动：1800/900/600 大于等于总时长被跳过，armed = {60, 30, 15}
        CountdownSession s = start300();
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
        CountdownSession s = startHour();
        assertEquals(0, s.advanceMilestones(3600));
        assertEquals(30, s.advanceMilestones(29));
        assertEquals(15, s.advanceMilestones(15));
        assertEquals(0, s.advanceMilestones(14), "被跨越节拍已静默清除，不补发");
    }

    @Test
    void addUpwardDoesNotRefireBeats() {
        CountdownSession s = start60Milestones();
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
        CountdownSession s = startHour();
        assertTrue(s.milestonesEnabled());
        assertTrue(s.soundEnabled());
        assertEquals(0, s.advanceMilestones(2000));
        s.setMilestonesEnabled(false, 2000);
        assertFalse(s.milestonesEnabled());
        assertEquals(0, s.advanceMilestones(1500), "禁用期间不触发");
        s.setMilestonesEnabled(true, 1500);
        // 重新武装：小于当前秒的节拍生效，已流逝的 1800 不再响
        assertEquals(900, s.advanceMilestones(900));
    }

    private static CountdownSession start300() {
        return CountdownSession.start(300, EnumSet.of(CountdownChannel.BOSSBAR), "l", 5L, true, null, 0);
    }

    private static CountdownSession startHour() {
        return CountdownSession.start(3600, EnumSet.of(CountdownChannel.BOSSBAR), "l", 5L, true, null, 0);
    }

    private static CountdownSession start60Milestones() {
        return CountdownSession.start(60, EnumSet.of(CountdownChannel.BOSSBAR), "l", 5L, true, null, 0);
    }
}
