package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

//#if MC >= 12105
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.EnumMap;

/**
 * 共享 EntityEquipment 的底层 EnumMap。PlayerEquipment 外壳不共享：
 * 其 get/set(MAINHAND) 必须继续委托给各自身体的 Inventory.selected。
 */
@Mixin(EntityEquipment.class)
public interface EntityEquipmentAccessor {

    @Accessor("items")
    EnumMap<EquipmentSlot, ItemStack> pry$items();

    @Mutable
    @Accessor("items")
    void pry$setItems(EnumMap<EquipmentSlot, ItemStack> items);
}
//#endif
