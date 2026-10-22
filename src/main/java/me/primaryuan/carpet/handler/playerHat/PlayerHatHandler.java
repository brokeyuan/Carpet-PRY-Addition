package me.primaryuan.carpet.handler.playerHat;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 原使用完成后的整组交换，不再调用物品使用或交互事件。 */
public final class PlayerHatHandler {
    private PlayerHatHandler() {}

    public static Snapshot beforeUse(ServerPlayer player, InteractionHand hand) {
        if (!eligible(player, hand)) return null;
        ItemStack main = player.getMainHandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        return new Snapshot(main, main.copy(), head, head.copy());
    }

    public static boolean tryEquip(ServerPlayer player, InteractionHand hand, Snapshot before) {
        if (before == null || !eligible(player, hand)) return false;
        ItemStack main = player.getMainHandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (main != before.main || head != before.head
                || !ItemStack.matches(main, before.mainCopy) || !ItemStack.matches(head, before.headCopy)
                || ItemStack.isSameItemSameComponents(main, head)
                || (!player.isCreative() && EnchantmentHelper.has(head, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                || !aimsAtAir(player)) return false;
        player.setItemSlot(EquipmentSlot.HEAD, main);
        player.setItemInHand(InteractionHand.MAIN_HAND, head);
        return true;
    }

    private static boolean eligible(ServerPlayer player, InteractionHand hand) {
        return CarpetPrimaryuanSettings.playerHat && hand == InteractionHand.MAIN_HAND
                && player.isAlive() && !player.isSpectator() && !player.isUsingItem()
                && player.getOffhandItem().isEmpty() && player.canUseSlot(EquipmentSlot.HEAD)
                && HatUsePolicy.allows(player.getMainHandItem());
    }

    private static boolean aimsAtAir(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 direction = useDirection(player);
        if (player.level().clip(new ClipContext(start, start.add(direction.scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return false;
        Vec3 ray = direction.scale(player.entityInteractionRange());
        return ProjectileUtil.getEntityHitResult(player, start, start.add(ray),
                player.getBoundingBox().expandTowards(ray).inflate(1.0),
                entity -> !entity.isSpectator() && entity.isPickable(), ray.lengthSqr()) == null;
    }

    static Vec3 useDirection(ServerPlayer player) {
        // 使用包更新当前视角；getViewVector 会读可能滞后的头部动画朝向。
        return player.calculateViewVector(player.getXRot(), player.getYRot());
    }

    public record Snapshot(ItemStack main, ItemStack mainCopy, ItemStack head, ItemStack headCopy) {}
}
