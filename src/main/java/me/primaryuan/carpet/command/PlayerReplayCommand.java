package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayNames;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.ReplayPermissions;
import me.primaryuan.carpet.replay.phantom.PhantomEntityTracker;
import me.primaryuan.carpet.replay.recorder.RecordingRoster;
import me.primaryuan.carpet.replay.recorder.RecordingTask;
import me.primaryuan.carpet.replay.recorder.ReplayRecorder;
import me.primaryuan.carpet.replay.storage.ReplayFavorites;
import me.primaryuan.carpet.replay.storage.ReplayStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /playereplay：两条回放规则共用的录制、列表、访问与幻影回放入口。
 * 根命令对所有玩家开放；管理操作、实例控制、脚本执行在服务端逐项鉴权。
 * 面板按钮只携带稳定录像 ID 与来源页码，点击时重新校验当前权限/任务状态。
 */
public final class PlayerReplayCommand
{
    private static final DateTimeFormatter AUTO_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int PAGE_SIZE = 5;

    private PlayerReplayCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        {
            var root = literal("playereplay")
                    .requires(source -> anyReplayRuleEnabled())
                    .executes(PlayerReplayCommand::mainPanel);

            root.then(literal("panel").executes(PlayerReplayCommand::mainPanel));

            // ===== 录制：准备页 → 开始 =====
            var record = literal("record").executes(ctx -> prepare(ctx, null));
            record.then(argument("名称", StringArgumentType.string())
                    .executes(ctx -> prepare(ctx, StringArgumentType.getString(ctx, "名称"))));
            record.then(literal("begin").executes(PlayerReplayCommand::startTask));
            record.then(literal("objects").executes(PlayerReplayCommand::objectsPanel));
            record.then(literal("invite")
                    .then(argument("玩家", StringArgumentType.string())
                            .executes(PlayerReplayCommand::invitePlayer)));
            record.then(literal("uninvite")
                    .then(argument("玩家UUID", StringArgumentType.string())
                            .executes(PlayerReplayCommand::uninvitePlayer)));
            record.then(literal("self")
                    .then(literal("on").executes(ctx -> toggleSelf(ctx, true)))
                    .then(literal("off").executes(ctx -> toggleSelf(ctx, false))));
            record.then(literal("settings").executes(PlayerReplayCommand::settingsPanel));
            record.then(literal("duration")
                    .then(argument("秒", IntegerArgumentType.integer(ReplayLimits.MIN_DURATION_TICKS / 20, ReplayLimits.MAX_DURATION_TICKS / 20))
                            .executes(PlayerReplayCommand::setDuration)));
            record.then(literal("access")
                    .then(literal("public").executes(ctx -> setTaskAccess(ctx, ReplayAccess.PUBLIC)))
                    .then(literal("private").executes(ctx -> setTaskAccess(ctx, ReplayAccess.PRIVATE))));
            record.then(literal("pause").executes(PlayerReplayCommand::recordPause)
                    .then(argument("任务ID", StringArgumentType.string())
                            .executes(ctx -> recordPauseById(ctx, StringArgumentType.getString(ctx, "任务ID")))));
            record.then(literal("resume").executes(PlayerReplayCommand::recordResume)
                    .then(argument("任务ID", StringArgumentType.string())
                            .executes(ctx -> recordResumeById(ctx, StringArgumentType.getString(ctx, "任务ID")))));
            record.then(literal("stop").executes(PlayerReplayCommand::stopRecording)
                    .then(argument("任务ID", StringArgumentType.string())
                            .executes(ctx -> stopRecordingById(ctx, StringArgumentType.getString(ctx, "任务ID")))));
            record.then(literal("retry").executes(PlayerReplayCommand::retryRecording)
                    .then(argument("任务ID", StringArgumentType.string())
                            .executes(ctx -> retryRecordingById(ctx, StringArgumentType.getString(ctx, "任务ID")))));
            record.then(literal("leave").executes(PlayerReplayCommand::leaveRecording));
            record.then(literal("discard").executes(ctx -> discardRecording(ctx, false, null))
                    .then(literal("confirm")
                            .then(argument("任务ID", StringArgumentType.string())
                                    .executes(ctx -> discardRecording(ctx, true,
                                            StringArgumentType.getString(ctx, "任务ID"))))));
            root.then(record);
            // 兼容旧入口：on/stop/discard 仍可用，内部走同一任务与保存逻辑
            root.then(literal("on")
                    .executes(ctx -> prepare(ctx, null))
                    .then(argument("名称", StringArgumentType.string())
                            .executes(ctx -> prepare(ctx, StringArgumentType.getString(ctx, "名称")))));
            root.then(literal("stop").executes(PlayerReplayCommand::stopRecording));
            root.then(literal("discard")
                    .executes(ctx -> discardRecording(ctx, false, null))
                    .then(literal("confirm")
                            .then(argument("任务ID", StringArgumentType.string())
                                    .executes(ctx -> discardRecording(ctx, true,
                                            StringArgumentType.getString(ctx, "任务ID"))))));

            // ===== 邀请响应 =====
            var invite = literal("invite")
                    .then(literal("accept")
                            .then(argument("任务ID", StringArgumentType.string())
                                    .then(argument("令牌", StringArgumentType.string())
                                            .executes(ctx -> acceptInvite(ctx, true)))))
                    .then(literal("deny")
                            .then(argument("任务ID", StringArgumentType.string())
                                    .then(argument("令牌", StringArgumentType.string())
                                            .executes(ctx -> acceptInvite(ctx, false)))));
            root.then(invite);

            // ===== 幻影播放 =====
            var start = literal("start")
                    .requires(source -> phantomEnabled())
                    .executes(ctx -> startPlayback(ctx, null, false, false))
                    .then(argument("名称", StringArgumentType.string())
                            .executes(ctx -> startPlayback(ctx, StringArgumentType.getString(ctx, "名称"), false, false))
                            .then(literal("here")
                                    .executes(ctx -> startPlayback(ctx, StringArgumentType.getString(ctx, "名称"), true, false))));
            root.then(start);
            root.then(literal("startid")
                    .requires(source -> phantomEnabled())
                    .then(argument("录像ID", StringArgumentType.string())
                            .executes(ctx -> startPlaybackById(ctx, false, false))
                            .then(literal("here")
                                    .executes(ctx -> startPlaybackById(ctx, true, false)))));
            root.then(literal("loop")
                    .requires(source -> phantomEnabled())
                    .then(argument("名称", StringArgumentType.string())
                            .executes(ctx -> startPlayback(ctx, StringArgumentType.getString(ctx, "名称"), false, true))));
            root.then(literal("loopid")
                    .requires(source -> phantomEnabled())
                    .then(argument("录像ID", StringArgumentType.string())
                            .executes(ctx -> startPlaybackById(ctx, false, true))));
            root.then(literal("pause")
                    .requires(source -> phantomEnabled())
                    .then(argument("回放ID", IntegerArgumentType.integer())
                            .executes(ctx -> pauseResume(ctx, IntegerArgumentType.getInteger(ctx, "回放ID"), true))));
            root.then(literal("resume")
                    .requires(source -> phantomEnabled())
                    .then(argument("回放ID", IntegerArgumentType.integer())
                            .executes(ctx -> pauseResume(ctx, IntegerArgumentType.getInteger(ctx, "回放ID"), false))));
            root.then(literal("end")
                    .requires(source -> phantomEnabled())
                    .then(argument("回放ID", IntegerArgumentType.integer())
                            .executes(PlayerReplayCommand::endPlayback)));
            root.then(literal("playings")
                    .requires(source -> phantomEnabled())
                    .executes(PlayerReplayCommand::playingsPanel));
            root.then(literal("endall")
                    .requires(source -> phantomEnabled())
                    .executes(PlayerReplayCommand::endAll));

            // ===== 列表/详情/管理 =====
            var list = literal("list").executes(ctx -> listPanel(ctx, "all", 1));
            list.then(argument("页", IntegerArgumentType.integer(1))
                    .executes(ctx -> listPanel(ctx, "all", IntegerArgumentType.getInteger(ctx, "页"))));
            list.then(literal("mine").executes(ctx -> listPanel(ctx, "mine", 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> listPanel(ctx, "mine", IntegerArgumentType.getInteger(ctx, "页")))));
            root.then(list);
            var favlist = literal("favlist").executes(ctx -> listPanel(ctx, "fav", 1));
            favlist.then(argument("页", IntegerArgumentType.integer(1))
                    .executes(ctx -> listPanel(ctx, "fav", IntegerArgumentType.getInteger(ctx, "页"))));
            root.then(favlist);

            var infoId = argument("录像ID", StringArgumentType.string())
                    .executes(ctx -> infoPanel(ctx, StringArgumentType.getString(ctx, "录像ID"), "all", 1));
            infoId.then(argument("来源", StringArgumentType.string())
                    .executes(ctx -> infoPanel(ctx, StringArgumentType.getString(ctx, "录像ID"),
                            StringArgumentType.getString(ctx, "来源"), 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> infoPanel(ctx, StringArgumentType.getString(ctx, "录像ID"),
                                    StringArgumentType.getString(ctx, "来源"),
                                    IntegerArgumentType.getInteger(ctx, "页")))));
            root.then(literal("info").then(infoId));

            var renameId = argument("录像ID", StringArgumentType.string())
                    .then(argument("新名称", StringArgumentType.string())
                            .executes(ctx -> renameReplay(ctx, StringArgumentType.getString(ctx, "录像ID"),
                                    StringArgumentType.getString(ctx, "新名称"), "all", 1)));
            renameId.then(argument("来源", StringArgumentType.string())
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .then(argument("新名称", StringArgumentType.string())
                                    .executes(ctx -> renameReplay(ctx, StringArgumentType.getString(ctx, "录像ID"),
                                            StringArgumentType.getString(ctx, "新名称"),
                                            StringArgumentType.getString(ctx, "来源"),
                                            IntegerArgumentType.getInteger(ctx, "页"))))));
            root.then(literal("rename").then(renameId));

            var accessId = argument("录像ID", StringArgumentType.string())
                    .then(literal("public").executes(ctx -> accessReplay(ctx, StringArgumentType.getString(ctx, "录像ID"),
                            ReplayAccess.PUBLIC, "all", 1)))
                    .then(literal("private").executes(ctx -> accessReplay(ctx, StringArgumentType.getString(ctx, "录像ID"),
                            ReplayAccess.PRIVATE, "all", 1)))
                    .then(argument("来源", StringArgumentType.string())
                            .then(argument("页", IntegerArgumentType.integer(1))
                                    .then(literal("public").executes(ctx -> accessReplay(ctx,
                                            StringArgumentType.getString(ctx, "录像ID"), ReplayAccess.PUBLIC,
                                            StringArgumentType.getString(ctx, "来源"),
                                            IntegerArgumentType.getInteger(ctx, "页"))))
                                    .then(literal("private").executes(ctx -> accessReplay(ctx,
                                            StringArgumentType.getString(ctx, "录像ID"), ReplayAccess.PRIVATE,
                                            StringArgumentType.getString(ctx, "来源"),
                                            IntegerArgumentType.getInteger(ctx, "页"))))));
            root.then(literal("access").then(accessId));

            var favId = argument("录像ID", StringArgumentType.string())
                    .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), true, "all", 1));
            favId.then(argument("来源", StringArgumentType.string())
                    .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), true,
                            StringArgumentType.getString(ctx, "来源"), 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), true,
                                    StringArgumentType.getString(ctx, "来源"),
                                    IntegerArgumentType.getInteger(ctx, "页")))));
            root.then(literal("fav").then(favId));
            var unfavId = argument("录像ID", StringArgumentType.string())
                    .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), false, "all", 1));
            unfavId.then(argument("来源", StringArgumentType.string())
                    .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), false,
                            StringArgumentType.getString(ctx, "来源"), 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> favorite(ctx, StringArgumentType.getString(ctx, "录像ID"), false,
                                    StringArgumentType.getString(ctx, "来源"),
                                    IntegerArgumentType.getInteger(ctx, "页")))));
            root.then(literal("unfav").then(unfavId));

            var deleteId = argument("录像ID", StringArgumentType.string())
                    .executes(ctx -> deleteConfirm(ctx, StringArgumentType.getString(ctx, "录像ID"), "all", 1));
            deleteId.then(argument("来源", StringArgumentType.string())
                    .executes(ctx -> deleteConfirm(ctx, StringArgumentType.getString(ctx, "录像ID"),
                            StringArgumentType.getString(ctx, "来源"), 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> deleteConfirm(ctx, StringArgumentType.getString(ctx, "录像ID"),
                                    StringArgumentType.getString(ctx, "来源"),
                                    IntegerArgumentType.getInteger(ctx, "页")))));
            var delConfirm = literal("confirm").executes(ctx -> deleteExec(ctx,
                    StringArgumentType.getString(ctx, "录像ID"), "all", 1));
            delConfirm.then(argument("来源", StringArgumentType.string())
                    .executes(ctx -> deleteExec(ctx, StringArgumentType.getString(ctx, "录像ID"),
                            StringArgumentType.getString(ctx, "来源"), 1))
                    .then(argument("页", IntegerArgumentType.integer(1))
                            .executes(ctx -> deleteExec(ctx, StringArgumentType.getString(ctx, "录像ID"),
                                    StringArgumentType.getString(ctx, "来源"),
                                    IntegerArgumentType.getInteger(ctx, "页")))));
            deleteId.then(delConfirm);
            root.then(literal("delete").then(deleteId));

            dispatcher.register(root);
        });
    }

    // ==================== 基础 ====================

    public static boolean anyReplayRuleEnabled()
    {
        return CarpetPrimaryuanSettings.playerReplay || CarpetPrimaryuanSettings.fakePlayerScript;
    }

    public static boolean phantomEnabled()
    {
        return CarpetPrimaryuanSettings.playerReplay;
    }

    public static boolean hasLevel2(CommandSourceStack source)
    {
        //#if MC <= 12110
        //$$ return source.hasPermission(2);
        //#else
        return net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //#endif
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx)
    {
        try
        {
            return ctx.getSource().getPlayerOrException();
        }
        catch (Exception e)
        {
            ctx.getSource().sendFailure(Component.literal("仅玩家可用"));
            return null;
        }
    }

    private static String fmtDuration(int ticks)
    {
        int s = ticks / 20;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String accessName(ReplayAccess access)
    {
        return access == ReplayAccess.PRIVATE ? "私有" : "公开";
    }

    private static boolean admin(ServerPlayer player)
    {
        return ReplayPermissions.isAdmin(player);
    }

    private static RecordingTask ownedTask(ServerPlayer player)
    {
        return ReplayRecorder.taskForOwner(player.getStringUUID());
    }

    private static RecordingTask manageTask(ServerPlayer player, String taskId)
    {
        RecordingTask task = ReplayRecorder.taskById(parseUuid(taskId));
        if (task == null || !ReplayPermissions.canManage(task.ownerUuid(), player.getStringUUID(), admin(player)))
        {
            return null;
        }
        return task;
    }

    private static UUID parseUuid(String value)
    {
        try
        {
            return UUID.fromString(value);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // ==================== 主面板 ====================

    private static int mainPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        sendMainPanel(player, false);
        return 1;
    }

    static void sendMainPanel(ServerPlayer player, boolean fresh)
    {
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            player.sendSystemMessage(ReplayUi.text("玩家回放", ChatFormatting.BOLD));
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("开始录制", "/playereplay record", "先选择录制对象，再开始采集", ChatFormatting.GREEN),
                    " ",
                    ReplayUi.button("录像列表", "/playereplay list", null, ChatFormatting.AQUA)));
            return;
        }
        if (task.preparing())
        {
            sendPreparePanel(player, task);
            return;
        }
        sendRecordingPanel(player, task, fresh);
    }

    /** 供 /playerscript record 复用同一准备流程 */
    public static void sendPreparePanel(ServerPlayer player, RecordingTask task)
    {
        player.sendSystemMessage(ReplayUi.text("准备录制", ChatFormatting.BOLD));
        String self = task.roster.selfSelected() ? "自己" : "（未选择自己）";
        player.sendSystemMessage(ReplayUi.line(
                "录制对象：", ReplayUi.text(self, ChatFormatting.WHITE), " ",
                ReplayUi.button("修改", "/playereplay record objects", "邀请或添加在线玩家", ChatFormatting.YELLOW)));
        List<RecordingRoster.Entry> joined = new ArrayList<>();
        List<RecordingRoster.Entry> accepted = new ArrayList<>();
        List<RecordingRoster.Entry> invited = new ArrayList<>();
        for (RecordingRoster.Entry e : task.roster.entries())
        {
            switch (e.state)
            {
                case JOINED -> joined.add(e);
                case ACCEPTED -> accepted.add(e);
                case INVITED -> invited.add(e);
                default -> { }
            }
        }
        if (!joined.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("已加入：" + names(joined)));
        }
        if (!accepted.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("已同意：" + names(accepted)));
        }
        if (!invited.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("等待回复：" + names(invited) + "（开始时未回复的邀请失效）"));
        }
        player.sendSystemMessage(ReplayUi.line(
                ReplayUi.button("开始", "/playereplay record begin", "只采集已加入者", ChatFormatting.GREEN),
                " ",
                ReplayUi.button("更多设置", "/playereplay record settings", "录制时长与访问范围", ChatFormatting.YELLOW),
                " ",
                ReplayUi.button("返回", "/playereplay panel", null, ChatFormatting.GRAY)));
    }

    private static String names(List<RecordingRoster.Entry> entries)
    {
        StringBuilder sb = new StringBuilder();
        for (RecordingRoster.Entry e : entries)
        {
            if (sb.length() > 0)
            {
                sb.append("、");
            }
            sb.append(e.name);
        }
        return sb.toString();
    }

    private static void sendRecordingPanel(ServerPlayer player, RecordingTask task, boolean fresh)
    {
        if (task.saving())
        {
            player.sendSystemMessage(ReplayUi.text("正在保存：" + task.name(), ChatFormatting.YELLOW));
            player.sendSystemMessage(ReplayUi.line(ReplayUi.gray("请稍候，保存结果会单独提示")));
            return;
        }
        if (task.failed())
        {
            player.sendSystemMessage(ReplayUi.text("保存失败：" + task.name(), ChatFormatting.RED));
            player.sendSystemMessage(ReplayUi.gray("原因：" + (task.failure() == null ? "未知" : task.failure())
                    + "；数据仍保留，可重试"));
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("重试保存", "/playereplay record retry", null, ChatFormatting.GREEN),
                    " ",
                    ReplayUi.button("放弃", "/playereplay record discard", "需二次确认", ChatFormatting.RED)));
            return;
        }
        String title = task.paused() ? "录制已暂停：" : (fresh ? "录制已开始：" : "正在录制：");
        player.sendSystemMessage(ReplayUi.text(title + task.name(), ChatFormatting.BOLD));
        player.sendSystemMessage(ReplayUi.line("录制对象：", ReplayUi.gray(task.participantSummary())));
        if (task.paused())
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("继续", "/playereplay record resume", "重新同步输入状态并继续", ChatFormatting.GREEN),
                    " ",
                    ReplayUi.button("结束并保存", "/playereplay record stop", null, ChatFormatting.YELLOW),
                    " ",
                    ReplayUi.button("放弃", "/playereplay record discard", "需二次确认", ChatFormatting.RED)));
        }
        else
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("暂停", "/playereplay record pause", "冻结共同时间轴", ChatFormatting.YELLOW),
                    " ",
                    ReplayUi.button("结束并保存", "/playereplay record stop", null, ChatFormatting.GREEN),
                    " ",
                    ReplayUi.button("放弃", "/playereplay record discard", "需二次确认", ChatFormatting.RED)));
        }
        player.sendSystemMessage(ReplayUi.line(
                ReplayUi.button("录制对象", "/playereplay record objects", "录制中仍可邀请；接受后从加入时刻录制",
                        ChatFormatting.AQUA),
                " ",
                ReplayUi.button("设置", "/playereplay record settings", null, ChatFormatting.AQUA)));
    }

    // ==================== 准备与邀请 ====================

    private static int prepare(CommandContext<CommandSourceStack> ctx, String name)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        String generated = name;
        if (generated == null)
        {
            generated = AUTO_NAME.format(java.time.LocalDateTime.now());
            MinecraftServer server = ctx.getSource().getServer();
            int suffix = 1;
            while (ReplayStorage.nameExists(server, player.getStringUUID(), generated, null))
            {
                suffix++;
                generated = AUTO_NAME.format(java.time.LocalDateTime.now()) + "-" + suffix;
            }
        }
        if (!ReplayNames.isValid(generated))
        {
            ctx.getSource().sendFailure(Component.literal("名称只能包含中英文、数字、下划线、连字符，1~32 字符"));
            return 0;
        }
        if (ReplayRecorder.taskForOwner(player.getStringUUID()) != null)
        {
            sendMainPanel(player, false);
            return 1;
        }
        RecordingTask task = ReplayRecorder.prepare(player, generated);
        sendPreparePanel(player, task);
        return 1;
    }

    private static int startTask(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有准备中的录制"));
            return 0;
        }
        String error = ReplayRecorder.start(task, ctx.getSource().getServer());
        if (error != null)
        {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        sendMainPanel(player, true);
        String limitation = ReplayRecorder.captureLimitation();
        if (limitation != null && CarpetPrimaryuanSettings.fakePlayerScript)
        {
            player.sendSystemMessage(ReplayUi.text(limitation, ChatFormatting.YELLOW));
        }
        return 1;
    }

    private static int objectsPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        sendObjectsPanel(player, ctx.getSource().getServer(), task);
        return 1;
    }

    private static void sendObjectsPanel(ServerPlayer player, MinecraftServer server, RecordingTask task)
    {
        player.sendSystemMessage(ReplayUi.text("录制对象", ChatFormatting.BOLD));
        boolean isAdmin = admin(player);
        for (RecordingRoster.Entry e : task.roster.entries())
        {
            String state = switch (e.state)
            {
                case JOINED -> "已加入";
                case ACCEPTED -> "已同意";
                case INVITED -> "等待回复";
                case DECLINED -> "已拒绝";
                case LEFT -> "已退出";
            };
            var line = ReplayUi.line(ReplayUi.text(e.name, ChatFormatting.WHITE), ReplayUi.gray("（" + state + "）"));
            if (e.state == RecordingRoster.State.INVITED)
            {
                line.append(" ").append(ReplayUi.button("取消邀请",
                        "/playereplay record uninvite " + e.uuid, null, ChatFormatting.YELLOW));
            }
            if (task.preparing() && e.joinMethod == ReplayParticipant.JoinMethod.OWNER)
            {
                line.append(" ").append(ReplayUi.button("移除自己",
                        "/playereplay record self off", "管理员可只录其他人", ChatFormatting.RED));
            }
            player.sendSystemMessage(line);
        }
        if (task.preparing() && !task.roster.selfSelected())
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("加入自己", "/playereplay record self on", null, ChatFormatting.GREEN)));
        }
        player.sendSystemMessage(ReplayUi.gray("可邀请的在线玩家："));
        List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
        online.sort(Comparator.comparing(p -> p.getName().getString()));
        int shown = 0;
        for (ServerPlayer target : online)
        {
            if (target == player && task.roster.get(player.getStringUUID()) != null)
            {
                continue;
            }
            RecordingRoster.Entry entry = task.roster.get(target.getStringUUID());
            if (entry != null)
            {
                continue;
            }
            if (shown++ >= 12)
            {
                break;
            }
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.text(target.getName().getString(), ChatFormatting.WHITE), " ",
                    ReplayUi.button(isAdmin ? "直接添加" : "邀请",
                            "/playereplay record invite " + target.getName().getString(),
                            isAdmin ? "管理员直接录制，无需对方同意；对方会收到通知"
                                    : "对方同意后加入；开始只采集已加入者",
                            isAdmin ? ChatFormatting.RED : ChatFormatting.GREEN)));
        }
        player.sendSystemMessage(ReplayUi.line(ReplayUi.gray(" "),
                ReplayUi.button("返回", task.preparing() ? "/playereplay panel" : "/playereplay record settings",
                        null, ChatFormatting.GRAY)));
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        String targetName = StringArgumentType.getString(ctx, "玩家");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null)
        {
            ctx.getSource().sendFailure(Component.literal("玩家不在线: " + targetName));
            return 0;
        }
        if (target == player)
        {
            ctx.getSource().sendFailure(Component.literal("不能邀请自己，请用“移除自己/加入自己”"));
            return 0;
        }
        boolean isAdmin = admin(player);
        RecordingRoster.InviteResult result = ReplayRecorder.invite(task, player, target, isAdmin);
        String error = switch (result)
        {
            case OK -> null;
            case ALREADY_JOINED -> "该玩家已在录制对象中";
            case ALREADY_INVITED -> "已经邀请过，等待对方回复";
            case NO_SLOT -> "录制对象已达上限（" + ReplayLimits.MAX_PARTICIPANTS + " 人）";
            case NOT_ALLOWED -> "该玩家已退出，不能重复邀请";
            default -> "无法邀请该玩家";
        };
        if (error != null)
        {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        sendObjectsPanel(player, ctx.getSource().getServer(), task);
        return 1;
    }

    private static int uninvitePlayer(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        UUID target = parseUuid(StringArgumentType.getString(ctx, "玩家UUID"));
        if (target == null || !ReplayRecorder.cancelInvite(task, target.toString()))
        {
            ctx.getSource().sendFailure(Component.literal("没有可取消的邀请"));
            return 0;
        }
        sendObjectsPanel(player, ctx.getSource().getServer(), task);
        return 1;
    }

    private static int toggleSelf(CommandContext<CommandSourceStack> ctx, boolean on)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null || !ReplayRecorder.toggleSelf(task, on))
        {
            ctx.getSource().sendFailure(Component.literal("当前不能修改录制对象"));
            return 0;
        }
        sendObjectsPanel(player, ctx.getSource().getServer(), task);
        return 1;
    }

    private static int acceptInvite(CommandContext<CommandSourceStack> ctx, boolean accept)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        UUID taskId = parseUuid(StringArgumentType.getString(ctx, "任务ID"));
        UUID token = parseUuid(StringArgumentType.getString(ctx, "令牌"));
        if (taskId == null || token == null)
        {
            ctx.getSource().sendFailure(Component.literal("邀请链接已损坏，请让创作者重新邀请"));
            return 0;
        }
        String error = accept
                ? ReplayRecorder.acceptInvite(player, taskId, token, ctx.getSource().getServer())
                : ReplayRecorder.denyInvite(player, taskId, token);
        if (error != null)
        {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int settingsPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        sendSettingsPanel(player, task);
        return 1;
    }

    private static void sendSettingsPanel(ServerPlayer player, RecordingTask task)
    {
        player.sendSystemMessage(ReplayUi.text("录制设置", ChatFormatting.BOLD));
        player.sendSystemMessage(ReplayUi.line("录制时长：",
                ReplayUi.text(fmtDuration(task.configuredDurationTicks()), ChatFormatting.WHITE), " ",
                task.roster.settingsLocked()
                        ? ReplayUi.gray("（已锁定）")
                        : ReplayUi.suggest("修改", "/playereplay record duration ", "输入秒数回车", ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.line("访问范围：",
                ReplayUi.text(accessName(task.access()), ChatFormatting.WHITE), " ",
                task.roster.settingsLocked()
                        ? ReplayUi.gray("（邀请发出后锁定；保存后可在详情页修改）")
                        : ReplayUi.button("切换", "/playereplay record access "
                                + (task.access() == ReplayAccess.PUBLIC ? "private" : "public"),
                                "邀请会按此条件说明", ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.gray("达到时长自动结束并保存；邀请中说明的时长与访问条件与实际一致"));
        player.sendSystemMessage(ReplayUi.line(ReplayUi.gray(" "),
                ReplayUi.button("返回", "/playereplay panel", null, ChatFormatting.GRAY)));
    }

    private static int setDuration(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        int seconds = IntegerArgumentType.getInteger(ctx, "秒");
        if (task.setDuration(seconds) != RecordingRoster.InviteResult.OK)
        {
            ctx.getSource().sendFailure(Component.literal("邀请发出后录制时长已锁定"));
            return 0;
        }
        sendSettingsPanel(player, task);
        return 1;
    }

    private static int setTaskAccess(CommandContext<CommandSourceStack> ctx, ReplayAccess access)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ownedTask(player);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务"));
            return 0;
        }
        if (task.setAccess(access) != RecordingRoster.InviteResult.OK)
        {
            ctx.getSource().sendFailure(Component.literal("邀请发出后访问范围已锁定"));
            return 0;
        }
        sendSettingsPanel(player, task);
        return 1;
    }

    // ==================== 录制控制 ====================

    private static int recordPause(CommandContext<CommandSourceStack> ctx)
    {
        return recordPauseById(ctx, null);
    }

    private static int recordPauseById(CommandContext<CommandSourceStack> ctx, String taskId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = taskId == null ? ownedTask(player) : manageTask(player, taskId);
        if (task == null || !ReplayRecorder.pause(task))
        {
            ctx.getSource().sendFailure(Component.literal("当前不可暂停"));
            return 0;
        }
        sendMainPanel(player, false);
        return 1;
    }

    private static int recordResume(CommandContext<CommandSourceStack> ctx)
    {
        return recordResumeById(ctx, null);
    }

    private static int recordResumeById(CommandContext<CommandSourceStack> ctx, String taskId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = taskId == null ? ownedTask(player) : manageTask(player, taskId);
        if (task == null || !ReplayRecorder.resume(task))
        {
            ctx.getSource().sendFailure(Component.literal("当前不在暂停中"));
            return 0;
        }
        sendMainPanel(player, false);
        return 1;
    }

    private static int stopRecording(CommandContext<CommandSourceStack> ctx)
    {
        return stopRecordingById(ctx, null);
    }

    private static int stopRecordingById(CommandContext<CommandSourceStack> ctx, String taskId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = taskId == null ? ownedTask(player) : manageTask(player, taskId);
        if (task == null || (!task.running() && !task.failed()))
        {
            ctx.getSource().sendFailure(Component.literal("未在录制或无权管理"));
            return 0;
        }
        boolean accepted = ReplayRecorder.stopAndSave(ctx.getSource().getServer(), task, (ok, note) ->
        {
            if (ok)
            {
                player.sendSystemMessage(ReplayUi.line(
                        ReplayUi.text("保存成功", ChatFormatting.GREEN),
                        note == null ? null : ReplayUi.gray("（" + note + "）"),
                        " ",
                        ReplayUi.button("录像列表", "/playereplay list", null, ChatFormatting.AQUA)));
            }
            else
            {
                player.sendSystemMessage(ReplayUi.text("保存失败：" + (note == null ? "限额/写入" : note),
                        ChatFormatting.RED));
                player.sendSystemMessage(ReplayUi.line(
                        ReplayUi.button("重试保存", "/playereplay record retry", null, ChatFormatting.GREEN)));
            }
        });
        if (!accepted)
        {
            ctx.getSource().sendFailure(Component.literal(
                    "保存未能启动：" + (task.failure() == null ? "未知原因" : task.failure())));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("正在保存…"), false);
        return 1;
    }

    private static int retryRecording(CommandContext<CommandSourceStack> ctx)
    {
        return retryRecordingById(ctx, null);
    }

    private static int retryRecordingById(CommandContext<CommandSourceStack> ctx, String taskId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = taskId == null ? ownedTask(player) : manageTask(player, taskId);
        if (task == null || !task.failed())
        {
            ctx.getSource().sendFailure(Component.literal("没有可重试的保存任务或无权管理"));
            return 0;
        }
        boolean accepted = ReplayRecorder.stopAndSave(ctx.getSource().getServer(), task, (ok, note) ->
        {
            if (ok)
            {
                player.sendSystemMessage(ReplayUi.text("保存成功", ChatFormatting.GREEN));
            }
            else
            {
                player.sendSystemMessage(ReplayUi.text("重试失败：" + (note == null ? "写入失败" : note),
                        ChatFormatting.RED));
            }
        });
        if (!accepted)
        {
            ctx.getSource().sendFailure(Component.literal("重试未能启动"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("正在重试保存…"), false);
        return 1;
    }

    private static int discardRecording(CommandContext<CommandSourceStack> ctx, boolean confirmed,
                                        String expectedTaskId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = expectedTaskId == null ? ownedTask(player) : manageTask(player, expectedTaskId);
        if (task == null)
        {
            ctx.getSource().sendFailure(Component.literal("没有进行中的录制任务或无权管理"));
            return 0;
        }
        if (confirmed && (expectedTaskId == null || !task.id().toString().equals(expectedTaskId)))
        {
            ctx.getSource().sendFailure(Component.literal("录制已更换，原确认按钮已失效，请重新操作"));
            sendMainPanel(player, false);
            return 0;
        }
        if (!confirmed)
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.text("确认放弃本次录制？", ChatFormatting.RED), " ",
                    ReplayUi.gray("已采集内容将删除且不可恢复"), " ",
                    ReplayUi.button("确认放弃", "/playereplay record discard confirm " + task.id(),
                            null, ChatFormatting.RED),
                    " ",
                    ReplayUi.button("取消", "/playereplay panel", null, ChatFormatting.GRAY)));
            return 1;
        }
        ReplayRecorder.discard(task);
        sendMainPanel(player, false);
        return 1;
    }

    private static int leaveRecording(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ReplayRecorder.taskForParticipant(player.getStringUUID());
        if (task == null || task.preparing())
        {
            ctx.getSource().sendFailure(Component.literal("当前没有可退出的录制"));
            return 0;
        }
        if (!task.leave(player))
        {
            ctx.getSource().sendFailure(Component.literal("管理员直接添加的参与者不能主动退出"));
            return 0;
        }
        player.sendSystemMessage(ReplayUi.text("已退出录制；此前已录内容保留，重连不会自动续录",
                ChatFormatting.YELLOW));
        ReplayRecorder.notifyOwner(ctx.getSource().getServer(), task,
                player.getName().getString() + " 已退出录制");
        return 1;
    }

    // ==================== 播放 ====================

    private static int startPlayback(CommandContext<CommandSourceStack> ctx, String name, boolean here, boolean loop)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        List<ReplayStorage.ReplayEntry> entries = accessibleEntries(ctx.getSource().getServer(), player, "all");
        if (entries.isEmpty())
        {
            ctx.getSource().sendFailure(Component.literal("没有可访问的录像"));
            return 0;
        }
        ReplayStorage.ReplayEntry chosen = null;
        if (name == null)
        {
            chosen = entries.get(0);
        }
        else
        {
            for (ReplayStorage.ReplayEntry e : entries)
            {
                if (e.name().equals(name))
                {
                    chosen = e;
                    break;
                }
            }
        }
        if (chosen == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在或无权访问: " + name));
            return 0;
        }
        return doStart(ctx, player, chosen.recordingId(), here, loop);
    }

    private static int startPlaybackById(CommandContext<CommandSourceStack> ctx, boolean here, boolean loop)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        return doStart(ctx, player, StringArgumentType.getString(ctx, "录像ID"), here, loop);
    }

    private static int doStart(CommandContext<CommandSourceStack> ctx, ServerPlayer player,
                               String recordingId, boolean here, boolean loop)
    {
        ReplayData data = ReplayStorage.load(ctx.getSource().getServer(), recordingId);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在或损坏"));
            return 0;
        }
        if (!ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权访问该录像"));
            return 0;
        }
        if (here)
        {
            String dim = dimensionOf(player);
            boolean allSame = true;
            for (ReplayParticipant p : data.participants)
            {
                for (ReplayParticipant.Segment seg : p.segments)
                {
                    if (seg.visual.segments.stream().anyMatch(s -> !s.dimension.equals(dim)))
                    {
                        allSame = false;
                        break;
                    }
                }
            }
            if (!allSame)
            {
                ctx.getSource().sendFailure(Component.literal("here 仅支持与当前维度一致的录像"));
                return 0;
            }
        }
        var inst = PhantomEntityTracker.start(ctx.getSource().getServer(), data, data.metadata.name,
                player, here ? player : null, loop);
        if (inst == null)
        {
            ctx.getSource().sendFailure(Component.literal("回放实例已达上限"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "回放 #" + inst.playbackId + "（" + inst.name + "，" + inst.participantCount() + " 名参与者"
                        + (loop ? "，循环" : "") + "）"), false);
        return 1;
    }

    private static String dimensionOf(ServerPlayer p)
    {
        //#if MC >= 12111
        return p.level().dimension().identifier().toString();
        //#else
        //$$ return p.level().dimension().location().toString();
        //#endif
    }

    private static int pauseResume(CommandContext<CommandSourceStack> ctx, int id, boolean pause)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        var inst = PhantomEntityTracker.get(id);
        if (inst == null || !ReplayPermissions.canControl(inst.viewerUuid, player.getStringUUID(), admin(player))
                || !ReplayPermissions.canView(inst.replay.metadata.access, inst.replay.metadata.ownerUuid,
                player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("回放不存在或无权控制"));
            return 0;
        }
        boolean ok = pause ? PhantomEntityTracker.pause(id) : PhantomEntityTracker.resume(id);
        if (ok)
        {
            ctx.getSource().sendSuccess(() -> Component.literal((pause ? "已暂停 " : "已恢复 ") + id), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("回放状态不符: " + id));
        return 0;
    }

    private static int endPlayback(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(ctx, "回放ID");
        var inst = PhantomEntityTracker.get(id);
        if (inst == null || !ReplayPermissions.canControl(inst.viewerUuid, player.getStringUUID(), admin(player))
                || !ReplayPermissions.canView(inst.replay.metadata.access, inst.replay.metadata.ownerUuid,
                player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("回放不存在或无权控制"));
            return 0;
        }
        PhantomEntityTracker.end(ctx.getSource().getServer(), id);
        ctx.getSource().sendSuccess(() -> Component.literal("已结束 " + id), false);
        return 1;
    }

    private static int endAll(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        if (admin(player))
        {
            PhantomEntityTracker.shutdownAll();
            ctx.getSource().sendSuccess(() -> Component.literal("已结束全部回放"), false);
        }
        else
        {
            int before = PhantomEntityTracker.activeCount();
            PhantomEntityTracker.endForViewer(ctx.getSource().getServer(), player.getStringUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("已结束自己的回放"), false);
        }
        return 1;
    }

    private static int playingsPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        boolean isAdmin = admin(player);
        List<PhantomEntityTracker.PhantomInstance> list = isAdmin
                ? PhantomEntityTracker.listActive() : PhantomEntityTracker.listVisibleTo(player);
        player.sendSystemMessage(ReplayUi.text("运行中的回放", ChatFormatting.BOLD));
        if (list.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("（无）"));
        }
        for (var inst : list)
        {
            player.sendSystemMessage(ReplayUi.line(
                    "#" + inst.playbackId + " " + inst.name
                            + (isAdmin ? " · " + inst.viewerName : "") + " · ",
                    ReplayUi.gray(fmtDuration(inst.clock.tick) + " · " + inst.participantCount() + " 人"),
                    " ",
                    inst.clock.paused
                            ? ReplayUi.button("继续", "/playereplay resume " + inst.playbackId, null, ChatFormatting.GREEN)
                            : ReplayUi.button("暂停", "/playereplay pause " + inst.playbackId, null, ChatFormatting.YELLOW),
                    " ",
                    ReplayUi.button("结束", "/playereplay end " + inst.playbackId, null, ChatFormatting.RED)));
        }
        player.sendSystemMessage(ReplayUi.line(ReplayUi.gray(" "),
                ReplayUi.button("全部结束", "/playereplay endall", null, ChatFormatting.RED),
                " ",
                ReplayUi.button("刷新", "/playereplay playings", null, ChatFormatting.GRAY),
                " ",
                ReplayUi.button("返回", "/playereplay panel", null, ChatFormatting.GRAY)));
        return 1;
    }

    // ==================== 列表 ====================

    private static int listPanel(CommandContext<CommandSourceStack> ctx, String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        sendListPanel(player, ctx.getSource().getServer(), from, page);
        return 1;
    }

    private static List<ReplayStorage.ReplayEntry> accessibleEntries(MinecraftServer server, ServerPlayer player, String from)
    {
        boolean isAdmin = admin(player);
        List<ReplayStorage.ReplayEntry> all = new ArrayList<>();
        for (ReplayStorage.ReplayEntry e : ReplayStorage.listAll(server))
        {
            if (!ReplayPermissions.canView(e.access(), e.ownerUuid(), player.getStringUUID(), isAdmin))
            {
                continue;
            }
            if ("mine".equals(from) && !e.ownerUuid().equals(player.getStringUUID()))
            {
                continue;
            }
            all.add(e);
        }
        if ("fav".equals(from))
        {
            List<String> fav = ReplayFavorites.list(server, player.getStringUUID());
            if (fav == null)
            {
                return null;
            }
            all.removeIf(e -> !fav.contains(e.recordingId()));
        }
        return all;
    }

    private static void sendListPanel(ServerPlayer player, MinecraftServer server, String from, int page)
    {
        List<ReplayStorage.ReplayEntry> all = accessibleEntries(server, player, from);
        String title = switch (from)
        {
            case "fav" -> "收藏录像";
            case "mine" -> "我的录像";
            default -> "录像列表";
        };
        player.sendSystemMessage(ReplayUi.text(title, ChatFormatting.BOLD));
        if (all == null)
        {
            player.sendSystemMessage(ReplayUi.text("收藏读取失败，已保守显示为空；请管理员检查收藏文件",
                    ChatFormatting.RED));
            all = new ArrayList<>();
        }
        if (all.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("（没有录像）"));
        }
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int cur = Math.min(Math.max(page, 1), pages);
        for (int i = (cur - 1) * PAGE_SIZE; i < Math.min(cur * PAGE_SIZE, all.size()); i++)
        {
            ReplayStorage.ReplayEntry e = all.get(i);
            String click = "/playereplay info " + e.recordingId() + " " + from + " " + cur;
            String hover = "点击查看操作"
                    + "\n录制者: " + e.ownerName()
                    + "\n访问范围: " + accessName(e.access())
                    + (e.recovered() ? "\n状态: 异常恢复（可能缺少最后一段）" : "")
                    + "\n起点: " + shortDim(e.initialDimension())
                    + String.format(" %.1f %.1f %.1f", e.initialX(), e.initialY(), e.initialZ());
            player.sendSystemMessage(summaryLine(e, click, hover));
        }
        var nav = ReplayUi.line(ReplayUi.gray(" "));
        String listCmd = switch (from)
        {
            case "fav" -> "favlist";
            case "mine" -> "list mine";
            default -> "list";
        };
        appendNav(nav, all.isEmpty() ? null : "/playereplay " + listCmd + " " + Math.max(1, cur - 1),
                cur > 1, "上一页");
        appendNav(nav, all.isEmpty() ? null : "/playereplay " + listCmd + " " + Math.min(pages, cur + 1),
                cur < pages, "下一页");
        player.sendSystemMessage(nav);
        var filters = ReplayUi.line();
        if (!"all".equals(from))
        {
            filters.append(ReplayUi.button("全部", "/playereplay list 1", null, ChatFormatting.AQUA));
            filters.append(" ");
        }
        if (!"mine".equals(from))
        {
            filters.append(ReplayUi.button("只看我的", "/playereplay list mine 1", null, ChatFormatting.AQUA));
            filters.append(" ");
        }
        if (!"fav".equals(from))
        {
            filters.append(ReplayUi.button("收藏", "/playereplay favlist 1", null, ChatFormatting.AQUA));
            filters.append(" ");
        }
        if (CarpetPrimaryuanSettings.playerReplay)
        {
            filters.append(ReplayUi.button("运行中的回放(" + PhantomEntityTracker.activeCount() + ")",
                    "/playereplay playings", null, ChatFormatting.AQUA));
            filters.append(" ");
        }
        filters.append(ReplayUi.button("返回", "/playereplay panel", null, ChatFormatting.GRAY));
        player.sendSystemMessage(filters);
    }

    /** 统一摘要行：名称 · yyyy/MM/dd · HH:mm:ss · mm:ss */
    private static MutableComponent summaryLine(ReplayStorage.ReplayEntry e, String click, String hover)
    {
        Instant at = Instant.ofEpochMilli(e.createdAt());
        String date = DATE_FMT.format(at.atZone(ZoneId.systemDefault()));
        String time = TIME_FMT.format(at.atZone(ZoneId.systemDefault()));
        MutableComponent line = ReplayUi.line(
                ReplayUi.text(e.name(), ChatFormatting.WHITE), " · ",
                ReplayUi.gray(date + " · " + time + " · " + fmtDuration(e.durationTicks())));
        if (click != null || hover != null)
        {
            java.util.function.UnaryOperator<net.minecraft.network.chat.Style> decorate = style ->
            {
                net.minecraft.network.chat.Style s = style;
                if (click != null)
                {
                    s = s.withClickEvent(ReplayUi.runCommand(click));
                }
                if (hover != null)
                {
                    s = s.withHoverEvent(ReplayUi.showText(
                            Component.literal(hover).withStyle(ChatFormatting.GRAY)));
                }
                return s;
            };
            line.withStyle(decorate);
            for (net.minecraft.network.chat.Component child : line.getSiblings())
            {
                if (child instanceof MutableComponent mc)
                {
                    mc.withStyle(decorate);
                }
            }
        }
        return line;
    }

    private static void appendNav(MutableComponent line, String command, boolean active, String label)
    {
        if (active && command != null)
        {
            line.append(ReplayUi.button(label, command, null, ChatFormatting.AQUA));
        }
        else
        {
            line.append(Component.literal("[" + label + "]").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ==================== 详情 ====================

    private static int infoPanel(CommandContext<CommandSourceStack> ctx, String id, String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        sendInfoPanel(player, ctx.getSource().getServer(), id, from, page);
        return 1;
    }

    private static void sendInfoPanel(ServerPlayer player, MinecraftServer server,
                                      String id, String from, int page)
    {
        ReplayData data = ReplayStorage.load(server, id);
        if (data == null)
        {
            player.sendSystemMessage(ReplayUi.text("录像不存在或已损坏（请刷新列表）", ChatFormatting.RED));
            player.sendSystemMessage(ReplayUi.line(ReplayUi.button("返回列表",
                    "/playereplay " + listCommand(from) + " " + Math.max(1, page), null, ChatFormatting.GRAY)));
            return;
        }
        boolean isAdmin = admin(player);
        if (!ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), isAdmin))
        {
            player.sendSystemMessage(ReplayUi.text("无权访问该录像", ChatFormatting.RED));
            return;
        }
        ReplayStorage.ReplayEntry entry = ReplayStorage.entry(server, id);
        player.sendSystemMessage(ReplayUi.text("录像详情", ChatFormatting.BOLD));
        if (entry != null)
        {
            player.sendSystemMessage(summaryLine(entry, null, null));
        }
        else
        {
            player.sendSystemMessage(ReplayUi.line(ReplayUi.text(data.metadata.name, ChatFormatting.WHITE)));
        }
        player.sendSystemMessage(ReplayUi.line("录制者：",
                ReplayUi.gray(data.metadata.ownerName)));
        player.sendSystemMessage(ReplayUi.line("访问范围：",
                ReplayUi.text(accessName(data.metadata.access),
                        data.metadata.access == ReplayAccess.PUBLIC ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.line("参与者：",
                ReplayUi.gray(participantNames(data))));
        player.sendSystemMessage(ReplayUi.line("起点：", ReplayUi.gray(shortDim(data.metadata.initialDimension)
                + " · " + (int) data.metadata.initialX + " " + (int) data.metadata.initialY
                + " " + (int) data.metadata.initialZ)));
        if (data.metadata.recoveredFromCrash)
        {
            player.sendSystemMessage(ReplayUi.text("异常恢复：本录像只恢复到最近一次自动检查点，可能缺少最后一段",
                    ChatFormatting.RED));
        }
        List<String> favorites = ReplayFavorites.list(server, player.getStringUUID());
        boolean faved = favorites != null && favorites.contains(id);
        var actions = ReplayUi.line();
        if (CarpetPrimaryuanSettings.playerReplay)
        {
            actions.append(ReplayUi.button("播放", "/playereplay startid " + id,
                    "以你自己的独立观看实例播放幻影", ChatFormatting.GREEN));
        }
        if (CarpetPrimaryuanSettings.fakePlayerScript && isAdmin)
        {
            if (actions.getSiblings().size() > 0)
            {
                actions.append(" ");
            }
            actions.append(ReplayUi.button("假人执行", "/playerscript setup " + id,
                    "选择参与者后召唤假人执行其操作", ChatFormatting.AQUA));
        }
        if (actions.getSiblings().size() > 0)
        {
            actions.append(" ");
        }
        actions.append(faved
                ? ReplayUi.button("取消收藏", "/playereplay unfav " + id + " " + from + " " + page,
                "仅移除你自己的收藏引用", ChatFormatting.YELLOW)
                : ReplayUi.button("收藏", "/playereplay fav " + id + " " + from + " " + page,
                "收藏只保存引用，不授予管理权限", ChatFormatting.YELLOW));
        player.sendSystemMessage(actions);

        if (ReplayPermissions.canManage(data.metadata.ownerUuid, player.getStringUUID(), isAdmin))
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.suggest("改名", "/playereplay rename " + id + " " + from + " " + page + " ",
                            "点击后补齐新名称回车", ChatFormatting.YELLOW), " ",
                    ReplayUi.button(data.metadata.access == ReplayAccess.PUBLIC ? "设为私有" : "设为公开",
                            "/playereplay access " + id + " "
                                    + (data.metadata.access == ReplayAccess.PUBLIC ? "private" : "public")
                                    + " " + from + " " + page,
                            "保存后立即生效；旧按钮/收藏/设置页会重新校验", ChatFormatting.YELLOW), " ",
                    ReplayUi.button("删除", "/playereplay delete " + id + " " + from + " " + page,
                            "需二次确认，不可恢复", ChatFormatting.RED)));
        }
        player.sendSystemMessage(ReplayUi.line(ReplayUi.button("返回列表",
                "/playereplay " + listCommand(from) + " " + Math.max(1, page), null, ChatFormatting.GRAY)));
    }

    private static String participantNames(ReplayData data)
    {
        StringBuilder sb = new StringBuilder();
        for (ReplayParticipant p : data.participants)
        {
            if (sb.length() > 0)
            {
                sb.append("、");
            }
            sb.append(p.name);
        }
        return sb.length() == 0 ? "（无）" : sb.toString();
    }

    private static String listCommand(String from)
    {
        return switch (from)
        {
            case "fav" -> "favlist";
            case "mine" -> "list mine";
            default -> "list";
        };
    }

    private static String shortDim(String dimKey)
    {
        return switch (dimKey)
        {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimKey;
        };
    }

    // ==================== 管理 ====================

    private static int renameReplay(CommandContext<CommandSourceStack> ctx, String id,
                                    String newName, String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        ReplayData data = ReplayStorage.load(ctx.getSource().getServer(), id);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在"));
            return 0;
        }
        if (!ReplayPermissions.canManage(data.metadata.ownerUuid, player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权管理该录像"));
            return 0;
        }
        if (ReplayStorage.rename(ctx.getSource().getServer(), id, newName))
        {
            sendInfoPanel(player, ctx.getSource().getServer(), id, from, page);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("重命名失败（名称非法或同名录像已存在）"));
        return 0;
    }

    private static int accessReplay(CommandContext<CommandSourceStack> ctx, String id, ReplayAccess access,
                                    String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        ReplayData data = ReplayStorage.load(ctx.getSource().getServer(), id);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在"));
            return 0;
        }
        if (!ReplayPermissions.canManage(data.metadata.ownerUuid, player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权管理该录像"));
            return 0;
        }
        if (!ReplayStorage.setAccess(ctx.getSource().getServer(), id, access))
        {
            ctx.getSource().sendFailure(Component.literal("访问范围写入失败"));
            return 0;
        }
        sendInfoPanel(player, ctx.getSource().getServer(), id, from, page);
        return 1;
    }

    private static int deleteConfirm(CommandContext<CommandSourceStack> ctx, String id, String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        ReplayData data = ReplayStorage.load(ctx.getSource().getServer(), id);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在（可能已删除）"));
            return 0;
        }
        if (!ReplayPermissions.canManage(data.metadata.ownerUuid, player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权删除该录像"));
            return 0;
        }
        player.sendSystemMessage(ReplayUi.line(
                ReplayUi.text("确认删除录像 " + data.metadata.name + "？", ChatFormatting.RED),
                ReplayUi.gray("（不可恢复，收藏引用会失效）")));
        player.sendSystemMessage(ReplayUi.line(
                ReplayUi.button("确认删除", "/playereplay delete " + id + " confirm " + from + " " + page,
                        null, ChatFormatting.RED), " ",
                ReplayUi.button("取消", "/playereplay info " + id + " " + from + " " + page,
                        null, ChatFormatting.GRAY)));
        return 1;
    }

    private static int deleteExec(CommandContext<CommandSourceStack> ctx, String id, String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ReplayData data = ReplayStorage.load(server, id);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在（可能已删除）"));
            return 0;
        }
        if (!ReplayPermissions.canManage(data.metadata.ownerUuid, player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权删除该录像"));
            return 0;
        }
        if (ReplayStorage.delete(server, id))
        {
            ReplayFavorites.remove(server, player.getStringUUID(), id);
            sendListPanel(player, server, from, page);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("删除失败"));
        return 0;
    }

    private static int favorite(CommandContext<CommandSourceStack> ctx, String id, boolean add,
                                String from, int page)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ReplayData data = ReplayStorage.load(server, id);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在（可能已删除）"));
            return 0;
        }
        if (!ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), admin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权收藏该录像"));
            return 0;
        }
        boolean ok = add
                ? ReplayFavorites.add(server, player.getStringUUID(), id)
                : ReplayFavorites.remove(server, player.getStringUUID(), id);
        if (!ok)
        {
            ctx.getSource().sendFailure(Component.literal(add ? "收藏写入失败" : "取消收藏写入失败"));
            return 0;
        }
        sendInfoPanel(player, server, id, from, page);
        return 1;
    }
}
