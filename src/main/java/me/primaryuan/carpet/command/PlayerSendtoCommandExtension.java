package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.SendtoLinkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 独立的 /player &lt;name&gt; sendto 命令节点（假人背包链接 spec）。
 *
 * 命令树形状（once/continuous/interval/after/perTick/randomly/stop）由
 * {@link FrequencyCommandTree} 统一构建；&lt;target&gt; 参数节点（建立链接）在此追加。
 * 整棵树通过根节点 requires 谓词受 {@link CarpetPrimaryuanSettings#fakePlayerSendto}
 * 控制可见性；规则变更时由 RuleObserver 触发命令树重新下发，可见性立即生效。
 *
 * 链接的校验、存储、调度与物品转移统一委托 {@link SendtoLinkManager}。
 * 接入方式与 dropall 一致：由 mixins 的 PlayerCommandExtensionsMixin 注入 Carpet 的 /player 命令树。
 */
public final class PlayerSendtoCommandExtension {

    private PlayerSendtoCommandExtension() {}

    /**
     * 构建独立的 sendto 命令 builder。
     * 根节点 requires 绑定 {@link CarpetPrimaryuanSettings#fakePlayerSendto}：
     * 规则关闭时整棵子树不可见、不可执行（Brigadier 按节点谓词过滤下发）。
     *
     * @return sendto 命令的 LiteralArgumentBuilder
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildSendtoNode() {
        LiteralArgumentBuilder<CommandSourceStack> root = FrequencyCommandTree.build("sendto", new SendtoHandler())
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerSendto);
        // <target>：建立 源假人 → 目标假人 的链接并立即开始转移（默认 continuous）
        root.then(Commands.argument("target", StringArgumentType.word())
                .suggests(CommandSupport::suggestOnlinePlayers)
                .executes(SendtoHandler::addLink));
        return root;
    }

    /** sendto 的频率命令业务处理：把模式落到 {@link SendtoLinkManager} 并发送反馈 */
    private static final class SendtoHandler implements FrequencyCommandTree.Handler {

        @Override
        public int once(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            String playerName = player.getName().getString();

            int moved = SendtoLinkManager.transferOnce(source.getServer(), player);
            if (moved < 0) {
                // no_links 文案含两个 %s：假人名 + 提示命令中的 /player <名>
                source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.no_links", playerName, playerName));
                return 0;
            }
            if (moved == 0) {
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.transferred_zero", playerName), false);
                return 0;
            }
            final int count = moved;
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.sendto.transferred_once", count), true);
            return 1;
        }

        @Override
        public int startMode(CommandContext<CommandSourceStack> ctx, FrequencyCommandTree.ModeSpec spec)
                throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            String playerName = player.getName().getString();

            if (!SendtoLinkManager.setMode(player, spec.mode(), spec.interval(), spec.min(), spec.max())) {
                // no_links 文案含两个 %s：假人名 + 提示命令中的 /player <名>
                source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.no_links", playerName, playerName));
                return 0;
            }

            Object[] args = FrequencyCommandTree.concat(playerName, spec.displayArgs());
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.sendto.started_" + spec.keySuffix(), args), true);
            return 1;
        }

        @Override
        public int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            String playerName = player.getName().getString();

            SendtoLinkManager.LinkSummary summary = SendtoLinkManager.stopAndRemove(player);
            if (summary == null) {
                // no_links 文案含两个 %s：假人名 + 提示命令中的 /player <名>
                source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.no_links", playerName, playerName));
                return 0;
            }
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.sendto.stopped_all",
                    playerName, summary.stacks(), summary.items()), true);
            return 1;
        }

        /**
         * sendto &lt;target&gt;：建立链接并立即开始转移（新源默认 continuous），
         * 反馈该源当前的完整链接列表。
         */
        static int addLink(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            String playerName = player.getName().getString();
            String targetName = StringArgumentType.getString(ctx, "target");

            SendtoLinkManager.LinkResult result = SendtoLinkManager.addLink(source.getServer(), player, targetName);
            if (result == SendtoLinkManager.LinkResult.SUCCESS) {
                List<String> links = SendtoLinkManager.getLinks(player);
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.linked", playerName, targetName), true);
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.sendto.current_links", playerName, String.join(", ", links)), false);
                return 1;
            }

            switch (result) {
                case SOURCE_NOT_FAKE:
                    source.sendFailure(ServerI18n.tr(
                            "carpetprimaryuan.command.sendto.source_not_fake", playerName));
                    break;
                case TARGET_OFFLINE:
                    source.sendFailure(ServerI18n.tr(
                            "carpetprimaryuan.command.sendto.target_offline", targetName));
                    break;
                case TARGET_NOT_FAKE:
                    source.sendFailure(ServerI18n.tr(
                            "carpetprimaryuan.command.sendto.target_not_fake", targetName));
                    break;
                case SELF_LINK:
                    source.sendFailure(ServerI18n.tr(
                            "carpetprimaryuan.command.sendto.self_link", playerName, targetName));
                    break;
                case DUPLICATE:
                default:
                    source.sendFailure(ServerI18n.tr(
                            "carpetprimaryuan.command.sendto.duplicate", playerName, targetName));
                    break;
            }
            return 0;
        }
    }
}
