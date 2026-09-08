package me.primaryuan.carpet.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.DropSlotScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * 独立的 /player &lt;name&gt; dropall 命令节点（v3 spec）。
 *
 * 命令树形状（once/continuous/interval/after/perTick/randomly/stop）由
 * {@link FrequencyCommandTree} 统一构建；整棵树通过根节点 requires 谓词受
 * {@link CarpetPrimaryuanSettings#fakePlayerDropStackModifiers} 控制可见性，
 * 规则变更时由 RuleObserver 触发命令树重新下发，可见性立即生效。
 *
 * 调度统一委托 {@link DropSlotScheduler}，slotKey 固定为 "dropall"。
 * 接入方式与 sendto 一致：由 mixins 的 PlayerCommandExtensionsMixin 注入 Carpet 的 /player 命令树。
 */
public final class PlayerCommandExtension {

    /** 任务槽位 key：标识 dropall 任务，用于 DropSlotScheduler 内部去重 */
    private static final String SLOT_KEY = "dropall";

    private PlayerCommandExtension() {}

    /**
     * 构建独立的 dropall 命令 builder。
     * 根节点 requires 绑定 {@link CarpetPrimaryuanSettings#fakePlayerDropStackModifiers}：
     * 规则关闭时整棵子树不可见、不可执行（Brigadier 按节点谓词过滤下发）。
     *
     * @return dropall 命令的 LiteralArgumentBuilder
     */
    public static LiteralArgumentBuilder<CommandSourceStack> buildDropAllNode() {
        return FrequencyCommandTree.build("dropall", new DropAllHandler())
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerDropStackModifiers);
    }

    /** dropall 的频率命令业务处理：把模式落到 {@link DropSlotScheduler} 并发送反馈 */
    private static final class DropAllHandler implements FrequencyCommandTree.Handler {

        @Override
        public int once(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            DropSlotScheduler.dropOnce(player, DropSlotScheduler.SLOT_ALL);
            return 1;
        }

        @Override
        public int startMode(CommandContext<CommandSourceStack> ctx, FrequencyCommandTree.ModeSpec spec)
                throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;
            String playerName = player.getName().getString();

            if (!DropSlotScheduler.start(player, DropSlotScheduler.SLOT_ALL, SLOT_KEY,
                    spec.mode(), spec.interval(), spec.min(), spec.max(), source)) {
                source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.dropall.already_running", SLOT_KEY));
                return 0;
            }

            // 反馈参数：假人名 + 模式展示参数 + slotKey
            Object[] args = FrequencyCommandTree.concat(playerName, spec.displayArgs(), SLOT_KEY);
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.dropall.started_" + spec.keySuffix(), args), true);
            return 1;
        }

        @Override
        public int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
            CommandSourceStack source = ctx.getSource();
            ServerPlayer player = CommandSupport.resolvePlayer(ctx);
            if (player == null) return 0;

            DropSlotScheduler.StopSummary summary = DropSlotScheduler.stop(player, SLOT_KEY);
            if (summary.result() == DropSlotScheduler.StopResult.NO_TASK) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.dropall.no_task", SLOT_KEY));
                return 0;
            }
            final int dropped = summary.droppedStacks();
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.dropall.stopped", SLOT_KEY, dropped), true);
            return 1;
        }
    }
}
