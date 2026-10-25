package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountdownTimeTest {

    @Test
    void parsesPlainAndUnitForms() {
        assertEquals(60, CountdownTime.parseSeconds("60"));
        assertEquals(60, CountdownTime.parseSeconds("60s"));
        assertEquals(60, CountdownTime.parseSeconds("1m"));
        assertEquals(90, CountdownTime.parseSeconds("1m30s"));
        assertEquals(7200, CountdownTime.parseSeconds("2h"));
        assertEquals(5400, CountdownTime.parseSeconds("1h30m"));
        assertEquals(3600, CountdownTime.parseSeconds("1h"));
        assertEquals(90, CountdownTime.parseSeconds(" 1M30S "));
    }

    @Test
    void parsesBoundaries() {
        assertEquals(1, CountdownTime.parseSeconds("1"));
        assertEquals(86400, CountdownTime.parseSeconds("86400"));
        assertEquals(86400, CountdownTime.parseSeconds("24h"));
        assertEquals(86400, CountdownTime.parseSeconds("1440m"));
    }

    @Test
    void rejectsInvalidInput() {
        String[] bad = {"0", "-1", "+60", "abc", "1x", "1.5h", "1m1m", "30s1m", "1m0s",
                "86401", "24h1s", "", "   ", "s", "1m30", "h", "1h30", "１", "0m30s", "1e3"};
        for (String input : bad) {
            assertThrows(IllegalArgumentException.class, () -> CountdownTime.parseSeconds(input),
                    () -> "应拒绝: " + input);
        }
    }

    @Test
    void ceilsNanosWithoutEarlyZero() {
        assertEquals(0, CountdownTime.ceilSeconds(0));
        assertEquals(0, CountdownTime.ceilSeconds(-1));
        assertEquals(1, CountdownTime.ceilSeconds(300_000_000L));
        assertEquals(1, CountdownTime.ceilSeconds(1_000_000_000L));
        assertEquals(2, CountdownTime.ceilSeconds(1_200_000_000L));
    }

    @Test
    void formatsClock() {
        assertEquals("00:05", CountdownTime.formatClock(5));
        assertEquals("01:05", CountdownTime.formatClock(65));
        assertEquals("01:00:00", CountdownTime.formatClock(3600));
        assertEquals("01:01:01", CountdownTime.formatClock(3661));
        assertEquals("00:00", CountdownTime.formatClock(0));
        assertEquals("00:00", CountdownTime.formatClock(-3));
    }

    @Test
    void formatsTitleNumbers() {
        assertEquals("5", CountdownTime.formatTitleNumber(5));
        assertEquals("59", CountdownTime.formatTitleNumber(59));
        assertEquals("01:00", CountdownTime.formatTitleNumber(60));
        assertEquals("01:05", CountdownTime.formatTitleNumber(65));
    }
}
