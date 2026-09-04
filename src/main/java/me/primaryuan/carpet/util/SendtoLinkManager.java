package me.primaryuan.carpet.util;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 假人背包链接（sendto）核心管理器。
 *
 * 维护 源假人 → 有序目标假人列表 的单向物品流链接，每个源附带一个轮询光标
 * 与一个调度任务（模式对齐 {@link DropSlotScheduler}）。
 *
 * 转移粒度：每次触发只转移一组——源背包（槽位 0–35，跳过盔甲 36–39 与副手 40）
 * 中第一个非空槽位的整组物品，目标同样只接收进槽位 0–35。
 * 每次成功转移后光标 +1，下一组流向下一个目标，多目标逐组轮流分配。
 *
 * 防刷物品核心：原子定量移动——先计算目标背包实际可插入的数量
 * （第一轮合并同类未满堆，第二轮放入空槽），源堆再按实际插入量扣减，
 * 插入多少扣多少；目标满或无效时该堆完整保留在源背包。
 *
 * 调度模式（与 dropall 一致）：
 * - CONTINUOUS：每 tick 转一组（建立链接后的默认模式）
 * - INTERVAL <ticks>：每 ticks 转一组
 * - AFTER <ticks>：延迟 ticks 后转一组（成功后暂停，链接保留）
 * - PERTICK <times>：每秒 times 次转一组
 * - RANDOMLY <min> <max>：随机间隔 min-max tick 转一组
 * - NONE：有链接但暂停自动转移（after 一次性完成 / 规则关闭）
 *
 * 生命周期（双路径清理，保证假人下线后链接无残留）：
 * - 链接只存内存，不写任何文件，服务器重启后全部失效；
 * - 事件路径：监听 Fabric ServerPlayConnectionEvents.DISCONNECT（假人的该事件由
 *   PlayerListFakePlayerEventsMixin 触发，但受 FixBluemap 规则控制，默认关闭），
 *   假人下线时移除所有涉及它（作为源或作为目标）的链接；
 * - 懒清理兜底：每轮转移时检测源/目标有效性，源无效则移除其全部链接，
 *   目标无效则剔除对应链接——即使 FixBluemap 关闭也能在最多一个调度周期内清掉；
 * - 监听 ServerLifecycleEvents.SERVER_STOPPING，服务器停止时清空全部链接；
 * - CarpetPrimaryuanSettings.fakePlayerSendto 为 false 时暂停转移（链接保留）。
 */
public final class SendtoLinkManager {

    /** 转移范围：主背包 + 快捷栏（槽位 0–35），跳过盔甲槽 36–39 与副手 40 */
    private static final int MAIN_INVENTORY_SIZE = 36;

    private SendtoLinkManager() {}

    /** addLink 校验结果，命令层据此给出对应的反馈信息 */
    public enum LinkResult {
        /** 链接建立成功 */
        SUCCESS,
        /** 源不是 carpet 假人 */
        SOURCE_NOT_FAKE,
        /** 目标不在线 */
        TARGET_OFFLINE,
        /** 目标不是 carpet 假人 */
        TARGET_NOT_FAKE,
        /** A→A 自链接 */
        SELF_LINK,
        /** 链接已存在 */
        DUPLICATE
    }

    /** 转移调度模式；NONE = 有链接但暂停自动转移 */
    public enum Mode {
        NONE, CONTINUOUS, INTERVAL, AFTER, PERTICK, RANDOMLY
    }

    /** 单个源假人的链接集合：有序目标列表 + 轮询光标 + 调度状态 + 累计统计 */
    private static final class SourceLinks {
        final List<String> targets = new ArrayList<>();
        int cursor = 0;
        Mode mode = Mode.CONTINUOUS;
        int interval = 1;   // INTERVAL / PERTICK：触发间隔（tick）
        int min = 1;        // RANDOMLY：最小间隔
        int max = 1;        // RANDOMLY：最大间隔
        int ticksUntilNext = 1;
        int transferredStacks = 0;
        int transferredItems = 0;
    }

    /** 源假人名 → 链接集合（仅存内存，不做任何持久化） */
    private static final Map<String, SourceLinks> LINKS = new ConcurrentHashMap<>();

    private static boolean initialized = false;

    // ===== Public API =====

    /**
     * 初始化：注册 tick 转移调度、假人下线清理与服务器停止清空监听。
     * 由 CarpetPrimaryuanServer.onGameStarted 调用一次。
     */
    public static void init() {
        if (initialized) return;
        synchronized (SendtoLinkManager.class) {
            if (initialized) return;

            // 每 tick 检查每个源的调度任务（模式与 DropSlotScheduler 一致）
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                // 规则关闭：暂停转移（链接保留，重新开启后恢复）
                if (!CarpetPrimaryuanSettings.fakePlayerSendto) return;
                if (LINKS.isEmpty()) return;
                for (Map.Entry<String, SourceLinks> entry : LINKS.entrySet()) {
                    tickSource(server, entry.getKey(), entry.getValue());
                }
            });

            // 假人下线时移除所有涉及它的链接（作为源或作为目标）；
            // 真实玩家触发本事件时不会命中任何链接，无副作用
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                    removeAllLinksInvolving(handler.player));

            // 服务器停止时清空全部链接（链接不持久化）
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> LINKS.clear());

            initialized = true;
        }
    }

    /**
     * 建立链接：源假人 → 目标假人，并确保调度任务在运行。
     * 新建的源条目以 CONTINUOUS（每 tick 一组）启动；已有条目追加目标不改当前频率。
     * 源与目标必须都是在线的 carpet 假人（carpet 原生 EntityPlayerMPFake），
     * 不允许 A→A 自链接与重复链接。
     * 注意：不从 ServerPlayer 取 server（ServerPlayer#getServer 在 1.21.10+ 映射中不存在），
     * 由调用方传入。
     *
     * @param server     当前服务器实例（由命令侧 CommandSourceStack#getServer 提供）
     * @param source     命令解析出的源玩家（已在线）
     * @param targetName 命令中输入的目标名
     * @return 校验结果，见 {@link LinkResult}
     */
    public static LinkResult addLink(MinecraftServer server, ServerPlayer source, String targetName) {
        if (!(source instanceof EntityPlayerMPFake)) {
            return LinkResult.SOURCE_NOT_FAKE;
        }
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return LinkResult.TARGET_OFFLINE;
        }
        if (!(target instanceof EntityPlayerMPFake)) {
            return LinkResult.TARGET_NOT_FAKE;
        }
        if (target.getUUID().equals(source.getUUID())) {
            return LinkResult.SELF_LINK;
        }
        String canonicalTargetName = target.getName().getString();
        SourceLinks links = LINKS.computeIfAbsent(source.getName().getString(), k -> new SourceLinks());
        if (indexOfTarget(links, canonicalTargetName) >= 0) {
            return LinkResult.DUPLICATE;
        }
        links.targets.add(canonicalTargetName);
        // 新条目默认 CONTINUOUS 启动；若此前处于 NONE（after 完成后暂停），追加目标时恢复调度
        if (links.mode == Mode.NONE) {
            links.mode = Mode.CONTINUOUS;
            links.ticksUntilNext = 1;
        }
        return LinkResult.SUCCESS;
    }

    /**
     * 设置该源的转移调度模式（须已建立至少一条链接）。
     *
     * @param mode     目标模式（不含 NONE）
     * @param interval INTERVAL / PERTICK 的触发间隔（tick）
     * @param min      RANDOMLY 最小间隔
     * @param max      RANDOMLY 最大间隔
     * @return 是否设置成功（无链接时 false）
     */
    public static boolean setMode(ServerPlayer source, Mode mode, int interval, int min, int max) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null || links.targets.isEmpty()) {
            return false;
        }
        links.mode = mode;
        links.interval = interval;
        links.min = min;
        links.max = max;
        // 首次延迟：INTERVAL/PERTICK/AFTER 用 interval，RANDOMLY 用 min（保证最小延迟）
        links.ticksUntilNext = mode == Mode.RANDOMLY ? min : Math.max(1, interval);
        return true;
    }

    /**
     * 立即转移一组（命令层 once 子节点）。不影响既有调度节奏。
     *
     * @return 实际转移的物品数量；-1 表示该源没有任何链接；0 表示源背包为空
     */
    public static int transferOnce(MinecraftServer server, ServerPlayer source) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null || links.targets.isEmpty()) {
            return -1;
        }
        ServerPlayer src = source;
        if (!isValidFake(src)) {
            LINKS.remove(source.getName().getString());
            return -1;
        }
        int moved = transferOneStack(server, source.getName().getString(), links, src);
        if (moved > 0) {
            links.transferredStacks++;
            links.transferredItems += moved;
        }
        return moved;
    }

    /**
     * 停止并移除该源的全部链接与调度任务。
     *
     * @return [累计转移组数, 累计转移物品数]；null 表示该源没有任何链接
     */
    public static int[] stopAndRemove(ServerPlayer source) {
        SourceLinks links = LINKS.remove(source.getName().getString());
        if (links == null) {
            return null;
        }
        return new int[]{links.transferredStacks, links.transferredItems};
    }

    /**
     * 获取该源当前的目标名列表（用于命令反馈）。
     */
    public static List<String> getLinks(ServerPlayer source) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null) {
            return List.of();
        }
        return new ArrayList<>(links.targets);
    }

    // ===== Internal：调度 =====

    /**
     * 每 tick 处理单个源的调度任务。
     * 源无效（下线/移除）时懒清理其全部链接；到触发时机时转移一组。
     */
    private static void tickSource(MinecraftServer server, String sourceName, SourceLinks links) {
        if (links.mode == Mode.NONE || links.targets.isEmpty()) {
            return;
        }
        // 源已下线/无效：移除其全部链接（懒清理兜底——FixBluemap 规则关闭时
        // 假人不触发 Fabric DISCONNECT 事件，靠这里清掉残留）
        ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
        if (!isValidFake(source)) {
            LINKS.remove(sourceName);
            return;
        }

        if (--links.ticksUntilNext > 0) {
            return;
        }

        // 到时机，尝试转移一组（源背包空时 moved == 0，任务保留等待新物品）
        int moved = transferOneStack(server, sourceName, links, source);
        if (moved > 0) {
            links.transferredStacks++;
            links.transferredItems += moved;
        }

        // 计算下一次触发时机（源背包空也保留任务，等待新物品）
        switch (links.mode) {
            case CONTINUOUS:
                links.ticksUntilNext = 1;
                break;
            case INTERVAL:
            case PERTICK:
                links.ticksUntilNext = links.interval;
                break;
            case AFTER:
                if (moved > 0) {
                    // 一次性任务成功执行：暂停调度（链接保留，可再设频率重启）
                    links.mode = Mode.NONE;
                } else {
                    // 背包空，每 tick 检查等待物品
                    links.ticksUntilNext = 1;
                }
                break;
            case RANDOMLY:
                links.ticksUntilNext = links.max > links.min
                        ? ThreadLocalRandom.current().nextInt(links.max - links.min + 1) + links.min
                        : links.min;
                break;
            default:
                links.mode = Mode.NONE;
                break;
        }
    }

    // ===== Internal：转移 =====

    /**
     * 转移一组：源背包（槽位 0–35）中第一个非空槽位的整组物品，
     * 从光标处目标开始依次尝试插入（第一个目标收不完的部分给下一个）。
     * 成功转移后光标 +1，下一组流向下一个目标（多目标逐组轮流分配）。
     *
     * @return 实际转移的物品数量（0 表示源背包为空或全部目标无法接收）
     */
    private static int transferOneStack(MinecraftServer server, String sourceName, SourceLinks links, ServerPlayer source) {
        if (links.targets.isEmpty()) {
            return 0;
        }
        int size = links.targets.size();
        // 预解析本轮可用目标；已下线/无效的目标剔除对应链接（懒清理）
        List<ServerPlayer> roundTargets = new ArrayList<>(size);
        List<String> invalidTargets = null;
        for (int i = 0; i < size; i++) {
            String targetName = links.targets.get((links.cursor + i) % size);
            ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
            if (isValidFake(target)) {
                roundTargets.add(target);
            } else {
                if (invalidTargets == null) {
                    invalidTargets = new ArrayList<>();
                }
                invalidTargets.add(targetName);
            }
        }
        if (invalidTargets != null) {
            for (String invalidName : invalidTargets) {
                int index = indexOfTarget(links, invalidName);
                if (index >= 0) {
                    removeTargetAt(links, index);
                }
            }
            if (links.targets.isEmpty()) {
                LINKS.remove(sourceName);
                return 0;
            }
        }

        // 找源背包中第一个非空槽位（仅 0–35）
        Inventory sourceInv = source.getInventory();
        int slot = -1;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (!sourceInv.getItem(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return 0;
        }

        ItemStack stack = sourceInv.getItem(slot);
        int remaining = stack.getCount();
        for (ServerPlayer target : roundTargets) {
            if (remaining <= 0) {
                break;
            }
            remaining -= insertIntoInventory(target.getInventory(), stack, remaining);
        }
        // 原子扣减：插入多少扣多少；一个都没插进时源堆保持原样
        int moved = stack.getCount() - remaining;
        if (moved <= 0) {
            return 0;
        }
        if (moved >= stack.getCount()) {
            // 全部转移，清空槽位
            sourceInv.setItem(slot, ItemStack.EMPTY);
        } else {
            // 部分转移，按实际插入量扣减
            stack.setCount(stack.getCount() - moved);
        }

        // 本次发生转移：推进光标，下一组流向下一个目标
        links.cursor = (links.cursor + 1) % links.targets.size();
        return moved;
    }

    /**
     * 把源堆中最多 maxCount 个物品原子插入目标背包（仅槽位 0–35）。
     * 第一轮找目标中同类且未满的堆合并，第二轮放入空槽。
     *
     * @return 实际插入数量（0 表示目标背包本轮无法接收该物品）
     */
    private static int insertIntoInventory(Inventory inv, ItemStack sourceStack, int maxCount) {
        int moved = 0;
        int remaining = maxCount;

        // 第一轮：合并到目标中同类且未满的堆
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            ItemStack targetStack = inv.getItem(i);
            if (targetStack.isEmpty() || targetStack.getCount() >= targetStack.getMaxStackSize()) {
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(targetStack, sourceStack)) {
                continue;
            }
            int take = Math.min(targetStack.getMaxStackSize() - targetStack.getCount(), remaining);
            targetStack.setCount(targetStack.getCount() + take);
            moved += take;
            remaining -= take;
        }

        // 第二轮：放入空槽
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (!inv.getItem(i).isEmpty()) {
                continue;
            }
            int take = Math.min(sourceStack.getMaxStackSize(), remaining);
            ItemStack newStack = sourceStack.copy();
            newStack.setCount(take);
            inv.setItem(i, newStack);
            moved += take;
            remaining -= take;
        }
        return moved;
    }

    /** 目标是否为当前有效的 carpet 假人（在线、未断开、未被移除） */
    private static boolean isValidFake(ServerPlayer player) {
        return player instanceof EntityPlayerMPFake
                && !player.hasDisconnected()
                && !player.isRemoved();
    }

    /** 移除所有涉及该假人的链接（作为源：移除其全部链接；作为目标：从各源的目标列表中移除） */
    private static void removeAllLinksInvolving(ServerPlayer player) {
        String name = player.getName().getString();
        // 作为源：移除其发出的全部链接
        LINKS.remove(name);
        // 作为目标：从所有源的目标列表中移除该假人
        Iterator<Map.Entry<String, SourceLinks>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            SourceLinks links = it.next().getValue();
            int index = indexOfTarget(links, name);
            if (index >= 0) {
                removeTargetAt(links, index);
                if (links.targets.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    /** 在目标列表中查找指定目标名（忽略大小写），未找到返回 -1 */
    private static int indexOfTarget(SourceLinks links, String targetName) {
        for (int i = 0; i < links.targets.size(); i++) {
            if (links.targets.get(i).equalsIgnoreCase(targetName)) {
                return i;
            }
        }
        return -1;
    }

    /** 移除目标列表中指定位置的条目，并同步修正轮询光标 */
    private static void removeTargetAt(SourceLinks links, int index) {
        links.targets.remove(index);
        // 被移除的目标在光标之前时光标回退，保持指向的相对位置不变
        if (index < links.cursor) {
            links.cursor--;
        }
        if (links.targets.isEmpty()) {
            links.cursor = 0;
        } else if (links.cursor >= links.targets.size()) {
            // 光标越界时回卷
            links.cursor = 0;
        }
    }
}
