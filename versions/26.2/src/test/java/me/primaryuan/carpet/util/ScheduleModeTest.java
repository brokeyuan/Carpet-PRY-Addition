package me.primaryuan.carpet.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ScheduleMode#nextDelay} 的语义契约（dropall / sendto 两个调度器共用） */
class ScheduleModeTest {

    @Test
    void continuousAlwaysOneTick() {
        assertEquals(1, ScheduleMode.CONTINUOUS.nextDelay(7, 3, 9, true));
        assertEquals(1, ScheduleMode.CONTINUOUS.nextDelay(7, 3, 9, false));
    }

    @Test
    void intervalAndPerTickUseConfiguredInterval() {
        assertEquals(40, ScheduleMode.INTERVAL.nextDelay(40, 0, 0, true));
        assertEquals(40, ScheduleMode.INTERVAL.nextDelay(40, 0, 0, false));
        assertEquals(2, ScheduleMode.PERTICK.nextDelay(2, 0, 0, false));
    }

    @Test
    void afterEndsOnlyOnSuccess() {
        assertEquals(-1, ScheduleMode.AFTER.nextDelay(10, 0, 0, true));
        assertEquals(1, ScheduleMode.AFTER.nextDelay(10, 0, 0, false));
    }

    @Test
    void noneEndsImmediately() {
        assertEquals(-1, ScheduleMode.NONE.nextDelay(10, 0, 0, true));
        assertEquals(-1, ScheduleMode.NONE.nextDelay(10, 0, 0, false));
    }

    @Test
    void randomDelayStaysWithinBounds() {
        for (int i = 0; i < 200; i++) {
            int delay = ScheduleMode.RANDOMLY.nextDelay(0, 5, 9, true);
            assertTrue(delay >= 5 && delay <= 9, "延迟越界 [5,9]: " + delay);
        }
    }

    @Test
    void randomWithEqualBoundsIsConstant() {
        assertEquals(7, ScheduleMode.RANDOMLY.nextDelay(0, 7, 7, true));
    }

    @Test
    void perTickIntervalConversion() {
        assertEquals(20, ScheduleMode.perTickInterval(1));
        assertEquals(10, ScheduleMode.perTickInterval(2));
        assertEquals(1, ScheduleMode.perTickInterval(20));
        assertEquals(1, ScheduleMode.perTickInterval(100)); // 超频至少隔 1 tick
        assertEquals(20, ScheduleMode.perTickInterval(0));  // 非法输入按最慢频率处理（防除零）
    }
}
