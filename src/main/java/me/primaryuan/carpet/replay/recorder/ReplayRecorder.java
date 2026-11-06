package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.storage.ReplayStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 回放任务管理器：准备、邀请、录制、保存与服务器生命周期锚点。
 * 两条规则共用同一套任务/存储；幻影与假人执行分别由各自规则门控。
 */
public final class ReplayRecorder
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final long CHECKPOINT_INTERVAL_TICKS = 600;
    private static final Map<UUID, RecordingTask> TASKS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_CHECKPOINT = new java.util.HashMap<>();

    private ReplayRecorder() {}

    public static String captureLimitation()
    {
        //#if MC >= 12103
        return null;
        //#else
        //$$ return "1.21/1.21.1 普通步行/跳跃不发送输入包，假人脚本只能记录骑乘输入；需要完整步行脚本请使用 1.21.3 及以上版本";
        //#endif
    }

    // ==================== 任务查找 ====================

    public static RecordingTask taskById(UUID id)
    {
        return id == null ? null : TASKS.get(id);
    }

    public static RecordingTask taskForOwner(String ownerUuid)
    {
        for (RecordingTask task : TASKS.values())
        {
            if (task.ownerUuid().equals(ownerUuid) && task.state() != RecordingTask.State.COMPLETED
                    && task.state() != RecordingTask.State.DISCARDED)
            {
                return task;
            }
        }
        return null;
    }

    public static RecordingTask taskForParticipant(String uuid)
    {
        for (RecordingTask task : TASKS.values())
        {
            RecordingRoster.Entry e = task.roster.get(uuid);
            if (e != null && e.joined())
            {
                return task;
            }
        }
        return null;
    }

    public static List<RecordingTask> allTasks()
    {
        return new ArrayList<>(TASKS.values());
    }

    /** 每个输入包：把事件转发给该玩家参与的所有任务（同一玩家可被多个任务录制） */
    public static void dispatch(ServerPlayer player, Consumer<CaptureSink> action)
    {
        String uuid = player.getStringUUID();
        for (RecordingTask task : TASKS.values())
        {
            RecordingSession session = task.sessionFor(uuid);
            if (session != null)
            {
                try
                {
                    action.accept(session);
                }
                catch (Throwable e)
                {
                    LOGGER.error("[replay] 输入捕获异常（任务 {}）", task.name(), e);
                }
            }
        }
    }

    // ==================== 准备 ====================

    /**
     * 打开/复用准备任务。重复点击不会产生大量空任务：同一创作者复用未开始任务；
     * 已开始的任务直接返回供面板展示。
     */
    public static RecordingTask prepare(ServerPlayer owner, String name)
    {
        RecordingTask existing = taskForOwner(owner.getStringUUID());
        if (existing != null)
        {
            return existing;
        }
        RecordingTask task = new RecordingTask(owner, name);
        TASKS.put(task.id(), task);
        return task;
    }

    public static String start(RecordingTask task, MinecraftServer server)
    {
        if (task == null)
        {
            return "没有准备中的录制";
        }
        String error = task.start(server);
        if (error == null)
        {
            LAST_CHECKPOINT.put(task.id(), (long) server.getTickCount());
            notifyOwner(server, task, "录制已开始：" + task.name());
        }
        return error;
    }

    public static RecordingRoster.InviteResult invite(RecordingTask task, ServerPlayer inviter,
                                                      ServerPlayer target, boolean admin)
    {
        if (!task.preparing() && !task.running())
        {
            return RecordingRoster.InviteResult.NOT_ALLOWED;
        }
        RecordingRoster.InviteResult result = task.roster.invite(
                target.getStringUUID(), profileName(target), admin);
        if (result == RecordingRoster.InviteResult.OK)
        {
            if (admin)
            {
                if (task.state() == RecordingTask.State.RECORDING || task.state() == RecordingTask.State.PAUSED)
                {
                    task.joinDirect(target, inviter.level().getServer());
                }
                target.sendSystemMessage(Component.literal(
                        inviter.getName().getString() + " 已将你加入录像（管理员直接录制）"));
                target.sendSystemMessage(Component.literal("录制时长：" + fmtDuration(task.configuredDurationTicks())
                        + "；访问范围：" + (task.access() == me.primaryuan.carpet.replay.common.ReplayAccess.PUBLIC
                        ? "公开" : "私有") + "。管理员直接添加的参与者不能主动退出。"));
                notifyOwner(inviter.level().getServer(), task,
                        "已直接加入录制对象：" + target.getName().getString());
            }
            else
            {
                RecordingRoster.Entry entry = task.roster.inviteEntry(target.getStringUUID());
                if (entry != null && entry.token != null)
                {
                    sendInvite(inviter, target, task, entry.token);
                }
            }
        }
        return result;
    }

    private static void sendInvite(ServerPlayer inviter, ServerPlayer target, RecordingTask task, UUID token)
    {
        String time = fmtDuration(task.configuredDurationTicks());
        String access = task.access() == me.primaryuan.carpet.replay.common.ReplayAccess.PUBLIC
                ? "公开（所有具备回放权限的玩家可观看与提取操作）"
                : "私有（仅创作者与管理员可观看与提取操作）";
        target.sendSystemMessage(Component.literal(inviter.getName().getString() + " 邀请你参与录像"));
        target.sendSystemMessage(me.primaryuan.carpet.command.ReplayUi.line(
                me.primaryuan.carpet.command.ReplayUi.button("同意",
                        "/playereplay invite accept " + task.id() + " " + token,
                        "录制时长：" + time + "\n访问范围：" + access, net.minecraft.ChatFormatting.GREEN),
                " ",
                me.primaryuan.carpet.command.ReplayUi.button("拒绝",
                        "/playereplay invite deny " + task.id() + " " + token,
                        "拒绝后不会加入本次录像", net.minecraft.ChatFormatting.RED)));
    }

    /** 受邀者处理邀请；返回反馈文本 */
    public static String acceptInvite(ServerPlayer invitee, UUID taskId, UUID token, MinecraftServer server)
    {
        RecordingTask task = TASKS.get(taskId);
        if (task == null || (task.state() != RecordingTask.State.PREPARING
                && task.state() != RecordingTask.State.RECORDING
                && task.state() != RecordingTask.State.PAUSED))
        {
            return "录制任务已结束，邀请已失效";
        }
        boolean recording = task.state() == RecordingTask.State.RECORDING
                || task.state() == RecordingTask.State.PAUSED;
        RecordingRoster.InviteResult result = task.roster.accept(invitee.getStringUUID(), token, recording);
        if (result == RecordingRoster.InviteResult.OK)
        {
            if (recording)
            {
                task.joinLate(invitee, server);
                invitee.sendSystemMessage(Component.literal("已加入，正在记录你的行为"));
                invitee.sendSystemMessage(me.primaryuan.carpet.command.ReplayUi.line(
                        me.primaryuan.carpet.command.ReplayUi.button("退出录制",
                                "/playereplay record leave", "普通邀请加入者可随时退出；已录内容保留",
                                net.minecraft.ChatFormatting.RED)));
                notifyOwner(server, task, invitee.getName().getString() + " 已加入录制");
                return null;
            }
            invitee.sendSystemMessage(Component.literal("已同意，等待创作者开始录制"));
            notifyOwner(server, task, invitee.getName().getString() + " 已同意参与录像");
            return null;
        }
        return switch (result)
        {
            case ALREADY_JOINED -> "你已经加入本次录像";
            case OLD_TOKEN -> "该邀请已失效（可能已重新邀请），请使用最新邀请";
            case NOT_FOUND -> "邀请不存在或已被取消";
            case NOT_INVITED -> "该邀请已处理，不能再次使用";
            default -> "无法加入本次录像";
        };
    }

    public static String denyInvite(ServerPlayer invitee, UUID taskId, UUID token)
    {
        RecordingTask task = TASKS.get(taskId);
        if (task == null)
        {
            return "录制任务已结束，邀请已失效";
        }
        RecordingRoster.InviteResult result = task.roster.deny(invitee.getStringUUID(), token);
        return switch (result)
        {
            case OK -> "已拒绝本次邀请";
            case OLD_TOKEN -> "该邀请已失效，请使用最新邀请";
            case NOT_FOUND -> "邀请不存在或已被取消";
            default -> "该邀请已处理";
        };
    }

    public static boolean cancelInvite(RecordingTask task, String targetUuid)
    {
        return task.roster.cancelInvite(targetUuid) == RecordingRoster.InviteResult.OK;
    }

    public static boolean toggleSelf(RecordingTask task, boolean on)
    {
        if (!task.preparing())
        {
            return false;
        }
        if (on)
        {
            task.roster.addSelf();
        }
        else
        {
            task.roster.removeSelf();
        }
        return true;
    }

    // ==================== 运行控制 ====================

    public static boolean pause(RecordingTask task)
    {
        return task != null && task.pause();
    }

    public static boolean resume(RecordingTask task)
    {
        return task != null && task.resume();
    }

    public static boolean discard(RecordingTask task)
    {
        if (task == null)
        {
            return false;
        }
        task.markDiscarded();
        TASKS.remove(task.id());
        LAST_CHECKPOINT.remove(task.id());
        return true;
    }

    /**
     * 结束并保存。失败时任务保留在 FAILED 状态，可重试；绝不先移除唯一数据副本。
     */
    public static boolean stopAndSave(MinecraftServer server, RecordingTask task,
                                      java.util.function.BiConsumer<Boolean, String> onDone)
    {
        if (task == null || task.saving())
        {
            return false;
        }
        if (!task.running() && !task.failed())
        {
            return false;
        }
        if (!task.failed())
        {
            if (!task.finishTracks())
            {
                task.markSaveFailed("没有有效录制内容");
                if (onDone != null)
                {
                    onDone.accept(false, "没有有效录制内容");
                }
                return false;
            }
        }
        task.markSaving();
        ReplayData data = task.data();
        if (ReplayStorage.nameExists(server, data.metadata.ownerUuid, data.metadata.name, data.metadata.recordingId))
        {
            data.metadata.name = ReplayStorage.uniqueName(server, data.metadata.ownerUuid, data.metadata.name);
        }
        boolean accepted = ReplayStorage.saveAsync(server, data, (ok, note) ->
        {
            if (ok)
            {
                task.markCompleted();
                TASKS.remove(task.id());
                LAST_CHECKPOINT.remove(task.id());
                ReplayStorage.deleteCheckpoint(server, task.id().toString());
            }
            else
            {
                task.markSaveFailed(note == null ? "写入失败" : note);
            }
            if (onDone != null)
            {
                onDone.accept(ok, note);
            }
        });
        if (accepted)
        {
            task.markSaveAccepted();
        }
        else
        {
            task.markSaveFailed("保存未能启动");
        }
        return accepted;
    }

    // ==================== Tick ====================

    public static void tick(MinecraftServer server)
    {
        boolean actionBar = server.getTickCount() % 20 == 0;
        for (RecordingTask task : new ArrayList<>(TASKS.values()))
        {
            try
            {
                if (task.state() == RecordingTask.State.RECORDING
                        || task.state() == RecordingTask.State.PAUSED)
                {
                    boolean auto = task.onServerTick(server);
                    if (auto)
                    {
                        autoStop(server, task);
                    }
                    checkpointIfDue(server, task);
                }
                if (actionBar && (task.state() == RecordingTask.State.RECORDING
                        || task.state() == RecordingTask.State.PAUSED))
                {
                    sendActionBars(server, task);
                }
            }
            catch (Throwable e)
            {
                LOGGER.error("[replay] 录制任务异常，转入保存失败态: {}", task.name(), e);
                task.runtimeFailure("录制异常：" + e.getClass().getSimpleName());
                notifyOwner(server, task, "录制异常已中止，数据保留；可使用 /playereplay record retry 重试保存");
            }
        }
    }

    private static void autoStop(MinecraftServer server, RecordingTask task)
    {
        String cap = fmtDuration(task.configuredDurationTicks());
        boolean accepted = stopAndSave(server, task, (ok, note) ->
        {
            if (ok)
            {
                notifyOwner(server, task, "录制达到时长上限 " + cap + "，已自动结束并保存");
            }
            else
            {
                notifyOwner(server, task, "录制自动保存失败：" + (note == null ? "未知原因" : note)
                        + "；可使用 /playereplay record retry 重试");
            }
        });
        if (!accepted)
        {
            notifyOwner(server, task, "录制达到时长上限，但保存未能启动；请使用 /playereplay record retry");
        }
    }

    private static void checkpointIfDue(MinecraftServer server, RecordingTask task)
    {
        long now = server.getTickCount();
        Long last = LAST_CHECKPOINT.get(task.id());
        if (last != null && now - last < CHECKPOINT_INTERVAL_TICKS)
        {
            return;
        }
        LAST_CHECKPOINT.put(task.id(), now);
        if (task.tick() <= 0)
        {
            return;
        }
        ReplayData snapshot = task.snapshotCheckpoint();
        ReplayStorage.saveCheckpointAsync(server, snapshot);
    }

    private static void sendActionBars(MinecraftServer server, RecordingTask task)
    {
        int s = task.tick() / 20;
        String time = String.format("%02d:%02d", s / 60, s % 60);
        Component msg = task.paused()
                ? Component.literal("录制已暂停 · " + time).withStyle(net.minecraft.ChatFormatting.YELLOW)
                : Component.literal("录制中 · " + time).withStyle(net.minecraft.ChatFormatting.GREEN);
        for (RecordingRoster.Entry e : task.roster.joinedEntries())
        {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.uuid));
            if (p == null)
            {
                continue;
            }
            //#if MC >= 260102
            p.sendSystemMessage(msg, true);
            //#else
            //$$ p.displayClientMessage(msg, true);
            //#endif
        }
    }

    public static void notifyOwner(MinecraftServer server, RecordingTask task, String text)
    {
        ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(task.ownerUuid()));
        if (owner != null)
        {
            owner.sendSystemMessage(Component.literal(text));
        }
    }

    /**
     * 正常停服：封存全部录制内容并等待 IO 队列完成；开服不恢复任何录制任务。
     */
    public static void shutdownAndSave(MinecraftServer server)
    {
        for (RecordingTask task : new ArrayList<>(TASKS.values()))
        {
            if (task.preparing())
            {
                task.markDiscarded();
                continue;
            }
            if (task.state() == RecordingTask.State.FAILED && task.data().durationTicks() > 0)
            {
                if (ReplayStorage.saveBlocking(server, task.data()))
                {
                    ReplayStorage.deleteCheckpoint(server, task.id().toString());
                }
                else
                {
                    ReplayStorage.saveCheckpointBlocking(server, task.data());
                }
                task.markCompleted();
                continue;
            }
            if (task.running())
            {
                if (task.finishTracks())
                {
                    if (ReplayStorage.saveBlocking(server, task.data()))
                    {
                        ReplayStorage.deleteCheckpoint(server, task.id().toString());
                    }
                    else
                    {
                        ReplayStorage.saveCheckpointBlocking(server, task.data());
                    }
                }
            }
            task.markCompleted();
        }
        TASKS.clear();
        LAST_CHECKPOINT.clear();
        ReplayStorage.flushBlocking();
    }

    public static int activeCount()
    {
        int n = 0;
        for (RecordingTask task : TASKS.values())
        {
            if (task.running())
            {
                n++;
            }
        }
        return n;
    }

    public static int maxDurationTicks()
    {
        return ReplayLimits.MAX_DURATION_TICKS;
    }

    private static String fmtDuration(int ticks)
    {
        int s = ticks / 20;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String profileName(ServerPlayer p)
    {
        //#if MC >= 12110
        return p.getGameProfile().name();
        //#else
        //$$ return p.getGameProfile().getName();
        //#endif
    }
}
