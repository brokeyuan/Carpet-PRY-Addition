package me.primaryuan.carpet.mixins.rule.realisticPlayerScale;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * realisticPlayerScale 规则的鞘翅滑翔联动。
 *
 * 原版鞘翅滑翔的移动距离不随 minecraft:scale 缩放：travelFallFlying 内的
 * 转向/升力/阻力项全是硬编码常量，烟花推进（FireworkRocketEntity）也只是把
 * 速度拉向"视线方向 × 1.5"的固定控制器——大体型滑翔、烟花加速的极速与
 * 普通体型完全相同，只有重力项随重力属性（√scale）生效。
 *
 * 这里对 travelFallFlying 中 move() 的位移参数按移动速度联动因子缩放
 * （因子 = 1 + scale_speed 修改器 amount，与行走/创造飞行速度同曲线，随
 * realisticPlayerScale 模式变化：true/safety 为 √scale 平缓曲线，strict 为
 * 线性 scale；safety 模式因子含小体型 0.3 保底）。只缩放传给 move 的
 * 位移、不改写实体存储的速度，物理无正反馈、任意体型下都稳定收敛；
 * 滑翔极速、烟花极速与转向半径随之随体型等比变化（大体型转向半径更大，
 * 符合几何相似）。
 *
 * 已知取舍：撞墙伤害（handleFallFlyingCollisions）按存储速度计算，仍为
 * 原版量级，不随位移缩放。
 *
 * 生效判定与 FOV 补偿一致：以移动速度属性上是否存在我们的 scale_speed
 * 瞬态修改器为准（该修改器仅在实际生效且 scale≠1.0 时存在，并随属性同步
 * 到客户端），不读取 Carpet 规则字段，服务端（含假人）与客户端本地预测
 * 行为一致，无 desync。
 *
 * 仅在 Minecraft 1.21.5+ 生效：低于该版本无 scale 属性与 travelFallFlying
 * 拆分，方法体被预处理清空，且 mixins.json 不注册本类。
 * 注意 @At target 的 owner 是 Entity（move 声明于 Entity，LivingEntity 未
 * 覆写，调用点的字节码 owner 为声明类）——写成 LivingEntity 会匹配 0 目标。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyArg(
            method = "travelFallFlying",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/phys/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private Vec3 realisticPlayerScale$scaleElytraMovement(Vec3 movement) {
        //#if MC >= 12105
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.world.entity.player.Player)) {
            return movement;
        }
        AttributeInstance speedAttr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) {
            return movement;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier modifierId = net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#else
        //$$ net.minecraft.resources.ResourceLocation modifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "scale_speed");
        //#endif
        AttributeModifier scaleModifier = speedAttr.getModifier(modifierId);
        if (scaleModifier == null) {
            return movement; // 规则未生效或 scale == 1.0（修改器不存在）
        }
        double factor = 1.0D + scaleModifier.amount();
        if (!Double.isFinite(factor) || factor <= 0.0D || factor == 1.0D) {
            return movement;
        }
        return movement.scale(factor);
        //#else
        //$$ return movement;
        //#endif
    }
}
