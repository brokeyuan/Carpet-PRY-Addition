package me.primaryuan.carpet.mixins.rule.playerScaleLinkedEntities;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * playerScaleLinkedEntities 规则的缩放端。
 *
 * ServerLevel.addFreshEntity 是服务端实体添加的收口：
 * 盔甲架（ArmorStandItem）走的 addFreshEntityWithPassengers 是
 * ServerLevelAccessor 的 default 方法，内部逐个委托 addFreshEntity；
 * 刷怪蛋等物品路径，以及铁傀儡/雪傀儡/铜傀儡的构造生成（放南瓜的 onPlace
 * 同步发生在玩家的 useItemOn 漏斗内）同样最终落到这里。
 * 在此读取 SpawnContext 中的"当前操作玩家"，把玩家当前 minecraft:scale
 * 属性值写入生成生物的基础值——模型、碰撞箱随原版属性自动生效并同步到
 * 客户端（含纯原版客户端），幼崽等已有修饰符在此基础上叠加（比例保持）。
 *
 * 覆盖范围与边界：
 * - 仅覆盖有 scale 属性的生物实体；投掷物、掉落物、物品展示框、船、矿车、
 *   TNT 等非生物实体不联动（不触碰渲染与碰撞箱）
 * - 玩家体型为 1.0 时不写任何值，不干扰模组自定义的基础体型
 * - 写入的是快照基础值，生成后不随玩家体型变化
 * - 发射器、刷怪笼等非玩家来源不在物品使用漏斗内，不联动
 * 方法按名注入（addFreshEntity 为 ServerLevel 对 LevelWriter 的唯一覆写，
 * 1.21.5~26.2 已逐一验证存在且无重载）。
 * 仅在 Minecraft 1.21.5+ 注册（mixins.json 门控）。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void playerScaleLinkedEntities$scaleSpawnedEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        //#if MC >= 12105
        if (!CarpetPrimaryuanSettings.playerScaleLinkedEntities) {
            return;
        }
        net.minecraft.server.level.ServerPlayer player = SpawnContext.ACTING_PLAYER.get();
        if (player == null || !(entity instanceof LivingEntity living)) {
            return;
        }
        AttributeInstance playerScaleAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (playerScaleAttr == null) {
            return;
        }
        double playerScale = playerScaleAttr.getValue();
        if (!Double.isFinite(playerScale) || playerScale <= 0.0D || playerScale == 1.0D) {
            return;
        }
        AttributeInstance scaleAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scaleAttr != null && scaleAttr.getBaseValue() != (float) playerScale) {
            scaleAttr.setBaseValue(playerScale);
        }
        //#endif
    }
}
