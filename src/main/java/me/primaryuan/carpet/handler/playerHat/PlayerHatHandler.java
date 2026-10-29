package me.primaryuan.carpet.handler.playerHat;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
//#if MC < 12103
//$$ import net.minecraft.world.item.ArmorItem;
//#else
import net.minecraft.world.item.equipment.Equippable;
//#endif
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        if (isProtectedHeadItem(head)
                || main != before.main || head != before.head
                || !ItemStack.matches(main, before.mainCopy) || !ItemStack.matches(head, before.headCopy)
                || ItemStack.isSameItemSameComponents(main, head)
                || (!player.isCreative() && EnchantmentHelper.has(head, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                || !aimsAtAir(player)) return false;
        player.setItemSlot(EquipmentSlot.HEAD, main);
        player.setItemInHand(InteractionHand.MAIN_HAND, head);
        return true;
    }

    /**
     * 头部已有正常护甲（含海龟壳）或图腾时禁止右键换帽，防误摘防护装备。
     * 只看头部槽位，不影响 /hat 命令。盔甲识别以原版 #minecraft:head_armor
     * 标签为准（26.x 含铜盔，1.21~1.21.10 无铜盔，数据驱动）；ArmorItem 实例
     * 兜底接住未入标签的模组盔甲（1.21.5 起原版不再有 ArmorItem，仅作 instanceof）；
     * EQUIPPABLE 兜底只收 swappable 的组件——南瓜、头颅这类 swappable=false 的
     * 可穿戴物不在此列；damageOnHurt 再挡掉可交换但非护甲的头戴物（烟花等）。
     * 图腾单独特判。
     */
    static boolean isProtectedHeadItem(ItemStack head) {
        if (head.is(Items.TOTEM_OF_UNDYING)) return true;
        if (head.isEmpty()) return false;
        if (head.is(ItemTags.HEAD_ARMOR)) return true;
        //#if MC >= 12103
        // 1.21.3+ 盔甲全部携带 EQUIPPABLE（原版走 ArmorMaterial.humanoidProperties），
        // swappable+damageOnHurt 过滤掉南瓜、头颅等不可交换的可穿戴物。
        // 26.x 铜盔、模组盔甲（含 1.21.5+ 无 ArmorItem 的版本）都由这里接住。
        var equippable = head.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.HEAD
                && equippable.swappable() && equippable.damageOnHurt();
        //#else
        //$$ // 1.21/1.21.1 无 EQUIPPABLE 组件，按类型判定；ArmorItem 兜底未入标签的模组盔甲
        //$$ return head.getItem() instanceof ArmorItem armor && armor.getType() == ArmorItem.Type.HELMET;
        //#endif
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
