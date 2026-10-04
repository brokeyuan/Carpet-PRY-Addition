package me.primaryuan.carpet.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 中央 tick 调度器：mod 内所有周期任务的统一注册点。
 *
 * 此前 TppCommand / DropSlotScheduler / SendtoLinkManager 各自实现一套
 * END_SERVER_TICK 惰性注册与双检锁样板；本类将其收敛为一处。
 * 所有任务仅在服务器主线程执行；Fabric 的 tick 事件为 JVM 级全局注册，
 * 本类只在首次注册时挂载事件监听（配合调用方的一次性初始化标志），
 * 因此任务集跨同一 JVM 内的服务器实例存活——各任务需自行在 tick 中
 * 检测状态有效性（玩家下线、服务器更换等），并在 SERVER_STOPPING
 * 清理各自持有的业务状态（本类不再代为清空，否则第二次启动后任务静默失效）。
 */
public final class ServerTickScheduler {

    /** 每 tick 推进一次的任务；返回 false 表示已结束，自动注销 */
    public interface TickTask {
        boolean tick(MinecraftServer server);
    }

    private static final Set<TickTask> TASKS = new LinkedHashSet<>();
    private static boolean registered = false;
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    private ServerTickScheduler() {}

    /**
     * 注册每 tick 任务（是否重复注册由调用方自行保证，本类不判重）。
     *
     * @return 注销句柄
     */
    public static synchronized Runnable register(TickTask task) {
        ensureRegistered();
        TASKS.add(task);
        return () -> TASKS.remove(task);
    }

    /**
     * 注册延迟一次性任务：delayTicks 个 tick 后执行一次并自动注销。
     * delayTicks=0 表示当前 tick 末尾执行。
     */
    public static synchronized void registerDelayed(int delayTicks, TickTask task) {
        if (delayTicks <= 0) {
            register(task);
            return;
        }
        int[] left = {delayTicks};
        register(server -> {
            if (--left[0] > 0) {
                return true;
            }
            // 一次性契约：到期执行后强制注销，不透传内部任务的返回值——
            // 那是"任务自身是否继续"的语义，透传会让包装任务在 left 计负后
            // 退化为每 tick 重复执行
            task.tick(server);
            return false;
        });
    }

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(ServerTickScheduler::runTasks);
    }

    /**
     * 单 tick 的任务循环：快照遍历 + 集中删除。
     *
     * <p>不能直接 TASKS.removeIf：JDK 默认 removeIf 在遍历阶段逐个执行任务且不检查
     * modCount，任务执行体内再调 register/registerDelayed（如红包确认 → 下一 tick 关
     * 菜单）会把元素 add 进 TASKS，删除阶段 LinkedHashIterator 检测到 modCount 变化
     * 即抛 ConcurrentModificationException 崩服（2026-10-03 正式服红包实崩实证）。
     * 快照方案下任务内注册的新任务进入 TASKS 但不在本 tick 快照中，下一 tick 执行，
     * registerDelayed(1) 的"下一 tick 末尾"语义保持。</p>
     */
    static void runTasks(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        List<TickTask> snapshot = new ArrayList<>(TASKS);
        List<TickTask> dead = null;
        for (TickTask task : snapshot) {
            boolean keep;
            try {
                keep = task.tick(server);
            } catch (Throwable t) {
                // 逐任务异常隔离：单个任务崩溃记日志并淘汰，不上抛拖垮服务器 tick
                //（同 BrainManager 单脑崩溃摘除的思路）
                LOGGER.error("[ServerTickScheduler] Task crashed and was removed", t);
                keep = false;
            }
            if (!keep) {
                if (dead == null) {
                    dead = new ArrayList<>();
                }
                dead.add(task);
            }
        }
        if (dead != null) {
            TASKS.removeAll(dead);
        }
    }
}
