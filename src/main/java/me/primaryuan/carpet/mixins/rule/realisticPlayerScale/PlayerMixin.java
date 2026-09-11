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
 * 且全部 syncable，瞬态修改器会同步客户端，本地预测无 desync）。四种模式：
 * - false：关闭
 * - true（平缓）：所有联动量按 √scale 曲线缩放（大体型增速更平缓、小体型更飘逸），无保底
 * - safety（平缓+保底）：曲线同 true，另为小体型（scale<1.0）提供保底——
 *   速度/飞行/重力 0.3×，跳跃/台阶/交互/摔落 0.5×，极端缩小（如 0.1）仍可玩
 * - strict（严格等比）：速度/台阶/交互/摔落严格 ×scale，跳跃初速 ×scale^0.75
 *   （与重力 √scale 配套，跳高 ∝ scale），无任何保底，完全按几何比例行动
 * 联动项：
 * - 行走速度 MOVEMENT_SPEED / 飞行速度 Abilities.flyingSpeed（默认 0.05，玩家属性表无 flying_speed）
 * - 跳跃初速 JUMP_STRENGTH
 * - 台阶高度 STEP_HEIGHT
 * - 方块交互距离 BLOCK_INTERACTION_RANGE / 攻击距离 ENTITY_INTERACTION_RANGE
 * - 摔落安全距离 SAFE_FALL_DISTANCE
 * - 重力 GRAVITY：三种模式均 ×√scale（加速度量按平方根联动；线性缩放会使
 *   16 倍体型终端速度约 62 格/tick 失控）
 * 鞘翅滑翔/烟花的位移缩放由 LivingEntityMixin 按移动速度联动因子实现（因子随模式变化）。
 * 所有修改器均为瞬态（transient）、不写入 NBT，规则关闭或 scale 回到 1.0 后自动移除。
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
        String mode = CarpetPrimaryuanSettings.realisticPlayerScale;
        if ("false".equalsIgnoreCase(mode)) {
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
            realisticPlayerScale$updateModifier(
                    self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY), "scale_gravity", 0.0D);
            if (abilities.getFlyingSpeed() != realisticPlayerScale$DEFAULT_FLYING_SPEED) {
                abilities.setFlyingSpeed(realisticPlayerScale$DEFAULT_FLYING_SPEED);
                self.onUpdateAbilities();
            }
            return;
        }

        // true=平缓（√scale 曲线，无保底）；safety=平缓+小体型保底；strict=严格等比（无保底）。
        // 未知取值回落平缓模式（与 options 顺序中的 true 一致）
        boolean strict = "strict".equalsIgnoreCase(mode);
        boolean safety = "safety".equalsIgnoreCase(mode);
        double scale = scaleAttr.getValue();
        double sqrtScale = Math.sqrt(scale);

        // 移动速度：true/safety 用 √scale 平缓曲线，strict 严格等比 ×scale；safety 对小体型保底 0.3
        double speedMul = strict ? scale : sqrtScale;
        if (safety) {
            speedMul = Math.max(speedMul, 0.3D);
        }
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), "scale_speed", speedMul - 1.0D);
        // 跳跃初速：true/safety 用 √scale（跳高 ∝ √scale）；strict 用 scale^0.75
        // （与重力 √scale 配套，jump²/gravity ∝ scale，跳高随体型等比放大）；safety 对小体型保底 0.5
        double jumpMul = strict ? Math.pow(scale, 0.75D) : sqrtScale;
        if (safety) {
            jumpMul = Math.max(jumpMul, 0.5D);
        }
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH), "scale_jump", jumpMul - 1.0D);
        // 台阶高度：true/safety 用 √scale，strict 严格等比 ×scale；safety 对小体型保底 0.5（确保小人可跨地毯）
        double sizeMul = strict ? scale : sqrtScale;
        if (safety) {
            sizeMul = Math.max(sizeMul, 0.5D);
        }
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT), "scale_step", sizeMul - 1.0D);
        // 方块交互 / 攻击距离：与台阶同曲线；safety 对小体型保底 0.5（确保小人可交互）
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE), "scale_reach", sizeMul - 1.0D);
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE), "scale_reach", sizeMul - 1.0D);
        // 摔落安全距离：与台阶同曲线；safety 对小体型保底 0.5（确保小人不被秒杀）
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE), "scale_fall", sizeMul - 1.0D);
        // 重力（下落加速度）：三种模式均 ×√scale（加速度量按平方根联动，线性缩放会使
        // 16 倍体型终端速度约 62 格/tick 失控）；仅 safety 对小体型保底 0.3。
        // 创造飞行时原版会覆盖竖直速度使重力无效；鞘翅滑翔的重力项则走本属性
        double gravityMul = safety ? Math.max(sqrtScale, 0.3D) : sqrtScale;
        realisticPlayerScale$updateModifier(
                self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY), "scale_gravity", gravityMul - 1.0D);

        // 飞行速度：与移动速度同曲线（true/safety √scale、strict 线性，safety 有保底）
        double flyMul = speedMul;
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
