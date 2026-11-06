package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayNames;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.ReplayPermissions;
import me.primaryuan.carpet.replay.recorder.RecordingTask;
import me.primaryuan.carpet.replay.recorder.ReplayRecorder;
import me.primaryuan.carpet.replay.script.CarpetScriptSession;
import me.primaryuan.carpet.replay.script.ReplayScriptException;
import me.primaryuan.carpet.replay.script.ReplayScriptExtractor;
import me.primaryuan.carpet.replay.script.ScriptScheduler;
import me.primaryuan.carpet.replay.storage.ReplayStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /playerscript：把录像中某位参与者的操作交给自动召唤的 Carpet 假人重演。
 * 设置按操作者、录像 ID、参与者 UUID 隔离；多人录像先选参与者。
 * 自动召唤假人结束自动下线；外部绑定只解除控制。
 */
public final class PlayerScriptCommand
{
    private static final DateTimeFormatter AUTO_NAME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 面板设置态（会话级内存）：(玩家 UUID, 录像 ID, 参与者 UUID) → 设置 */
    private static final Map<SetupKey, Setup> SETUPS = new ConcurrentHashMap<>();
    /** 当前为哪份录像选择了哪名参与者（仅 UI 选择态，不属于执行设置） */
    private static final Map<SelectKey, String> SELECTED = new ConcurrentHashMap<>();

    private record SetupKey(UUID player, String recordingId, String participantUuid) {}

    private record SelectKey(UUID player, String recordingId) {}

    private static final class Setup
    {
        String bot;
        boolean botSetByUser;
        int count = 1;
        boolean infinite;
        boolean roundReset;
    }

    private PlayerScriptCommand() {}

    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        {
            var setup = literal("setup")
                    .then(argument("录像ID", StringArgumentType.string())
                            .executes(ctx -> setupPanel(ctx, StringArgumentType.getString(ctx, "录像ID")))
                            .then(literal("participant")
                                    .then(argument("参与者UUID", StringArgumentType.string())
                                            .executes(ctx -> setupParticipant(ctx,
                                                    StringArgumentType.getString(ctx, "录像ID"),
                                                    StringArgumentType.getString(ctx, "参与者UUID")))))
                            .then(literal("bot")
                                    .then(argument("假人名", StringArgumentType.string())
                                            .executes(PlayerScriptCommand::setupBot)))
                            .then(literal("count")
                                    .then(argument("次数", IntegerArgumentType.integer(1))
                                            .executes(PlayerScriptCommand::setupCount)))
                            .then(literal("inf").executes(PlayerScriptCommand::setupInf))
                            .then(literal("mode")
                                    .then(literal("reset").executes(ctx -> setupMode(ctx, true)))
                                    .then(literal("continue").executes(ctx -> setupMode(ctx, false)))));
            dispatcher.register(literal("playerscript")
                    .requires(source -> CarpetPrimaryuanSettings.fakePlayerScript
                            && PlayerReplayCommand.hasLevel2(source))
                    .executes(PlayerScriptCommand::mainPanel)
                    .then(literal("panel").executes(PlayerScriptCommand::mainPanel))
                    .then(literal("record")
                            .executes(ctx -> record(ctx, null))
                            .then(argument("名称", StringArgumentType.string())
                                    .executes(ctx -> record(ctx, StringArgumentType.getString(ctx, "名称"))))
                            .then(literal("stop").executes(PlayerScriptCommand::recordStop)))
                    .then(setup)
                    .then(literal("run")
                            .then(argument("录像ID", StringArgumentType.string())
                                    .executes(ctx -> runById(ctx, false))
                                    //#if MC >= 12103
                                    // 现代版本输入完整，无需 force
                                    //#else
                                    //$$ .then(literal("force").executes(ctx -> runById(ctx, true)))
                                    //#endif
                            )
                            .then(argument("名称", StringArgumentType.string())
                                    .then(literal("as")
                                            .then(argument("假人", StringArgumentType.string())
                                                    .executes(ctx -> runExternal(ctx,
                                                            StringArgumentType.getString(ctx, "名称"),
                                                            StringArgumentType.getString(ctx, "假人"), false))
                                                    //#if MC >= 12103
                                                    // 现代版本输入完整，无需 force
                                                    //#else
                                                    //$$ .then(literal("force")
                                                    //$$         .executes(ctx -> runExternal(ctx,
                                                    //$$                 StringArgumentType.getString(ctx, "名称"),
                                                    //$$                 StringArgumentType.getString(ctx, "假人"), true)))
                                                    //#endif
                                            ))))
                    .then(literal("pause")
                            .then(argument("假人", StringArgumentType.string())
                                    .executes(ctx -> pauseResume(ctx, true))))
                    .then(literal("resume")
                            .then(argument("假人", StringArgumentType.string())
                                    .executes(ctx -> pauseResume(ctx, false))))
                    .then(literal("stop")
                            .then(argument("假人", StringArgumentType.string())
                                    .executes(PlayerScriptCommand::stopScript)))
                    .then(literal("status")
                            .then(argument("假人", StringArgumentType.string())
                                    .executes(PlayerScriptCommand::status)))
                    .then(literal("scripts").executes(PlayerScriptCommand::scriptsPanel))
                    .then(literal("list").executes(PlayerScriptCommand::list)));
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> clearSetups(handler.getPlayer().getUUID()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server ->
                {
                    SETUPS.clear();
                    SELECTED.clear();
                });
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

    private static Setup setup(ServerPlayer player, String recordingId)
    {
        String participant = SELECTED.getOrDefault(new SelectKey(player.getUUID(), recordingId), "");
        return SETUPS.computeIfAbsent(new SetupKey(player.getUUID(), recordingId, participant), key ->
        {
            Setup s = new Setup();
            s.bot = ScriptScheduler.nextFreeBotName();
            return s;
        });
    }

    public static void clearSetups(UUID playerId)
    {
        SETUPS.keySet().removeIf(key -> key.player().equals(playerId));
        SELECTED.keySet().removeIf(key -> key.player().equals(playerId));
    }

    // ==================== 主面板 ====================

    private static int mainPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        sendMainPanel(player);
        return 1;
    }

    private static void sendMainPanel(ServerPlayer player)
    {
        RecordingTask task = ReplayRecorder.taskForOwner(player.getStringUUID());
        player.sendSystemMessage(ReplayUi.text("假人脚本", ChatFormatting.BOLD));
        if (task == null)
        {
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("开始录制", "/playereplay record", "先进入准备页选择录制对象",
                            ChatFormatting.GREEN), " ",
                    ReplayUi.button("录像列表", "/playereplay list", null, ChatFormatting.AQUA), " ",
                    ReplayUi.button("运行中的脚本(" + ScriptScheduler.activeCount() + ")",
                            "/playerscript scripts", null, ChatFormatting.AQUA)));
        }
        else if (task.preparing())
        {
            PlayerReplayCommand.sendPreparePanel(player, task);
        }
        else
        {
            player.sendSystemMessage(ReplayUi.line("录制：", task.name(), " · ",
                    ReplayUi.gray(fmtDuration(task.tick()))));
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.button("结束并保存", "/playereplay record stop", null, ChatFormatting.GREEN)));
        }
    }

    // ==================== 录制（与玩家回放共用准备流程） ====================

    private static int record(CommandContext<CommandSourceStack> ctx, String name)
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
        RecordingTask task = ReplayRecorder.taskForOwner(player.getStringUUID());
        if (task == null)
        {
            task = ReplayRecorder.prepare(player, generated);
        }
        PlayerReplayCommand.sendPreparePanel(player, task);
        return 1;
    }

    private static int recordStop(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        RecordingTask task = ReplayRecorder.taskForOwner(player.getStringUUID());
        if (task == null || (!task.running() && !task.failed()))
        {
            ctx.getSource().sendFailure(Component.literal("未在录制"));
            return 0;
        }
        boolean accepted = ReplayRecorder.stopAndSave(ctx.getSource().getServer(), task, (ok, note) ->
        {
            if (ok)
            {
                player.sendSystemMessage(ReplayUi.line(
                        ReplayUi.text("保存成功", ChatFormatting.GREEN),
                        note == null ? null : ReplayUi.gray("（" + note + "）"), " ",
                        ReplayUi.button("打开假人脚本", "/playerscript panel", null, ChatFormatting.AQUA)));
            }
            else
            {
                player.sendSystemMessage(ReplayUi.text("保存失败：" + (note == null ? "写入失败" : note),
                        ChatFormatting.RED));
            }
        });
        if (!accepted)
        {
            ctx.getSource().sendFailure(Component.literal("保存未能启动"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("正在保存…"), false);
        return 1;
    }

    // ==================== 设置页/参与者选择 ====================

    private static int setupPanel(CommandContext<CommandSourceStack> ctx, String recordingId)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        ReplayData data = loadAccessible(ctx, player, recordingId);
        if (data == null)
        {
            return 0;
        }
        List<ReplayParticipant> selectable = ReplayScriptExtractor.selectable(data);
        if (selectable.isEmpty())
        {
            ctx.getSource().sendFailure(Component.literal("录像没有可执行的参与者轨道"));
            return 0;
        }
        if (selectable.size() > 1)
        {
            sendParticipantPanel(player, recordingId, selectable);
            return 1;
        }
        SELECTED.put(new SelectKey(player.getUUID(), recordingId), selectable.get(0).uuid);
        return renderSetup(player, ctx.getSource().getServer(), recordingId) ? 1 : 0;
    }

    private static int setupParticipant(CommandContext<CommandSourceStack> ctx, String recordingId, String participantUuid)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        ReplayData data = loadAccessible(ctx, player, recordingId);
        if (data == null)
        {
            return 0;
        }
        ReplayParticipant p = data.participant(participantUuid);
        if (p == null || !p.hasContent())
        {
            ctx.getSource().sendFailure(Component.literal("录像中没有该参与者或没有有效轨道"));
            return 0;
        }
        SELECTED.put(new SelectKey(player.getUUID(), recordingId), participantUuid);
        return renderSetup(player, ctx.getSource().getServer(), recordingId) ? 1 : 0;
    }

    private static void sendParticipantPanel(ServerPlayer player, String recordingId, List<ReplayParticipant> participants)
    {
        player.sendSystemMessage(ReplayUi.text("选择回放参与者", ChatFormatting.BOLD));
        for (ReplayParticipant p : participants)
        {
            int ticks = 0;
            for (ReplayParticipant.Segment seg : p.segments)
            {
                ticks += Math.max(0, seg.duration());
            }
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.text(p.name, ChatFormatting.WHITE), ReplayUi.gray(" · " + fmtDuration(ticks)), " ",
                    ReplayUi.button("选择", "/playerscript setup " + recordingId + " participant " + p.uuid,
                            "只提取该参与者的在线片段", ChatFormatting.GREEN)));
        }
        player.sendSystemMessage(ReplayUi.line(ReplayUi.button("返回", "/playereplay info " + recordingId,
                null, ChatFormatting.GRAY)));
    }

    private static ReplayData loadAccessible(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String recordingId)
    {
        ReplayData data = ReplayStorage.load(ctx.getSource().getServer(), recordingId);
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在（可能已删除，请刷新面板）"));
            return null;
        }
        if (!ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), ReplayPermissions.isAdmin(player)))
        {
            ctx.getSource().sendFailure(Component.literal("无权访问该录像"));
            return null;
        }
        return data;
    }

    private static boolean renderSetup(ServerPlayer player, MinecraftServer server, String recordingId)
    {
        ReplayData data = ReplayStorage.load(server, recordingId);
        if (data == null)
        {
            player.sendSystemMessage(ReplayUi.text("录像不存在（可能已删除，请刷新面板）", ChatFormatting.RED));
            return false;
        }
        if (!ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), ReplayPermissions.isAdmin(player)))
        {
            player.sendSystemMessage(ReplayUi.text("无权访问该录像", ChatFormatting.RED));
            return false;
        }
        String participantUuid = SELECTED.getOrDefault(new SelectKey(player.getUUID(), recordingId), "");
        ReplayParticipant participant = data.participant(participantUuid);
        if (participant == null)
        {
            player.sendSystemMessage(ReplayUi.text("参与者选择已失效，请重新选择", ChatFormatting.RED));
            return false;
        }
        Setup s = setup(player, recordingId);
        if (s.bot == null || (!s.botSetByUser
                && (ScriptScheduler.isNameTaken(s.bot)
                || server.getPlayerList().getPlayerByName(s.bot) != null)))
        {
            s.bot = ScriptScheduler.nextFreeBotName(
                    candidate -> server.getPlayerList().getPlayerByName(candidate) != null);
        }
        int captured = 0;
        for (ReplayParticipant.Segment seg : participant.segments)
        {
            captured += Math.max(0, seg.duration());
        }
        sendSetupPanel(player, recordingId, data.metadata.name, participant, captured, s, data.participants.size() > 1);
        return true;
    }

    private static void sendSetupPanel(ServerPlayer player, String recordingId, String name,
                                       ReplayParticipant participant, int capturedTicks, Setup s, boolean multi)
    {
        player.sendSystemMessage(ReplayUi.text("假人脚本", ChatFormatting.BOLD).copy()
                .append(ReplayUi.gray(" · " + name + " · " + fmtDuration(capturedTicks))));
        player.sendSystemMessage(ReplayUi.line("参与者：",
                ReplayUi.text(participant.name, ChatFormatting.WHITE),
                multi ? ReplayUi.button("切换", "/playerscript setup " + recordingId,
                        "多人录像先选参与者", ChatFormatting.YELLOW) : ReplayUi.gray("")));
        player.sendSystemMessage(ReplayUi.line(
                "假人：", ReplayUi.text(s.bot, ChatFormatting.WHITE), " ",
                ReplayUi.suggest("修改", "/playerscript setup " + recordingId + " bot ",
                        "点击后补齐假人名回车", ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.line(
                "执行：",
                ReplayUi.text(s.infinite ? "无限循环" : s.count + " 次", ChatFormatting.WHITE), " ",
                ReplayUi.suggest("修改", "/playerscript setup " + recordingId + " count ",
                        "输入次数回车", ChatFormatting.YELLOW), " ",
                s.infinite
                        ? ReplayUi.suggest("改次数", "/playerscript setup " + recordingId + " count ",
                                "输入次数回车", ChatFormatting.YELLOW)
                        : ReplayUi.button("无限循环", "/playerscript setup " + recordingId + " inf",
                                null, ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.line(
                "循环位置：",
                ReplayUi.text(s.roundReset ? "每轮回到起点" : "接续当前位置", ChatFormatting.WHITE), " ",
                ReplayUi.button("切换", "/playerscript setup " + recordingId + " mode "
                        + (s.roundReset ? "continue" : "reset"),
                        "回到起点=每轮开始前传送回本次执行的初始位置", ChatFormatting.YELLOW)));
        player.sendSystemMessage(ReplayUi.gray("离线空档：已提取该参与者实际在线片段并顺接，面板/日志会保留该说明"));
        player.sendSystemMessage(ReplayUi.gray("完成处理：自动下线"));
        player.sendSystemMessage(ReplayUi.line(
                ReplayUi.button("开始执行", "/playerscript run " + recordingId, null, ChatFormatting.GREEN),
                " ",
                ReplayUi.button("返回", "/playereplay list", null, ChatFormatting.GRAY)));
    }

    private static int setupBot(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        String bot = StringArgumentType.getString(ctx, "假人名");
        if (bot.length() < 3 || bot.length() > 16 || !bot.matches("[A-Za-z0-9_]+"))
        {
            ctx.getSource().sendFailure(Component.literal("假人名限 3~16 位字母数字下划线"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "录像ID");
        Setup s = setup(player, id);
        s.bot = bot;
        s.botSetByUser = true;
        return renderSetup(player, ctx.getSource().getServer(), id) ? 1 : 0;
    }

    private static int setupCount(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "录像ID");
        Setup s = setup(player, id);
        s.count = IntegerArgumentType.getInteger(ctx, "次数");
        s.infinite = false;
        return renderSetup(player, ctx.getSource().getServer(), id) ? 1 : 0;
    }

    private static int setupInf(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "录像ID");
        Setup s = setup(player, id);
        s.infinite = true;
        return renderSetup(player, ctx.getSource().getServer(), id) ? 1 : 0;
    }

    private static int setupMode(CommandContext<CommandSourceStack> ctx, boolean roundReset)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "录像ID");
        Setup s = setup(player, id);
        s.roundReset = roundReset;
        return renderSetup(player, ctx.getSource().getServer(), id) ? 1 : 0;
    }

    // ==================== 启动 ====================

    private static int runById(CommandContext<CommandSourceStack> ctx, boolean forceLegacy)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        //#if MC >= 12103
        // 现代版本可直接执行
        //#else
        //$$ if (!forceLegacy)
        //$$ {
        //$$     ctx.getSource().sendFailure(Component.literal(
        //$$             "1.21/1.21.1 无法录制普通步行输入，脚本只能骑乘/静止交互；确需执行请用 run <录像ID> force"));
        //$$     return 0;
        //$$ }
        //#endif
        if (!carpet.utils.CommandHelper.canUseCommand(ctx.getSource(), carpet.CarpetSettings.commandPlayer))
        {
            ctx.getSource().sendFailure(Component.literal(
                    "当前服务器 /player 权限设置不允许召唤假人（commandPlayer）"));
            return 0;
        }
        if (ctx.getSource().getServer().getPlayerList().getPlayerCount()
                >= ctx.getSource().getServer().getMaxPlayers())
        {
            ctx.getSource().sendFailure(Component.literal("服务器玩家数量已达上限，无法召唤假人"));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "录像ID");
        ReplayData source = loadAccessible(ctx, player, id);
        if (source == null)
        {
            return 0;
        }
        String participantUuid = SELECTED.getOrDefault(new SelectKey(player.getUUID(), id), "");
        if (participantUuid.isEmpty())
        {
            List<ReplayParticipant> selectable = ReplayScriptExtractor.selectable(source);
            if (selectable.size() == 1)
            {
                participantUuid = selectable.get(0).uuid;
                SELECTED.put(new SelectKey(player.getUUID(), id), participantUuid);
            }
            else
            {
                ctx.getSource().sendFailure(Component.literal(
                        "多人录像请先用 /playerscript setup <录像ID> 选择参与者"));
                return 0;
            }
        }
        ReplayData script;
        try
        {
            script = ReplayScriptExtractor.extract(source, participantUuid);
        }
        catch (ReplayScriptException e)
        {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        Setup s = setup(player, id);
        if (ScriptScheduler.isNameTaken(s.bot)
                || ctx.getSource().getServer().getPlayerList().getPlayerByName(s.bot) != null)
        {
            ctx.getSource().sendFailure(Component.literal("假人名 " + s.bot + " 已被占用，请修改假人名"));
            return 0;
        }
        GameType gameType = player.gameMode.getGameModeForPlayer();
        var spec = new ScriptScheduler.ScriptSessionSpec(s.bot,
                s.infinite ? CarpetScriptSession.RunMode.LOOP
                        : (s.count == 1 ? CarpetScriptSession.RunMode.ONCE : CarpetScriptSession.RunMode.REPEAT),
                s.count, s.roundReset, true,
                script.metadata.initialDimension,
                script.metadata.initialX, script.metadata.initialY, script.metadata.initialZ,
                script.metadata.initialYaw, gameType, player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("正在召唤假人 " + s.bot + "…"), false);
        boolean accepted = ScriptScheduler.requestLaunch(ctx.getSource().getServer(), source.metadata.name, script, spec,
                session -> player.sendSystemMessage(ReplayUi.line(
                        ReplayUi.text("脚本已启动", ChatFormatting.GREEN), " ",
                        ReplayUi.button("运行面板", "/playerscript scripts", null, ChatFormatting.AQUA))),
                () -> player.sendSystemMessage(ReplayUi.text("假人召唤失败（超时或占用）", ChatFormatting.RED)),
                () -> canStillRun(ctx.getSource().getServer(), player, id));
        if (!accepted)
        {
            ctx.getSource().sendFailure(Component.literal(
                    "假人召唤失败（名字占用、维度无效或创建被拒绝）"));
            return 0;
        }
        return 1;
    }

    /** 异步绑定前重新校验当前访问权限，旧设置页/旧请求不能绕过 */
    private static boolean canStillRun(MinecraftServer server, ServerPlayer player, String recordingId)
    {
        ReplayData data = ReplayStorage.load(server, recordingId);
        return data != null && ReplayPermissions.canView(data.metadata.access, data.metadata.ownerUuid,
                player.getStringUUID(), ReplayPermissions.isAdmin(player));
    }

    private static int runExternal(CommandContext<CommandSourceStack> ctx, String name, String botName,
                                   boolean forceLegacy)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        //#if MC >= 12103
        // 现代版本可直接执行
        //#else
        //$$ if (!forceLegacy)
        //$$ {
        //$$     ctx.getSource().sendFailure(Component.literal(
        //$$             "1.21/1.21.1 无法录制普通步行输入，脚本只能骑乘/静止交互；确需执行请追加 force"));
        //$$     return 0;
        //$$ }
        //#endif
        ServerPlayer fake = ctx.getSource().getServer().getPlayerList().getPlayerByName(botName);
        if (!(fake instanceof carpet.patches.EntityPlayerMPFake))
        {
            ctx.getSource().sendFailure(Component.literal("假人不存在（先 /player " + botName + " spawn）"));
            return 0;
        }
        if (BrainManager.hasBrain(fake))
        {
            ctx.getSource().sendFailure(Component.literal("假人正被 brain 接管，先 /player " + botName + " brain off"));
            return 0;
        }
        if (ScriptScheduler.isBound(fake.getId()))
        {
            ctx.getSource().sendFailure(Component.literal("假人已被其他脚本控制: " + botName));
            return 0;
        }
        ReplayData data = null;
        for (var entry : ReplayStorage.listAll(ctx.getSource().getServer()))
        {
            if (entry.name().equals(name) && ReplayPermissions.canView(entry.access(), entry.ownerUuid(),
                    player.getStringUUID(), ReplayPermissions.isAdmin(player)))
            {
                data = ReplayStorage.load(ctx.getSource().getServer(), entry.recordingId());
                break;
            }
        }
        if (data == null)
        {
            ctx.getSource().sendFailure(Component.literal("录像不存在、损坏或无权访问: " + name));
            return 0;
        }
        List<ReplayParticipant> selectable = ReplayScriptExtractor.selectable(data);
        if (selectable.size() != 1)
        {
            ctx.getSource().sendFailure(Component.literal("多人录像请用 run <录像ID>，先选择参与者"));
            return 0;
        }
        ReplayData script;
        try
        {
            script = ReplayScriptExtractor.extract(data, selectable.get(0).uuid);
        }
        catch (ReplayScriptException e)
        {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        CarpetScriptSession session = ScriptScheduler.bindExternal(fake, name, script,
                CarpetScriptSession.RunMode.ONCE, 1, player.getUUID());
        if (session == null)
        {
            ctx.getSource().sendFailure(Component.literal("假人已被其他脚本控制: " + botName));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "脚本 " + name + " -> " + botName + "（外部假人，停止后不下线）"), false);
        return 1;
    }

    // ==================== 运行/控制 ====================

    private static int scriptsPanel(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        player.sendSystemMessage(ReplayUi.text("运行中的脚本", ChatFormatting.BOLD));
        var sessions = ScriptScheduler.listActive();
        if (sessions.isEmpty())
        {
            player.sendSystemMessage(ReplayUi.gray("（无）"));
        }
        for (CarpetScriptSession s : sessions)
        {
            String loops = s.totalLoops() < 0
                    ? "第 " + (s.loopsDone() + 1) + " 轮（无限）"
                    : "第 " + (s.loopsDone() + 1) + "/" + s.totalLoops() + " 轮";
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.text(s.fakeName() + " · " + s.scriptName(), ChatFormatting.WHITE)));
            player.sendSystemMessage(ReplayUi.line(
                    ReplayUi.gray(loops + " · " + fmtDuration(s.playTick()) + " / " + fmtDuration(s.totalTicks())
                            + " · " + (s.state() == CarpetScriptSession.State.PAUSED ? "已暂停" : "运行中"))));
            boolean canControl = ReplayPermissions.canManage(s.ownerUuid() == null ? null : s.ownerUuid().toString(),
                    player.getStringUUID(), ReplayPermissions.isAdmin(player));
            if (canControl)
            {
                player.sendSystemMessage(ReplayUi.line(
                        "  ",
                        s.state() == CarpetScriptSession.State.PAUSED
                                ? ReplayUi.button("继续", "/playerscript resume " + s.fakeName(), null, ChatFormatting.GREEN)
                                : ReplayUi.button("暂停", "/playerscript pause " + s.fakeName(), null, ChatFormatting.YELLOW),
                        " ",
                        ReplayUi.button("停止并下线", "/playerscript stop " + s.fakeName(),
                                "自动召唤的假人会下线；外部假人仅解除控制", ChatFormatting.RED),
                        " ",
                        ReplayUi.button("刷新", "/playerscript scripts", null, ChatFormatting.GRAY)));
            }
        }
        player.sendSystemMessage(ReplayUi.line(ReplayUi.gray(" "),
                ReplayUi.button("返回", "/playerscript panel", null, ChatFormatting.GRAY)));
        return 1;
    }

    private static CarpetScriptSession session(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer fake = ctx.getSource().getServer().getPlayerList()
                .getPlayerByName(StringArgumentType.getString(ctx, "假人"));
        if (fake == null)
        {
            return null;
        }
        return ScriptScheduler.get(fake.getId());
    }

    private static boolean canControl(CommandContext<CommandSourceStack> ctx, CarpetScriptSession s)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return false;
        }
        boolean ok = ReplayPermissions.canManage(s.ownerUuid() == null ? null : s.ownerUuid().toString(),
                player.getStringUUID(), ReplayPermissions.isAdmin(player));
        if (!ok)
        {
            ctx.getSource().sendFailure(Component.literal("无权控制该脚本"));
        }
        return ok;
    }

    private static int pauseResume(CommandContext<CommandSourceStack> ctx, boolean pause)
    {
        CarpetScriptSession s = session(ctx);
        if (s == null)
        {
            ctx.getSource().sendFailure(Component.literal("该假人没有运行中的脚本"));
            return 0;
        }
        if (!canControl(ctx, s))
        {
            return 0;
        }
        if (pause)
        {
            s.pause();
        }
        else
        {
            s.resume();
        }
        ctx.getSource().sendSuccess(() -> Component.literal((pause ? "已暂停 " : "已恢复 ") + s.fakeName()
                + (pause ? "（假人仍受重力与碰撞影响）" : "")), false);
        return 1;
    }

    private static int stopScript(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer fake = ctx.getSource().getServer().getPlayerList()
                .getPlayerByName(StringArgumentType.getString(ctx, "假人"));
        CarpetScriptSession s = fake == null ? null : ScriptScheduler.get(fake.getId());
        if (s == null)
        {
            ctx.getSource().sendFailure(Component.literal("该假人没有运行中的脚本"));
            return 0;
        }
        if (!canControl(ctx, s))
        {
            return 0;
        }
        boolean owned = s.ownsFake();
        ScriptScheduler.stopAndCleanup(s.fakeEntityId(), true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                owned ? "已停止并下线 " + s.fakeName() : "已停止（外部假人 " + s.fakeName() + " 不下线）"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx)
    {
        CarpetScriptSession s = session(ctx);
        if (s == null)
        {
            ctx.getSource().sendFailure(Component.literal("该假人没有运行中的脚本"));
            return 0;
        }
        String line = s.scriptName() + " tick=" + s.playTick() + "/" + s.totalTicks()
                + " 轮次=" + s.loopsDone() + (s.totalLoops() < 0 ? "/∞" : "/" + s.totalLoops())
                + " 状态=" + s.state();
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        ServerPlayer player = player(ctx);
        if (player == null)
        {
            return 0;
        }
        List<ReplayStorage.ReplayEntry> names = ReplayStorage.listAll(ctx.getSource().getServer());
        if (names.isEmpty())
        {
            ctx.getSource().sendSuccess(() -> Component.literal("没有脚本录像"), false);
            return 1;
        }
        String joined = String.join(", ", names.stream().map(ReplayStorage.ReplayEntry::name).toList());
        ctx.getSource().sendSuccess(() -> Component.literal("脚本录像: " + joined), false);
        return 1;
    }
}
