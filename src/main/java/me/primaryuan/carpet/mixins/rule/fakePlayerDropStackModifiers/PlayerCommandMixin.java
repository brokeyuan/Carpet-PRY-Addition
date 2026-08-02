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
        // 完全手动构建 dropStack 命令树：
        // 为每个 slot 节点（all/mainhand/offhand/<slot>）保留原版 executes + 追加修饰子节点。
        cir.setReturnValue(PlayerCommandExtension.rebuildDropStackBuilder());
    }
}
