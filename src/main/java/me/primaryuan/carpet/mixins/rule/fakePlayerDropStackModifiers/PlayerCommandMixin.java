package me.primaryuan.carpet.mixins.rule.fakePlayerDropStackModifiers;

import carpet.commands.PlayerCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.command.PlayerCommandExtension;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerCommand.class)
public class PlayerCommandMixin {

    @Inject(
            method = "makeDropCommand",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void pry$extendDropStack(
            String actionName,
            boolean dropAll,
            CallbackInfoReturnable<LiteralArgumentBuilder<CommandSourceStack>> cir
    ) {
        // makeDropCommand 也用于 "drop" 节点（dropAll=false），只扩展 "dropStack" 节点
        if (!"dropStack".equals(actionName)) {
            return;
        }
        // 无条件替换命令树：规则检查下沉到各 executes 回调内部运行时判断。
        // 原因：命令树只在服务器启动时注册一次，运行时切换规则不会重建命令树。
        // 因此注册时必须无条件挂载，规则关闭时由回调内部回退到原版行为。
        cir.setReturnValue(PlayerCommandExtension.rebuildDropStackBuilder());
    }
}
