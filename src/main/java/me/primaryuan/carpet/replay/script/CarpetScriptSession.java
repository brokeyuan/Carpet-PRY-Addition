package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.ReplayData;
import net.minecraft.server.MinecraftServer;

/**
 * Carpet 假人上的脚本会话：组合通用状态机（SessionCore）与 Carpet 驱动（CarpetFakeDriver）。
 * 绑定实体实例：实体 id + 实体引用校验（非 UUID 索引，兼容未来同 UUID 多实例）。
 * ownsFake：假人由本会话自动召唤时为 true，结束后由调度器统一下线；外部绑定的假人只解除控制。
 */
public final class CarpetScriptSession
{
    public enum State { RUNNING, PAUSED, FINISHED, STOPPED, INVALID }

    public enum RunMode { ONCE, REPEAT, LOOP }

    private final String scriptName;
    private final String fakeName;
    private final int fakeEntityId;
    private final boolean ownsFake;
    private final FakeDriver driver;
    private final java.util.UUID ownerUuid;
    private final me.primaryuan.carpet.replay.script.core.SessionCore core;

    public CarpetScriptSession(String scriptName, String fakeName, int fakeEntityId,
                               ReplayData replay, FakeDriver driver, RunMode mode, int repeatCount,
                               boolean ownsFake, boolean roundReset)
    {
        this(scriptName, fakeName, fakeEntityId, replay, driver, mode, repeatCount, ownsFake,
                roundReset, null);
    }

    public CarpetScriptSession(String scriptName, String fakeName, int fakeEntityId,
                               ReplayData replay, FakeDriver driver, RunMode mode, int repeatCount,
                               boolean ownsFake, boolean roundReset, java.util.UUID ownerUuid)
    {
        this.scriptName = scriptName;
        this.fakeName = fakeName;
        this.fakeEntityId = fakeEntityId;
        this.ownsFake = ownsFake;
        this.driver = driver;
        this.ownerUuid = ownerUuid;
        int maxLoops = switch (mode)
        {
            case ONCE -> 1;
            case REPEAT -> Math.max(1, repeatCount);
            case LOOP -> -1;
        };
        this.core = new me.primaryuan.carpet.replay.script.core.SessionCore(
                replay, driver, maxLoops, roundReset);
    }

    public String scriptName()
    {
        return scriptName;
    }

    public String fakeName()
    {
        return fakeName;
    }

    public int fakeEntityId()
    {
        return fakeEntityId;
    }

    /** 假人是否由本脚本自动召唤（结束时需要下线） */
    public boolean ownsFake()
    {
        return ownsFake;
    }

    /** 发起脚本的玩家（失效时定向反馈；外部绑定时为执行指令的玩家） */
    public java.util.UUID ownerUuid()
    {
        return ownerUuid;
    }

    /** 非正常结束原因（INVALID 时非空） */
    public String failureReason()
    {
        return core.failureReason();
    }

    /** 外部假人成功接管后清空原有动作并强制首帧完整应用 */
    public void prepareTakeover()
    {
        core.prepareTakeover();
    }

    public State state()
    {
        return switch (core.state())
        {
            case RUNNING -> State.RUNNING;
            case PAUSED -> State.PAUSED;
            case FINISHED -> State.FINISHED;
            case STOPPED -> State.STOPPED;
            case INVALID -> State.INVALID;
        };
    }

    public int playTick()
    {
        return core.playTick();
    }

    public int totalTicks()
    {
        return core.totalTicks();
    }

    public int loopsDone()
    {
        return core.loopsDone();
    }

    public int totalLoops()
    {
        return core.totalLoops();
    }

    public void onServerTick()
    {
        core.tick();
    }

    public void pause()
    {
        core.pause();
    }

    public void resume()
    {
        core.resume();
    }

    public void stop()
    {
        core.stop();
    }

    /** 下线假人（仅调度器在 ownsFake 会话上调用；经驱动端口便于测试与版本隔离） */
    public void takeOffline()
    {
        driver.takeOffline();
    }

    /** 供面板刷新：关联的服务器（启动后记录） */
    public void attachServer(MinecraftServer server)
    {
        // 保留扩展位：当前驱动直接持有假人引用
    }
}
