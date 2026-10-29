package me.primaryuan.carpet.handler.playerHat;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HatUsePolicyTest {
    @BeforeAll static void bootstrap() { TestRegistries.bootstrap(); }

    @Test void ordinaryItemsAndBlocksAreAllowed() {
        for (Item item : new Item[]{Items.STONE, Items.DIRT, Items.DIAMOND, Items.PLAYER_HEAD, Items.TOTEM_OF_UNDYING}) {
            assertTrue(HatUsePolicy.allows(new ItemStack(item)), item.toString());
        }
    }

    @Test void functionalItemsAreExcludedEvenWhenTheirUseWouldPass() {
        for (Item item : new Item[]{Items.APPLE, Items.POTION, Items.BUCKET, Items.WATER_BUCKET,
                Items.BOW, Items.SHIELD, Items.SNOWBALL, Items.IRON_HELMET, Items.DIAMOND_AXE,
                Items.DIAMOND_HOE, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.MACE,
                Items.TRIDENT, Items.SHEARS, Items.FLINT_AND_STEEL, Items.FISHING_ROD}) {
            assertFalse(HatUsePolicy.allows(new ItemStack(item)), item.toString());
        }
    }

    @Test void componentsAreCheckedPerStackRatherThanCachedPerItemClass() {
        var plain = new ItemStack(Items.DIAMOND);
        var food = plain.copy(); food.set(DataComponents.FOOD, new ItemStack(Items.APPLE).get(DataComponents.FOOD));
        var edible = plain.copy(); edible.set(DataComponents.CONSUMABLE, new ItemStack(Items.APPLE).get(DataComponents.CONSUMABLE));
        var equipment = plain.copy(); equipment.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build());
        assertTrue(HatUsePolicy.allows(plain));
        assertFalse(HatUsePolicy.allows(food));
        assertFalse(HatUsePolicy.allows(edible));
        assertFalse(HatUsePolicy.allows(equipment));
        assertTrue(HatUsePolicy.allows(plain));
    }

    @Test void entityEquippingComponentsAreFunctionalEvenWithoutAirUse() {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                .setSwappable(false).setEquipOnInteract(true).build());
        assertFalse(HatUsePolicy.allows(stack));
    }

    @Test void passReturningCustomInteractionsAreStillExcluded() {
        assertFalse(HatUsePolicy.hasCustomInteraction(Item.class));
        assertFalse(HatUsePolicy.hasCustomInteraction(BlockItem.class));
        assertTrue(HatUsePolicy.hasCustomInteraction(AirUse.class));
        assertTrue(HatUsePolicy.hasCustomInteraction(BlockUse.class));
        assertTrue(HatUsePolicy.hasCustomInteraction(EntityUse.class));
        assertTrue(HatUsePolicy.hasCustomInteraction(InheritedUse.class));
    }

    @Test void blacklistRebindingIsVisibleAfterClassDecisionWasCached() throws Exception {
        var stack = new ItemStack(Items.DIAMOND);
        var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOrThrow(net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(Items.DIAMOND).orElseThrow());
        var original = holder.tags().toList();
        var bind = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bind.setAccessible(true);
        assertTrue(HatUsePolicy.allows(stack));
        try {
            var tags = new java.util.ArrayList<>(original);
            tags.add(HatUsePolicy.BLACKLIST);
            bind.invoke(holder, tags);
            assertFalse(HatUsePolicy.allows(stack));
        } finally {
            bind.invoke(holder, original);
        }
        assertTrue(HatUsePolicy.allows(stack));
    }

    private static class AirUse extends Item {
        AirUse() { super(new Item.Properties()); }
        @Override public InteractionResult use(Level level, Player player, InteractionHand hand) { return InteractionResult.PASS; }
    }
    private static class InheritedUse extends AirUse {}
    private static class BlockUse extends Item {
        BlockUse() { super(new Item.Properties()); }
        @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.PASS; }
    }
    private static class EntityUse extends Item {
        EntityUse() { super(new Item.Properties()); }
        @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) { return InteractionResult.PASS; }
    }
}
