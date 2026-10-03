package me.primaryuan.carpet.handler.redPacket;

import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 红包（redPacket）：/redpacket 发红包，聊天框广播可点击领取，纯服务端。
 *
 * <p>流程：命令（份数+祝福语）→ 类型选择 GUI → 物品投放/专属对象选择（专属：点玩家两次
 * 选中确认，再投放物品）→ [口令：铁砧设口令] → 广播可点击消息 → 领取。份额在发出时按
 * 类型切好（{@link RedPacketSplitter}，总量守恒），领取取走一整份；过期未领份额原样退回
 * 发送者（离线则暂存内存、上线补发——重启即失，文档如实注明）。防滥用：领取点击防抖、
 * 发送冷却、每人同时最多 3 个未结束红包。</p>
 */
public final class RedPacketManager {

    /** 红包有效期（tick，3 分钟） */
    private static final long EXPIRE_TICKS = 3 * 60 * 20L;
    /** 过期红包保留时长（tick，30 分钟，供"已过期"提示后清理） */
    private static final long EXPIRED_KEEP_TICKS = 30 * 60 * 20L;
    /** 发送冷却（tick，10 秒） */
    private static final long SEND_COOLDOWN_TICKS = 200;
    /** 领取点击防抖（tick） */
    private static final long CLAIM_DEBOUNCE_TICKS = 10;
    /** 每人同时未结束红包上限 */
    private static final int MAX_ACTIVE_PER_PLAYER = 3;

    private static final Map<Integer, RedPacket> PACKETS = new LinkedHashMap<>();
    private static final Map<UUID, List<ItemStack>> OFFLINE_REFUNDS = new HashMap<>();
    private static final Map<UUID, GuiSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SEND = new HashMap<>();
    private static final Map<UUID, Long> LAST_CLAIM = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static boolean registered = false;
    private static int nextId = 1;
    private static MinecraftServer currentServer;

    private RedPacketManager() {}

    // ==================== 生命周期 ====================

    /** CarpetPrimaryuanServer.onGameStarted 调用（一次性） */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        // 广播/在线查询用的服务器实例（GUI 与领取路径都发生在 tick 之间）
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentServer = null);
        // 口令领取：聊天框打出正确口令
        ServerMessageEvents.CHAT_MESSAGE.register(RedPacketManager::onChatMessage);
        // 离线暂存退回：上线补发
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                deliverOfflineRefunds(handler.player));
        // 停服：开放中的 GUI 会话原样退回（内存态红包/暂存不跨重启，文档如实注明）
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (GuiSession session : new ArrayList<>(SESSIONS.values())) {
                session.returnContainerItems();
            }
            SESSIONS.clear();
            PACKETS.clear();
            OFFLINE_REFUNDS.clear();
            LAST_SEND.clear();
            LAST_CLAIM.clear();
        });
        ServerTickScheduler.register(RedPacketManager::tick);
    }

    /** 过期检查 + 过期红包清理，每 20 tick 一次 */
    private static boolean tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            long now = server.getTickCount();
            List<Integer> expired = new ArrayList<>();
            for (RedPacket packet : PACKETS.values()) {
                if (!packet.expired && now >= packet.expireTick) {
                    packet.expired = true;
                    refundUnclaimed(packet, true);
                }
                if (packet.expired && now >= packet.expireTick + EXPIRED_KEEP_TICKS) {
                    expired.add(packet.id);
                }
            }
            for (Integer id : expired) {
                PACKETS.remove(id);
            }
        }
        return true;
    }

    // ==================== GUI 会话 ====================

    private static final class GuiSession {
        final ServerPlayer player;
        RedPacket.Type type;
        final int count;
        final String message;
        RedPacketGui.RedPacketContainer container;
        List<ItemStack> payload;
        List<String> targetNames;
        List<UUID> targetIds;
        int selectedSlot = -1;
        UUID targetId;
        String targetName;
        boolean awaitingPassword;

        GuiSession(ServerPlayer player, RedPacket.Type type, int count, String message) {
            this.player = player;
            this.type = type;
            this.count = count;
            this.message = message;
        }

        /** 收集投放槽 0-44 的物品并清空容器 */
        List<ItemStack> collectPayload() {
            List<ItemStack> payload = new ArrayList<>();
            for (int slot = 0; slot < 45; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    payload.add(stack.copyWithCount(stack.getCount()));
                    container.forceSet(slot, ItemStack.EMPTY);
                }
            }
            return payload;
        }

        void returnContainerItems() {
            if (container == null) {
                return;
            }
            List<ItemStack> remaining = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    remaining.add(stack);
                    container.forceSet(slot, ItemStack.EMPTY);
                }
            }
            giveItems(player, remaining);
        }
    }

    // ==================== 入口：打开类型选择 ====================

    /** 命令入口：校验通过后打开类型选择 GUI */
    public static void openTypeMenu(ServerPlayer player, int count, String message) {
        GuiSession session = new GuiSession(player, null, count, message);
        SESSIONS.put(player.getUUID(), session);
        Component[] names = new Component[RedPacket.Type.values().length];
        String[] lore = new String[RedPacket.Type.values().length];
        for (RedPacket.Type type : RedPacket.Type.values()) {
            names[type.ordinal()] = ServerI18n.tr("carpetprimaryuan.redpacket.type." + type.name().toLowerCase());
            lore[type.ordinal()] = ServerI18n.tr("carpetprimaryuan.redpacket.gui.pick").getString();
        }
        ItemStack[] icons = new ItemStack[RedPacket.Type.values().length];
        for (RedPacket.Type type : RedPacket.Type.values()) {
            icons[type.ordinal()] = RedPacketGui.icon(RedPacketGui.redShulkerBox(),
                    names[type.ordinal()].getString(), lore[type.ordinal()]);
        }
        RedPacketGui.openTypeMenu(player, ServerI18n.tr("carpetprimaryuan.redpacket.gui.title"),
                icons, index -> onTypePicked(player.getUUID(), RedPacket.Type.values()[index]));
    }

    private static void onTypePicked(UUID playerId, RedPacket.Type type) {
        ServerPlayer player = findOnlinePlayer(playerId);
        GuiSession session = SESSIONS.get(playerId);
        if (player == null || session == null) {
            return;
        }
        // 类型点击统一延迟到下一 tick，避免在点击包处理中途切换菜单
        ServerTickScheduler.registerDelayed(1, server -> {
            openAfterTypePick(player, type);
            return false;
        });
    }

    private static void openAfterTypePick(ServerPlayer player, RedPacket.Type type) {
        GuiSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.type != null) {
            return;
        }
        session.type = type;
        if (type == RedPacket.Type.TARGETED) {
            openTargetSelect(session);
        } else {
            openItemInput(session);
        }
    }

    // ==================== 物品投放 GUI ====================

    private static void openItemInput(GuiSession session) {
        ServerPlayer player = session.player;
        // 关闭（含取消/直接 ESC）：把 0-44 槽剩余物品原样退回（giveItems 放不下的掉脚下）
        Runnable onRemoved = () -> {
            RedPacketGui.RedPacketContainer container = session.container;
            if (container == null) {
                return;
            }
            List<ItemStack> remaining = new ArrayList<>();
            for (int slot = 0; slot < 45; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    remaining.add(stack);
                    container.forceSet(slot, ItemStack.EMPTY);
                }
            }
            GuiSession open = SESSIONS.get(player.getUUID());
            if (open == session && !remaining.isEmpty()) {
                giveItems(player, remaining);
            }
            if (open == session && !open.awaitingPassword) {
                SESSIONS.remove(player.getUUID());
            }
        };
        session.container = RedPacketGui.openItemInput(player,
                ServerI18n.tr("carpetprimaryuan.redpacket.gui.items"),
                RedPacketGui.icon(net.minecraft.world.item.Items.BARRIER,
                        ServerI18n.tr("carpetprimaryuan.redpacket.gui.cancel").getString()),
                RedPacketGui.icon(RedPacketGui.limeDye(),
                        ServerI18n.tr("carpetprimaryuan.redpacket.gui.confirm").getString()),
                RedPacketGui.icon(net.minecraft.world.item.Items.HOPPER,
                        ServerI18n.tr("carpetprimaryuan.redpacket.gui.clear").getString()),
                slot -> onItemButton(player.getUUID(), slot),
                onRemoved);
    }

    private static void onItemButton(UUID playerId, int rawSlot) {
        ServerPlayer player = findOnlinePlayer(playerId);
        GuiSession session = SESSIONS.get(playerId);
        if (player == null || session == null) {
            return;
        }
        ServerTickScheduler.registerDelayed(1, server -> {
            if (rawSlot == RedPacketGui.SLOT_CANCEL) {
                // removed() 负责退回剩余物品，这里只收尾
                player.closeContainer();
            } else if (rawSlot == RedPacketGui.SLOT_CLEAR) {
                List<ItemStack> items = session.collectPayload();
                giveItems(player, items);
            } else if (rawSlot == RedPacketGui.SLOT_CONFIRM) {
                onConfirmPayload(session);
            }
            return false;
        });
    }

    private static void onConfirmPayload(GuiSession session) {
        List<ItemStack> payload = session.collectPayload();
        if (payload.isEmpty()) {
            session.player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.empty"));
            return;
        }
        if (session.type == RedPacket.Type.PASSWORD) {
            // 物品锁定，进入口令设置；口令取消/无效则整体退回
            session.payload = payload;
            session.awaitingPassword = true;
            ServerPlayer player = session.player;
            RedPacketGui.openPasswordAnvil(player,
                    ServerI18n.tr("carpetprimaryuan.redpacket.gui.password"),
                    text -> onPasswordSet(session, text),
                    () -> {
                        // 铁砧关闭：未设成口令则退回红包物品并结束会话
                        GuiSession open = SESSIONS.get(player.getUUID());
                        if (open == session && open.awaitingPassword) {
                            giveItems(player, session.payload);
                            SESSIONS.remove(player.getUUID());
                        }
                    });
            return;
        }
        createAndBroadcast(session, payload, session.targetId, null);
    }

    private static void onPasswordSet(GuiSession session, String text) {
        String password = sanitizeMessage(text);
        if (password.isEmpty() || RedPacketGui.PASSWORD_PAPER_NAME.equals(password)
                || password.length() > 32) {
            session.player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.password_invalid"));
            return;
        }
        session.awaitingPassword = false;
        createAndBroadcast(session, session.payload, null, password);
    }

    // ==================== 专属对象选择 ====================

    private static void openTargetSelect(GuiSession session) {
        List<String> names = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer online : session.player.level().getServer().getPlayerList().getPlayers()) {
            if (online.getUUID().equals(session.player.getUUID())) {
                continue;
            }
            names.add(online.getName().getString());
            ids.add(online.getUUID());
        }
        if (names.isEmpty()) {
            session.player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.target_none"));
            SESSIONS.remove(session.player.getUUID());
            return;
        }
        session.targetNames = names;
        session.targetIds = ids;
        List<ItemStack> heads = new ArrayList<>();
        for (String name : names) {
            heads.add(RedPacketGui.head(name));
        }
        session.container = RedPacketGui.openTargetSelect(session.player,
                ServerI18n.tr("carpetprimaryuan.redpacket.gui.target"), heads,
                slot -> onHeadClicked(session, slot));
    }

    private static void onHeadClicked(GuiSession session, int slot) {
        if (slot >= session.targetNames.size()) {
            return;
        }
        ServerPlayer player = session.player;
        if (session.selectedSlot == slot) {
            // 两次点击同一玩家 = 选中并确认，进入物品投放
            session.targetId = session.targetIds.get(slot);
            session.targetName = session.targetNames.get(slot);
            ServerTickScheduler.registerDelayed(1, server -> {
                player.closeContainer();
                openItemInput(SESSIONS.get(player.getUUID()));
                return false;
            });
            return;
        }
        // 单次点击 = 选中：高亮 + 说明
        int previous = session.selectedSlot;
        session.selectedSlot = slot;
        String hint = ServerI18n.tr("carpetprimaryuan.redpacket.gui.selected").getString();
        if (previous >= 0) {
            ItemStack head = RedPacketGui.head(session.targetNames.get(previous));
            session.container.forceSet(previous, head);
        }
        ItemStack selected = RedPacketGui.head(session.targetNames.get(slot));
        RedPacketGui.markSelected(selected, hint);
        session.container.forceSet(slot, selected);
    }

    // ==================== 发出 ====================

    private static void createAndBroadcast(GuiSession session, List<ItemStack> payload,
                                           UUID target, String password) {
        ServerPlayer player = session.player;
        MinecraftServer server = player.level().getServer();
        long now = server.getTickCount();
        Long last = LAST_SEND.get(player.getUUID());
        if (last != null && now - last < SEND_COOLDOWN_TICKS) {
            giveItems(player, payload);
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.cooldown"));
            SESSIONS.remove(player.getUUID());
            closeNextTick(player);
            return;
        }
        int active = 0;
        for (RedPacket packet : PACKETS.values()) {
            if (!packet.expired && packet.senderId.equals(player.getUUID())) {
                active++;
            }
        }
        if (active >= MAX_ACTIVE_PER_PLAYER) {
            giveItems(player, payload);
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.limit"));
            SESSIONS.remove(player.getUUID());
            closeNextTick(player);
            return;
        }

        int shares = session.type == RedPacket.Type.TARGETED ? 1 : Math.max(1, session.count);
        List<List<ItemStack>> split = switch (session.type) {
            case LUCKY, PASSWORD -> RedPacketSplitter.lucky(payload, shares, RANDOM);
            case NORMAL -> RedPacketSplitter.even(payload, shares, RANDOM);
            case TARGETED -> RedPacketSplitter.whole(payload);
        };
        RedPacket packet = new RedPacket(nextId++, player.getUUID(), player.getName().getString(),
                session.type, session.message, split, target, session.targetName, now + EXPIRE_TICKS);
        packet.password = password;
        PACKETS.put(packet.id, packet);
        LAST_SEND.put(player.getUUID(), now);
        SESSIONS.remove(player.getUUID());
        closeNextTick(player);
        broadcast(server, packet);
    }

    /** 关闭菜单统一延迟到下一 tick，避免在点击包处理中途切换容器 */
    private static void closeNextTick(ServerPlayer player) {
        ServerTickScheduler.registerDelayed(1, server -> {
            if (player.hasDisconnected()) {
                return false;
            }
            player.closeContainer();
            return false;
        });
    }

    /** 聊天框广播：xxx发了个类型 [红包：祝福语]，亮红可点击，悬浮详情 */
    private static void broadcast(MinecraftServer server, RedPacket packet) {
        String typeName = ServerI18n.tr("carpetprimaryuan.redpacket.type."
                + packet.type.name().toLowerCase()).getString();
        String command = "/redpacket claim " + packet.id;
        Component hover = Component.literal("")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.message", packet.message))
                .append("\n")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.type", typeName))
                .append("\n")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.shares", packet.sharesLeft()))
                .append("\n")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.time"))
                .append("\n")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.claim"));
        MutableComponent clickable = Component.literal(
                ServerI18n.tr("carpetprimaryuan.redpacket.clickable", packet.message).getString());
        clickable.setStyle(RedPacketGui.claimStyle(command, hover));
        MutableComponent line = Component.literal(packet.senderName)
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.broadcast", typeName))
                .append(clickable);
        server.getPlayerList().broadcastSystemMessage(line, false);
    }

    // ==================== 领取 ====================

    /** 聊天点击领取（/redpacket claim <id>） */
    public static void claim(ServerPlayer player, int id) {
        RedPacket packet = PACKETS.get(id);
        if (packet == null) {
            return;
        }
        if (packet.expired) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.expired", packet.senderName));
            return;
        }
        if (packet.sharesLeft() <= 0) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.done", packet.senderName));
            return;
        }
        if (packet.senderId.equals(player.getUUID())) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.own"));
            return;
        }
        if (packet.claimed.contains(player.getUUID())) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.claimed"));
            return;
        }
        long now = player.level().getServer().getTickCount();
        Long last = LAST_CLAIM.get(player.getUUID());
        if (last != null && now - last < CLAIM_DEBOUNCE_TICKS) {
            return;
        }
        LAST_CLAIM.put(player.getUUID(), now);
        if (packet.type == RedPacket.Type.TARGETED && !packet.target.equals(player.getUUID())) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.exclusive", packet.targetName));
            return;
        }
        if (packet.type == RedPacket.Type.PASSWORD && packet.password != null) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.password_hint"));
            return;
        }
        finishClaim(player, packet);
    }

    /** 口令聊天领取：打出与某口令红包完全一致的文本即领取 */
    private static void onChatMessage(net.minecraft.network.chat.PlayerChatMessage message,
                                      ServerPlayer sender,
                                      net.minecraft.network.chat.ChatType.Bound parameters) {
        String content = message.signedContent();
        if (content == null || content.isBlank()) {
            return;
        }
        String text = content.trim();
        if (text.isEmpty()) {
            return;
        }
        RedPacket matched = null;
        boolean matchedClaimed = false;
        boolean matchedOwn = false;
        for (RedPacket packet : PACKETS.values()) {
            if (packet.expired || packet.type != RedPacket.Type.PASSWORD
                    || packet.password == null || packet.sharesLeft() <= 0
                    || !packet.password.equals(text)) {
                continue;
            }
            if (packet.senderId.equals(sender.getUUID())) {
                matchedOwn = true;
                continue;
            }
            if (packet.claimed.contains(sender.getUUID())) {
                matchedClaimed = true;
                continue;
            }
            matched = packet;
            break;
        }
        if (matched != null) {
            finishClaim(sender, matched);
        } else if (matchedOwn) {
            sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.own"));
        } else if (matchedClaimed) {
            sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.claimed"));
        }
    }

    /** 校验全通过的最终领取：扣一份、物品进包、消息、领完即结束 */
    private static void finishClaim(ServerPlayer player, RedPacket packet) {
        List<ItemStack> share = packet.takeShare(player.getUUID());
        giveItems(player, share);
        player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.claim_success",
                packet.senderName, itemsDescription(share)));
        if (packet.sharesLeft() <= 0) {
            // 领完立即结束
            PACKETS.remove(packet.id);
        }
    }

    // ==================== 退回 ====================

    private static void refundUnclaimed(RedPacket packet, boolean notify) {
        List<ItemStack> items = packet.unclaimedItems();
        if (items.isEmpty()) {
            return;
        }
        ServerPlayer sender = findOnlinePlayer(packet.senderId);
        if (sender != null) {
            giveItems(sender, items);
            if (notify) {
                sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.refund",
                        itemsDescription(items)));
            }
        } else {
            OFFLINE_REFUNDS.computeIfAbsent(packet.senderId, k -> new ArrayList<>()).addAll(items);
        }
    }

    private static void deliverOfflineRefunds(ServerPlayer player) {
        List<ItemStack> refunds = OFFLINE_REFUNDS.remove(player.getUUID());
        if (refunds != null && !refunds.isEmpty()) {
            giveItems(player, refunds);
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.offline_refund",
                    itemsDescription(refunds)));
        }
    }

    // ==================== 工具 ====================

    private static void giveItems(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copyWithCount(stack.getCount());
            // Inventory.add 会就地缩减 copy（放得下多少拿多少），返回值仅表示"至少放入一件"；
            // 背包只装得下一部分时返回 true——剩余必须无条件掉落，否则蒸发（字节码实证）
            player.getInventory().add(copy);
            if (!copy.isEmpty()) {
                dropAtFeet(player, copy);
            }
        }
    }

    /** 26.3 起 drop 第三参改 Prediction 枚举（对齐 DropSlotScheduler 既有分叉） */
    private static void dropAtFeet(ServerPlayer player, ItemStack stack) {
        //#if MC >= 260300
        //$$ player.drop(stack, true, net.minecraft.util.Prediction.SERVER_ONLY);
        //#else
        player.drop(stack, false, true);
        //#endif
    }

    /** 物品列表描述：同类合并 "钻石×3、苹果×1" */
    private static String itemsDescription(List<ItemStack> stacks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(ServerI18n.tr("carpetprimaryuan.redpacket.msg.item_sep").getString());
            }
            sb.append(entry.getKey()).append("×").append(entry.getValue());
        }
        return sb.toString();
    }

    /** 过滤 §、换行、回车（祝福语与口令共用） */
    public static String sanitizeMessage(String raw) {
        return raw.replace("§", "").replace("\n", "").replace("\r", "");
    }

    private static ServerPlayer findOnlinePlayer(UUID id) {
        if (currentServer == null) {
            return null;
        }
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(id)) {
                return player;
            }
        }
        return null;
    }
}
