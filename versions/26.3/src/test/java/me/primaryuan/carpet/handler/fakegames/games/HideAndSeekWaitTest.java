package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 寻找者等待区：无强制传送，仅按边界与宽限判定。 */
class HideAndSeekWaitTest {

    @Test
    void insideWaitAreaIsSafe() {
        assertFalse(HideAndSeekGame.outsideWaitArea(0, 0, 0));
        assertFalse(HideAndSeekGame.outsideWaitArea(2.0, 1.0, 2.0));
        assertFalse(HideAndSeekGame.outsideWaitArea(0, 2.9, 0));
    }

    @Test
    void outsideWaitAreaIsFlagged() {
        assertTrue(HideAndSeekGame.outsideWaitArea(3.1, 0, 0));
        assertTrue(HideAndSeekGame.outsideWaitArea(0, 0, -3.1));
        assertTrue(HideAndSeekGame.outsideWaitArea(0, 3.1, 0));
    }
}
