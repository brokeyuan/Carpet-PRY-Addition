package me.primaryuan.carpet.mixins.rule.betterSnowball;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Snowball 类在 1.21.11 起移入 throwableitemprojectile 子包；
// 按版本选取唯一存在的 target，避免多 targets 下必然出现的 "@Mixin target was not found" 警告
//#if MC >= 12111
@Mixin(targets = "net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball")
//#else
//$$ @Mixin(targets = "net.minecraft.world.entity.projectile.Snowball")
//#endif
public abstract class SnowballMixin {

    @Inject(
            method = "onHitEntity",
            at = @At("HEAD")
    )
    private void onBetterSnowBallHit(EntityHitResult result, CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.betterSnowball) {
            return;
        }

        Entity target = result.getEntity();
        if (!(target instanceof Player player)) {
            return;
        }

        Entity self = (Entity) (Object) this;

        // 客户端逻辑侧不做任何处理：单人游戏中客户端线程同样会执行本注入点，
        // 而 hurtServer 需要 ServerLevel，ClientLevel 强转会直接崩溃；
        // 击退/伤害只允许服务端施加，客户端本地预测也会造成抖动
        if (self.level().isClientSide()) {
            return;
        }

        // Damage: 2.0 = 1 heart
        Projectile projectile = (Projectile) (Object) this;
        var damageSource = player.damageSources().thrown(self, projectile.getOwner());
        //#if MC >= 12103
        // 1.21.3+: hurt(DamageSource, float) 已弃用，迁移到 hurtServer
        boolean hurt = player.hurtServer((ServerLevel) self.level(), damageSource, 2.0F);
        //#else
        //$$ // 1.21 / 1.21.1: 旧 API 尚未弃用
        //$$ boolean hurt = player.hurt(damageSource, 2.0F);
        //#endif

        // Knockback: push player in the snowball's travel direction.
        // 仅在伤害真正生效时施加：hurt 被无敌帧拦下（同一目标 i-frame 内重复命中）
        // 或目标本身无敌（创造/旁观）时返回 false，此时不再 push，
        // 避免多雪球同时命中时击退绕过无敌帧成倍叠加
        if (hurt) {
            Vec3 motion = self.getDeltaMovement().normalize().scale(0.5);
            player.push(motion.x, 0.2, motion.z);
            //#if MC >= 260300
            //$$ // 26.3: Entity.hurtMarked 拆分为 needsSync/syncVelocity（markHurt 现写 syncVelocity）
            //$$ player.syncVelocity = true;
            //#else
            player.hurtMarked = true;
            //#endif
        }
    }
}
