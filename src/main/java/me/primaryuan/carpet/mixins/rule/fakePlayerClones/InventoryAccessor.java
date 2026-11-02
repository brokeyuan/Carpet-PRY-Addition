package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 直接访问 Inventory 的底层槽位容器，用于把同组身体接到同一份存储。
 * final 字段通过 @Mutable 访问器替换（仅初始化阶段，服务端线程）。
 */
@Mixin(Inventory.class)
public interface InventoryAccessor {

    @Accessor("items")
    NonNullList<ItemStack> pry$items();

    @Mutable
    @Accessor("items")
    void pry$setItems(NonNullList<ItemStack> items);

    //#if MC >= 12105
    @Accessor("equipment")
    net.minecraft.world.entity.EntityEquipment pry$equipment();

    @Mutable
    @Accessor("equipment")
    void pry$setEquipment(net.minecraft.world.entity.EntityEquipment equipment);
    //#else
    //$$ @Accessor("armor")
    //$$ NonNullList<ItemStack> pry$armor();
    //$$
    //$$ @Mutable
    //$$ @Accessor("armor")
    //$$ void pry$setArmor(NonNullList<ItemStack> armor);
    //$$
    //$$ @Accessor("offhand")
    //$$ NonNullList<ItemStack> pry$offhand();
    //$$
    //$$ @Mutable
    //$$ @Accessor("offhand")
    //$$ void pry$setOffhand(NonNullList<ItemStack> offhand);
    //$$
    //$$ @Accessor("compartments")
    //$$ List<NonNullList<ItemStack>> pry$compartments();
    //$$
    //$$ @Mutable
    //$$ @Accessor("compartments")
    //$$ void pry$setCompartments(List<NonNullList<ItemStack>> compartments);
    //#endif
}
