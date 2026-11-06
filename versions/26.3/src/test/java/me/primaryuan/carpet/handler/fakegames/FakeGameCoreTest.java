package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.handler.fakegames.games.ElytraGame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 假游戏基础契约：ID 唯一、设置默认值合法、区域预设与高速线段判定。 */
class FakeGameCoreTest {

    @Test
    void gameIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (GameType type : GameType.values()) {
            assertTrue(ids.add(type.id), "duplicate game id: " + type.id);
        }
        assertEquals(GameType.values().length, ids.size());
    }

    @Test
    void everySettingDefaultIsAValidChoice() {
        for (GameType type : GameType.values()) {
            for (GameType.Setting setting : type.settings()) {
                assertNotNull(setting.key());
                assertNotNull(setting.def());
                if (!setting.choices().isEmpty()) {
                    assertTrue(setting.choices().contains(setting.def()),
                            type.id + "." + setting.key() + " default not in choices");
                }
            }
        }
    }

    @Test
    void defaultRegionPresetIsBounded() {
        FakeGameSettings.Preset preset = FakeGameSettings.defaultPreset();
        assertNotNull(preset);
        assertEquals("standard", preset.name());
        assertTrue(preset.radius() >= FakeGameSettings.MIN_RADIUS);
        assertTrue(preset.radius() <= FakeGameSettings.maxRadius());
        assertTrue(preset.minY() < preset.maxY());
    }

    @Test
    void trainingGameAllowsSolo() {
        assertEquals(1, GameType.SHOOTING.minPlayers);
        assertTrue(GameType.SHOOTING.training);
        assertTrue(GameType.ELYTRA.training);
    }

    @Test
    void segmentSphereDetectsHighSpeedPassAndMiss() {
        Vec3 start = new Vec3(0, 0, 0);
        Vec3 end = new Vec3(10, 0, 0);
        assertTrue(ElytraGame.segmentHitsSphere(start, end, new Vec3(5, 1.5, 0), 2.0));
        assertFalse(ElytraGame.segmentHitsSphere(start, end, new Vec3(5, 3.0, 0), 2.0));
        assertTrue(ElytraGame.segmentHitsSphere(start, end, new Vec3(0.5, 0, 0), 1.0));
        assertFalse(ElytraGame.segmentHitsSphere(start, end, new Vec3(11, 0, 0), 0.5));
    }

    @Test
    void unavailableGamesAreExplicitlyMarked() {
        assertNotNull(GameType.DRAW_GUESS.unavailableKey());
        assertNotNull(GameType.MURDER_MYSTERY.unavailableKey());
        assertNotNull(GameType.WEREWOLF.unavailableKey());
        assertEquals(null, GameType.SHOOTING.unavailableKey());
    }
}
