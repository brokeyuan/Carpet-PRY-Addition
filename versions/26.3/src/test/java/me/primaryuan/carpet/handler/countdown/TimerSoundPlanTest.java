package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerSoundPlanTest {

    @Test
    void fancyTicksAcrossTenSecondsAscending() {
        for (int sec = 10; sec >= 1; sec--) {
            Optional<TimerSoundPlan.Cue> cue = TimerSoundPlan.tick(TimerSoundPlan.Tier.FANCY, sec);
            assertTrue(cue.isPresent(), "fancy 第 " + sec + " 秒应有倒数音");
            assertEquals(TimerSoundPlan.Id.TICK_PLING, cue.get().id());
            assertEquals(0.6F, cue.get().volume());
            // 音高按半音阶递增：10→1.0，1→约 1.68
            assertEquals((float) Math.pow(2.0, (10 - sec) / 12.0), cue.get().pitch(), 1e-6);
        }
        assertTrue(TimerSoundPlan.tick(TimerSoundPlan.Tier.FANCY, 11).isEmpty(), "窗口外无声");
    }

    @Test
    void normalTicksOnlyLastThree() {
        assertTrue(TimerSoundPlan.tick(TimerSoundPlan.Tier.NORMAL, 4).isEmpty());
        for (int sec = 3; sec >= 1; sec--) {
            Optional<TimerSoundPlan.Cue> cue = TimerSoundPlan.tick(TimerSoundPlan.Tier.NORMAL, sec);
            assertTrue(cue.isPresent());
            assertEquals(TimerSoundPlan.Id.TICK_HAT, cue.get().id());
            assertEquals(0.4F, cue.get().volume());
            assertEquals(1.0F, cue.get().pitch());
        }
    }

    @Test
    void silentTiersNeverSound() {
        for (int sec = 1; sec <= 12; sec++) {
            assertTrue(TimerSoundPlan.tick(TimerSoundPlan.Tier.NONE, sec).isEmpty());
        }
        assertTrue(TimerSoundPlan.milestone(TimerSoundPlan.Tier.NONE).isEmpty());
        assertTrue(TimerSoundPlan.finish(TimerSoundPlan.Tier.NONE).isEmpty());
        assertTrue(TimerSoundPlan.opening(TimerSoundPlan.Tier.NONE, 5, false).isEmpty());
        assertTrue(TimerSoundPlan.upStop(TimerSoundPlan.Tier.NONE).isEmpty());
    }

    @Test
    void fancyMilestoneAndFinish() {
        assertEquals(TimerSoundPlan.Id.MILESTONE_CHIME, TimerSoundPlan.milestone(TimerSoundPlan.Tier.FANCY).get().id());
        assertTrue(TimerSoundPlan.milestone(TimerSoundPlan.Tier.NORMAL).isEmpty(), "normal 无长程节点音");
        assertEquals(TimerSoundPlan.Id.FINISH_BELL, TimerSoundPlan.finish(TimerSoundPlan.Tier.NORMAL).get().id());
        assertEquals(0.6F, TimerSoundPlan.finish(TimerSoundPlan.Tier.NORMAL).get().volume());
        assertEquals(0.8F, TimerSoundPlan.finish(TimerSoundPlan.Tier.FANCY).get().volume());
    }

    @Test
    void openingOnlyForFancyWithoutOverlap() {
        assertTrue(TimerSoundPlan.opening(TimerSoundPlan.Tier.NORMAL, 30, false).isEmpty());
        assertTrue(TimerSoundPlan.opening(TimerSoundPlan.Tier.FANCY, 30, false).isPresent());
        assertEquals(TimerSoundPlan.Id.OPEN_CHIME, TimerSoundPlan.opening(TimerSoundPlan.Tier.FANCY, 30, false).get().id());
        // 启动已在倒数窗口：改发倒数音，不叠加开场音
        assertEquals(TimerSoundPlan.Id.TICK_PLING,
                TimerSoundPlan.opening(TimerSoundPlan.Tier.FANCY, 8, true).get().id());
        // simple / 无声层永不响
        assertTrue(TimerSoundPlan.opening(TimerSoundPlan.Tier.NONE, 30, false).isEmpty());
    }

    @Test
    void legacyRecipeKept() {
        assertEquals(TimerSoundPlan.Id.MILESTONE_BELL, TimerSoundPlan.legacyMilestone().get().id());
        assertEquals(TimerSoundPlan.Id.FINISH_LEVELUP, TimerSoundPlan.finish(TimerSoundPlan.Tier.LEGACY).get().id());
        assertTrue(TimerSoundPlan.milestone(TimerSoundPlan.Tier.LEGACY).isEmpty(),
                "旧入口节拍音走 legacyMilestone 专用配方");
        assertTrue(TimerSoundPlan.opening(TimerSoundPlan.Tier.LEGACY, 30, false).isEmpty(),
                "旧入口无开场音（保留原行为）");
        assertTrue(TimerSoundPlan.upStop(TimerSoundPlan.Tier.LEGACY).isEmpty());
    }

    @Test
    void upStopFollowsFinishRecipe() {
        assertTrue(TimerSoundPlan.upStop(TimerSoundPlan.Tier.NORMAL).isPresent());
        assertTrue(TimerSoundPlan.upStop(TimerSoundPlan.Tier.FANCY).isPresent());
        assertEquals(0.8F, TimerSoundPlan.upStop(TimerSoundPlan.Tier.FANCY).get().volume());
    }
}
