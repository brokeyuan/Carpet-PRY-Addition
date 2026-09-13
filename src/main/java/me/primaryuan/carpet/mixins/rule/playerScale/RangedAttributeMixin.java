package me.primaryuan.carpet.mixins.rule.playerScale;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放行 SCALE 属性的任意有限正值，与 /scale 的硬边界（仅要求 value > 0，上不封顶）保持一致。
 * 原版 RangedAttribute 声明范围 0.0625–16.0，calculateValue→sanitizeValue
 * 会把超范围值静默夹紧；放行正值后命令反馈值与实际生效值一致。
 * 非正值与非有限值（NaN/Infinity）不拦截，回落原版夹紧逻辑，兜底数据包/模组
 * 设置的非法值（原版自身不会设置超范围值）。
 * 身份比较仅命中 SCALE 属性；全局生效（含其他实体）。
 * 所有受支持的 Minecraft 版本均注册本类（SCALE 属性与 sanitizeValue(double)
 * 自 1.20.5 起存在，1.21~1.21.4 与 1.21.5+ 签名一致，已逐一核实）。
 */
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void playerScale$widenScaleRange(double value, CallbackInfoReturnable<Double> cir) {
        if (value > 0 && Double.isFinite(value)
                && (Object) this == net.minecraft.world.entity.ai.attributes.Attributes.SCALE.value()) {
            cir.setReturnValue(value);
        }
    }
}
