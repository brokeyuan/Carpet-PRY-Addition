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
 * 注入点按版本分支（已用映射后字节码逐一核实）：
 * - 1.21.3+：travelFallFlying（该拆分自 1.21.2 起），方法内 move 调用仅在
 *   鞘翅状态下执行，无需额外甄别；
 * - 1.21~1.21.1：无 travelFallFlying 拆分，鞘翅物理在 travel(Vec3) 内联，
 *   改注入 travel 的 move 调用点——该方法内 move 为各移动分支共用，
 *   处理器内以 isFallFlying() 甄别，仅鞘翅时缩放。
 * 注意 @At target 的 owner 是 LivingEntity：编译器对继承方法调用点
 * （this.move(...)）按接收者静态类型编译 owner，1.21~26.2 各版本的
 * 运行时字节码均为 LivingEntity（1.21.x intermediary 为 class_1309;
 * method_5784(class_1313;class_243)）——写成声明类 Entity 反而匹配 0 目标。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyArg(
            //#if MC >= 12103
            method = "travelFallFlying",
            //#else
            //$$ method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
            //#endif
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"
            )
    )
    private Vec3 realisticPlayerScale$scaleElytraMovement(Vec3 movement) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof net.minecraft.world.entity.player.Player)) {
            return movement;
        }
        //#if MC < 12103
        // 1.21~1.21.1 的注入点是 travel 内各移动分支共用的 move 调用，仅鞘翅分支缩放
        if (!self.isFallFlying()) {
            return movement;
        }
        //#endif
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
    }
}
