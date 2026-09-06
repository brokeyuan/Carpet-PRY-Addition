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
 * 所有任务仅在服务器主线程执行；服务器停止时全部任务自动放弃，
 * 静态任务集不跨服务器实例存活。
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
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TASKS.clear());
    }
}
