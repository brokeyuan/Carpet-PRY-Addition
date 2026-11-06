package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputTrack;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;
import me.primaryuan.carpet.replay.common.VisualTrack;

/**
 * 单个参与者一次在线片段的录制时间轴纯逻辑（无 MC 依赖，可 JUnit 单测）。
 * Tick 为相对当前片段的序号；片段的绝对起点由 {@link ReplayParticipant.Segment#startTick} 保存。
 * <p>
 * 暂停语义：
 * - 暂停期间时间轴冻结：不推进 Tick、不入轨帧与事件。
 * - 暂停期间的移动输入包仅同步基线（不标记差量）。
 * - 持续动作在暂停期间只维护最终状态；恢复时补一次状态对齐。
 * - 恢复时置位段边界：下一帧强制开启新视觉段。
 */
public final class RecordingTimeline
{
    private final ReplayParticipant.Segment segment;
    private int tick;
    private boolean finished;
    private boolean paused;
    private boolean pendingSegmentBreak;
    private String currentDimension = "";
    private VisualSegment currentSegment;
    private int lastMask = -1;
    private int currentMask;
    private boolean maskDirty;
    private int seqTick = -1;
    private int seqInTick;

    private boolean digHeld;
    private boolean useHeldMain;
    private boolean useHeldOff;
    private boolean pausedDigHeld;
    private boolean pausedUseMain;
    private boolean pausedUseOff;
    private int lastEntityUseId = Integer.MIN_VALUE;
    private int lastEntityUseTick = Integer.MIN_VALUE;

    public RecordingTimeline(ReplayParticipant.Segment segment)
    {
        this.segment = segment;
    }

    public RecordingTimeline(int startTick)
    {
        this(new ReplayParticipant.Segment(startTick));
    }

    public ReplayParticipant.Segment segment()
    {
        return segment;
    }

    public VisualTrack visual()
    {
        return segment.visual;
    }

    public InputTrack input()
    {
        return segment.input;
    }

    public int tick()
    {
        return tick;
    }

    public int startTick()
    {
        return segment.startTick;
    }

    public boolean finished()
    {
        return finished;
    }

    public boolean paused()
    {
        return paused;
    }

    /** 记录首帧选中槽位基线（0~8），保证开始已选工具但未换槽也能重演 */
    public void setInitialSlot(int slot)
    {
        if (finished || slot < 0 || slot > 8)
        {
            return;
        }
        InputEvent e = new InputEvent(0, nextSeq(), InputEventType.SELECT_SLOT);
        e.a = slot;
        segment.input.events.add(e);
    }

    public boolean pause()
    {
        if (finished || paused)
        {
            return false;
        }
        paused = true;
        maskDirty = false;
        pausedDigHeld = digHeld;
        pausedUseMain = useHeldMain;
        pausedUseOff = useHeldOff;
        return true;
    }

    public boolean resume()
    {
        return resume(-1);
    }

    /** 恢复并写入当前槽位基线；slot<0 表示不记录 */
    public boolean resume(int slot)
    {
        if (finished || !paused)
        {
            return false;
        }
        paused = false;
        pendingSegmentBreak = true;
        reconcileHoldsAtResume();
        syncSlot(slot);
        return true;
    }

    /** 在下一个提交 Tick 前记录当前槽位基线（恢复/暂停期间新加入的会话） */
    public void syncSlot(int slot)
    {
        if (finished || slot < 0 || slot > 8)
        {
            return;
        }
        InputEvent e = addEvent(InputEventType.SELECT_SLOT, -1);
        if (e != null)
        {
            e.a = slot;
        }
    }

    private void reconcileHoldsAtResume()
    {
        if (digHeld != pausedDigHeld)
        {
            addEvent(digHeld ? InputEventType.DIG_START : InputEventType.DIG_ABORT, -1);
        }
        boolean wasUseHeld = pausedUseMain || pausedUseOff;
        boolean nowUseHeld = useHeldMain || useHeldOff;
        if (nowUseHeld && !wasUseHeld)
        {
            addUseDown(useHeldMain ? 0 : 1, InputEventType.USE_TARGET_AIR, -1);
        }
        else if (!nowUseHeld && wasUseHeld)
        {
            addUseUp(-1);
        }
    }

    /** 结束片段：固化相对长度与绝对终点 */
    public void finish()
    {
        if (finished)
        {
            return;
        }
        segment.input.ensureDuration(tick);
        segment.input.trimToDuration();
        segment.endTick = segment.startTick + tick;
        finished = true;
    }

    public void abort()
    {
        finished = true;
    }

    public void onMovementInput(int mask)
    {
        if (finished)
        {
            return;
        }
        currentMask = mask;
        if (paused)
        {
            lastMask = mask;
            return;
        }
        if (mask != lastMask)
        {
            lastMask = mask;
        }
        maskDirty = true;
    }

    public InputEvent addEntityUse(int hand, int entityId)
    {
        if (finished)
        {
            return null;
        }
        if (entityId == lastEntityUseId && tick == lastEntityUseTick)
        {
            return null;
        }
        lastEntityUseId = entityId;
        lastEntityUseTick = tick;
        return addUseDown(hand, InputEventType.USE_TARGET_ENTITY, entityId);
    }

    public InputEvent addUseDown(int hand, byte targetKind, int diagnosticId)
    {
        if (finished)
        {
            return null;
        }
        if (targetKind == InputEventType.USE_TARGET_AIR && (hand == 0 || hand == 1))
        {
            if (hand == 0)
            {
                useHeldMain = true;
            }
            else
            {
                useHeldOff = true;
            }
        }
        if (paused)
        {
            return null;
        }
        InputEvent e = addEvent(InputEventType.USE_DOWN, hand);
        if (e != null)
        {
            e.a = targetKind;
            e.d = diagnosticId;
        }
        return e;
    }

    public InputEvent addUseUp(int hand)
    {
        if (finished)
        {
            return null;
        }
        if (hand < 0)
        {
            useHeldMain = false;
            useHeldOff = false;
        }
        else if (hand == 0)
        {
            useHeldMain = false;
        }
        else if (hand == 1)
        {
            useHeldOff = false;
        }
        if (paused)
        {
            return null;
        }
        return addEvent(InputEventType.USE_UP, hand);
    }

    public InputEvent addEvent(InputEventType type, int hand)
    {
        if (finished)
        {
            return null;
        }
        updateHoldState(type);
        if (paused)
        {
            return null;
        }
        ensureInput();
        InputEvent e = new InputEvent(tick, nextSeq(), type);
        e.hand = (byte) hand;
        segment.input.events.add(e);
        if (type == InputEventType.SWING || type == InputEventType.ATTACK_ENTITY)
        {
            segment.visual.animEvents.add(new VisualTrack.AnimEvent(
                    tick, e.seq, VisualTrack.ANIM_SWING_MAIN, e.hand));
        }
        return e;
    }

    private void updateHoldState(InputEventType type)
    {
        switch (type)
        {
            case DIG_START -> digHeld = true;
            case DIG_FINISH, DIG_ABORT -> digHeld = false;
            default -> { }
        }
    }

    /**
     * 提交一 Tick。
     * @return 是否真实入轨；暂停中返回 false，超出时长上限由任务层处理
     */
    public boolean commitTick(String dimension, VisualFrame frame, float lookYaw, float lookPitch)
    {
        if (finished || paused)
        {
            return false;
        }
        if (segment.input.duration() == 0)
        {
            segment.input.ensureDuration(1);
        }
        segment.input.ensureDuration(tick + 1);
        if (pendingSegmentBreak || !dimension.equals(currentDimension) || currentSegment == null)
        {
            currentSegment = new VisualSegment(dimension, tick);
            segment.visual.segments.add(currentSegment);
            currentDimension = dimension;
            pendingSegmentBreak = false;
        }
        if (!maskDirty)
        {
            if (lastMask < 0)
            {
                lastMask = 0;
            }
            currentMask = lastMask;
        }
        segment.input.movementMasks[tick] = (byte) currentMask;
        segment.input.yaw[tick] = lookYaw;
        segment.input.pitch[tick] = lookPitch;
        maskDirty = false;
        currentSegment.frames.add(frame);
        tick++;
        return true;
    }

    private void ensureInput()
    {
        if (segment.input.duration() == 0)
        {
            segment.input.ensureDuration(Math.max(1, tick + 1));
        }
    }

    private int nextSeq()
    {
        if (seqTick != tick)
        {
            seqTick = tick;
            seqInTick = 0;
        }
        return seqInTick++;
    }
}
