package me.primaryuan.carpet.replay.script.core;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputTrack;
import me.primaryuan.carpet.replay.common.PlaybackClock;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.script.AttackPlaybackController;
import me.primaryuan.carpet.replay.script.FakeDriver;
import me.primaryuan.carpet.replay.script.InputStateController;
import me.primaryuan.carpet.replay.script.ItemUseController;

import java.util.List;

/**
 * 脚本状态机核心（纯逻辑，经 FakeDriver 驱动，可脱离 MC 单测）。
 * 半开区间 [0, durationTicks)；循环只重置播放状态；时间轴不因结果偏离而改变。
 *
 * 提交与消费时序（Carpet 实证：start 只登记动作，实体 onUpdate 才执行）：
 *   END N 提交帧 t → ENTITY N+1 消费 → END N+1 结算帧 t 并提交下一帧
 * 因此末帧/轮末帧提交后保留一个结算 Tick，不允许同次 stopAll 或下线。
 */
public final class SessionCore
{
    public enum State { RUNNING, PAUSED, FINISHED, STOPPED, INVALID }

    private final PlaybackClock clock;
    private final FakeDriver driver;
    private final ReplayData replay;
    private final InputStateController input = new InputStateController();
    private final AttackPlaybackController attack = new AttackPlaybackController();
    private final ItemUseController itemUse = new ItemUseController();
    private State state = State.RUNNING;
    /** "每轮回到起点"：轮间由 driver.onNewRound 做明确的位置重置；轮内永不纠偏 */
    private final boolean roundReset;
    private String failureReason;
    /** 事件游标：按 tick 单调前进，换轮归零，暂停恢复不回退 */
    private int eventCursor;
    /** 上一 Tick 提交的帧等待实体 Tick 消费 */
    private boolean settlePending;
    /** 结算后需要先处理轮间重置再提交下一轮首帧 */
    private boolean awaitingNewRound;
    /** 暂停恢复后重建持续输入 */
    private boolean rebuildPending;
    /** 外部假人接管：首帧无条件完整落地（含全零） */
    private boolean forceFirstApply;
    /** 初始选中槽基线只应用一次 */
    private boolean initialSlotDone;

    public SessionCore(ReplayData replay, FakeDriver driver, int maxLoops, boolean roundReset)
    {
        this.replay = replay;
        this.driver = driver;
        this.roundReset = roundReset;
        this.clock = new PlaybackClock(replay.durationTicks(), maxLoops);
        inputTrack().ensureOrdered();
    }

    public SessionCore(ReplayData replay, FakeDriver driver, int maxLoops)
    {
        this(replay, driver, maxLoops, false);
    }

    public State state()
    {
        return state;
    }

    public String failureReason()
    {
        return failureReason;
    }

    public int playTick()
    {
        return clock.tick;
    }

    public int totalTicks()
    {
        return replay.durationTicks();
    }

    public int loopsDone()
    {
        return clock.loopsDone;
    }

    public int totalLoops()
    {
        return clock.maxLoops;
    }

    /**
     * 外部假人成功接管后调用：清掉原有 ActionPack，并让首帧无条件完整应用。
     * 必须在取得独占所有权之后调用，绑定失败不得先清动作。
     */
    public void prepareTakeover()
    {
        driver.releaseAll();
        input.reset();
        forceFirstApply = true;
    }

    public void tick()
    {
        if (state == State.STOPPED || state == State.FINISHED || state == State.INVALID)
        {
            return;
        }
        // 暂停状态也要检查死亡/断线：失效立即回收，不占名字
        if (!driver.isValid())
        {
            fail("假人已失效（死亡/断线/卸载）");
            return;
        }
        if (state == State.PAUSED)
        {
            return;
        }

        if (settlePending)
        {
            settlePending = false;
            if (clock.finished)
            {
                releaseControl();
                state = State.FINISHED;
                return;
            }
            if (awaitingNewRound)
            {
                awaitingNewRound = false;
                eventCursor = 0;
                // 上一轮尾帧已消费，新轮首帧尚未提交：此刻才回起点
                if (roundReset && !driver.onNewRound())
                {
                    fail("轮间传送失败（维度不存在或目标无效）");
                    return;
                }
            }
        }

        int t = clock.advance();
        if (t < 0)
        {
            return;
        }
        if (rebuildPending)
        {
            rebuildPending = false;
            attack.rebuild(driver);
            itemUse.rebuild(driver);
        }
        applyTick(t);
        if (clock.finished)
        {
            settlePending = true;
        }
        else if (clock.atRoundStart())
        {
            settlePending = true;
            awaitingNewRound = true;
        }
    }

    private void applyTick(int t)
    {
        InputTrack track = inputTrack();
        // 脚本在线片段边界：上一段末帧已在上一实体 Tick 消费（settlePending 已处理），
        // 此刻先释放旧持续动作，再无条件重建本段首帧基线。
        if (t > 0 && track.segmentStarts.contains(t))
        {
            releaseSegmentBoundary();
        }
        driver.look(track.yawAt(t), track.pitchAt(t));
        int mask = track.maskAt(t);
        if (forceFirstApply)
        {
            forceFirstApply = false;
            input.forceApply(driver, mask);
        }
        else
        {
            input.apply(driver, mask);
        }
        if (!initialSlotDone)
        {
            initialSlotDone = true;
            applyInitialSlot();
        }
        applyEvents(t);
    }

    /** 新录像首帧已带 SELECT_SLOT；旧录像退回视觉首帧槽位作为基线 */
    private void applyInitialSlot()
    {
        List<InputEvent> events = inputTrack().events;
        for (InputEvent e : events)
        {
            if (e.tick != 0)
            {
                break;
            }
            if (e.type == InputEventType.SELECT_SLOT)
            {
                return;
            }
        }
        int slot = firstVisualSlot();
        if (slot >= 0 && slot <= 8)
        {
            driver.setSlot(slot);
        }
    }

    private int firstVisualSlot()
    {
        var participant = replay.primaryParticipant();
        var frame = participant == null ? null : participant.firstFrame();
        return frame == null ? -1 : frame.slot;
    }

    private InputTrack inputTrack()
    {
        var participant = replay.primaryParticipant();
        if (participant == null || participant.segments.isEmpty())
        {
            return new InputTrack();
        }
        return participant.segments.get(0).input;
    }

    private void releaseSegmentBoundary()
    {
        attack.release(driver);
        itemUse.releaseAll(driver);
        input.reset();
        driver.releaseAll();
        forceFirstApply = true;
    }

    private void applyEvents(int t)
    {
        List<InputEvent> events = inputTrack().events;
        while (eventCursor < events.size())
        {
            InputEvent e = events.get(eventCursor);
            if (e.tick < t)
            {
                eventCursor++;
                continue;
            }
            if (e.tick > t)
            {
                break;
            }
            dispatch(e);
            eventCursor++;
        }
    }

    private void dispatch(InputEvent e)
    {
        switch (e.type)
        {
            case SELECT_SLOT -> driver.setSlot(e.a);
            case SWING -> attack.handle(driver, InputEventType.SWING, e.hand);
            case ATTACK_ENTITY -> attack.handle(driver, InputEventType.ATTACK_ENTITY, e.hand);
            case DIG_START -> attack.handle(driver, InputEventType.DIG_START, e.hand);
            case DIG_FINISH, DIG_ABORT -> attack.handle(driver, e.type, e.hand);
            case USE_DOWN -> itemUse.handle(driver, InputEventType.USE_DOWN, e.hand, e.a);
            case USE_UP -> itemUse.handle(driver, InputEventType.USE_UP, e.hand, -1);
            case DROP_ITEM -> driver.dropItem(e.hand, e.a == 1);
            case SWAP_HANDS -> driver.swapHands();
            case SCREEN_OPEN -> {
            }
        }
    }

    public void pause()
    {
        if (state != State.RUNNING)
        {
            return;
        }
        clock.paused = true;
        attack.suspend(driver);
        itemUse.suspend(driver);
        input.reset();
        driver.releaseAll();
        state = State.PAUSED;
    }

    public void resume()
    {
        if (state != State.PAUSED)
        {
            return;
        }
        clock.paused = false;
        state = State.RUNNING;
        // 下一帧提交前重建逻辑上仍持续的挖掘/使用（不重发瞬时事件）
        rebuildPending = true;
    }

    public void stop()
    {
        if (state == State.STOPPED || state == State.INVALID)
        {
            return;
        }
        releaseControl();
        clock.finished = true;
        state = State.STOPPED;
    }

    private void fail(String reason)
    {
        failureReason = reason;
        releaseControl();
        state = State.INVALID;
    }

    private void releaseControl()
    {
        attack.release(driver);
        itemUse.releaseAll(driver);
        input.reset();
        driver.releaseAll();
    }
}
