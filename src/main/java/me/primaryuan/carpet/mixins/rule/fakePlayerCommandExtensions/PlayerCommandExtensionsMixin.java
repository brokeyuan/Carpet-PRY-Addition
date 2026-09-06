package me.primaryuan.carpet.mixins.rule.fakePlayerCommandExtensions;

import carpet.commands.PlayerCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.primaryuan.carpet.command.PlayerCommandExtension;
import me.primaryuan.carpet.command.PlayerSendtoCommandExtension;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 dropall 与 sendto 两个扩展子命令节点挂到 Carpet 的 /player 命令树上
 * （原为两个逐字相同的 mixin，合并以减少对 Carpet 字节码的注入面）。
 *
 * Brigadier 的 dispatcher.register 对同名 literal 节点会自动合并子节点：
 * 在 Carpet 原版 /player 注册完成后，再次注册
 * literal("player").then(argument("player").then(...))
 * 会被合并进已存在的 /player &lt;name&gt; 命令树，扩展节点作为新子节点追加到 &lt;player&gt; 下。
 * 无需重复 requires / suggests：原版已注册，合并时保留原节点谓词与建议。
 * 各扩展节点自身的可见性由其根节点 requires 谓词受对应规则控制，
 * 规则变更时由 CarpetPrimaryuanServer 的 RuleObserver 触发命令树重新下发。
 */
@Mixin(PlayerCommand.class)
public class PlayerCommandExtensionsMixin {

    @Inject(
            method = "register",
            at = @At("RETURN"),
            remap = false
    )
    private static void pry$registerPlayerExtensions(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext commandBuildContext,
            CallbackInfo ci
    ) {
        dispatcher.register(
                Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(PlayerCommandExtension.buildDropAllNode())
                                .then(PlayerSendtoCommandExtension.buildSendtoNode()))
        );
    }
}
