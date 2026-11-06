package me.primaryuan.carpet.replay.script;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.replay.common.ReplayData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 脚本调度器：假人实体 id → 运行会话；自动召唤（createFake 异步出假人后绑定）与统一清理。
 * 归属：仅会话自动召唤（ownsFake）的假人在结束/停止/异常/停服时下线；
 * 外部绑定（/player 手动召唤）只解除控制，绝不下线。
 */
public final class ScriptScheduler
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    /** 会话键：假人实体 id（非 UUID，预留同 UUID 多实例兼容） */
    private static final Map<Integer, CarpetScriptSession> ACTIVE = new ConcurrentHashMap<>();
    /** 等待假人上线的启动请求：botName → 请求 */
    private static final Map<String, PendingLaunch> PENDING = new ConcurrentHashMap<>();
    /** 假人上线等待上限（tick） */
    private static final long LAUNCH_TIMEOUT_TICKS = 200;

    private ScriptScheduler() {}

    /** 待启动请求 */
    private static final class PendingLaunch
    {
        final String scriptName;
        final ReplayData replay;
        final ScriptSessionSpec spec;
        final MinecraftServer server;
        final Consumer<CarpetScriptSession> onBound;
        final Runnable onFail;
        final java.util.function.BooleanSupplier stillAllowed;
        long deadlineTick;

        PendingLaunch(String scriptName, ReplayData replay, ScriptSessionSpec spec,
                      MinecraftServer server, Consumer<CarpetScriptSession> onBound,
                      Runnable onFail, java.util.function.BooleanSupplier stillAllowed, long deadlineTick)
        {
            this.scriptName = scriptName;
            this.replay = replay;
            this.spec = spec;
            this.server = server;
            this.onBound = onBound;
            this.onFail = onFail;
            this.stillAllowed = stillAllowed;
            this.deadlineTick = deadlineTick;
        }
    }

    /** 启动参数（命令面板收集） */
    public record ScriptSessionSpec(String botName, CarpetScriptSession.RunMode mode,
                                    int repeatCount, boolean roundReset, boolean ownsFake,
                                    String startDimKey, double startX, double startY, double startZ,
                                    float startYaw, net.minecraft.world.level.GameType gameType,
                                    java.util.UUID ownerUuid)
    {
        public ScriptSessionSpec(String botName, CarpetScriptSession.RunMode mode, int repeatCount,
                                 boolean roundReset, boolean ownsFake, String startDimKey,
                                 double startX, double startY, double startZ, float startYaw,
                                 net.minecraft.world.level.GameType gameType)
        {
            this(botName, mode, repeatCount, roundReset, ownsFake, startDimKey,
                    startX, startY, startZ, startYaw, gameType, null);
        }
    }

    /**
     * 自动召唤假人并启动脚本。
     * createFake 内部异步拉取 profile，假人上线后由 tick 完成绑定；等待超时判定失败。
     *
     * @return false = 名字被占用、维度无效或请求未接受（不接管不踢人）
     */
    public static boolean requestLaunch(MinecraftServer server, String scriptName, ReplayData replay,
                                        ScriptSessionSpec spec,
                                        Consumer<CarpetScriptSession> onBound, Runnable onFail)
    {
        return requestLaunch(server, scriptName, replay, spec, onBound, onFail, () -> true);
    }

    public static boolean requestLaunch(MinecraftServer server, String scriptName, ReplayData replay,
                                        ScriptSessionSpec spec, Consumer<CarpetScriptSession> onBound,
                                        Runnable onFail, java.util.function.BooleanSupplier stillAllowed)
    {
        if (spec.ownsFake() && PENDING.containsKey(spec.botName()))
        {
            return false;
        }
        if (PENDING.size() >= me.primaryuan.carpet.replay.common.ReplayLimits.MAX_PENDING_SCRIPTS
                || ACTIVE.size() >= me.primaryuan.carpet.replay.common.ReplayLimits.MAX_ACTIVE_SCRIPTS)
        {
            return false;
        }
        boolean accepted;
        try
        {
            accepted = EntityPlayerMPFake.createFake(spec.botName(), server,
                    new net.minecraft.world.phys.Vec3(spec.startX(), spec.startY(), spec.startZ()),
                    spec.startYaw(), 0f,
                    dimKeyOf(spec.startDimKey()), spec.gameType(), false);
        }
        catch (RuntimeException e)
        {
            // 录制维度无法解析/不存在：明确失败，不留下半启动请求
            LOGGER.error("[replay] 假人召唤参数无效（维度 {}）", spec.startDimKey(), e);
            return false;
        }
        if (!accepted)
        {
            return false;
        }
        PENDING.put(spec.botName(), new PendingLaunch(scriptName, replay, spec, server,
                onBound, onFail, stillAllowed, server.getTickCount() + LAUNCH_TIMEOUT_TICKS));
        return true;
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKeyOf(String dimKey)
    {
        //#if MC >= 12111
        return net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.parse(dimKey));
        //#else
        //$$ return net.minecraft.resources.ResourceKey.create(
        //$$         net.minecraft.core.registries.Registries.DIMENSION,
        //$$         net.minecraft.resources.ResourceLocation.parse(dimKey));
        //#endif
    }

    /** 绑定外部已在线假人（兼容入口：run <名> as <假人>，ownsFake=false） */
    public static CarpetScriptSession bindExternal(ServerPlayer fake, String scriptName,
                                                   ReplayData replay, CarpetScriptSession.RunMode mode, int count)
    {
        return bindExternal(fake, scriptName, replay, mode, count, null);
    }

    public static synchronized CarpetScriptSession bindExternal(ServerPlayer fake, String scriptName,
                                                                ReplayData replay, CarpetScriptSession.RunMode mode,
                                                                int count, UUID ownerUuid)
    {
        if (!(fake instanceof EntityPlayerMPFake) || ACTIVE.containsKey(fake.getId())
                || ACTIVE.size() >= me.primaryuan.carpet.replay.common.ReplayLimits.MAX_ACTIVE_SCRIPTS)
        {
            return null;
        }
        CarpetFakeDriver driver = CarpetFakeDriver.of(fake, UUID.randomUUID().toString());
        if (driver == null)
        {
            return null;
        }
        String fakeName;
        //#if MC >= 12110
        fakeName = fake.getGameProfile().name();
        //#else
        //$$ fakeName = fake.getGameProfile().getName();
        //#endif
        CarpetScriptSession session = new CarpetScriptSession(scriptName, fakeName,
                fake.getId(), replay, driver, mode, count, false, false, ownerUuid);
        CarpetScriptSession bound = bindSession(session);
        if (bound != null)
        {
            try
            {
                // 取得独占所有权之后才清空原有 ActionPack，并强制首帧完整应用
                bound.prepareTakeover();
            }
            catch (Throwable e)
            {
                ACTIVE.remove(bound.fakeEntityId());
                LOGGER.error("[replay] 接管外部假人失败: {}", fakeName, e);
                return null;
            }
        }
        return bound;
    }

    /** 登记会话（占用检查 + 入队）。包内可见：单测直接构造会话验证调度清理逻辑 */
    static synchronized CarpetScriptSession bindSession(CarpetScriptSession session)
    {
        if (ACTIVE.containsKey(session.fakeEntityId())
                || ACTIVE.size() >= me.primaryuan.carpet.replay.common.ReplayLimits.MAX_ACTIVE_SCRIPTS)
        {
            return null;
        }
        ACTIVE.put(session.fakeEntityId(), session);
        return session;
    }

    public static CarpetScriptSession get(int fakeEntityId)
    {
        return ACTIVE.get(fakeEntityId);
    }

    /** 假人是否已被脚本控制 */
    public static boolean isBound(int fakeEntityId)
    {
        return ACTIVE.containsKey(fakeEntityId);
    }

    /** 假人名是否已被运行中/待启动脚本占用 */
    public static boolean isNameTaken(String botName)
    {
        if (PENDING.containsKey(botName))
        {
            return true;
        }
        for (CarpetScriptSession s : ACTIVE.values())
        {
            if (s.fakeName().equals(botName))
            {
                return true;
            }
        }
        return false;
    }

    /** 自动生成的可用假人名（ReplayBot、ReplayBot2…） */
    public static String nextFreeBotName()
    {
        return nextFreeBotName(name -> false);
    }

    /** 自动名同时避开脚本占用与调用方额外判定（如在线玩家） */
    public static String nextFreeBotName(java.util.function.Predicate<String> alsoTaken)
    {
        for (int i = 1; i < 100; i++)
        {
            String n = i == 1 ? "ReplayBot" : "ReplayBot" + i;
            if (!isNameTaken(n) && !alsoTaken.test(n))
            {
                return n;
            }
        }
        return "ReplayBot" + UUID.randomUUID().toString().substring(0, 4);
    }

    /** 停止并按归属下线 */
    public static synchronized void stopAndCleanup(int fakeEntityId, boolean takeOffline)
    {
        CarpetScriptSession s = ACTIVE.remove(fakeEntityId);
        if (s == null)
        {
            return;
        }
        try
        {
            s.stop();
        }
        finally
        {
            if (takeOffline && s.ownsFake())
            {
                safeOffline(s);
            }
        }
    }

    /** 每 Tick：绑定待启动请求 → 推进运行会话 → 出队清理 */
    public static void tick(MinecraftServer server)
    {
        // 待启动绑定
        Iterator<Map.Entry<String, PendingLaunch>> pit = PENDING.entrySet().iterator();
        while (pit.hasNext())
        {
            PendingLaunch pl = pit.next().getValue();
            ServerPlayer fake = server.getPlayerList().getPlayerByName(pl.spec.botName());
            if (fake instanceof EntityPlayerMPFake)
            {
                pit.remove();
                bindAndRun(pl, fake);
            }
            else if (server.getTickCount() > pl.deadlineTick)
            {
                //#if MC >= 12104
                if (EntityPlayerMPFake.isSpawningPlayer(pl.spec.botName()))
                {
                    // Carpet 仍在异步拉取 profile：延长等待，避免假人迟到上线后无人接管
                    pl.deadlineTick = server.getTickCount() + LAUNCH_TIMEOUT_TICKS;
                }
                else
                {
                    pit.remove();
                    LOGGER.error("[replay] 假人上线超时: {}", pl.spec.botName());
                    if (pl.onFail != null)
                    {
                        pl.onFail.run();
                    }
                }
                //#else
                //$$ pit.remove();
                //$$ LOGGER.error("[replay] 假人上线超时: {}", pl.spec.botName());
                //$$ if (pl.onFail != null)
                //$$ {
                //$$     pl.onFail.run();
                //$$ }
                //#endif
            }
        }
        // 运行会话
        Iterator<Map.Entry<Integer, CarpetScriptSession>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext())
        {
            CarpetScriptSession session = it.next().getValue();
            try
            {
                session.onServerTick();
            }
            catch (Throwable e)
            {
                // Error 同样接住：会话终止并按归属清理，不炸服务器
                LOGGER.error("[replay] 脚本会话异常，终止并下线: {}", session.fakeName(), e);
                it.remove();
                safeStop(session);
                safeOffline(session);
                continue;
            }
            CarpetScriptSession.State state = session.state();
            if (state != CarpetScriptSession.State.RUNNING
                    && state != CarpetScriptSession.State.PAUSED)
            {
                it.remove();
                if (state == CarpetScriptSession.State.INVALID)
                {
                    notifyOwner(server, session, session.failureReason());
                }
                // FINISHED/STOPPED/INVALID：ownsFake 会话自动下线
                safeOffline(session);
            }
        }
    }

    private static void safeStop(CarpetScriptSession session)
    {
        try
        {
            session.stop();
        }
        catch (Throwable e)
        {
            LOGGER.warn("[replay] 停止会话失败: {}", session.fakeName(), e);
        }
    }

    private static void safeOffline(CarpetScriptSession session)
    {
        if (!session.ownsFake())
        {
            return;
        }
        try
        {
            session.takeOffline();
        }
        catch (Throwable e)
        {
            LOGGER.warn("[replay] 下线假人失败: {}", session.fakeName(), e);
        }
    }

    private static void notifyOwner(MinecraftServer server, CarpetScriptSession session, String reason)
    {
        if (session.ownerUuid() == null)
        {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerUuid());
        if (owner != null)
        {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "脚本已中断：" + (reason == null ? "假人失效" : reason)));
        }
    }

    private static void bindAndRun(PendingLaunch pl, ServerPlayer fake)
    {
        if (pl.stillAllowed != null && !pl.stillAllowed.getAsBoolean())
        {
            LOGGER.warn("[replay] 脚本启动回调权限已失效，取消绑定: {}", pl.spec.botName());
            if (fake instanceof carpet.patches.EntityPlayerMPFake fakePlayer)
            {
                fakePlayer.kill(net.minecraft.network.chat.Component.literal("脚本启动已取消"));
            }
            if (pl.onFail != null)
            {
                pl.onFail.run();
            }
            return;
        }
        CarpetFakeDriver driver = CarpetFakeDriver.of(fake, UUID.randomUUID().toString(),
                pl.spec.startDimKey(), pl.spec.startX(), pl.spec.startY(), pl.spec.startZ(), pl.spec.startYaw());
        if (driver == null)
        {
            if (pl.onFail != null)
            {
                pl.onFail.run();
            }
            return;
        }
        CarpetScriptSession session = new CarpetScriptSession(pl.scriptName, pl.spec.botName(),
                fake.getId(), pl.replay, driver, pl.spec.mode(), pl.spec.repeatCount(),
                pl.spec.ownsFake(), pl.spec.roundReset(), pl.spec.ownerUuid());
        if (ACTIVE.putIfAbsent(session.fakeEntityId(), session) != null)
        {
            if (pl.onFail != null)
            {
                pl.onFail.run();
            }
            return;
        }
        if (pl.onBound != null)
        {
            pl.onBound.accept(session);
        }
    }

    /** 规则关闭/服务器停止：全部会话停止，ownsFake 假人下线 */
    public static synchronized void shutdownAll()
    {
        try
        {
            for (CarpetScriptSession s : ACTIVE.values())
            {
                safeStop(s);
                safeOffline(s);
            }
        }
        finally
        {
            ACTIVE.clear();
            PENDING.clear();
        }
    }

    public static int activeCount()
    {
        return ACTIVE.size();
    }

    /** 运行中会话快照（面板用） */
    public static java.util.List<CarpetScriptSession> listActive()
    {
        return java.util.List.copyOf(ACTIVE.values());
    }
}
