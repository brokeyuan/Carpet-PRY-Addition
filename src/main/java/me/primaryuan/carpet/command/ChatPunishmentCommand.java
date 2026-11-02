package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.chatPunishment.ChatPunishmentManager;
import me.primaryuan.carpet.handler.chatPunishment.PunishmentMode;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * /chatpunish：聊天惩罚管理（规则 chatPunishment 控制，仅管理员，见
 * {@link CommandSupport#isAdmin}）。
 *
 * 结构：
 *   set <玩家> <模式> [时长]   设置/覆盖惩罚（每玩家仅一种；时长 s/m/h/d，缺省永久）
 *   clear <玩家>               解除惩罚
 *   list                       列出未到期惩罚（名称、模式、剩余时间或永久）
 *
 * 玩家用原版 GameProfileArgument（同 /ban 的档案解析）：在线补全、离线走
 * 档案缓存，UUID 可靠，未知玩家由原版报错，不凭猜测造档案。模式为字面量
 * 节点，自动补全，未知值由 Brigadier 拒绝。
 */
public final class ChatPunishmentCommand {

    private ChatPunishmentCommand() {}

    // ==================== 注册 ====================

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("chatpunish")
                    // 规则关闭或非管理员时整棵命令树不可见
                    .requires(source -> CarpetPrimaryuanSettings.chatPunishment && CommandSupport.isAdmin(source))
                    .then(buildSet())
                    .then(Commands.literal("clear")
                            .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                    .executes(ChatPunishmentCommand::clear)))
                    .then(Commands.literal("list")
                            .executes(ChatPunishmentCommand::list)));
        });
    }

    /** set <玩家> <模式> [时长]：玩家节点共享，八种模式字面量各自挂可选时长 */
    private static LiteralArgumentBuilder<CommandSourceStack> buildSet() {
        RequiredArgumentBuilder<CommandSourceStack, ?> player = Commands.argument("player", GameProfileArgument.gameProfile());
        for (PunishmentMode mode : PunishmentMode.values()) {
            player.then(Commands.literal(mode.id())
                    .executes(ctx -> apply(ctx, mode, 0))
                    .then(Commands.argument("duration", StringArgumentType.word())
                            .executes(ctx -> applyWithDuration(ctx, mode))));
        }
        return Commands.literal("set").then(player);
    }

    // ==================== 动作 ====================

    private static int applyWithDuration(CommandContext<CommandSourceStack> ctx, PunishmentMode mode) throws CommandSyntaxException {
        String spec = StringArgumentType.getString(ctx, "duration");
        Long millis = ChatPunishmentManager.parseDuration(spec);
        if (millis == null) {
            ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.chatpunish.bad_duration", spec));
            return 0;
        }
        long expireAt;
        try {
            expireAt = ChatPunishmentManager.expiryFrom(System.currentTimeMillis(), millis);
        } catch (ArithmeticException e) {
            ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.chatpunish.bad_duration", spec));
            return 0;
        }
        return apply(ctx, mode, expireAt);
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, PunishmentMode mode, long expireAt) throws CommandSyntaxException {
        long now = System.currentTimeMillis();
        int count = 0;
        boolean failed = false;
        //#if MC < 12110
        //$$ for (com.mojang.authlib.GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
        //$$     UUID id = profile.getId();
        //$$     String name = profile.getName();
        //#else
        for (net.minecraft.server.players.NameAndId profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            UUID id = profile.id();
            String name = profile.name();
//#endif
            ChatPunishmentManager.Result result = ChatPunishmentManager.set(id, name, mode, expireAt);
            if (result != ChatPunishmentManager.Result.APPLIED) {
                String feedbackName = name;
                ctx.getSource().sendFailure(failureMessage(result, feedbackName));
                failed = true;
                continue;
            }
            String duration = expireAt == 0
                    ? ServerI18n.tr("carpetprimaryuan.chatpunish.duration_permanent").getString()
                    : ServerI18n.tr("carpetprimaryuan.chatpunish.duration_timed",
                            ChatPunishmentManager.formatRemaining(expireAt - now)).getString();
            String feedbackName = name;
            ctx.getSource().sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.chatpunish.set",
                    feedbackName, mode.id(), duration), false);
            count++;
        }
        return failed ? 0 : count;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int cleared = 0;
        boolean failed = false;
        //#if MC < 12110
        //$$ for (com.mojang.authlib.GameProfile profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
        //$$     UUID id = profile.getId();
        //$$     String name = profile.getName();
        //#else
        for (net.minecraft.server.players.NameAndId profile : GameProfileArgument.getGameProfiles(ctx, "player")) {
            UUID id = profile.id();
            String name = profile.name();
//#endif
            ChatPunishmentManager.Result result = ChatPunishmentManager.clear(id);
            String feedbackName = name;
            if (result == ChatPunishmentManager.Result.APPLIED) {
                ctx.getSource().sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.chatpunish.cleared", feedbackName), false);
                cleared++;
            } else if (result == ChatPunishmentManager.Result.NOT_FOUND) {
                ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.chatpunish.not_found", feedbackName));
            } else {
                ctx.getSource().sendFailure(failureMessage(result, feedbackName));
                failed = true;
            }
        }
        return failed ? 0 : cleared;
    }

    private static Component failureMessage(ChatPunishmentManager.Result result, String name) {
        return switch (result) {
            case READ_ONLY -> ServerI18n.tr("carpetprimaryuan.chatpunish.read_only");
            case IO_ERROR -> ServerI18n.tr("carpetprimaryuan.chatpunish.save_failed", name);
            default -> ServerI18n.tr("carpetprimaryuan.chatpunish.save_failed", name);
        };
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (!ChatPunishmentManager.storageWritable()) {
            ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.chatpunish.read_only"));
            return 0;
        }
        List<ChatPunishmentManager.Entry> entries = ChatPunishmentManager.activeEntries();
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.chatpunish.list_empty"), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        int count = entries.size();
        ctx.getSource().sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.chatpunish.list_header", count), false);
        for (ChatPunishmentManager.Entry entry : entries) {
            String duration = entry.expireAt() == 0
                    ? ServerI18n.tr("carpetprimaryuan.chatpunish.duration_permanent").getString()
                    : ServerI18n.tr("carpetprimaryuan.chatpunish.duration_remaining",
                            ChatPunishmentManager.formatRemaining(entry.expireAt() - now)).getString();
            String name = entry.name();
            String mode = entry.mode().id();
            ctx.getSource().sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.chatpunish.list_entry",
                    name, mode, duration), false);
        }
        return count;
    }
}
