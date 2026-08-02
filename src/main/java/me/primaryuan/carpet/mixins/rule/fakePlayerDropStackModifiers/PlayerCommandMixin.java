package me.primaryuan.carpet.mixins.rule.fakePlayerDropStackModifiers;

import carpet.commands.PlayerCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
            remap = false
    )
    private static void pry$extendDropStack(
            String actionName,
            boolean dropAll,
            CallbackInfoReturnable<LiteralArgumentBuilder<CommandSourceStack>> cir
    ) {
        // makeDropCommand 也用于 "drop" 节点（dropAll=false），只扩展 "dropStack" 节点
        LiteralArgumentBuilder<CommandSourceStack> original = cir.getReturnValue();
        if (!"dropStack".equals(original.getLiteral())) {
            return;
        }
        // Brigadier 的 then() 会自动合并同名子节点：原有的 literal("all").executes(...) 保留，
        // 新追加的 once/continuous/interval/after/perTick/randomly/stop 子节点合并进去。
        PlayerCommandExtension.extendDropStackNode(original);
    }
}
