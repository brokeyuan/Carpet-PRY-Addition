package me.primaryuan.carpet.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoalSelector 调度语义锁（26.3/1.21.11/1.21 字节码对齐的奇偶节奏）：
 * 完整 tick（偶相）= 清理 + 候选评估 + 推进全部运行目标；轻量 tick（奇相）=
 * 只推进 requiresUpdateEveryTick 的运行目标。canStart 冷却按"评估次数"计
 * （每 2 游戏 tick 评估一次），interval=N 表示每 N 次评估放行一次。
 */
class BrainGoalSelectorTest {

    /** 完整 tick（偶相） */
    private static void full(PlayerGoalSelector selector) {
        selector.tick(true);
    }

    /** 轻量 tick（奇相） */
    private static void light(PlayerGoalSelector selector) {
        selector.tick(false);
    }

    @Test
    void cooldownCountsEvaluations() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal goal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        goal.canUseResult = true;
        goal.canContinueResult = false; // 一次性目标：每轮 start 即 stop，测重启节奏
        goal.setInterval(5); // 每 5 次评估放行一次 = 每 10 游戏刻一个候选窗
        selector.addGoal(1, goal);

        // 40 tick = 20 次评估；评估只在完整 tick 发生 → 恰好 4 次启动
        int starts = 0;
        for (int i = 0; i < 40; i++) {
            int before = goal.startCount;
            if (i % 2 == 0) {
                full(selector);
            } else {
                light(selector);
            }
            if (goal.startCount > before) {
                starts++;
            }
        }
        assertEquals(4, starts, "40 tick、间隔 5 次评估（周期 10 tick）→ 恰好 4 次启动");
    }

    @Test
    void lightTickSkipsEvaluationAndCleanup() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal idle = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        idle.canUseResult = true;
        selector.addGoal(1, idle);

        light(selector);
        assertEquals(0, idle.startCount, "轻量 tick 不评估候选（canUse 不调用）");
        full(selector);
        assertEquals(1, idle.startCount, "完整 tick 才评估候选");
    }

    @Test
    void runningGoalTicksFollowsRueContract() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal plain = new BrainTestStubs.StubGoal(PlayerGoal.Flag.LOOK);
        BrainTestStubs.StubGoal rue = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE) {
            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }
        };
        plain.canUseResult = true;
        rue.canUseResult = true;
        selector.addGoal(1, plain);
        selector.addGoal(1, rue);
        full(selector); // 两个都启动（旗标不相交）
        assertEquals(1, plain.startCount);
        assertEquals(1, rue.startCount);

        plain.tickCount = 0;
        rue.tickCount = 0;
        light(selector); // 轻量 tick：只推进 rUE
        assertEquals(0, plain.tickCount, "非 rUE 目标轻量 tick 不推进");
        assertEquals(1, rue.tickCount, "rUE 目标每 tick 推进");
        full(selector); // 完整 tick：全部推进
        assertEquals(1, plain.tickCount);
        assertEquals(2, rue.tickCount);
    }

    @Test
    void canContinueOnlyCheckedOnFullTicks() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal goal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        goal.canUseResult = true;
        selector.addGoal(1, goal);
        full(selector);
        assertEquals(1, goal.startCount);

        goal.canContinueResult = false; // 条件失效：轻量 tick 不查续行，完整 tick 才停
        light(selector);
        assertEquals(0, goal.stopCount, "轻量 tick 不查 canContinueToUse（原版清理在完整 tick 内）");
        full(selector);
        assertEquals(1, goal.stopCount, "完整 tick 清理停掉失效目标");
    }

    @Test
    void flagConflictBlocksLowerPriority() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal low = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        BrainTestStubs.StubGoal high = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        low.canUseResult = true;
        selector.addGoal(3, low);
        selector.addGoal(1, high);

        // 候选按优先级排序：高优先级 canUse=false 时低优先级启动
        full(selector);
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE));
        assertEquals(1, low.startCount);
        assertEquals(0, high.startCount);
        // 高优先级就绪：同旗标冲突，抢占运行中的低优先级（下一完整 tick）
        high.canUseResult = true;
        full(selector);
        assertEquals(1, low.stopCount, "高优先级应抢占低优先级（stop 被调用）");
        assertEquals(1, high.startCount);
        assertTrue(selector.hasRunningMoveGoal());
    }

    @Test
    void equalPriorityDoesNotPreempt() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal first = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        BrainTestStubs.StubGoal second = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        first.canUseResult = true;
        second.canUseResult = true;
        selector.addGoal(2, first);
        selector.addGoal(2, second);

        full(selector);
        assertEquals(1, first.startCount);
        full(selector);
        assertEquals(0, second.startCount, "同优先级不抢占（原版语义：排队等待）");
        assertEquals(0, first.stopCount);
    }

    @Test
    void nonConflictingFlagsRunTogether() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal move = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        BrainTestStubs.StubGoal target = new BrainTestStubs.StubGoal(PlayerGoal.Flag.TARGET);
        move.canUseResult = true;
        target.canUseResult = true;
        selector.addGoal(2, move);
        selector.addGoal(1, target);

        full(selector);
        assertEquals(1, move.startCount);
        assertEquals(1, target.startCount, "旗标不相交的目标并行运行");
    }

    @Test
    void stopResetsRunningAllowsRestart() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal goal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        goal.canUseResult = true;
        goal.setInterval(1);
        selector.addGoal(1, goal);

        full(selector);
        assertEquals(1, goal.startCount);
        // canContinueToUse=false：轻量 tick 不清理，完整 tick stop 后同 tick 重启
        //（interval=1 无冷却）——stop 复位 running 正是"一次性 Goal"缺陷的回归防线
        goal.canContinueResult = false;
        light(selector);
        assertEquals(0, goal.stopCount);
        full(selector);
        assertEquals(1, goal.stopCount, "完整 tick 清理停掉失效目标");
        assertEquals(2, goal.startCount, "stop 后 running 复位，Goal 立即重启");
    }

    @Test
    void removeAllGoalsStopsRunningAndClears() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal goal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        goal.canUseResult = true;
        goal.setInterval(1);
        selector.addGoal(1, goal);
        full(selector);
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE));

        selector.removeAllGoals();
        assertEquals(1, goal.stopCount, "removeAllGoals 须对运行中目标调用 stop");
        assertFalse(selector.hasRunningFlag(PlayerGoal.Flag.MOVE));
        // 清空后同一 Goal 重新注册可正常运行
        selector.addGoal(1, goal);
        full(selector);
        assertEquals(2, goal.startCount);
    }

    @Test
    void removeGoalDetachesRunningInstance() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        BrainTestStubs.StubGoal goal = new BrainTestStubs.StubGoal(PlayerGoal.Flag.MOVE);
        goal.canUseResult = true;
        selector.addGoal(1, goal);
        full(selector);
        assertTrue(selector.isRegistered(goal));
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE));

        selector.removeGoal(goal);
        assertFalse(selector.isRegistered(goal), "removeGoal 移出注册表");
        assertFalse(selector.hasRunningFlag(PlayerGoal.Flag.MOVE), "运行中的实例一并停用");
        assertEquals(1, goal.stopCount);
        // 重新注册（武器形态换装路径）可再次启动
        selector.addGoal(1, goal);
        full(selector);
        assertEquals(2, goal.startCount);
    }
}
