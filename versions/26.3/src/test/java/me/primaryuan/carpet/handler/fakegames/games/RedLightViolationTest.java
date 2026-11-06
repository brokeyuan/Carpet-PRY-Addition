package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 123木头人红灯违规判定：三维位移容差 + 外力豁免 */
class RedLightViolationTest {

    private static boolean violated(double dx, double dy, double dz,
                                    boolean hurt, boolean water, boolean lava, boolean climbing) {
        return RedLightGreenLightGame.redViolation(dx, dy, dz, hurt, water, lava, climbing);
    }

    @Test
    void withinToleranceIsSafe() {
        // 容差 0.75：走动一步（~0.22/tick）远小于容差
        assertFalse(violated(0.22, 0, 0, false, false, false, false));
        // 斜向合成也小于容差
        assertFalse(violated(0.3, 0, 0.3, false, false, false, false));
        // 纯视角转动不产生位移
        assertFalse(violated(0, 0, 0, false, false, false, false));
        // 网络延迟带来的小幅漂移
        assertFalse(violated(0.4, 0.1, 0.4, false, false, false, false));
    }

    @Test
    void beyondToleranceIsViolation() {
        // 疾跑一步
        assertTrue(violated(0.9, 0, 0, false, false, false, false));
        // 原地跳（垂直分量 ~1.25 超容差）
        assertTrue(violated(0, 1.25, 0, false, false, false, false));
        // 快速位移
        assertTrue(violated(1.0, 0, 0, false, false, false, false));
    }

    @Test
    void externalForcesAreExempt() {
        // 击退位移
        assertFalse(violated(1.2, 0.3, 0.5, true, false, false, false));
        // 水流/熔岩推带
        assertFalse(violated(1.0, 0, 0, false, true, false, false));
        assertFalse(violated(1.0, 0, 0, false, false, true, false));
        // 攀爬滑落
        assertFalse(violated(0, -1.5, 0, false, false, false, true));
    }

    @Test
    void toleranceIsPerTickSnapshotNotAccumulatingWindow() {
        // 快照固定于红灯开始：慢走持续累积超过容差后同样判负
        assertTrue(violated(0.6, 0, 0.6, false, false, false, false));
    }
}
