package me.primaryuan.carpet.mixins.rule.realisticPlayerScale;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * realisticPlayerScale 规则：更真实的玩家大小。
 *
 * 玩家 scale 属性偏离 1.0 时，以相同倍率同步调整移动/飞行速度（速度倍率 = scale 值）。
 * 修改器为瞬态（transient）属性、不写入 NBT，规则关闭或 scale 回到 1.0 后自动移除。
 * 仅在 Minecraft 1.21.5+ 生效：1.21~1.21.4 无 Attributes.SCALE，
 * 方法体被预处理清空，且 mixins.json 不注册本 mixin。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * 每 tick 末尾幂等地将两个速度属性上的瞬态修改器同步为 scale 偏移量
     * （MULTIPLY_TOTAL，amount = scale - 1）。数值无变化时不执行任何 add/remove，
     * 避免属性被标记 dirty 而产生每 tick 属性同步包。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void realisticPlayerScale$onTick(CallbackInfo callbackInfo) {
        //#if MC >= 12105
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        AttributeInstance scaleAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        AttributeInstance moveSpeedAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        AttributeInstance flySpeedAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FLYING_SPEED);
        if (scaleAttr == null || moveSpeedAttr == null || flySpeedAttr == null) {
            return;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier speedModifierId = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#else
        //$$ net.minecraft.resources.ResourceLocation speedModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#endif
        if (!CarpetPrimaryuanSettings.realisticPlayerScale) {
            // 规则关闭：移除两个速度属性上可能残留的修改器
            moveSpeedAttr.removeModifier(speedModifierId);
            flySpeedAttr.removeModifier(speedModifierId);
            return;
        }
        double amount = scaleAttr.getValue() - 1.0D;
        // MOVEMENT_SPEED：幂等更新
        AttributeModifier moveModifier = moveSpeedAttr.getModifier(speedModifierId);
        if (amount == 0.0D) {
            if (moveModifier != null) {
                moveSpeedAttr.removeModifier(speedModifierId);
            }
        } else if (moveModifier == null) {
            moveSpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (moveModifier.amount() != amount) {
            moveSpeedAttr.removeModifier(speedModifierId);
            moveSpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        // FLYING_SPEED：幂等更新
        AttributeModifier flyModifier = flySpeedAttr.getModifier(speedModifierId);
        if (amount == 0.0D) {
            if (flyModifier != null) {
                flySpeedAttr.removeModifier(speedModifierId);
            }
        } else if (flyModifier == null) {
            flySpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (flyModifier.amount() != amount) {
            flySpeedAttr.removeModifier(speedModifierId);
            flySpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        //#endif
    }
}
