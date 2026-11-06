package me.primaryuan.carpet.replay;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.replay.phantom.PhantomEntityTracker;
import me.primaryuan.carpet.replay.recorder.ReplayRecorder;
import me.primaryuan.carpet.replay.script.ScriptScheduler;
import me.primaryuan.carpet.replay.storage.ReplayStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 回放功能的 tick 与生命周期锚点：
 * - 每 Tick 末：录制任务推进、幻影回放推进、脚本会话推进
 * - 正常停服：保存全部录制并等待 IO 完成；开服不恢复任务
 * - 开服：只把崩溃检查点转成已结束录像（标记非完整）
 * - 规则关闭：终止对应规则会话；录制为两规则共用，全部关闭时保存收尾
 */
public final class ReplayLifecycle
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static boolean registered;

    private ReplayLifecycle() {}

    public static synchronized void init()
    {
        if (registered)
        {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            boolean any = CarpetPrimaryuanSettings.playerReplay || CarpetPrimaryuanSettings.fakePlayerScript;
            if (!any)
            {
                return;
            }
            ReplayRecorder.tick(server);
            if (CarpetPrimaryuanSettings.playerReplay)
            {
                PhantomEntityTracker.tick(server);
            }
            if (CarpetPrimaryuanSettings.fakePlayerScript)
            {
                ScriptScheduler.tick(server);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
        {
            int recovered = ReplayStorage.recoverCheckpoints(server);
            if (recovered > 0)
            {
                LOGGER.warn("[replay] 已把 {} 份崩溃检查点恢复为已结束录像（非完整录制）", recovered);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
        {
            ReplayRecorder.shutdownAndSave(server);
            PhantomEntityTracker.shutdownAll();
            ScriptScheduler.shutdownAll();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PhantomEntityTracker.endForViewer(server, handler.getPlayer().getStringUUID()));
        LOGGER.info("[replay] 回放生命周期锚点已注册");
    }

    /** playerReplay 规则关闭：清理幻影；录制器两规则共用 */
    public static void onPlayerReplayDisabled()
    {
        PhantomEntityTracker.shutdownAll();
        maybeShutdownRecorder();
    }

    /** fakePlayerScript 规则关闭：清理脚本会话 */
    public static void onFakePlayerScriptDisabled()
    {
        ScriptScheduler.shutdownAll();
        maybeShutdownRecorder();
    }

    private static void maybeShutdownRecorder()
    {
        if (!CarpetPrimaryuanSettings.playerReplay && !CarpetPrimaryuanSettings.fakePlayerScript)
        {
            // 两条规则都关闭：保存已录制内容，不静默丢弃
            var server = me.primaryuan.carpet.CarpetPrimaryuanServer.currentServer();
            if (server != null)
            {
                ReplayRecorder.shutdownAndSave(server);
            }
        }
    }
}
