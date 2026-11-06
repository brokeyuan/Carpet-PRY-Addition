package me.primaryuan.carpet.handler.fakegames;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连续接触计数：目标切换、中断、多人同时接触与“每 tick 每人最多 +1”。 */
class SustainCounterTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void onlyConsecutiveTicksReachThreshold() {
        SustainCounter counter = new SustainCounter(5);
        for (int i = 0; i < 4; i++) {
            assertTrue(counter.update(List.of(A)).isEmpty());
        }
        assertEquals(Set.of(A), counter.update(List.of(A)));
    }

    @Test
    void missedTickResetsCount() {
        SustainCounter counter = new SustainCounter(5);
        for (int i = 0; i < 4; i++) {
            counter.update(List.of(A));
        }
        assertEquals(0, counter.update(List.of()).size());
        for (int i = 0; i < 4; i++) {
            assertTrue(counter.update(List.of(A)).isEmpty());
        }
        assertEquals(Set.of(A), counter.update(List.of(A)));
    }

    @Test
    void switchingTargetDoesNotCarryCount() {
        SustainCounter counter = new SustainCounter(5);
        for (int i = 0; i < 4; i++) {
            counter.update(List.of(A));
        }
        // B 接触一 tick 时 A 已离开，A 的旧计数必须清零
        counter.update(List.of(B));
        assertTrue(counter.update(List.of(A)).isEmpty());
        for (int i = 0; i < 3; i++) {
            counter.update(List.of(A));
        }
        assertEquals(Set.of(A), counter.update(List.of(A)));
    }

    @Test
    void tenTickWindowDoesNotCarryAcrossTargets() {
        SustainCounter counter = new SustainCounter(10);
        for (int i = 0; i < 9; i++) {
            counter.update(List.of(A));
        }
        counter.update(List.of(B));
        assertTrue(counter.update(List.of(A)).isEmpty());
    }

    @Test
    void multipleTargetsAccumulateIndependently() {
        SustainCounter counter = new SustainCounter(3);
        assertTrue(counter.update(List.of(A, B)).isEmpty());
        assertTrue(counter.update(List.of(A, B)).isEmpty());
        assertEquals(Set.of(A, B), counter.update(List.of(A, B)));
    }
}
