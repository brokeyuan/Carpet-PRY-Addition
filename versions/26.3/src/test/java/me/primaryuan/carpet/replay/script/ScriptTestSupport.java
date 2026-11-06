package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.InputFlags;
import me.primaryuan.carpet.replay.common.InputTrack;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualTrack;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;

import java.util.ArrayList;
import java.util.List;

/** 脚本状态机测试的公共构造与桩驱动 */
final class ScriptTestSupport
{
    private ScriptTestSupport() {}

    /**
     * 桩驱动：模拟 Carpet ActionPack 的"提交 / 实体 Tick 消费"两阶段。
     * 动作方法只进入 pending；consumeTick 才代表实体 onUpdate 真正执行。
     * releaseAll 会清掉未消费动作（等价 stopAll），因此尾帧是否被消费可验证。
     */
    static final class StubDriver implements FakeDriver
    {
        final List<String> log = new ArrayList<>();
        /** 已提交、等待实体 Tick 消费的动作 */
        final List<String> pending = new ArrayList<>();
        /** 已被实体 Tick 消费的动作 */
        final List<String> executed = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
        final List<String> drops = new ArrayList<>();
        /** 轮间重置回调次数（roundReset 测试） */
        int newRounds;
        /** 归属下线次数（自动下线归属测试） */
        int offlines;
        float lastYaw;
        float lastPitch;
        int mask;
        boolean jump;
        boolean sneak;
        boolean sprint;
        boolean attackHold;
        boolean useHoldMain;
        boolean useHoldOff;
        boolean valid = true;
        int released;
        /** 首帧强制完整应用次数 */
        int forcedApplies;

        private void add(String s) { log.add(s); }

        private void act(String s)
        {
            log.add(s);
            pending.add(s);
        }

        /** 模拟实体 Tick 的 ActionPack.onUpdate：把待执行动作标记为已执行 */
        void consumeTick()
        {
            executed.addAll(pending);
            pending.clear();
        }

        @Override
        public void setMovementMask(int mask)
        {
            this.mask = mask;
            add("mask=" + InputFlags.describe(mask));
        }

        @Override
        public void setJump(boolean jump) { this.jump = jump; add("jump=" + jump); }

        @Override
        public void setSneaking(boolean sneak) { this.sneak = sneak; add("sneak=" + sneak); }

        @Override
        public void setSprinting(boolean sprint) { this.sprint = sprint; add("sprint=" + sprint); }

        @Override
        public void look(float yaw, float pitch)
        {
            lastYaw = yaw;
            lastPitch = pitch;
            add("look=" + yaw + "," + pitch);
        }

        @Override
        public void setSlot(int slot)
        {
            slots.add(slot);
            add("slot=" + slot);
        }

        @Override
        public boolean startAttackHold()
        {
            attackHold = true;
            act("attackHold=start");
            return true;
        }

        @Override
        public void stopAttackHold()
        {
            attackHold = false;
            act("attackHold=stop");
        }

        @Override
        public boolean attackOnce()
        {
            act("attackOnce");
            return true;
        }

        @Override
        public boolean startUseHold(int hand)
        {
            if (hand == 0) { useHoldMain = true; } else { useHoldOff = true; }
            act("useHold=start" + hand);
            return true;
        }

        @Override
        public void stopUseHold(int hand)
        {
            if (hand == 0) { useHoldMain = false; } else { useHoldOff = false; }
            act("useHold=stop" + hand);
        }

        @Override
        public boolean useOnce(int hand)
        {
            act("useOnce" + hand);
            return true;
        }

        @Override
        public void dropItem(int hand, boolean all)
        {
            drops.add(hand + "," + all);
            add("drop=" + hand + "," + all);
        }

        @Override
        public void swapHands() { act("swap"); }

        @Override
        public boolean isValid() { return valid; }

        @Override
        public void releaseAll()
        {
            released++;
            attackHold = false;
            useHoldMain = false;
            useHoldOff = false;
            mask = 0;
            // Carpet stopAll 会取消尚未消费的动作：尾帧未消费时这里会丢掉它
            pending.clear();
            add("releaseAll");
        }

        @Override
        public boolean onNewRound()
        {
            newRounds++;
            add("newRound");
            return true;
        }

        @Override
        public void takeOffline()
        {
            offlines++;
            add("takeOffline");
        }

        @Override
        public boolean teleportTo(String dimKey, double x, double y, double z, float yaw, float pitch)
        {
            add("teleport=" + dimKey + "," + x + "," + y + "," + z + "," + yaw);
            return true;
        }

        long count(String prefix)
        {
            return log.stream().filter(s -> s.startsWith(prefix)).count();
        }
    }

    /** 构造一段 durationTicks 的单参与者录像：输入掩码与朝向线性可预期 */
    static ReplayData data(int ticks)
    {
        ReplayData data = new ReplayData();
        data.metadata.recordingId = "t";
        data.metadata.ownerUuid = "owner-uuid";
        data.metadata.ownerName = "Owner";
        data.metadata.name = "n";
        data.metadata.durationTicks = ticks;
        data.metadata.initialDimension = "minecraft:overworld";
        ReplayParticipant p = new ReplayParticipant("owner-uuid", "Owner",
                ReplayParticipant.JoinMethod.OWNER);
        ReplayParticipant.Segment segment = p.openSegment(0);
        segment.endTick = ticks;
        segment.input.ensureDuration(ticks);
        VisualSegment seg = new VisualSegment("minecraft:overworld", 0);
        for (int i = 0; i < ticks; i++)
        {
            segment.input.movementMasks[i] = (byte) InputFlags.FORWARD;
            segment.input.yaw[i] = i * 10f;
            segment.input.pitch[i] = 0f;
            seg.frames.add(new VisualFrame());
        }
        segment.visual.segments.add(seg);
        data.participants.add(p);
        return data;
    }

    static InputTrack input(ReplayData data)
    {
        return data.participants.get(0).segments.get(0).input;
    }

    static VisualTrack visual(ReplayData data)
    {
        return data.participants.get(0).segments.get(0).visual;
    }

    static InputEvent event(int tick, int seq, InputEventType type)
    {
        return new InputEvent(tick, seq, type);
    }
}
