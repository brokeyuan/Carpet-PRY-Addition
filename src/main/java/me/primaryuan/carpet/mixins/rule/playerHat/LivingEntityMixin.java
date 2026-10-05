package me.primaryuan.carpet.mixins.rule.playerHat;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
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

import java.util.List;

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

        // 原版首行早退必须尊重（26.3 字节码）：/kill、虚空等
        // BYPASSES_INVULNERABILITY 伤害不受图腾保护——RETURN 注入把所有 false 都当成
        // "手中无图腾"会连这类伤害一起复活
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
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

        // 清除全部负面效果（对齐原版 DEATH_PROTECTION 组件的 remove_effects 消费效果：
        // 凋零/毒等负面状态不随图腾保留）。快照迭代，removeEffect 会就地改表
        for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }

        player.setHealth(1.0F);
        // 参数对齐原版图腾（1.21.11 DeathProtection 组件字节码实测）：
        // REGENERATION(900,1) / ABSORPTION(100,1) / FIRE_RESISTANCE(800,0)。
        // 旧值 40/200/40 与原版全不符（火抗仅 2 秒，触发后仍可能被烧死）
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));

        player.level().broadcastEntityEvent(player, (byte) 35);

        // 统计与进度（原版流程）：图腾使用统计 + USED_TOTEM 触发"超越生死"进度。
        // 对齐原版字节码顺序：copy → shrink 原栈 → 触发器收副本——原栈 shrink 到空时
        // 触发器收到空栈，进度条件 items 匹配失败，"最后一颗图腾"场景不授予进度
        ItemStack totemCopy = headItem.copy();
        headItem.shrink(1);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
            //#if MC >= 260200
            //$$ net.minecraft.advancements.triggers.CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, totemCopy);
            //#else
            net.minecraft.advancements.CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, totemCopy);
            //#endif
        }

        cir.setReturnValue(true);
    }
}