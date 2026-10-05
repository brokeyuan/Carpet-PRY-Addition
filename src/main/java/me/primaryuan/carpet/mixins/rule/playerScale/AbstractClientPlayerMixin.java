package me.primaryuan.carpet.mixins.rule.playerScale;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * playerScale 规则的客户端部分：补偿体型缩放对动态 FOV 的影响。
 * （v1.1.8 起归属 playerScale；触发仍以属性同步中的 scale_speed 修改器为准——
 * 该修改器由 playerScalePhysics 的移速联动施加，故实际补偿仅在
 * playerScalePhysics 生效时产生 FOV 变化的场景下起作用。）
 *
 * 原版 AbstractClientPlayer#getFieldOfViewModifier 以移速属性当前值计算
 * FOV 倍率：f *= (移速值 / walkingSpeed + 1) / 2（疾跑/速度效果拉宽视野的来源）。
 * 体型缩放的移速瞬态修改器会同步到客户端，于是缩小时客户端认为
 * "变慢了"而收窄 FOV（scale=0.5 时 FOV×0.75），放大时反向拉宽。
 *
 * 补偿作用在<b>速度因子</b>上：@WrapOperation 包裹方法内唯一的
 * {@code getAttributeValue(MOVEMENT_SPEED)} 调用，把读到速度值除回 scale——
 * 下游全部按原版语义自然成立：飞行 ×1.1、FOV 效果设置（fovEffectScale）为 0 时
 * 原版本身就归一视野（补偿因子随之不参与）、望远镜分支的提前返回不被触碰。
 * （旧实现按返回值整体缩放，会连飞行加成、FOV 设置归一与望远镜倍率一起改。）
 *
 * 是否生效以"客户端收到的属性同步数据里存在我们的 scale_speed 修改器"判定，
 * 而非读取本类的 Carpet 规则字段（规则值不保证同步到未安装本模组的原版客户端），
 * 因此对纯原版客户端同样有效。
 *
 * 方法按名注入（各版本均无重载，已逐一验证；1.21~1.21.1 无参、1.21.3+ 带参，
 * 处理器不捕获外层参数故无需分叉）。javap 核实 1.21.11 与 26.3：方法内
 * getAttributeValue(Holder) 调用各恰一处，26.3 改用 isScoping/新镜筒公式但
 * 移速读取不变。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @WrapOperation(
            method = "getFieldOfViewModifier",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"
            )
    )
    private double playerScale$compensateSpeedAttribute(
            AbstractClientPlayer self, Holder<Attribute> attribute, Operation<Double> original) {
        double value = original.call(self, attribute);
        AttributeInstance speedAttr = self.getAttribute(attribute);
        if (speedAttr == null) {
            return value;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier speedModifierId = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#else
        //$$ net.minecraft.resources.ResourceLocation speedModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#endif
        AttributeModifier scaleModifier = speedAttr.getModifier(speedModifierId);
        if (scaleModifier == null) {
            return value; // 规则未生效（服务端未开启或 scale 修改器已被移除）
        }
        float scale = (float) (1.0D + scaleModifier.amount());
        if (scale == 1.0F || scale <= 0.0F) {
            return value;
        }
        return value / scale; // 原版公式读到的速度回到无缩放口径
    }
}
