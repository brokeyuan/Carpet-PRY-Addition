package me.primaryuan.carpet.mixins.rule.playerScaleModifiers;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 Player 注册 minecraft:scale 属性（base 值 1.0）。
 *
 * 仅在 Minecraft 1.21.5+ 上生效：Attributes.SCALE 字段从 1.21.5 起加入原版。
 * 1.21~1.21.4 由预处理指令移除字段引用，且 mixins.json 不注册此类。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "createAttributes", at = @At("RETURN"), cancellable = true, require = 0)
    private static void pryAddition$registerScaleAttribute(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        //#if MC >= 12105
        AttributeSupplier.Builder builder = cir.getReturnValue();
        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, 1.0D);
        cir.setReturnValue(builder);
        //#endif
    }
}
