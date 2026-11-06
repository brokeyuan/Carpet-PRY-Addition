package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 抢安全点占点判定：水平距离、高度差窗口、必须在地面 */
class SafeSpotClaimTest {

    private static boolean onSpot(double dx, double dz, double dy, boolean onGround) {
        return SafeSpotGame.standsOnSpot(dx, dz, dy, onGround);
    }

    @Test
    void standingOnSpotCenterCounts() {
        // 站在安全点方块中心（dy=0 表示脚与方块顶同高）
        assertTrue(onSpot(0, 0, 0, true));
        assertTrue(onSpot(0.4, 0.4, 0.1, true));
    }

    @Test
    void horizontalDistanceBound() {
        // 1.5 格判定半径边界：1.4 合成距离内，1.5 之外
        assertTrue(onSpot(1.4, 0, 0, true));
        assertFalse(onSpot(1.6, 0, 0, true));
        // 斜向合成超界
        assertFalse(onSpot(1.2, 1.2, 0, true));
    }

    @Test
    void verticalWindow() {
        // 允许站在标记点上方两格内（上到台阶/半砖）
        assertTrue(onSpot(0, 0, 2.0, true));
        assertFalse(onSpot(0, 0, 2.5, true));
        // 允许脚下略低（波浪容差）
        assertTrue(onSpot(0, 0, -0.4, true));
        assertFalse(onSpot(0, 0, -1.0, true));
    }

    @Test
    void mustBeOnGround() {
        // 跳跃掠过不计占点
        assertFalse(onSpot(0, 0, 0.5, false));
    }
}
