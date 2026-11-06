package me.primaryuan.carpet.handler.fakegames.games;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连续静止：20 tick 达标，移动/豁免/绕圈折返都会重置。 */
class StillnessTrackerTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void nineteenTicksAreSafeTwentyEliminates() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        Vec3 pos = new Vec3(1.0, 64.0, 1.0);
        for (int i = 0; i < 19; i++) {
            assertFalse(tracker.update(PLAYER, pos, true), "第 " + (i + 1) + " tick 不应违规");
        }
        assertTrue(tracker.update(PLAYER, pos, true), "连续 20 tick 应违规");
    }

    @Test
    void movementResetsTheWindow() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        Vec3 pos = new Vec3(1.0, 64.0, 1.0);
        for (int i = 0; i < 15; i++) {
            tracker.update(PLAYER, pos, true);
        }
        tracker.update(PLAYER, new Vec3(1.5, 64.0, 1.0), true);
        for (int i = 0; i < 19; i++) {
            assertFalse(tracker.update(PLAYER, new Vec3(1.5, 64.0, 1.0), true));
        }
        assertTrue(tracker.update(PLAYER, new Vec3(1.5, 64.0, 1.0), true));
    }

    @Test
    void circlingOrBacktrackingNeverCountsAsStill() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        for (int i = 0; i < 100; i++) {
            double angle = i * Math.PI / 6;
            Vec3 pos = new Vec3(Math.cos(angle), 64.0, Math.sin(angle));
            assertFalse(tracker.update(PLAYER, pos, true),
                    "持续绕圈不得因端点接近而误判");
        }
    }

    @Test
    void slowSubTickMovementStillResetsWindow() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        for (int i = 0; i < 20; i++) {
            // 0.015 格/tick 低于单 tick 容差，但 20 tick 累计约 0.285 格，不能判静止
            assertFalse(tracker.update(PLAYER, new Vec3(i * 0.015, 64.0, 0.0), true),
                    "慢速连续移动第 " + (i + 1) + " tick 不应判静止");
        }
    }

    @Test
    void smallStationaryJitterWithinPathBudgetStillCountsAsStill() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        for (int i = 0; i < 20; i++) {
            double x = (i % 2 == 0) ? 0.0025 : -0.0025;
            boolean violation = tracker.update(PLAYER, new Vec3(x, 64.0, 0.0), true);
            if (i < 19) {
                assertFalse(violation, "抖动第 " + (i + 1) + " tick 不应提前违规");
            } else {
                assertTrue(violation, "持续抖动仍应视为静止并达到 20 tick");
            }
        }
    }

    @Test
    void normalSneakingMovementResetsWindow() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        for (int i = 0; i < 100; i++) {
            // 潜行约 0.065 格/tick，必须高于逐个 tick 的静止容差
            assertFalse(tracker.update(PLAYER, new Vec3(i * 0.065, 64.0, 0.0), true));
        }
    }

    @Test
    void exemptionResetsAndDoesNotAccumulateAfterLanding() {
        StillnessTracker tracker = new StillnessTracker(0.02, 20);
        Vec3 pos = new Vec3(1.0, 64.0, 1.0);
        for (int i = 0; i < 15; i++) {
            tracker.update(PLAYER, pos, true);
        }
        assertFalse(tracker.update(PLAYER, pos, false));
        for (int i = 0; i < 19; i++) {
            assertFalse(tracker.update(PLAYER, pos, true));
        }
        assertTrue(tracker.update(PLAYER, pos, true));
    }
}
