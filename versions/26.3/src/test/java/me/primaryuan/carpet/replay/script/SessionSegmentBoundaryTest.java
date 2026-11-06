package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import me.primaryuan.carpet.replay.script.core.SessionCore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 片段边界：上一段末帧先被实体 Tick 消费，再释放旧持续动作并重建基线，不传送 */
class SessionSegmentBoundaryTest {

    @Test
    void releasesAfterConsumeAndForcesNextSegmentBaseline() {
        var driver = new ScriptTestSupport.StubDriver();
        ReplayData data = ScriptTestSupport.data(5);
        ScriptTestSupport.input(data).segmentStarts.add(2);
        InputEvent attack = ScriptTestSupport.event(1, 0, InputEventType.ATTACK_ENTITY);
        ScriptTestSupport.input(data).events.add(attack);
        SessionCore core = new SessionCore(data, driver, 1, false);

        core.tick(); // t0
        core.tick(); // t1: attack pending
        assertEquals(1, driver.pending.size());
        driver.consumeTick(); // 实体 Tick 消费上一段尾帧
        assertTrue(driver.executed.contains("attackOnce"));

        core.tick(); // t2: 边界，在消费之后释放旧持续动作
        int releaseAt = driver.log.indexOf("releaseAll");
        assertTrue(releaseAt >= 0, "片段边界必须释放旧持续动作");
        assertTrue(driver.released >= 1);
        assertTrue(driver.log.subList(releaseAt + 1, driver.log.size()).contains("look=20.0,0.0"),
                "释放后重建本段基线");
        assertFalse(driver.log.stream().anyMatch(l -> l.startsWith("teleport=")),
                "片段间不得按录像位置传送");
        assertTrue(driver.executed.contains("attackOnce"), "末帧动作必须已经消费，不能被释放丢掉");
        core.stop();
    }
}
