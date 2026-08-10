package me.primaryuan.carpet.util;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 假人持续清空背包的 tick 调度核心。
 *
 * 按 (玩家 UUID, slotKey) 维度去重，避免同槽位重复注册。
 * 通过 Fabric API 的 {@link ServerTickEvents#END_SERVER_TICK} 在主线程上调度丢出任务。
 */
public final class DropSlotScheduler {

    public static final int SLOT_ALL = -2;
    public static final int SLOT_MAINHAND = -1;
    public static final int SLOT_OFFHAND = 40;

    private DropSlotScheduler() {}

    private enum Mode {
        CONTINUOUS,
        INTERVAL,
        AFTER,
        PERTICK,
        RANDOMLY
    }

    private static final class DropTask {
        final ServerPlayer player;
        final UUID playerId;
        final int slot;
        final String slotKey;
        final Mode mode;
        final int interval; // for INTERVAL / PERTICK
        final int min;       // for RANDOMLY
        final int max;       // for RANDOMLY
        int ticksUntilNext;
        int droppedStacks;
        int droppedItems;
        final CommandSourceStack source;

        DropTask(ServerPlayer player, int slot, String slotKey, Mode mode,
                 int interval, int min, int max, CommandSourceStack source) {
            this.player = player;
            this.playerId = player.getUUID();
            this.slot = slot;
            this.slotKey = slotKey;
            this.mode = mode;
            this.interval = interval;
            this.min = min;
            this.max = max;
            this.ticksUntilNext = interval; // for INTERVAL/PERTICK/AFTER first delay
            this.source = source;
        }
    }

    private static final Map<UUID, Map<String, DropTask>> tasks = new ConcurrentHashMap<>();
    private static boolean registered = false;

    // ===== Public API =====

    public static boolean startContinuous(ServerPlayer player, int slot, String slotKey, CommandSourceStack source) {
        return start(player, slot, slotKey, Mode.CONTINUOUS, 1, 0, 0, source);
    }

    public static boolean startInterval(ServerPlayer player, int slot, String slotKey, int interval, CommandSourceStack source) {
        return start(player, slot, slotKey, Mode.INTERVAL, interval, 0, 0, source);
    }

    public static boolean startAfter(ServerPlayer player, int slot, String slotKey, int delay, CommandSourceStack source) {
        return start(player, slot, slotKey, Mode.AFTER, delay, 0, 0, source);
    }

    public static boolean startPerTick(ServerPlayer player, int slot, String slotKey, int times, CommandSourceStack source) {
        // 每秒 times 次：每 max(1, 20/times) tick 一次
        int interval = Math.max(1, 20 / Math.max(1, times));
        return start(player, slot, slotKey, Mode.PERTICK, interval, 0, 0, source);
    }

    public static boolean startRandomly(ServerPlayer player, int slot, String slotKey, int min, int max, CommandSourceStack source) {
        // 第一次延迟用 min（保证最小延迟）
        return start(player, slot, slotKey, Mode.RANDOMLY, min, min, max, source);
    }

    public static boolean stop(ServerPlayer player, String slotKey, CommandSourceStack source) {
        UUID id = player.getUUID();
        Map<String, DropTask> playerTasks = tasks.get(id);
        if (playerTasks == null) {
            source.sendSuccess(() -> ServerI18n.tr(source, "carpetprimaryuan.command.dropall.no_task", slotKey), false);
            return true;
        }
        DropTask task = playerTasks.remove(slotKey);
        if (task == null) {
            source.sendSuccess(() -> ServerI18n.tr(source, "carpetprimaryuan.command.dropall.no_task", slotKey), false);
            return true;
        }
        if (playerTasks.isEmpty()) {
            tasks.remove(id);
        }
        final int dropped = task.droppedStacks;
        source.sendSuccess(() -> ServerI18n.tr(source, "carpetprimaryuan.command.dropall.stopped", slotKey, dropped), true);
        return true;
    }

    public static void stopAll(UUID playerId) {
        Map<String, DropTask> playerTasks = tasks.remove(playerId);
        if (playerTasks != null) {
            playerTasks.clear();
        }
    }

    /**
     * 立即丢一次（用于命令层 once 子节点）。
     * 返回丢出的物品数量（0 表示没有可丢的物品）。
     */
    public static int dropOnce(ServerPlayer player, int slot) {
        return dropOneStack(player, slot);
    }

    // ===== Internal =====

    private static boolean start(ServerPlayer player, int slot, String slotKey, Mode mode,
                                  int interval, int min, int max, CommandSourceStack source) {
        ensureRegistered();
        UUID id = player.getUUID();
        Map<String, DropTask> playerTasks = tasks.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        if (playerTasks.containsKey(slotKey)) {
            return false;
        }
        DropTask task = new DropTask(player, slot, slotKey, mode, interval, min, max, source);
        playerTasks.put(slotKey, task);
        return true;
    }

    private static void ensureRegistered() {
        if (registered) return;
        synchronized (DropSlotScheduler.class) {
            if (registered) return;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (tasks.isEmpty()) return;
                // 遍历所有任务
                for (Map<String, DropTask> playerTasks : tasks.values()) {
                    // 复制 key 集合避免并发修改
                    for (String slotKey : playerTasks.keySet().toArray(new String[0])) {
                        DropTask task = playerTasks.get(slotKey);
                        if (task == null) continue;
                        tickTask(task, playerTasks);
                    }
                }
                // 清理空玩家表
                tasks.values().removeIf(Map::isEmpty);
            });
            registered = true;
        }
    }

    private static void tickTask(DropTask task, Map<String, DropTask> playerTasks) {
        ServerPlayer player = task.player;
        if (player == null || player.hasDisconnected() || player.isRemoved()) {
            task.source.sendSuccess(() -> ServerI18n.tr(task.source,
                    "carpetprimaryuan.command.dropall.player_offline",
                    player == null ? "?" : player.getName().getString(), task.slotKey), false);
            playerTasks.remove(task.slotKey);
            return;
        }

        task.ticksUntilNext--;
        if (task.ticksUntilNext > 0) return;

        // 到时机，尝试丢一组（背包空时 count == 0，任务保留等待新物品）
        int count = dropOneStack(player, task.slot);

        if (count > 0) {
            task.droppedStacks++;
            task.droppedItems += count;
        }

        // 计算下一次 ticksUntilNext（背包空也保留任务，等待新物品）
        switch (task.mode) {
            case CONTINUOUS:
                task.ticksUntilNext = 1;
                break;
            case INTERVAL:
                task.ticksUntilNext = task.interval;
                break;
            case AFTER:
                if (count > 0) {
                    // 一次性任务成功执行，移除
                    playerTasks.remove(task.slotKey);
                } else {
                    // 背包空，每 tick 检查等待物品
                    task.ticksUntilNext = 1;
                }
                break;
            case PERTICK:
                task.ticksUntilNext = task.interval;
                break;
            case RANDOMLY:
                task.ticksUntilNext = task.max > task.min
                        ? ThreadLocalRandom.current().nextInt(task.max - task.min + 1) + task.min
                        : task.min;
                break;
        }
    }

    /**
     * 从指定 slot 丢出一整组物品。
     * - slot == -2（all）：找第一个非空槽位丢出
     * - slot == -1（mainhand）：当前选中槽位
     * - 其他：直接对指定 slot 操作
     * 返回丢出的物品数量；0 表示没有可丢的物品。
     */
    private static int dropOneStack(ServerPlayer player, int slot) {
        Inventory inv = player.getInventory();
        if (slot == SLOT_ALL) {
            int size = inv.getContainerSize();
            for (int i = 0; i < size; i++) {
                if (!inv.getItem(i).isEmpty()) {
                    ItemStack stack = inv.removeItem(i, inv.getItem(i).getCount());
                    if (!stack.isEmpty()) {
                        player.drop(stack, false, true);
                        return stack.getCount();
                    }
                }
            }
            return 0;
        } else if (slot == SLOT_MAINHAND) {
            //#if MC < 12105
            //$$ int sel = inv.selected;
            //#else
            int sel = inv.getSelectedSlot();
            //#endif
            ItemStack current = inv.getItem(sel);
            if (current.isEmpty()) return 0;
            ItemStack stack = inv.removeItem(sel, current.getCount());
            if (!stack.isEmpty()) {
                player.drop(stack, false, true);
                return stack.getCount();
            }
            return 0;
        } else {
            ItemStack current = inv.getItem(slot);
            if (current.isEmpty()) return 0;
            ItemStack stack = inv.removeItem(slot, current.getCount());
            if (!stack.isEmpty()) {
                player.drop(stack, false, true);
                return stack.getCount();
            }
            return 0;
        }
    }
}
