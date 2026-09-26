package me.primaryuan.carpet.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TppCommand#buildFakePlayerName} 的取舍优先级契约：
 * 站点完整（必须）→ "_" 分隔符（可省略）→ 玩家名尽可能多，总长 ≤ 16。
 */
class FakePlayerNameTest {

    private static String build(String player, String station) {
        return TppCommand.buildFakePlayerName(player, station);
    }

    @Test
    void normalCaseKeepsFullPlayerNameAndStation() {
        assertEquals("Steve_home", build("Steve", "home"));
    }

    @Test
    void cjkNamesCountedByChars() {
        assertEquals("玩家一號_站點一號", build("玩家一號", "站點一號"));
    }

    @Test
    void playerTruncatedToRemainingBudget() {
        // 站点 11 字符 → 玩家名预算 = 16 - 11 - 1 = 4
        assertEquals("Long_netherhub01", build("LongPlayerName", "netherhub01"));
    }

    @Test
    void budgetOneTruncatesPlayerToSingleChar() {
        // 站点 14 字符 → 预算 1
        assertEquals("S_12345678901234", build("Steve", "12345678901234"));
        assertEquals(16, build("Steve", "12345678901234").length());
    }

    @Test
    void stationNearlyFullDropsPlayerAndSeparator() {
        // 站点 15 字符 → 预算 0 → 无玩家名、无分隔符
        assertEquals("123456789012345", build("Steve", "123456789012345"));
    }

    @Test
    void stationExactlyFullIsReturnedAsIs() {
        // 站点 16 字符 → 玩家名 0 字符，所有玩家共用站点名本身
        String station = "1234567890123456";
        assertEquals(station, build("Steve", station));
        assertEquals(station, build("Alex", station));
        assertEquals(16, station.length());
    }

    @Test
    void totalNeverExceedsLimit() {
        String[] stations = {"a", "abcd", "1234567890", "12345678901234", "123456789012345", "1234567890123456"};
        for (String station : stations) {
            String name = build("SomeVeryLongPlayerName", station);
            assertTrue(name.length() <= 16, "假人名超限: " + name + " (" + name.length() + ")");
            assertTrue(name.endsWith(station), "站点被截断: " + name);
        }
    }
}
