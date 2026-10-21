package me.primaryuan.carpet.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DropOnceTest {
    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void allDropsEveryOccupiedSlotIncludingEquipmentAndOffhand() throws Exception {
        var player = player();
        player.inventory.setItem(0, new ItemStack(Items.STONE, 64));
        player.inventory.setItem(5, new ItemStack(Items.DIAMOND, 3));
        player.inventory.setItem(39, new ItemStack(Items.IRON_HELMET));
        player.inventory.setItem(40, new ItemStack(Items.TORCH, 7));

        assertEquals(75, DropSlotScheduler.dropOnce(player, DropSlotScheduler.SLOT_ALL));
        assertTrue(player.inventory.isEmpty());
        assertEquals(4, player.dropped.size());
        assertEquals(75, player.dropped.stream().mapToInt(ItemStack::getCount).sum());
    }

    @Test
    void specificSlotLeavesTheRestOfTheInventoryUntouched() throws Exception {
        var player = player();
        player.inventory.setItem(0, new ItemStack(Items.STONE, 64));
        player.inventory.setItem(40, new ItemStack(Items.TORCH, 7));

        assertEquals(7, DropSlotScheduler.dropOnce(player, DropSlotScheduler.SLOT_OFFHAND));
        assertEquals(64, player.inventory.getItem(0).getCount());
        assertTrue(player.inventory.getItem(40).isEmpty());
        assertEquals(1, player.dropped.size());
    }

    @Test
    void emptyInventoryReturnsZeroAndDropsNothing() throws Exception {
        var player = player();
        assertEquals(0, DropSlotScheduler.dropOnce(player, DropSlotScheduler.SLOT_ALL));
        assertTrue(player.dropped.isEmpty());
    }

    private static StubPlayer player() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var player = (StubPlayer) ((Unsafe) field.get(null)).allocateInstance(StubPlayer.class);
        player.inventory = new Inventory(player, new EntityEquipment());
        player.dropped = new ArrayList<>();
        return player;
    }

    /** 只替代需要世界的掉落物生成，库存与丢弃逻辑使用真实实现。 */
    private static final class StubPlayer extends ServerPlayer {
        private Inventory inventory;
        private List<ItemStack> dropped;
        private StubPlayer() { super(null, null, null, null); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public ItemEntity drop(ItemStack stack, boolean includeThrower, Prediction prediction) {
            dropped.add(stack.copy());
            return null;
        }
    }
}
