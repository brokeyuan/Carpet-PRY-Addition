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
 * 玩家 scale 属性偏离 1.0 时，同步调整多项物理量（1.20.5+ 原版均已做成属性，
 * 且全部 syncable，瞬态修改器会同步客户端，本地预测无 desync）：
 * - 行走速度 MOVEMENT_SPEED：scale<1.0 用 √scale 曲线（+0.3 软保底），scale≥1.0 线性
 * - 跳跃高度 JUMP_STRENGTH：×√scale（+0.5 保底，确保小人可跳上地毯）
 * - 台阶高度 STEP_HEIGHT：×scale（+0.5 保底）
 * - 方块交互距离 BLOCK_INTERACTION_RANGE / 攻击距离 ENTITY_INTERACTION_RANGE：×scale（+0.5 保底）
 * - 摔落安全距离 SAFE_FALL_DISTANCE：×scale（+0.5 保底，巨人抗摔、小人脆弱但不致死）
 * - 飞行速度：玩家属性表无 flying_speed，由 Abilities.flyingSpeed（默认 0.05）控制，
 *   scale<1.0 用 √scale 曲线（+0.3 软保底），scale≥1.0 线性
 * 所有修改器均为瞬态（transient）、不写入 NBT，规则关闭或 scale 回到 1.0 后自动移除。
 * scale<1.0 的保底机制确保极端缩小（如 0.1）时仍保留基本可玩性。
 * 仅在 Minecraft 1.21.5+ 生效：1.21~1.21.4 无 Attributes.SCALE，
 * 方法体被预处理清空，且 mixins.json 不注册本 mixin。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    private static final float realisticPlayerScale$DEFAULT_FLYING_SPEED = 0.05F;

    /**
     * 每 tick 末尾幂等地同步各项物理量与 scale。数值无变化时不执行任何 add/remove/发包，
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
        if (scaleAttr == null) {
            return;
        }
        Abilities abilities = self.getAbilities();
        if (!CarpetPrimaryuanSettings.realisticPlayerScale) {
            // 规则关闭：移除残留的速度修改器并恢复默认飞行速度
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), "scale_speed", 0.0D);
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH), "scale_jump", 0.0D);
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT), "scale_step", 0.0D);
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE), "scale_reach", 0.0D);
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE), "scale_reach", 0.0D);
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE), "scale_fall", 0.0D);
            if (abilities.getFlyingSpeed() != realisticPlayerScale$DEFAULT_FLYING_SPEED) {
                abilities.setFlyingSpeed(realisticPlayerScale$DEFAULT_FLYING_SPEED);
                self.onUpdateAbilities();
            }
            return;
        }

        double scale = scaleAttr.getValue();

        // 移动速度：scale<1.0 用 √scale 曲线（更平缓）+ 0.3 软保底；scale≥1.0 线性
        double speedMul = scale >= 1.0D ? scale : Math.max(Math.sqrt(scale), 0.3D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), "scale_speed", speedMul - 1.0D);
        // 跳跃初速：×√scale（跳高 ∝ 体型）+ 0.5 保底（确保小人可跳上地毯）
        double jumpMul = Math.max(Math.sqrt(scale), 0.5D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH), "scale_jump", jumpMul - 1.0D);
        // 台阶高度：×scale + 0.5 保底（确保小人可跨地毯）
        double stepMul = Math.max(scale, 0.5D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT), "scale_step", stepMul - 1.0D);
        // 方块交互 / 攻击距离：×scale + 0.5 保底（确保小人可交互）
        double reachMul = Math.max(scale, 0.5D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE), "scale_reach", reachMul - 1.0D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE), "scale_reach", reachMul - 1.0D);
        // 摔落安全距离：×scale + 0.5 保底（确保小人不被秒杀）
        double fallMul = Math.max(scale, 0.5D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE), "scale_fall", fallMul - 1.0D);

        // 飞行速度：scale<1.0 用 √scale 曲线 + 0.3 软保底；scale≥1.0 线性
        double flyMul = scale >= 1.0D ? scale : Math.max(Math.sqrt(scale), 0.3D);
        float targetFlyingSpeed = (float) (realisticPlayerScale$DEFAULT_FLYING_SPEED * flyMul);
        if (abilities.getFlyingSpeed() != targetFlyingSpeed) {
            abilities.setFlyingSpeed(targetFlyingSpeed);
            self.onUpdateAbilities();
        }
        //#endif
    }

    /**
     * 幂等更新瞬态修改器（ADD_MULTIPLIED_TOTAL）：
     * amount == 0 时移除（不存在则为空操作）；不存在则添加；数值变化则替换。
     * 属性实例为 null 时（理论不会发生，属性均在默认属性表中）静默跳过。
     */
    private static void realisticPlayerScale$updateModifier(AttributeInstance attr, String idPath, double amount) {
        //#if MC >= 12105
        if (attr == null) {
            return;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", idPath);
        //#else
        //$$ net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", idPath);
        //#endif
        AttributeModifier existing = attr.getModifier(id);
        if (amount == 0.0D) {
            if (existing != null) {
                attr.removeModifier(id);
            }
        } else if (existing == null) {
            attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (existing.amount() != amount) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        //#endif
    }
}
