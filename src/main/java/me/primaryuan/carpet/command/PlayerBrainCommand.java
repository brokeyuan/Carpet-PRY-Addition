package me.primaryuan.carpet.command;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanServer;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /player &lt;name&gt; brain [mode] —— 假人脑子（AI 接管）命令。
 *
 * <p>用法：</p>
 * <ul>
 *   <li>{@code /player <name> brain}：查询当前 AI 模式</li>
 *   <li>{@code /player <name> brain zombie|skeleton|irongolem|spider|wolf|villager}：
 *       挂载/切换对应脑子（wolf 模式以命令执行者为主人做仇恨同步）</li>
 *   <li>{@code /player <name> brain off}：卸载脑子，恢复 Carpet 手动控制</li>
 * </ul>
 *
 * <p>整棵子树通过 requires 谓词受 {@link CarpetPrimaryuanSettings#fakePlayerBrain}
 * 控制可见性；规则关闭时不可见不可执行，已挂载的脑子也会在下一 tick
 * 由 BrainManager 自动卸载。接入方式与 dropall/sendto 一致：
 * 由 PlayerCommandExtensionsMixin 注入 Carpet 的 /player 命令树。</p>
 */
public final class PlayerBrainCommand {

    /** 可用的 AI 模式（不含 off，off 单独作为卸载语义处理） */
    private static final List<String> MODES = List.of(
            "zombie", "skeleton", "irongolem", "spider", "pillager", "wolf",
            "villager", "enderman", "off");

    private PlayerBrainCommand() {}

    /** 构建 brain 命令节点（挂到 /player <name> 下） */
    public static LiteralArgumentBuilder<CommandSourceStack> buildBrainNode() {
        return Commands.literal("brain")
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerBrain)
                .executes(PlayerBrainCommand::showStatus)
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(PlayerBrainCommand::suggestModes)
                        .executes(PlayerBrainCommand::setMode));
    }

    /** /player <name> brain：查询当前模式 */
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = CommandSupport.resolvePlayer(ctx);
        if (player == null) return 0;

        String name = CommandSupport.profileName(player);
        String mode = BrainManager.getModeKey(player);
        if (mode == null) {
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.status_off", name), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.status_on", name,
                    ServerI18n.tr("carpetprimaryuan.command.brain.mode_" + mode).getString()), false);
        }
        return 1;
    }

    /** /player <name> brain <mode>：挂载/切换/卸载 */
    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = CommandSupport.resolvePlayer(ctx);
        if (player == null) return 0;

        String name = CommandSupport.profileName(player);
        String mode = StringArgumentType.getString(ctx, "mode");

        // 目标必须是 Carpet 原生假人：真人没有 actionPack 接管语义
        if (!(player instanceof EntityPlayerMPFake)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.not_fake", name));
            return 0;
        }

        if ("off".equals(mode)) {
            if (BrainManager.detach(player)) {
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.brain.detached", name), true);
                return 1;
            }
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.no_brain", name));
            return 0;
        }

        // 狼模式需要一名真人玩家作为主人（仇恨同步的来源）；控制台无法提供
        UUID ownerUuid = null;
        if ("wolf".equals(mode)) {
            ServerPlayer owner = ctx.getSource().getPlayer();
            if (owner == null) {
                source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.brain.need_owner_player"));
                return 0;
            }
            ownerUuid = owner.getUUID();
        }

        try {
            String attached = BrainManager.attach(player, mode, ownerUuid);
            if (attached == null) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.unknown_mode", mode));
                return 0;
            }
            final String modeDisplay = ServerI18n.tr(
                    "carpetprimaryuan.command.brain.mode_" + attached).getString();
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.attached", name, modeDisplay), true);
            // 创造模式的假人不会被任何 AI 索敌、也无法索敌玩家（与原版生物的
            // 目标可见性一致）——最常见的"挂了脑子没反应"原因，主动提示
            if (player.isCreative()) {
                source.sendSystemMessage(ServerI18n.tr(
                        "carpetprimaryuan.command.brain.creative_hint", name));
            }
            return 1;
        } catch (Throwable t) {
            // 挂载失败必须可见：RCON 等来源会把异常吞成一句"unexpected error"，
            // 这里回显异常类型与首帧，并把完整堆栈写进服务器日志
            CarpetPrimaryuanServer.LOGGER.error("brain attach failed for {} ({})", name, mode, t);
            StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
            source.sendFailure(Component.literal("§cbrain attach failed: " + t
                    + (top != null ? " @ " + top : "")));
            return 0;
        }
    }

    /** 模式名补全：zombie / skeleton / irongolem / spider / wolf / villager / off */
    private static CompletableFuture<Suggestions> suggestModes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSupport.suggestMatching(builder, MODES);
        return builder.buildFuture();
    }
}
