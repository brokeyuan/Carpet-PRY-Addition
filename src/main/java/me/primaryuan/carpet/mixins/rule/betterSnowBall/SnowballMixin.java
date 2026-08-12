package me.primaryuan.carpet.mixins.rule.betterSnowBall;

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

@Mixin(targets = {
        "net.minecraft.world.entity.projectile.Snowball",
        "net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball"
})
public abstract class SnowballMixin {

    @Inject(
            method = "onHitEntity",
            at = @At("HEAD")
    )
    private void onBetterSnowBallHit(EntityHitResult result, CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.betterSnowBall) {
            return;
        }

        Entity target = result.getEntity();
        if (!(target instanceof Player player)) {
            return;
        }

        Entity self = (Entity) (Object) this;

        // Knockback: push player in the snowball's travel direction
        Vec3 motion = self.getDeltaMovement().normalize().scale(0.5);
        player.push(motion.x, 0.2, motion.z);
        player.hurtMarked = true;

        // Damage: 2.0 = 1 heart
        Projectile projectile = (Projectile) (Object) this;
        var damageSource = player.damageSources().thrown(self, projectile.getOwner());
        //#if MC >= 12103
        // 1.21.3+: hurt(DamageSource, float) 已弃用，迁移到 hurtServer
        player.hurtServer((ServerLevel) self.level(), damageSource, 2.0F);
        //#else
        //$$ // 1.21 / 1.21.1: 旧 API 尚未弃用
        //$$ player.hurt(damageSource, 2.0F);
        //#endif
    }
}
