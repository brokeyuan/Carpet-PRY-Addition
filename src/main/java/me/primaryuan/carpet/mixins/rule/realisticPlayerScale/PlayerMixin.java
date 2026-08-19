package me.primaryuan.carpet.mixins.rule.realisticPlayerScale;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * realisticPlayerScale 规则：更真实的玩家大小。
 *
 * 玩家 scale 属性偏离 1.0 时，以相同倍率同步调整移动/飞行速度（速度倍率 = scale 值）：
 * - 行走速度：MOVEMENT_SPEED 属性上的瞬态（transient）修改器（ADD_MULTIPLIED_TOTAL，
 *   amount = scale - 1），不写入 NBT，规则关闭或 scale 回到 1.0 后自动移除；
 * - 飞行速度：玩家属性表中没有 generic.flying_speed（getAttribute 恒为 null，不能走属性系统），
 *   创造飞行速度实际由 Abilities.flyingSpeed（默认 0.05）控制，故直接写 Abilities，
 *   且仅在数值变化时调用 onUpdateAbilities() 同步客户端。
 * 仅在 Minecraft 1.21.5+ 生效：1.21~1.21.4 无 Attributes.SCALE，
 * 方法体被预处理清空，且 mixins.json 不注册本 mixin。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    private static final float realisticPlayerScale$DEFAULT_FLYING_SPEED = 0.05F;

    /**
     * 每 tick 末尾幂等地同步两个速度与 scale。数值无变化时不执行任何 add/remove/发包，
     * 避免属性被标记 dirty 而产生每 tick 属性同步包。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void realisticPlayerScale$onTick(CallbackInfo callbackInfo) {
        //#if MC >= 12105
        Player self = (Player) (Object) this;
        // 仅服务端维护（含假人）；instanceof 判断避免 Level.isClientSide 字段在 1.21.9+ 私有化的版本差异
        if (!(self instanceof net.minecraft.server.level.ServerPlayer)) {
            return;
        }
        AttributeInstance scaleAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        AttributeInstance moveSpeedAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (scaleAttr == null || moveSpeedAttr == null) {
            return;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier speedModifierId = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#else
        //$$ net.minecraft.resources.ResourceLocation speedModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#endif
        Abilities abilities = self.getAbilities();
        if (!CarpetPrimaryuanSettings.realisticPlayerScale) {
            // 规则关闭：移除残留的速度修改器并恢复默认飞行速度
            moveSpeedAttr.removeModifier(speedModifierId);
            if (abilities.getFlyingSpeed() != realisticPlayerScale$DEFAULT_FLYING_SPEED) {
                abilities.setFlyingSpeed(realisticPlayerScale$DEFAULT_FLYING_SPEED);
                self.onUpdateAbilities();
            }
            return;
        }

        // MOVEMENT_SPEED：瞬态修改器幂等更新
        double amount = scaleAttr.getValue() - 1.0D;
        AttributeModifier moveModifier = moveSpeedAttr.getModifier(speedModifierId);
        if (amount == 0.0D) {
            if (moveModifier != null) {
                moveSpeedAttr.removeModifier(speedModifierId);
            }
        } else if (moveModifier == null) {
            moveSpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (moveModifier.amount() != amount) {
            moveSpeedAttr.removeModifier(speedModifierId);
            moveSpeedAttr.addTransientModifier(new AttributeModifier(speedModifierId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        // 飞行速度：Abilities.flyingSpeed = 0.05 * scale，仅数值变化时同步客户端
        float targetFlyingSpeed = (float) (realisticPlayerScale$DEFAULT_FLYING_SPEED * scaleAttr.getValue());
        if (abilities.getFlyingSpeed() != targetFlyingSpeed) {
            abilities.setFlyingSpeed(targetFlyingSpeed);
            self.onUpdateAbilities();
        }
        //#endif
    }
}
