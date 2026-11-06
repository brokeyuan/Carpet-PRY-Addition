package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次录制任务：准备页 → 录制/暂停 → 保存（可重试）。
 * 任务 UUID 稳定；名单状态由 {@link RecordingRoster} 纯逻辑维护。
 * 所有方法只在服务器主线程调用。
 */
public final class RecordingTask
{
    public enum State { PREPARING, RECORDING, PAUSED, SAVING, FAILED, COMPLETED, DISCARDED }

    private static final class RuntimeParticipant
    {
        final ReplayParticipant participant;
        RecordingSession session;
        int entityId = -1;

        RuntimeParticipant(ReplayParticipant participant)
        {
            this.participant = participant;
        }
    }

    public final RecordingRoster roster;
    private final ReplayData data = new ReplayData();
    private final Map<String, RuntimeParticipant> runtime = new LinkedHashMap<>();
    private State state = State.PREPARING;
    private int tick;
    private String failure;
    private boolean saveInFlight;

    public RecordingTask(ServerPlayer owner, String name)
    {
        this.roster = new RecordingRoster(UUID.randomUUID(), owner.getStringUUID(),
                profileName(owner), name);
        data.metadata.recordingId = roster.taskId.toString();
        data.metadata.ownerUuid = roster.ownerUuid;
        data.metadata.ownerName = roster.ownerName;
        data.metadata.name = name;
        data.metadata.createdAtEpochMillis = System.currentTimeMillis();
        data.metadata.access = ReplayAccess.PUBLIC;
    }

    public UUID id()
    {
        return roster.taskId;
    }

    public String name()
    {
        return roster.name;
    }

    public String ownerUuid()
    {
        return roster.ownerUuid;
    }

    public String ownerName()
    {
        return roster.ownerName;
    }

    public State state()
    {
        return state;
    }

    public boolean preparing()
    {
        return state == State.PREPARING;
    }

    public boolean running()
    {
        return state == State.RECORDING || state == State.PAUSED;
    }

    public boolean paused()
    {
        return state == State.PAUSED;
    }

    public boolean saving()
    {
        return state == State.SAVING;
    }

    public boolean failed()
    {
        return state == State.FAILED;
    }

    public String failure()
    {
        return failure;
    }

    public int tick()
    {
        return tick;
    }

    public int configuredDurationTicks()
    {
        return roster.durationTicks();
    }

    public ReplayAccess access()
    {
        return roster.access();
    }

    public ReplayData data()
    {
        return data;
    }

    /** 捕获转发用：该玩家在当前任务中的在线会话 */
    public RecordingSession sessionFor(String uuid)
    {
        RuntimeParticipant rt = runtime.get(uuid);
        return rt == null ? null : rt.session;
    }

    public ReplayData snapshot()
    {
        return data.copy();
    }

    public ReplayData snapshotCheckpoint()
    {
        ReplayData copy = data.copy();
        copy.metadata.durationTicks = Math.max(tick, 1);
        for (ReplayParticipant p : copy.participants)
        {
            for (ReplayParticipant.Segment seg : p.segments)
            {
                if (seg.endTick < 0)
                {
                    seg.endTick = seg.startTick + seg.input.duration();
                }
            }
        }
        return copy;
    }

    // ==================== 准备 ====================

    public RecordingRoster.InviteResult setDuration(int seconds)
    {
        if (roster.settingsLocked())
        {
            return RecordingRoster.InviteResult.NOT_ALLOWED;
        }
        roster.setDurationTicks(seconds * 20);
        return RecordingRoster.InviteResult.OK;
    }

    public RecordingRoster.InviteResult setAccess(ReplayAccess access)
    {
        if (roster.settingsLocked())
        {
            return RecordingRoster.InviteResult.NOT_ALLOWED;
        }
        roster.setAccess(access);
        data.metadata.access = roster.access();
        return RecordingRoster.InviteResult.OK;
    }

    /** 开始：至少一名已加入者；清理未回复邀请并固化名单与设置 */
    public String start(MinecraftServer server)
    {
        if (state != State.PREPARING)
        {
            return "任务已开始或已结束";
        }
        List<RecordingRoster.Entry> joined = roster.joinedEntries();
        List<RecordingRoster.Entry> accepted = new ArrayList<>();
        for (RecordingRoster.Entry e : roster.entries())
        {
            if (e.accepted())
            {
                accepted.add(e);
            }
        }
        RecordingRoster.InviteResult result = roster.start();
        if (result != RecordingRoster.InviteResult.OK)
        {
            return "至少需要一名录制对象";
        }
        state = State.RECORDING;
        data.metadata.access = roster.access();
        data.metadata.durationTicks = 0;
        joined.addAll(accepted);
        // 名单在 start 后已经全部是 JOINED；用 roster 的最终名单保证已同意者也在内
        for (RecordingRoster.Entry e : roster.joinedEntries())
        {
            openRuntime(e, server);
        }
        if (!joined.isEmpty() && runtime.isEmpty())
        {
            state = State.FAILED;
            failure = "没有可用的录制对象";
            return failure;
        }
        initInitialState(server);
        return null;
    }

    private void initInitialState(MinecraftServer server)
    {
        for (RecordingRoster.Entry e : roster.joinedEntries())
        {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.uuid));
            if (p != null)
            {
                data.metadata.initialDimension = dimensionOf(p);
                data.metadata.initialX = p.getX();
                data.metadata.initialY = p.getY();
                data.metadata.initialZ = p.getZ();
                data.metadata.initialYaw = p.getYRot();
                data.metadata.initialPitch = p.getXRot();
                return;
            }
        }
    }

    private static String dimensionOf(ServerPlayer p)
    {
        //#if MC >= 12111
        return p.level().dimension().identifier().toString();
        //#else
        //$$ return p.level().dimension().location().toString();
        //#endif
    }

    private static String profileName(ServerPlayer p)
    {
        //#if MC >= 12110
        return p.getGameProfile().name();
        //#else
        //$$ return p.getGameProfile().getName();
        //#endif
    }

    // ==================== 运行时参与者 ====================

    /** 录制中接受邀请后立即加入：从当前 Tick 开始记录，不补录此前行为 */
    public void joinLate(ServerPlayer player, MinecraftServer server)
    {
        RecordingRoster.Entry entry = roster.get(player.getStringUUID());
        if (entry == null || !entry.joined())
        {
            return;
        }
        openRuntime(entry, server);
    }

    private void openRuntime(RecordingRoster.Entry entry, MinecraftServer server)
    {
        if (runtime.containsKey(entry.uuid))
        {
            return;
        }
        ReplayParticipant participant = new ReplayParticipant(entry.uuid, entry.name, entry.joinMethod);
        ReplayParticipant.Segment segment = participant.openSegment(tick);
        data.participants.add(participant);
        RuntimeParticipant rt = new RuntimeParticipant(participant);
        runtime.put(entry.uuid, rt);
        ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(entry.uuid));
        if (p != null && usable(p))
        {
            rt.session = new RecordingSession(p, participant, segment);
            rt.entityId = p.getId();
        }
    }

    public void joinDirect(ServerPlayer player, MinecraftServer server)
    {
        RecordingRoster.Entry entry = roster.get(player.getStringUUID());
        if (entry == null || !entry.joined())
        {
            return;
        }
        openRuntime(entry, server);
    }

    /** 普通邀请参与者主动退出：结束当前片段并永久停止续录 */
    public boolean leave(ServerPlayer player)
    {
        RecordingRoster.InviteResult result = roster.leave(player.getStringUUID());
        if (result != RecordingRoster.InviteResult.OK)
        {
            return false;
        }
        closeRuntime(player.getStringUUID());
        return true;
    }

    private void closeRuntime(String uuid)
    {
        RuntimeParticipant rt = runtime.remove(uuid);
        if (rt != null && rt.session != null)
        {
            rt.session.finish();
        }
    }

    private static boolean usable(ServerPlayer p)
    {
        return p != null && !p.hasDisconnected() && !p.isRemoved() && p.isAlive();
    }

    /**
     * 每服务器 Tick：先维护在线片段与加入/退出，再采样；无人在线也推进共同时间轴。
     * @return true = 达到配置时长，应由调用方结束并保存
     */
    public boolean onServerTick(MinecraftServer server)
    {
        if (state == State.PAUSED)
        {
            maintainPresence(server, false);
            return false;
        }
        if (state != State.RECORDING)
        {
            return false;
        }
        maintainPresence(server, true);
        tick++;
        return tick >= roster.durationTicks();
    }

    private void maintainPresence(MinecraftServer server, boolean capture)
    {
        Iterator<Map.Entry<String, RuntimeParticipant>> it = runtime.entrySet().iterator();
        while (it.hasNext())
        {
            RuntimeParticipant rt = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(rt.participant.uuid));
            boolean online = usable(p);
            if (online && rt.session != null && rt.entityId != p.getId())
            {
                // 重生后实体已更换：结束旧片段，稍后按新实体开新片段
                rt.session.finish();
                rt.session = null;
                rt.entityId = -1;
            }
            if (online)
            {
                if (rt.session == null)
                {
                    ReplayParticipant.Segment segment = rt.participant.openSegment(tick);
                    rt.session = new RecordingSession(p, rt.participant, segment);
                    rt.entityId = p.getId();
                }
                if (capture)
                {
                    rt.session.commitTick();
                }
            }
            else if (rt.session != null)
            {
                rt.session.finish();
                rt.session = null;
                rt.entityId = -1;
            }
        }
    }

    /** 暂停：冻结共同时间轴；期间仍维护加入、退出、上下线 */
    public boolean pause()
    {
        if (state != State.RECORDING)
        {
            return false;
        }
        state = State.PAUSED;
        for (RuntimeParticipant rt : runtime.values())
        {
            if (rt.session != null)
            {
                rt.session.pause();
            }
        }
        return true;
    }

    public boolean resume()
    {
        if (state != State.PAUSED)
        {
            return false;
        }
        state = State.RECORDING;
        for (RuntimeParticipant rt : runtime.values())
        {
            if (rt.session != null)
            {
                if (rt.session.paused())
                {
                    rt.session.resume();
                }
                else
                {
                    rt.session.resyncBaseline();
                }
            }
        }
        return true;
    }

    // ==================== 结束 ====================

    /** 固化轨道；返回是否至少有一段有效内容 */
    public boolean finishTracks()
    {
        for (RuntimeParticipant rt : runtime.values())
        {
            if (rt.session != null)
            {
                rt.session.finish();
                rt.session = null;
                rt.entityId = -1;
            }
        }
        data.metadata.durationTicks = tick;
        if (tick <= 0)
        {
            return false;
        }
        for (ReplayParticipant p : data.participants)
        {
            p.segments.removeIf(seg -> seg.duration() <= 0);
        }
        data.participants.removeIf(p -> p.segments.isEmpty());
        data.diagnostics.captureLimitations.add("多人录像按参与者划分独立视觉/输入轨道，离线空档不写帧");
        String limitation = ReplayRecorder.captureLimitation();
        if (limitation != null)
        {
            data.diagnostics.captureLimitations.add(limitation);
        }
        return !data.participants.isEmpty();
    }

    /** 录制期异常：固化已有内容并进入可重试的保存失败态，不让异常冒到服务器主循环 */
    public void runtimeFailure(String reason)
    {
        if (state == State.RECORDING || state == State.PAUSED)
        {
            try
            {
                finishTracks();
            }
            catch (Throwable ignored)
            {
                // 异常路径尽力保留已有内存数据；保存时仍会做完整校验
            }
        }
        markSaveFailed(reason);
    }

    public void markSaving()
    {
        state = State.SAVING;
    }

    public void markSaveFailed(String reason)
    {
        state = State.FAILED;
        failure = reason;
    }

    public void markSaveAccepted()
    {
        saveInFlight = true;
    }

    public boolean saveInFlight()
    {
        return saveInFlight;
    }

    public void markCompleted()
    {
        state = State.COMPLETED;
        saveInFlight = false;
    }

    public void markDiscarded()
    {
        state = State.DISCARDED;
        for (RuntimeParticipant rt : runtime.values())
        {
            if (rt.session != null)
            {
                rt.session.abort();
            }
        }
        runtime.clear();
    }

    /** 供面板显示的录制对象摘要 */
    public String participantSummary()
    {
        StringBuilder sb = new StringBuilder();
        for (RecordingRoster.Entry e : roster.joinedEntries())
        {
            if (sb.length() > 0)
            {
                sb.append("、");
            }
            sb.append(e.name);
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
    }

    public int participantCount()
    {
        return runtime.size();
    }
}
