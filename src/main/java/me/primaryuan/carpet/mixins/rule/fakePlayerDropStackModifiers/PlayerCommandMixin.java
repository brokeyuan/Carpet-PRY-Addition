package me.primaryuan.carpet.mixins.rule.fakePlayerDropStackModifiers;

import carpet.commands.PlayerCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.primaryuan.carpet.command.PlayerCommandExtension;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerCommand.class)
public class PlayerCommandMixin {

    @Inject(
            method = "register",
            at = @At("RETURN"),
            remap = false
    )
    private static void pry$registerDropAll(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext commandBuildContext,
            CallbackInfo ci
    ) {
        // 在 Carpet 原版 /player 命令注册完成后，追加独立的 dropall 子节点。
        // Brigadier 的 dispatcher.register 对同名 literal 节点会自动合并子节点：
        // 这里再次注册 literal("player").then(argument("player").then(dropall))
        // 会被合并进已存在的 /player <name> 命令树，dropall 作为新子节点追加到 <player> 下。
        // 无需重复 requires / suggests：原版已注册，合并时保留原节点谓词与建议。
        // dropall 内部的规则检查下沉到各 executes 回调运行时判断（命令树只在启动时注册一次）。
        dispatcher.register(
                Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(PlayerCommandExtension.buildDropAllNode()))
        );
    }
}
