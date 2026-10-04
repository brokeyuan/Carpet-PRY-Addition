package me.primaryuan.carpet.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中央 tick 调度器的重入契约：任务执行体内再注册新任务不得破坏本 tick 循环
 * （2026-10-03 正式服红包 CME 崩服实证：removeIf 遍历阶段执行任务 → 任务内
 * register → 删除阶段 modCount 检测抛 ConcurrentModificationException）。
 */
public class ServerTickSchedulerTest {

    @Test
    void taskRegisteringInsideTickDoesNotThrow() {
        Runnable detach = ServerTickScheduler.register(server -> {
            // 任务执行体内注册新任务（崩溃路径的抽象复现）
            ServerTickScheduler.registerDelayed(1, s -> false);
            ServerTickScheduler.register(s -> false);
            return false;
        });
        // 崩溃前实现此处抛 ConcurrentModificationException
        ServerTickScheduler.runTasks(null);
        detach.run();
        ServerTickScheduler.runTasks(null); // 收尾轮，清理内部任务
        assertTrue(true);
    }

    @Test
    void taskRegisteredInsideTickRunsNextTick() {
        List<Integer> order = new ArrayList<>();
        Runnable detach = ServerTickScheduler.register(server -> {
            order.add(1);
            ServerTickScheduler.register(s -> {
                order.add(2);
                return false;
            });
            return order.size() == 1; // 第二轮自行结束
        });
        ServerTickScheduler.runTasks(null);
        assertEquals(List.of(1), order, "任务内注册的新任务不应在本 tick 执行");
        ServerTickScheduler.runTasks(null);
        assertEquals(List.of(1, 1, 2), order, "新任务应在下一 tick 执行（外层任务第二轮先跑）");
        detach.run();
    }

    @Test
    void delayedOneTicksFiresOnFirstRun() {
        AtomicBoolean fired = new AtomicBoolean(false);
        ServerTickScheduler.registerDelayed(1, server -> {
            fired.set(true);
            return false;
        });
        ServerTickScheduler.runTasks(null);
        assertTrue(fired.get(), "delayed(1) 应在注册后第 1 轮执行（即下一 tick 末尾）");
    }

    @Test
    void delayedTaskRunsOnceEvenIfInnerTaskReturnsTrue() {
        int[] runs = {0};
        ServerTickScheduler.registerDelayed(2, server -> {
            runs[0]++;
            return true; // 内部任务声称"继续"，也不得让包装任务常驻
        });
        ServerTickScheduler.runTasks(null);
        ServerTickScheduler.runTasks(null);
        ServerTickScheduler.runTasks(null);
        assertEquals(1, runs[0], "延迟任务到期执行一次即注销（内部返回值不透传）");
    }

    @Test
    void finishedTasksAreRemoved() {
        int[] runs = {0};
        Runnable detach = ServerTickScheduler.register(server -> {
            runs[0]++;
            return runs[0] < 2;
        });
        ServerTickScheduler.runTasks(null);
        ServerTickScheduler.runTasks(null);
        ServerTickScheduler.runTasks(null);
        assertEquals(2, runs[0], "返回 false 后应停止调度");
        detach.run();
    }
}
