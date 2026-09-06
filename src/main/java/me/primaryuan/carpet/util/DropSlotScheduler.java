package me.primaryuan.carpet.util;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 假人持续清空背包的 tick 调度核心。
 *
 * 按 (玩家 UUID, slotKey) 维度去重，避免同槽位重复注册。
 * 通过 Fabric API 的 {@link ServerTickEvents#END_SERVER_TICK} 在主线程上调度丢出任务。
 * 调度模式与延迟计算由 {@link ScheduleMode} 统一提供。
 */
public final class DropSlotScheduler {

    public static final int SLOT_ALL = -2;
    public static final int SLOT_MAINHAND = -1;
    public static final int SLOT_OFFHAND = 40;

    /** stop 结果：STOPPED = 已移除任务；NO_TASK = 该玩家没有对应任务 */
    public enum StopResult { STOPPED, NO_TASK }

    /** stop 结果与累计统计，供命令层反馈（调度器本身不发消息） */
    public record StopSummary(StopResult result, int droppedStacks) {
        public static StopSummary noTask() { return new StopSummary(StopResult.NO_TASK, 0); }
    }

    private DropSlotScheduler() {}

    private static final class DropTask {
        final ServerPlayer player;
        final UUID playerId;
        final int slot;
        final String slotKey;
        final ScheduleMode mode;
        final int interval; // for INTERVAL / PERTICK
        final int min;       // for RANDOMLY
        final int max;       // for RANDOMLY
        int ticksUntilNext;
        int droppedStacks;
        int droppedItems;
        final CommandSourceStack source;

        DropTask(ServerPlayer player, int slot, String slotKey, ScheduleMode mode,
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

    /** 玩家 UUID →（slotKey → 任务）；仅服务器主线程访问 */
    private static final Map<UUID, Map<String, DropTask>> tasks = new HashMap<>();
    private static boolean registered = false;

    // ===== Public API =====

    /**
     * 注册调度任务。按 (玩家 UUID, slotKey) 去重，同槽位已有任务时返回 false。
     * 模式语义与参数换算见 {@link ScheduleMode}；RANDOMLY 的首次延迟取 interval
     * （调用方传 min 以保证最小延迟）。
     */
    public static boolean start(ServerPlayer player, int slot, String slotKey, ScheduleMode mode,
                                int interval, int min, int max, CommandSourceStack source) {
        ensureRegistered();
        UUID id = player.getUUID();
        Map<String, DropTask> playerTasks = tasks.computeIfAbsent(id, k -> new HashMap<>());
        if (playerTasks.containsKey(slotKey)) {
            return false;
        }
        DropTask task = new DropTask(player, slot, slotKey, mode, interval, min, max, source);
        playerTasks.put(slotKey, task);
        return true;
    }

    public static StopSummary stop(ServerPlayer player, String slotKey) {
        Map<String, DropTask> playerTasks = tasks.get(player.getUUID());
        if (playerTasks == null) {
            return StopSummary.noTask();
        }
        DropTask task = playerTasks.remove(slotKey);
        if (task == null) {
            return StopSummary.noTask();
        }
        if (playerTasks.isEmpty()) {
            tasks.remove(player.getUUID());
        }
        return new StopSummary(StopResult.STOPPED, task.droppedStacks);
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

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        ServerTickScheduler.register(server -> {
            if (tasks.isEmpty()) return true;
            // 所有状态仅在服务器主线程访问，可直接迭代移除，无需防御性拷贝
            Iterator<Map.Entry<UUID, Map<String, DropTask>>> playerIt = tasks.entrySet().iterator();
            while (playerIt.hasNext()) {
                Map<String, DropTask> playerTasks = playerIt.next().getValue();
                playerTasks.values().removeIf(task -> !tickTask(task));
                if (playerTasks.isEmpty()) {
                    playerIt.remove();
                }
            }
            return true;
        });
    }

    /**
     * 推进单个任务（每 tick 调用）。
     *
     * @return false 表示任务已结束（玩家离线清理或 AFTER 完成），由调度循环移除
     */
    private static boolean tickTask(DropTask task) {
        ServerPlayer player = task.player;
        if (player == null || player.hasDisconnected() || player.isRemoved()) {
            task.source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.dropall.player_offline",
                    player == null ? "?" : player.getName().getString(), task.slotKey), false);
            return false;
        }

        task.ticksUntilNext--;
        if (task.ticksUntilNext > 0) return true;

        // 到时机，尝试丢一组（背包空时 count == 0，任务保留等待新物品）
        int count = dropOneStack(player, task.slot);

        if (count > 0) {
            task.droppedStacks++;
            task.droppedItems += count;
        }

        // 计算下一次 ticksUntilNext（背包空也保留任务，等待新物品）
        int next = task.mode.nextDelay(task.interval, task.min, task.max, count > 0);
        if (next < 0) {
            // AFTER 一次性任务成功执行，结束
            return false;
        }
        task.ticksUntilNext = next;
        return true;
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
        int index;
        if (slot == SLOT_ALL) {
            index = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (!inv.getItem(i).isEmpty()) {
                    index = i;
                    break;
                }
            }
            if (index < 0) return 0;
        } else if (slot == SLOT_MAINHAND) {
            //#if MC < 12105
            //$$ index = inv.selected;
            //#else
            index = inv.getSelectedSlot();
            //#endif
        } else {
            index = slot;
        }
        ItemStack current = inv.getItem(index);
        if (current.isEmpty()) return 0;
        ItemStack stack = inv.removeItem(index, current.getCount());
        if (stack.isEmpty()) return 0;
        player.drop(stack, false, true);
        return stack.getCount();
    }
}
