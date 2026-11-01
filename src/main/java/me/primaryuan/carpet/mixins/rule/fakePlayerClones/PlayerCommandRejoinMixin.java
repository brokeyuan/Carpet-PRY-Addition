package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import carpet.commands.PlayerCommand;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneSnapshotManager;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

/**
 * /player &lt;name&gt; rejoin（TIS 语义，fakePlayerClones 规则开启时可用）：
 * 整组按上一次 kill 的快照恢复上线。 Brigadier 同名 literal 合并进
 * Carpet /player &lt;name&gt; 树（同 PlayerCommandExtensionsMixin 机制）。
 * 规则关闭时整棵子树不可见。
 */
@Mixin(PlayerCommand.class)
public abstract class PlayerCommandRejoinMixin {

    @Inject(
            method = "register",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void pry$registerRejoin(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
                                           net.minecraft.commands.CommandBuildContext buildContext, CallbackInfo ci) {
        dispatcher.register(
                literal("player")
                        .then(argument("player", word())
                                .then(literal("rejoin")
                                        .requires(s -> CarpetPrimaryuanSettings.fakePlayerClones
                                                && me.primaryuan.carpet.clones.CloneVersionAdapter.isGameMaster(s))
                                        .executes(ctx -> {
                                            String name = word_arg(ctx, "player");
                                            var snap = CloneSnapshotManager.load(ctx.getSource().getServer(), name);
                                            if (snap == null) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "[clones] no snapshot for " + name));
                                                return 0;
                                            }
                                            int n = CloneSnapshotManager.rejoin(ctx.getSource().getServer(), snap);
                                            if (n < 0) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "[clones] rejoin " + name + " blocked: real player online"));
                                                return 0;
                                            }
                                            final int restored = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "[clones] rejoined " + name + " (" + restored + "/"
                                                    + snap.members.size() + ")"), true);
                                            return restored > 0 ? 1 : 0;
                                        }))));
    }

    private static String word_arg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String k) {
        try {
            return com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, k);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
