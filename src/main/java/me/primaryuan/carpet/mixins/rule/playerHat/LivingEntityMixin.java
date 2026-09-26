package me.primaryuan.carpet.mixins.rule.playerHat;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void playerHat$allowPlaceAnyItemInHeadEquipmentSlot(ItemStack itemStack, EquipmentSlot equipmentSlot, CallbackInfoReturnable<Boolean> cir) {
        if (!CarpetPrimaryuanSettings.playerHat) {
            return;
        }

        if (equipmentSlot == EquipmentSlot.HEAD) {
            LivingEntity livingEntity = (LivingEntity) (Object) this;
            if (livingEntity.isAlwaysTicking()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void playerHat$useHeadTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }

        if (!CarpetPrimaryuanSettings.playerHat) {
            return;
        }

        if (!((Object) this instanceof Player player)) {
            return;
        }

        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);

        if (!headItem.is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        player.setHealth(1.0F);
        // 参数对齐原版图腾（1.21.11 DeathProtection 组件字节码实测）：
        // REGENERATION(900,1) / ABSORPTION(100,1) / FIRE_RESISTANCE(800,0)。
        // 旧值 40/200/40 与原版全不符（火抗仅 2 秒，触发后仍可能被烧死）
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

        player.level().broadcastEntityEvent(player, (byte) 35);

        headItem.shrink(1);

        cir.setReturnValue(true);
    }
}