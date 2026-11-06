package me.primaryuan.carpet.handler.fakegames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GameType 枚举：id 解析、最低人数、自动结算标志与 lang 键约定 */
class GameTypeTest {

    @Test
    void byIdResolvesExactly() {
        for (GameType type : GameType.values()) {
            assertEquals(type, GameType.byId(type.id));
        }
        assertNull(GameType.byId("no_such_game"));
        assertNull(GameType.byId(""));
        assertNull(GameType.byId(null));
    }

    @Test
    void idsAreUniqueLowerSnake() {
        for (GameType type : GameType.values()) {
            assertTrue(type.id.matches("[a-z_]+"), "id 必须是小写+下划线: " + type.id);
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (GameType type : GameType.values()) {
            assertTrue(ids.add(type.id), "重复的游戏 id: " + type.id);
        }
    }

    @Test
    void playerCountsFollowGameDesign() {
        for (GameType type : GameType.values()) {
            assertTrue(type.minPlayers >= 1, "最低人数不能为 0: " + type.id);
            assertTrue(type.maxPlayers >= type.minPlayers, "人数上限不能低于下限: " + type.id);
        }
        assertEquals(3, GameType.TAG.minPlayers);
        assertEquals(3, GameType.HOT_POTATO.minPlayers);
        assertEquals(3, GameType.CHARADES.minPlayers);
        assertEquals(1, GameType.SHOOTING.minPlayers);
        assertTrue(GameType.SHOOTING.training);
    }

    @Test
    void autoLastStandingMatchesDesign() {
        // 淘汰制游戏走"仅剩一人自动结算"
        assertTrue(GameType.GRASS_IS_LAVA.autoLastStanding);
        assertTrue(GameType.RISING_LAVA.autoLastStanding);
        assertTrue(GameType.RED_LIGHT_GREEN_LIGHT.autoLastStanding);
        assertTrue(GameType.RANDOM_BANS.autoLastStanding);
        // 积分制/阵营/无淘汰游戏由玩法自行收尾
        assertFalse(GameType.SAFE_SPOT.autoLastStanding);
        assertFalse(GameType.HIDE_AND_SEEK.autoLastStanding);
        assertTrue(GameType.HOT_POTATO.autoLastStanding);
        assertFalse(GameType.TAG.autoLastStanding);
        assertFalse(GameType.KING_OF_THE_HILL.autoLastStanding);
        assertFalse(GameType.CHARADES.autoLastStanding);
    }

    @Test
    void langKeysFollowConvention() {
        assertEquals("carpetprimaryuan.fakegames.game.grass_is_lava.name",
                GameType.GRASS_IS_LAVA.nameKey());
        assertEquals("carpetprimaryuan.fakegames.game.grass_is_lava.desc",
                GameType.GRASS_IS_LAVA.descKey());
        assertEquals("carpetprimaryuan.fakegames.game.grass_is_lava.win",
                GameType.GRASS_IS_LAVA.winKey());
        assertNotNull(GameType.byId("charades"));
    }
}
