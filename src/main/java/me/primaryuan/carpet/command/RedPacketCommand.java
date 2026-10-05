package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.redPacket.RedPacketManager;
import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /redpacket 命令：发红包（规则 redPacket 控制可用性）。
 *
 * 结构（份数与祝福语均可省略——份数默认 = 当前在线人数（不含假人），祝福语默认 = 恭喜发财）：
 *   redpacket                        打开类型选择 GUI（全默认）
 *   redpacket <份数> [祝福语]          指定份数（1-100）
 *   redpacket [份数] @玩家 [祝福语]     专属直达（跳过类型选择与头像页，份数恒为 1）
 *   redpacket claim <id>              聊天框点击领取（内部命令，随广播消息下发）
 *   redpacket mute|unmute             退订/恢复本人红包广播
 *
 * 单 greedy 参数处理器内解析；claim/mute/unmute 字面量必须注册在 greedy 之前
 * （Brigadier 按插入序取第一个解析成功的分支，greedy 在前会吞掉字面子命令）。
 */
public final class RedPacketCommand {

    private RedPacketCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("redpacket")
                    // 主规则 = false 时整棵命令树不可见（boolean 规则直接判值，
                    // !"false".equals 是 String 三态规则的写法，对 boolean 恒真）
                    .requires(source -> CarpetPrimaryuanSettings.redPacket)
                    // 字面量子命令必须在 greedy 参数分支之前（防 greedy 吞分支）
                    .then(Commands.literal("claim")
                            .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                    .executes(RedPacketCommand::claim)))
                    .then(Commands.literal("mute").executes(RedPacketCommand::mute))
                    .then(Commands.literal("unmute").executes(RedPacketCommand::unmute))
                    .then(Commands.literal("list").executes(RedPacketCommand::list))
                    .then(Commands.literal("again").executes(RedPacketCommand::again))
                    .then(Commands.argument("args", StringArgumentType.greedyString())
                            .suggests(RedPacketCommand::suggestArgs)
                            .executes(RedPacketCommand::openRedPacket))
                    // /redpacket 裸命令：份数与祝福语全默认
                    .executes(RedPacketCommand::openBare));
        });
    }

    /**
     * greedy 参数补全：份数示例、@在线玩家、默认祝福语——把"单 greedy 手工解析"的
     * 语法契约（[份数] [@玩家] [祝福语]）透出到输入提示面。
     */
    private static CompletableFuture<Suggestions> suggestArgs(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> candidates = new ArrayList<>();
        candidates.add("1 ");
        candidates.add("10 ");
        for (ServerPlayer p : context.getSource().getServer().getPlayerList().getPlayers()) {
            candidates.add("@" + CommandSupport.profileName(p) + " ");
        }
        candidates.add(ServerI18n.tr("carpetprimaryuan.redpacket.default_message").getString());
        CommandSupport.suggestMatching(builder, candidates);
        return builder.buildFuture();
    }

    // ==================== 执行 ====================

    /** /redpacket 裸命令：份数与祝福语全默认 */
    private static int openBare(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        RedPacketManager.openTypeMenu(player, defaultCount(context.getSource().getServer()),
                ServerI18n.tr("carpetprimaryuan.redpacket.default_message").getString());
        return 1;
    }

    /**
     * 发红包解析入口：单 greedy 参数处理器内切分——
     * ① 首词为纯数字 → 份数（1-100，越界报错），缺省 = 在线人数（不含假人）；
     * ② 次词以 @ 开头 → 专属直达目标（按名不区分大小写匹配在线玩家，不能是自己）；
     * ③ 其余为祝福语，缺省 = 恭喜发财（lang 键随全局语言）。
     */
    private static int openRedPacket(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        // 参数解析在来源校验之前：解析错误（份数越界/目标缺失）对控制台同样可见
        String raw = StringArgumentType.getString(context, "args").trim();

        int count = -1;
        String rest = raw;
        int space = raw.indexOf(' ');
        String first = space < 0 ? raw : raw.substring(0, space);
        if (!first.isEmpty() && first.chars().allMatch(Character::isDigit)) {
            // isDigit 放行全角/Unicode 数字，Character.digit 同样能解析它们（parseInt 不抛），
            // 唯一异常源是超长溢出：9 位以内任何输入都交给下面的范围判断拒绝
            if (first.length() > 9) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.count_invalid"));
                return 0;
            }
            count = Integer.parseInt(first);
            if (count < 1 || count > 100) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.count_invalid"));
                return 0;
            }
            rest = space < 0 ? "" : raw.substring(space + 1).stripLeading();
        }

        ServerPlayer target = null;
        if (rest.startsWith("@")) {
            space = rest.indexOf(' ');
            String targetName = (space < 0 ? rest.substring(1) : rest.substring(1, space)).trim();
            rest = space < 0 ? "" : rest.substring(space + 1).stripLeading();
            target = targetName.isEmpty() ? null
                    : source.getServer().getPlayerList().getPlayerByName(targetName);
            if (target == null) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.target_missing", targetName));
                return 0;
            }
            ServerPlayer self = source.getPlayer();
            if (self != null && target == self) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.target_self"));
                return 0;
            }
        }

        String message = RedPacketManager.sanitizeMessage(rest);
        if (message.isEmpty()) {
            message = ServerI18n.tr("carpetprimaryuan.redpacket.default_message").getString();
        }
        if (message.length() > 32) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.message_invalid"));
            return 0;
        }
        if (count < 0) {
            count = defaultCount(source.getServer());
        }

        // 解析全部通过才要求玩家来源（红包必须有发送者）
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        if (target != null) {
            RedPacketManager.openTypeMenuTargeted(player, count, message, target);
        } else {
            RedPacketManager.openTypeMenu(player, count, message);
        }
        return 1;
    }

    /** 默认份数 = 当前在线人数（红包发给"所有在线的人"，假人不计入） */
    private static int defaultCount(MinecraftServer server) {
        int online = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p instanceof EntityPlayerMPFake)) {
                online++;
            }
        }
        return online;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        List<Component> lines = RedPacketManager.listOngoing(player);
        if (lines.isEmpty()) {
            context.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.list_empty"));
            return 0;
        }
        for (Component line : lines) {
            player.sendSystemMessage(line, false);
        }
        return 1;
    }

    private static int again(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        if (!RedPacketManager.reopenLast(player)) {
            context.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.redpacket.msg.again_none"));
            return 0;
        }
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        int id = IntegerArgumentType.getInteger(context, "id");
        RedPacketManager.claim(player, id);
        return 1;
    }

    /** mute 无参 = 翻转本人退订状态；unmute 子命令保留为显式恢复 */
    private static int mute(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        boolean muted = RedPacketManager.isMuted(player);
        return toggleMute(context, !muted);
    }

    private static int unmute(CommandContext<CommandSourceStack> context) {
        return toggleMute(context, false);
    }

    private static int toggleMute(CommandContext<CommandSourceStack> context, boolean mute) {
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        RedPacketManager.toggleMute(player, mute);
        player.sendSystemMessage(ServerI18n.tr(mute
                ? "carpetprimaryuan.redpacket.msg.mute_on"
                : "carpetprimaryuan.redpacket.msg.mute_off"));
        return 1;
    }
}
