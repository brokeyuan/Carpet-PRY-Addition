package me.primaryuan.carpet.mixins.rule.realisticPlayerScale;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * realisticPlayerScale 规则的客户端部分：补偿体型缩放对动态 FOV 的影响。
 *
 * 原版 AbstractClientPlayer#getFieldOfViewModifier 以移速属性当前值计算
 * FOV 倍率：f *= (移速值 / walkingSpeed + 1) / 2（疾跑/速度效果拉宽视野的来源）。
 * realisticPlayerScale 的移速瞬态修改器会同步到客户端，于是缩小时客户端认为
 * "变慢了"而收窄 FOV（scale=0.5 时 FOV×0.75），放大时反向拉宽。
 *
 * 这里在返回值上除回 scale 因子，使 FOV 表现与未缩放时一致，
 * 疾跑/速度药水的 FOV 拉宽效果保留。
 *
 * 是否生效以"客户端收到的属性同步数据里存在我们的 scale_speed 修改器"判定，
 * 而非读取本类的 Carpet 规则字段（规则值不保证同步到未安装本模组的原版客户端），
 * 因此对纯原版客户端同样有效。签名 getFieldOfViewModifier(boolean, float)
 * 在 1.21.5~26.2 各版本一致（已逐一验证）。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void realisticPlayerScale$compensateFov(boolean firstPerson, float partialTick, CallbackInfoReturnable<Float> cir) {
        //#if MC >= 12105
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        AttributeInstance speedAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) {
            return;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier speedModifierId = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#else
        //$$ net.minecraft.resources.ResourceLocation speedModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#endif
        AttributeModifier scaleModifier = speedAttr.getModifier(speedModifierId);
        if (scaleModifier == null) {
            return; // 规则未生效（服务端未开启或 scale 修改器已被移除）
        }
        float scale = (float) (1.0D + scaleModifier.amount());
        if (scale == 1.0F || scale <= 0.0F) {
            return;
        }
        float walkingSpeed = self.getAbilities().getWalkingSpeed();
        if (walkingSpeed <= 0.0F) {
            return;
        }
        // 原版已乘 (ratio+1)/2；期望表现为 (ratio/scale+1)/2，按比值缩放返回值
        float ratio = (float) (speedAttr.getValue() / walkingSpeed);
        float vanillaFactor = (ratio + 1.0F) / 2.0F;
        float compensatedFactor = (ratio / scale + 1.0F) / 2.0F;
        if (vanillaFactor != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() * (compensatedFactor / vanillaFactor));
        }
        //#endif
    }
}
