package me.primaryuan.carpet.handler.playerHead;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.mixins.rule.playerHead.AnvilMenuAccessor;
import me.primaryuan.carpet.mixins.rule.playerHead.ItemCombinerMenuAccessor;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用头颅的铁砧换肤会话。
 *
 * <p>只在铁砧发生有效改名时查询皮肤；查询期间清空结果槽并拒绝领取，成功后
 * 只给结果物品写入 PROFILE，输入物品与原版经验消耗路径保持不变。会话绑定
 * 服务器、玩家、菜单、名称与输入快照，改名/换输入/关菜单/断线/死亡/跨维度/
 * 规则关闭/停服都会作废旧请求。</p>
 */
public final class HeadAnvilHandler {
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final int DEBOUNCE_TICKS = 6;
    private static final int TIMEOUT_TICKS = 100;

    private static final class Session {
        final MinecraftServer server;
        final ServerPlayer player;
        final UUID playerId;
        final AnvilMenu menu;
        final HeadRenameSession core = new HeadRenameSession();
        ItemStack input = ItemStack.EMPTY;
        long dueTick;
        long queryStartTick;
        boolean failureNotified;

        Session(MinecraftServer server, ServerPlayer player, AnvilMenu menu) {
            this.server = server;
            this.player = player;
            this.playerId = player.getUUID();
            this.menu = menu;
        }

        void revise(String name, ItemStack input, long dueTick) {
            this.core.revise(name);
            this.input = input.copy();
            this.dueTick = dueTick;
            this.failureNotified = false;
        }

        boolean valid() {
            return !server.isStopped()
                    && !player.isRemoved()
                    && player.isAlive()
                    && !player.isChangingDimension()
                    && server.getPlayerList().getPlayer(playerId) == player
                    && player.containerMenu == menu
                    && ItemStack.matches(input, menu.getSlot(0).getItem());
        }
    }

    private static final Map<AnvilMenu, Session> SESSIONS = new IdentityHashMap<>();
    private static boolean initialized;

    private HeadAnvilHandler() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerTickScheduler.register(server -> {
            tick(server);
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    /** 停服/关规则：作废全部未完成请求，不改动已发放物品。 */
    public static void clear() {
        SESSIONS.clear();
        HeadSkinResolver.clear();
    }

    public static void onRuleChanged() {
        if (!CarpetPrimaryuanSettings.universalHead) {
            SESSIONS.clear();
            HeadSkinResolver.clear();
        }
    }

    /** 铁砧改名成功时建立防抖会话；未改名、空名称或非通用头颅不查询。 */
    public static void onNameChanged(AnvilMenu menu, boolean changed) {
        if (!changed || !CarpetPrimaryuanSettings.universalHead) return;
        Player player = ((ItemCombinerMenuAccessor) menu).pry$player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack input = menu.getSlot(0).getItem();
        if (!HeadItems.isUniversal(input)) return;
        String name = ((AnvilMenuAccessor) menu).pry$itemName();
        if (name == null || name.isBlank()) {
            SESSIONS.remove(menu);
            return;
        }

        MinecraftServer server = serverPlayer.level().getServer();
        Session session = SESSIONS.get(menu);
        if (session == null || !session.playerId.equals(serverPlayer.getUUID())) {
            session = new Session(server, serverPlayer, menu);
            SESSIONS.put(menu, session);
        }
        session.revise(name, input, server.getTickCount() + DEBOUNCE_TICKS);
        // 名字刚变化时立刻隐藏上一轮结果，防抖与查询期间都不可领取。
        clearResult(menu);

        // 名称就是头颅已有皮肤的来源时无需重新查询，直接复用快照。
        HeadData data = HeadData.read(input);
        if (data != null && data.hasTexture() && name.equalsIgnoreCase(data.sourceName())) {
            session.core.reuse(data.skin());
            refresh(menu);
        }
    }

    /** 原版生成结果后处理：未就绪清空结果槽，就绪则只替换结果物品的皮肤。 */
    public static void afterCreateResult(AnvilMenu menu) {
        if (!CarpetPrimaryuanSettings.universalHead) {
            SESSIONS.remove(menu);
            return;
        }
        Player player = ((ItemCombinerMenuAccessor) menu).pry$player();
        if (!(player instanceof ServerPlayer)) return;
        ItemStack input = menu.getSlot(0).getItem();
        if (!HeadItems.isUniversal(input)) {
            SESSIONS.remove(menu);
            return;
        }
        Session session = SESSIONS.get(menu);
        String name = ((AnvilMenuAccessor) menu).pry$itemName();
        if (session != null && (name == null || !name.equals(session.core.name())
                || !ItemStack.matches(session.input, input))) {
            SESSIONS.remove(menu);
            session = null;
        }
        if (session == null) return;
        if (!session.core.ready()) {
            clearResult(menu);
            return;
        }
        ItemStack result = menu.getSlot(2).getItem();
        if (result.isEmpty()) return;
        HeadItems.applySkin(result, session.core.skin());
        HeadData data = HeadData.read(result);
        if (data != null) {
            data.applySkin(session.core.skin());
            data.write(result);
            HeadItems.applyLore(result, data);
        }
    }

    /** 未就绪时拒绝一切领取路径（普通点击、Shift 点击、数字键、丢弃）。 */
    public static boolean blocksPickup(AnvilMenu menu) {
        Session session = SESSIONS.get(menu);
        return session != null && session.core.blocksPickup();
    }

    public static void onTake(AnvilMenu menu) {
        SESSIONS.remove(menu);
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        List<Session> due = new ArrayList<>();
        long now = server.getTickCount();
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.server != server || !session.valid()) {
                iterator.remove();
                continue;
            }
            HeadRenameSession.State state = session.core.state();
            if (state == HeadRenameSession.State.DEBOUNCING && now >= session.dueTick) {
                due.add(session);
            } else if (state == HeadRenameSession.State.QUERYING
                    && now - session.queryStartTick > TIMEOUT_TICKS) {
                if (session.core.fail(session.core.revision())) notifyFailure(session, null);
            }
        }
        for (Session session : due) {
            startQuery(session);
        }
    }

    private static void startQuery(Session session) {
        session.core.beginQuery();
        session.queryStartTick = session.server.getTickCount();
        int revision = session.core.revision();
        String name = session.core.name();
        HeadSkinResolver.resolve(session.server, name,
                skin -> {
                    if (SESSIONS.get(session.menu) != session) return;
                    if (session.core.complete(revision, skin)) refresh(session.menu);
                },
                problem -> {
                    if (SESSIONS.get(session.menu) != session) return;
                    if (session.core.fail(revision)) notifyFailure(session, problem);
                });
    }

    private static void notifyFailure(Session session, Throwable problem) {
        clearResult(session.menu);
        if (!session.failureNotified) {
            session.failureNotified = true;
            session.player.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.head.universal.lookup_failed", session.core.name()));
        }
        if (problem != null) {
            LOGGER.debug("[pry] 通用头颅皮肤查询失败: {}", session.core.name(), problem);
        }
    }

    private static void refresh(AnvilMenu menu) {
        try {
            menu.createResult();
        } catch (RuntimeException problem) {
            LOGGER.error("[pry] 通用头颅结果刷新失败", problem);
        }
        menu.broadcastChanges();
    }

    private static void clearResult(AnvilMenu menu) {
        menu.getSlot(2).set(ItemStack.EMPTY);
        ((AnvilMenuAccessor) menu).pry$cost().set(0);
    }
}
