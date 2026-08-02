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
        if (!CarpetPrimaryuanSettings.fakePlayerDropStackModifiers) {
            return;
        }
        // 重建整个 dropStack builder：复制 Carpet 原有子节点的 executes，
        // 并为每个子节点追加 once/continuous/interval/after/perTick/randomly/stop 修饰子节点。
        // 用 setReturnValue 替换，不依赖 Brigadier then() 的同名节点合并行为。
        cir.setReturnValue(PlayerCommandExtension.rebuildDropStackBuilder(cir.getReturnValue()));
    }
}
