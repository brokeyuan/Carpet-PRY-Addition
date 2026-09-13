package me.primaryuan.carpet.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashSet;
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

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            TASKS.removeIf(task -> !task.tick(server));
        });
    }
}
