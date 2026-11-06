package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 随机禁令"不准跳跃"判定：离地上升 + 外力豁免的边界 */
class RandomBansViolationTest {

    private static boolean jump(boolean onGround, boolean passenger, double dy,
                                boolean hurt, boolean water, boolean lava,
                                boolean climbing, boolean fallFlying) {
        return RandomBansGame.jumpViolation(onGround, passenger, dy,
                hurt, water, lava, climbing, fallFlying);
    }

    @Test
    void airborneRisingIsViolation() {
        // 跳跃上升：离地 + dy>0.05
        assertTrue(jump(false, false, 0.42, false, false, false, false, false));
        // 原地起跳初速最高一 tick
        assertTrue(jump(false, false, 0.78, false, false, false, false, false));
    }

    @Test
    void groundAndFallingAreSafe() {
        // 在地面不算（走台阶/楼梯时客户端上报 onGround=true）
        assertFalse(jump(true, false, 0.5, false, false, false, false, false));
        // 下落不算
        assertFalse(jump(false, false, -0.6, false, false, false, false, false));
        // 上升量可忽略（浮点抖动）
        assertFalse(jump(false, false, 0.01, false, false, false, false, false));
    }

    @Test
    void externalForcesAreExempt() {
        // 被击退（hurtTime > 0）
        assertFalse(jump(false, false, 0.4, true, false, false, false, false));
        // 水中/岩浆中浮升
        assertFalse(jump(false, false, 0.2, false, true, false, false, false));
        assertFalse(jump(false, false, 0.2, false, false, true, false, false));
        // 攀爬（梯子/藤蔓）
        assertFalse(jump(false, false, 0.2, false, false, false, true, false));
        // 鞘翅飞行
        assertFalse(jump(false, false, 0.9, false, false, false, false, true));
        // 骑乘坐骑
        assertFalse(jump(false, true, 0.5, false, false, false, false, false));
    }

    @Test
    void tinyRiseAfterKnockbackStillExempt() {
        assertFalse(jump(false, false, 0.06, true, false, false, false, false));
    }
}
