package me.primaryuan.carpet.mixins.rule.playerScale;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 Player 注册 minecraft:scale 属性（base 值 1.0）。
 *
 * 兜底保险：scale 属性自 1.20.5（23w51a）起已由原版 LivingEntity 的默认属性表
 * 注册（已核实 1.21/1.21.3 字节码 createLivingAttributes 均含 SCALE），base 值
 * 本就是 1.0，此处重复添加为幂等空操作。保留该 mixin 以防属性表在未来版本变动。
 * 所有受支持的 Minecraft 版本均注册本类。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "createAttributes", at = @At("RETURN"), cancellable = true, require = 0)
    private static void pryAddition$registerScaleAttribute(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        AttributeSupplier.Builder builder = cir.getReturnValue();
        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, 1.0D);
        cir.setReturnValue(builder);
    }
}
