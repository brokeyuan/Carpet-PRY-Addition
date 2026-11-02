package me.primaryuan.carpet.clones;

import me.primaryuan.carpet.mixins.rule.fakePlayerClones.InventoryAccessor;
//#if MC >= 12105
import me.primaryuan.carpet.mixins.rule.fakePlayerClones.EntityEquipmentAccessor;
import me.primaryuan.carpet.mixins.rule.fakePlayerClones.LivingEntityEquipmentAccessor;
//#endif
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 一组分身共享的权威槽位存储。
 *
 * <p>只共享底层容器对象，不共享 Inventory/PlayerEquipment 外壳：
 * 每个身体保留自己的 Inventory（selected、player 引用）与 PlayerEquipment
 * （MAINHAND 由该身体的 selected 决定）。</p>
 *
 * <ul>
 *   <li>1.21~1.21.4：items / armor / offhand 三个 NonNullList 与 compartments
 *       一起替换为同一组对象，getItem/setItem/removeItem 全部落同一存储。</li>
 *   <li>1.21.5+：共享 items 与 EntityEquipment 内部的 EnumMap；每具身体的
 *       PlayerEquipment 仍各自绑定自己，只有装备 map 是同一对象。</li>
 * </ul>
 */
public final class SharedInventory {

    private final NonNullList<ItemStack> items;
    //#if MC >= 12105
    private final java.util.EnumMap<EquipmentSlot, ItemStack> equipment;
    //#else
    //$$ private final NonNullList<ItemStack> armor;
    //$$ private final NonNullList<ItemStack> offhand;
    //#endif
    /** 首个成员是否已完成 playerdata 读取（避免后续成员用磁盘旧数据覆盖权威状态） */
    private boolean loaded;

    //#if MC >= 12105
    private SharedInventory(NonNullList<ItemStack> items,
                            java.util.EnumMap<EquipmentSlot, ItemStack> equipment) {
        this.items = items;
        this.equipment = equipment;
    }
    //#else
    //$$ private SharedInventory(NonNullList<ItemStack> items, NonNullList<ItemStack> armor,
    //$$                        NonNullList<ItemStack> offhand) {
    //$$     this.items = items;
    //$$     this.armor = armor;
    //$$     this.offhand = offhand;
    //$$ }
    //#endif

    /** 以首个成员当前（刚完成原版加载）的容器建立权威存储。 */
    public static SharedInventory adopt(ServerPlayer player) {
        Inventory inv = player.getInventory();
        InventoryAccessor invAcc = (InventoryAccessor) (Object) inv;
        //#if MC >= 12105
        var equip = ((LivingEntityEquipmentAccessor) player).pry$equipment();
        return new SharedInventory(invAcc.pry$items(),
                ((EntityEquipmentAccessor) (Object) equip).pry$items());
        //#else
        //$$ return new SharedInventory(invAcc.pry$items(), invAcc.pry$armor(), invAcc.pry$offhand());
        //#endif
    }

    /** 把另一具身体接到同一存储；只换容器，不动 selected / player 外壳。 */
    public void bind(ServerPlayer player) {
        Inventory inv = player.getInventory();
        InventoryAccessor invAcc = (InventoryAccessor) (Object) inv;
        invAcc.pry$setItems(items);
        //#if MC >= 12105
        var equip = ((LivingEntityEquipmentAccessor) player).pry$equipment();
        ((EntityEquipmentAccessor) (Object) equip).pry$setItems(equipment);
        //#else
        //$$ invAcc.pry$setArmor(armor);
        //$$ invAcc.pry$setOffhand(offhand);
        //$$ invAcc.pry$setCompartments(java.util.List.of(items, armor, offhand));
        //#endif
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void markLoaded() {
        loaded = true;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }
}
