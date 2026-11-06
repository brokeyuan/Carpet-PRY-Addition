package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 换手输入消费：空手/相同物品不影响，只由冷却决定是否发射。 */
class ShootingGameTest {

    @Test
    void swapAlwaysConsumedAndFiresWhenReady() {
        AtomicInteger fired = new AtomicInteger();
        int cooldown = ShootingGame.handleSwapRequest(0, fired::incrementAndGet);
        assertEquals(1, fired.get());
        assertEquals(5, cooldown);
    }

    @Test
    void swapDuringCooldownIsSwallowedWithoutFiring() {
        AtomicInteger fired = new AtomicInteger();
        int cooldown = ShootingGame.handleSwapRequest(3, fired::incrementAndGet);
        assertEquals(0, fired.get());
        assertEquals(3, cooldown);
    }
}
