package me.primaryuan.carpet.handler.redPacket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    /** 点击口令红包链接的痕迹（uuid → [tick, packetId]）：口令对错提示仅在其后短窗口内给出 */
    private static final Map<UUID, long[]> LAST_HINT = new HashMap<>();
    /** 口令对错提示的窗口（tick，60 秒） */
    private static final long HINT_WINDOW_TICKS = 60 * 20L;
    private static final Random RANDOM = new Random();
    private static boolean registered = false;
    private static int nextId = 1;
    private static MinecraftServer currentServer;
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 过期前提醒提前量（tick，30 秒） */
    private static final long EXPIRE_WARN_TICKS = 600;
    /** 每人最近一次成功发放的配置（/redpacket again 用，内存态） */
    private static final Map<UUID, LastSend> LAST_CONFIG = new HashMap<>();

    /**
     * 手气王稀有度分档（代码内置，保证"1 件高稀有 > 任意数量低稀有"）：
     * 档间价值差 = {@link #TIER_STRIDE}，大于单个红包物理上限的物品总数
     * （投放 GUI 45 格 × 64 = 2880，跨档价值不可能被数量追平）。
     * 0=常见（未列出物品一律此档）1=铁器级 2=钻石/绿宝石级 3=下界合金/图腾级 4=鞘翅/下界之星级。
     */
    private static final Map<String, Integer> ITEM_RARITY = new HashMap<>();

    /** 档间价值跨度（10^9，远大于同档内价值之和的可能上限） */
    private static final long TIER_STRIDE = 1_000_000_000L;

    /** 同档内单件价值（item id → 单件价值，未列出按 1/件；管理员可经 values.json 调整） */
    private static final Map<String, Long> ITEM_VALUES = new HashMap<>();

    static {
        ITEM_RARITY.put("minecraft:iron_ingot", 1);
        ITEM_RARITY.put("minecraft:experience_bottle", 1);
        ITEM_RARITY.put("minecraft:gold_ingot", 2);
        ITEM_RARITY.put("minecraft:golden_apple", 2);
        ITEM_RARITY.put("minecraft:diamond", 2);
        ITEM_RARITY.put("minecraft:emerald", 2);
        ITEM_RARITY.put("minecraft:enchanted_golden_apple", 3);
        ITEM_RARITY.put("minecraft:netherite_ingot", 3);
        ITEM_RARITY.put("minecraft:ancient_debris", 3);
        ITEM_RARITY.put("minecraft:totem_of_undying", 3);
        ITEM_RARITY.put("minecraft:nether_star", 4);
        ITEM_RARITY.put("minecraft:elytra", 4);

        ITEM_VALUES.put("minecraft:netherite_ingot", 300L);
        ITEM_VALUES.put("minecraft:nether_star", 1000L);
        ITEM_VALUES.put("minecraft:ancient_debris", 300L);
        ITEM_VALUES.put("minecraft:elytra", 1000L);
        ITEM_VALUES.put("minecraft:totem_of_undying", 500L);
        ITEM_VALUES.put("minecraft:enchanted_golden_apple", 500L);
        ITEM_VALUES.put("minecraft:golden_apple", 100L);
        ITEM_VALUES.put("minecraft:diamond", 200L);
        ITEM_VALUES.put("minecraft:emerald", 200L);
        ITEM_VALUES.put("minecraft:gold_ingot", 50L);
        ITEM_VALUES.put("minecraft:iron_ingot", 10L);
        ITEM_VALUES.put("minecraft:copper_ingot", 5L);
        ITEM_VALUES.put("minecraft:experience_bottle", 10L);
    }

    /** 发放配置快照（/redpacket again 复原用） */
    private record LastSend(RedPacket.Type type, int count, String message, UUID targetId, String targetName) {
    }

    /** 退订红包广播的玩家名（按名持久化于 config/carpet-pry-redpacket.json） */
    private static final Set<String> MUTED_NAMES = new HashSet<>();
    private static final Path MUTE_CONFIG_FILE = Path.of("config/carpet-pry-redpacket.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RedPacketManager() {}

    // ==================== 生命周期 ====================

    /** CarpetPrimaryuanServer.onGameStarted 调用（一次性） */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        loadMuteConfig();
        loadValueTable();
        // 广播/在线查询用的服务器实例（GUI 与领取路径都发生在 tick 之间）
        ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentServer = null);
        // 口令领取：聊天框打出正确口令
        ServerMessageEvents.CHAT_MESSAGE.register(RedPacketManager::onChatMessage);
        // 离线暂存退回：上线补发
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                deliverOfflineRefunds(handler.player));
        // 断线时把会话持有的物品进离线暂存（重进补发）——菜单不触发 removed，
        // 会话在下次发红包时才被覆盖，物品会凭空蒸发（生产实证）。
        // 覆盖两个阶段：物品投放中（物品在容器 0-44）与口令铁砧中（物品锁定在
        // session.payload，容器已收集清空）
        ServerPlayConnectionEvents.DISCONNECT.register((handler, sender) -> {
            java.util.UUID uuid = handler.player.getUUID();
            GuiSession session = SESSIONS.remove(uuid);
            if (session != null) {
                List<ItemStack> refund = new ArrayList<>();
                if (session.itemInputOpen) {
                    refund.addAll(session.drainContainer(45));
                }
                if (session.payload != null && !session.payload.isEmpty()) {
                    refund.addAll(session.payload);
                    session.payload = null;
                }
                if (!refund.isEmpty()) {
                    OFFLINE_REFUNDS.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(refund);
                }
            }
            LAST_SEND.remove(uuid);
            LAST_CLAIM.remove(uuid);
            LAST_HINT.remove(uuid);
        });
        // 停服：开放中的 GUI 会话原样退回；未过期红包的未领份额退回发送者
        //（消除"重启窗口内未领完红包物品消失"的损失；退不掉的打日志）。
        // 退款必须用本监听器入参 server：currentServer 先注册的监听器已置空（Fabric 按
        // 注册顺序回调），依赖静态引用会让在线发送者被误判离线、退回暂存后又被清空
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (GuiSession session : new ArrayList<>(SESSIONS.values())) {
                session.returnContainerItems();
            }
            for (RedPacket packet : PACKETS.values()) {
                if (!packet.expired && !packet.done && packet.sharesLeft() > 0) {
                    refundUnclaimed(server, packet, false);
                }
            }
            if (!OFFLINE_REFUNDS.isEmpty()) {
                LOGGER.warn("[RedPacket] {} offline refund entries could not be delivered (senders offline) and were lost on restart",
                        OFFLINE_REFUNDS.size());
            }
            SESSIONS.clear();
            PACKETS.clear();
            OFFLINE_REFUNDS.clear();
            LAST_SEND.clear();
            LAST_CLAIM.clear();
            LAST_HINT.clear();
            LAST_CONFIG.clear();
        });
        ServerTickScheduler.register(RedPacketManager::tick);
    }

    /** 过期检查 + 过期红包清理，每 20 tick 一次 */
    private static boolean tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) {
            long now = server.getTickCount();
            List<Integer> expired = new ArrayList<>();
            for (RedPacket packet : PACKETS.values()) {
                if (!packet.expired && !packet.warned && now >= packet.expireTick - EXPIRE_WARN_TICKS) {
                    packet.warned = true;
                    if (packet.sharesLeft() > 0) {
                        broadcastStatus(server, ServerI18n.tr(
                                "carpetprimaryuan.redpacket.msg.expiring", packet.senderName, packet.sharesLeft()));
                    }
                }
                // !done：提前领完的红包已结算过手气王，不再走过期分支——否则到期时
                // 会向全服误播"已过期"并二次结算；其到时清理由下方 done 条件承担
                if (!packet.expired && !packet.done && now >= packet.expireTick) {
                    packet.expired = true;
                    refundUnclaimed(server, packet, true);
                    broadcastStatus(server, ServerI18n.tr(
                            "carpetprimaryuan.redpacket.msg.expired_broadcast", packet.senderName));
                    settlePacket(server, packet);
                }
                if ((packet.expired || packet.done) && now >= packet.expireTick + EXPIRED_KEEP_TICKS) {
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
        /** 物品投放菜单打开中（断线/停服时仅此菜单的 0-44 玩家区需要退回） */
        boolean itemInputOpen;

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

        /** 取走容器 [0, slotLimit) 的物品（图标槽不参与退回——那是服务端注入的 UI 物品） */
        List<ItemStack> drainContainer(int slotLimit) {
            List<ItemStack> remaining = new ArrayList<>();
            if (container == null) {
                return remaining;
            }
            for (int slot = 0; slot < slotLimit && slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    remaining.add(stack);
                    container.forceSet(slot, ItemStack.EMPTY);
                }
            }
            return remaining;
        }

        /** 停服退回：物品投放中的容器余量 + 口令铁砧阶段锁定在 payload 的物品 */
        void returnContainerItems() {
            List<ItemStack> refund = new ArrayList<>();
            if (itemInputOpen) {
                refund.addAll(drainContainer(45));
            }
            if (payload != null && !payload.isEmpty()) {
                refund.addAll(payload);
                payload = null;
            }
            if (!refund.isEmpty()) {
                giveItems(player, refund);
            }
        }
    }

    // ==================== 入口：打开类型选择 ====================

    /**
     * 开新会话前清理旧会话：先摘除记录，再收取其持有物品（投放区余量 + 口令阶段锁定的
     * payload），然后关旧菜单。摘除必须先于关闭——旧菜单的关闭回调按会话身份判断是否
     * 退回，摘除后身份不再匹配，退款全部走这里单一路径，不会双发也不会漏发。
     */
    private static void abandonSession(ServerPlayer player) {
        GuiSession old = SESSIONS.remove(player.getUUID());
        if (old == null) {
            return;
        }
        List<ItemStack> refund = old.itemInputOpen ? old.drainContainer(45) : new ArrayList<>();
        if (old.payload != null && !old.payload.isEmpty()) {
            refund.addAll(old.payload);
            old.payload = null;
        }
        player.closeContainer();
        if (!refund.isEmpty()) {
            giveItems(player, refund);
        }
    }

    /** 命令入口：校验通过后打开类型选择 GUI */
    public static void openTypeMenu(ServerPlayer player, int count, String message) {
        abandonSession(player);
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

    /**
     * 专属红包命令直达（/redpacket &lt;份数&gt; @玩家 &lt;祝福语&gt;）：跳过类型选择与
     * 头像页，直接开物品投放——大服在线玩家超过头像页 54 格时命令是唯一入口。
     */
    public static void openTypeMenuTargeted(ServerPlayer player, int count, String message, ServerPlayer target) {
        abandonSession(player);
        GuiSession session = new GuiSession(player, RedPacket.Type.TARGETED, count, message);
        session.targetId = target.getUUID();
        session.targetName = target.getName().getString();
        SESSIONS.put(player.getUUID(), session);
        openItemInput(session);
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
            session.itemInputOpen = false;
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
        session.itemInputOpen = true;
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
        // 一次性门：口令提交过即拒绝重入（铁砧成品被重复点取时防止二次发放）
        if (!session.awaitingPassword) {
            return;
        }
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
                // closeContainer 同 tick 内玩家可能已断线（会话被 DISCONNECT 清除）：
                // 空判防空引用——调度器虽已逐任务隔离异常，任务本身仍应正常终止
                GuiSession open = SESSIONS.get(player.getUUID());
                if (open != null && open == session) {
                    openItemInput(open);
                }
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
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.cooldown",
                    (SEND_COOLDOWN_TICKS - (now - last) + 19) / 20));
            SESSIONS.remove(player.getUUID());
            closeNextTick(player);
            return;
        }
        int active = 0;
        for (RedPacket packet : PACKETS.values()) {
            if (!packet.expired && !packet.done && packet.senderId.equals(player.getUUID())) {
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
        // 稀疏提示：物品总量少于份数时部分份额为空（切分器语义），只提示不阻断
        if (session.type != RedPacket.Type.TARGETED) {
            int totalItems = 0;
            for (ItemStack stack : payload) {
                totalItems += stack.getCount();
            }
            if (totalItems < shares) {
                player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.sparse_warning"));
            }
        }
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
        LAST_CONFIG.put(player.getUUID(), new LastSend(packet.type, session.count,
                packet.message, packet.target, packet.targetName));
        SESSIONS.remove(player.getUUID());
        playDing(player, 1.2f);
        closeNextTick(player);
        broadcast(server, packet);
    }

    /** /redpacket again：用上次成功发放的类型/份数/祝福语重新打开投放 GUI */
    public static boolean reopenLast(ServerPlayer player) {
        LastSend last = LAST_CONFIG.get(player.getUUID());
        if (last == null) {
            return false;
        }
        abandonSession(player);
        GuiSession session = new GuiSession(player, last.type(), last.count(), last.message());
        if (last.targetId() != null) {
            session.targetId = last.targetId();
            session.targetName = last.targetName();
        }
        SESSIONS.put(player.getUUID(), session);
        openItemInput(session);
        return true;
    }

    /** /redpacket list：本人进行中的红包（id/类型/祝福语/剩余），供聊天输出 */
    public static java.util.List<Component> listOngoing(ServerPlayer player) {
        java.util.List<Component> lines = new ArrayList<>();
        for (RedPacket packet : PACKETS.values()) {
            if (!packet.senderId.equals(player.getUUID()) || packet.expired || packet.done) {
                continue;
            }
            String typeName = ServerI18n.tr("carpetprimaryuan.redpacket.type."
                    + packet.type.name().toLowerCase()).getString();
            lines.add(ServerI18n.tr("carpetprimaryuan.redpacket.msg.list_line",
                    packet.id, typeName, packet.message, packet.sharesLeft(), packet.shares.size()));
        }
        return lines;
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
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.claim"))
                .append("\n")
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.hover.rules"));
        MutableComponent clickable = me.primaryuan.carpet.util.ColorText.build(
                ServerI18n.tr("carpetprimaryuan.redpacket.clickable", packet.message).getString());
        clickable.setStyle(RedPacketGui.claimStyle(command, hover));
        MutableComponent line = Component.literal(packet.senderName)
                .append(ServerI18n.tr("carpetprimaryuan.redpacket.broadcast", typeName))
                .append(clickable);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (MUTED_NAMES.contains(p.getName().getString())) {
                continue;
            }
            if (packet.type == RedPacket.Type.TARGETED && packet.target != null
                    && p.getUUID().equals(packet.target)) {
                // 专属目标：金色"给你发的"+ 提示音，一眼知道是自己的
                MutableComponent exclusive = Component.literal(packet.senderName)
                        .append(ServerI18n.tr("carpetprimaryuan.redpacket.broadcast_exclusive"))
                        .append(clickable);
                p.sendSystemMessage(exclusive, false);
                playDing(p, 1.2f);
            } else {
                p.sendSystemMessage(line, false);
            }
        }
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
        if (packet.done || packet.sharesLeft() <= 0) {
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
            LAST_HINT.put(player.getUUID(), new long[]{now, packet.id});
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
        RedPacket matchedUnavailable = null;
        boolean matchedOwn = false;
        for (RedPacket packet : PACKETS.values()) {
            if (packet.type != RedPacket.Type.PASSWORD
                    || packet.password == null
                    || !packet.password.equals(text)) {
                continue;
            }
            if (packet.expired || packet.done || packet.sharesLeft() <= 0) {
                matchedUnavailable = packet;
                continue;
            }
            if (packet.senderId.equals(sender.getUUID())) {
                matchedOwn = true;
                continue;
            }
            if (packet.claimed.contains(sender.getUUID())) {
                matchedUnavailable = packet;
                continue;
            }
            matched = packet;
            break;
        }
        if (matched != null) {
            finishClaim(sender, matched);
            return;
        }
        if (matchedOwn) {
            sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.own"));
            return;
        }
        if (matchedUnavailable != null) {
            sender.sendSystemMessage(ServerI18n.tr(
                    matchedUnavailable.expired ? "carpetprimaryuan.redpacket.msg.expired"
                            : "carpetprimaryuan.redpacket.msg.done",
                    matchedUnavailable.senderName));
            return;
        }
        // 口令错误提示：仅在玩家近期点过某个口令红包链接（明确在试口令）时给出，
        // 避免普通聊天被误判刷屏
        long[] hint = LAST_HINT.get(sender.getUUID());
        if (hint != null) {
            long now = sender.level().getServer().getTickCount();
            RedPacket hinted = PACKETS.get((int) hint[1]);
            if (hinted != null && now - hint[0] < HINT_WINDOW_TICKS) {
                sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.password_wrong",
                        hinted.password == null ? 0 : hinted.password.length()));
            } else {
                LAST_HINT.remove(sender.getUUID());
            }
        }
    }

    /** 校验全通过的最终领取：扣一份、物品进包、消息（含份序）、发送者回执、领完即结束 */
    private static void finishClaim(ServerPlayer player, RedPacket packet) {
        List<ItemStack> share = packet.takeShare(player.getUUID());
        giveItems(player, share);
        playDing(player, 1.6f);
        int total = packet.shares.size();
        if (total > 1) {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.claim_success_indexed",
                    packet.senderName, packet.claimed.size(), total, itemsDescription(share)));
        } else {
            player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.redpacket.msg.claim_success",
                    packet.senderName, itemsDescription(share)));
        }
        // 领取全服广播：谁抢到了谁的红包（含物品内容）——社交展示，退订者不收；
        // 多份带份序，专属单份无份序。取代原"发送者私有回执"
        broadcastClaim(currentServer, total > 1
                ? ServerI18n.tr("carpetprimaryuan.redpacket.msg.claim_broadcast_indexed",
                        player.getName().getString(), packet.senderName,
                        packet.claimed.size(), total, itemsDescription(share))
                : ServerI18n.tr("carpetprimaryuan.redpacket.msg.claim_broadcast",
                        player.getName().getString(), packet.senderName, itemsDescription(share)));
        packet.claimerNames.put(player.getUUID(), player.getName().getString());
        if (packet.sharesLeft() <= 0) {
            // 领完：标记保留一段时间（后续点击仍能看到"已被领完"），到期由 tick 清理
            packet.done = true;
            broadcastStatus(currentServer, ServerI18n.tr(
                    "carpetprimaryuan.redpacket.msg.done_broadcast", packet.senderName));
            settlePacket(currentServer, packet);
        }
    }

    /**
     * 结算：领完/过期时触发——手气王全服广播。仅拼手气/口令两类随机切分、
     * 且红包只含**单种物品**时结算（混合物品的份额组合没有公平的"手气"可比性）：
     * 按稀有度价值最大者、并列取先领取。领取明细已改为逐份实时全服广播，不再汇总。
     */
    private static void settlePacket(MinecraftServer server, RedPacket packet) {
        if (packet.claimed.isEmpty() || server == null) {
            return;
        }
        if (packet.hasSingleItemType()
                && (packet.type == RedPacket.Type.LUCKY || packet.type == RedPacket.Type.PASSWORD)) {
            int kingIndex = packet.luckKingIndex(RedPacketManager::itemValue);
            if (kingIndex >= 0) {
                UUID kingId = packet.claimerAt(kingIndex);
                String kingName = packet.claimerNames.get(kingId);
                if (kingName != null) {
                    broadcastStatus(server, ServerI18n.tr(
                            "carpetprimaryuan.redpacket.msg.luck_king",
                            kingName, itemsDescription(packet.shares.get(kingIndex))));
                }
            }
        }
    }

    /** 领取消息全服广播（社交展示，色码随 lang 键），退订者不收 */
    private static void broadcastClaim(MinecraftServer server, net.minecraft.network.chat.Component message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!MUTED_NAMES.contains(p.getName().getString())) {
                p.sendSystemMessage(message, false);
            }
        }
    }

    /** 全服灰色状态广播（领完/过期），斜体弱化；退订者不收 */
    private static void broadcastStatus(MinecraftServer server, net.minecraft.network.chat.Component message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!MUTED_NAMES.contains(p.getName().getString())) {
                p.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
        }
    }

    /** 本人当前是否已退订红包广播 */
    public static boolean isMuted(ServerPlayer player) {
        return MUTED_NAMES.contains(player.getName().getString());
    }

    /** /redpacket mute|unmute：切换本人红包广播退订状态并持久化，返回是否实际变更 */
    public static boolean toggleMute(ServerPlayer player, boolean mute) {
        String name = player.getName().getString();
        boolean changed = mute ? MUTED_NAMES.add(name) : MUTED_NAMES.remove(name);
        if (changed) {
            saveMuteConfig();
        }
        return changed;
    }

    // ===== 退订名单持久化（模式同 PvpManager：UTF-8 + 临时文件原子替换） =====

    private static void loadMuteConfig() {
        if (!Files.exists(MUTE_CONFIG_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(MUTE_CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return;
            }
            MUTED_NAMES.clear();
            JsonElement mutedElem = json.get("muted");
            if (mutedElem != null && mutedElem.isJsonArray()) {
                mutedElem.getAsJsonArray().forEach(e -> {
                    if (e.isJsonPrimitive()) {
                        MUTED_NAMES.add(e.getAsString());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("[RedPacket] Failed to load mute config file", e);
        }
    }

    /**
     * 价值表加载：config/carpet-pry-values.json（item id → 单件价值，纯 map）。
     * 本模组只读不回写——缺失时用内置默认，管理员手改后重启生效。
     */
    private static void loadValueTable() {
        Path file = Path.of("config/carpet-pry-values.json");
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return;
            }
            int loaded = 0;
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    long value = entry.getValue().getAsLong();
                    if (value > 0) {
                        ITEM_VALUES.put(entry.getKey(), value);
                        loaded++;
                    }
                }
            }
            LOGGER.info("[RedPacket] Loaded {} item values from config/carpet-pry-values.json", loaded);
        } catch (Exception e) {
            LOGGER.error("[RedPacket] Failed to load value table, using built-in defaults", e);
        }
    }

    /**
     * 单件物品价值（稀有度档 × {@link #TIER_STRIDE} + 同档价值表 × 数量）：
     * 档位由代码内置保证跨档支配（1 颗钻石 > 任意数量泥土），价值表只调整
     * 同档内的排序粒度（默认值下同档总和远小于 TIER_STRIDE）。包级可见供单测。
     */
    static long itemValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int tier = ITEM_RARITY.getOrDefault(id, 0);
        return tier * TIER_STRIDE + ITEM_VALUES.getOrDefault(id, 1L) * stack.getCount();
    }

    private static void saveMuteConfig() {
        JsonObject config = new JsonObject();
        JsonArray mutedArr = new JsonArray();
        for (String name : MUTED_NAMES) {
            mutedArr.add(name);
        }
        config.add("muted", mutedArr);
        try {
            Path tmp = MUTE_CONFIG_FILE.resolveSibling(MUTE_CONFIG_FILE.getFileName() + ".tmp");
            Files.createDirectories(MUTE_CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, MUTE_CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("[RedPacket] Failed to save mute config file", e);
        }
    }

    /** 提示音（经验球音，与摸摸头已验证可听配方同源） */
    private static void playDing(ServerPlayer player, float pitch) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP),
                net.minecraft.sounds.SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                0.8F, pitch, player.getRandom().nextLong()));
    }

    // ==================== 退回 ====================

    /** 未领份额退回：发送者在线直接进包（放不下掉脚下），离线进暂存（上线补发） */
    private static void refundUnclaimed(MinecraftServer server, RedPacket packet, boolean notify) {
        List<ItemStack> items = packet.unclaimedItems();
        if (items.isEmpty()) {
            return;
        }
        ServerPlayer sender = findOnlinePlayer(server, packet.senderId);
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
        return findOnlinePlayer(currentServer, id);
    }

    private static ServerPlayer findOnlinePlayer(MinecraftServer server, UUID id) {
        if (server == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(id)) {
                return player;
            }
        }
        return null;
    }
}
