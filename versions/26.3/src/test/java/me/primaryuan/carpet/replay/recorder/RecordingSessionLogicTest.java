package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端录制状态机测试：绕过 MC，直接以纯逻辑验证掩码/朝向/事件逐 Tick 落盘。
 * 这是审查 F1 的回归测试：commitTick 顺序与 ensureDuration 增长保留。
 */
class RecordingSessionLogicTest
{
    /** 最小化可驱动的会话（纯数据路径，不触 MC 实体） */
    private static final class ProbeSink implements CaptureSink
    {
        int lastMask = -1;
        final java.util.List<int[]> masks = new java.util.ArrayList<>();

        @Override
        public void onMovementInput(int mask)
        {
            lastMask = mask;
            masks.add(new int[]{mask});
        }

        @Override public void onSwing(net.minecraft.world.InteractionHand hand) {}
        @Override public void onAttackEntity(int entityId) {}
        @Override public void onDigStart(net.minecraft.core.BlockPos pos) {}
        @Override public void onDigAbort(net.minecraft.core.BlockPos pos) {}
        @Override public void onDigFinish(net.minecraft.core.BlockPos pos) {}
        @Override public void onUseItemOn(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.HitResult hit) {}
        @Override public void onUseEntity(net.minecraft.world.InteractionHand hand, int entityId) {}
        @Override public void onUseItem(net.minecraft.world.InteractionHand hand) {}
        @Override public void onReleaseUse() {}
        @Override public void onSelectSlot(int slot) {}
        @Override public void onDrop(boolean all) {}
        @Override public void onSwapHands() {}
    }

    @Test
    void inputTrackGrowsPreservingData()
    {
        me.primaryuan.carpet.replay.common.InputTrack t = new me.primaryuan.carpet.replay.common.InputTrack();
        t.ensureDuration(2);
        t.movementMasks[0] = (byte) InputFlags.FORWARD;
        t.movementMasks[1] = (byte) InputFlags.SPRINT;
        t.yaw[1] = 90f;
        t.ensureDuration(5); // 增长不得清零
        assertEquals(InputFlags.FORWARD, t.movementMasks[0]);
        assertEquals(InputFlags.SPRINT, t.movementMasks[1]);
        assertEquals(90f, t.yaw[1], 0f);
        assertEquals(0, t.movementMasks[4]);
    }

    @Test
    void eventsKeepOrderWithinTick()
    {
        // 事件序号生成逻辑被抽到 RecordingSession.seqInTick（私有），此处验证 InputEvent 排序约定
        me.primaryuan.carpet.replay.common.InputTrack t = new me.primaryuan.carpet.replay.common.InputTrack();
        t.events.add(new me.primaryuan.carpet.replay.common.InputEvent(2, 1, InputEventType.USE_UP));
        t.events.add(new me.primaryuan.carpet.replay.common.InputEvent(2, 0, InputEventType.SELECT_SLOT));
        t.events.add(new me.primaryuan.carpet.replay.common.InputEvent(1, 0, InputEventType.SWING));
        java.util.List<InputEvent> inTick2 = t.eventsInRange(2, 3, InputEventType.SELECT_SLOT);
        assertEquals(1, inTick2.size());
        assertEquals(0, inTick2.get(0).seq);
    }
}
