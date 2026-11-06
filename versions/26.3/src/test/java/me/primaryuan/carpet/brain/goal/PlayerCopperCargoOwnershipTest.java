package me.primaryuan.carpet.brain.goal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 铜傀儡货物归属（八期）：旧库存（前槽同物 64）不被当本趟货物；
 * 本趟 ≤16；异组件不混投；放货只投本趟数量、旧库存原样。
 */
class PlayerCopperCargoOwnershipTest {

    static class GPlayer extends BrainTestStubs.FakePlayer {
        Map<BlockPos, SimpleContainer> containers = new HashMap<>();

        static GPlayer create(ServerLevel world, Vec3 pos) {
            GPlayer p = BrainTestStubs.FakePlayer.createFakeOf(GPlayer.class, UUID.randomUUID());
            p.containers = new HashMap<>();
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            return p;
        }
    }

    private ServerLevel world;
    private GPlayer golem;
    private SimpleContainer source;
    private SimpleContainer dest;

    private ChestMenu menu(SimpleContainer c) {
        return new ChestMenu(MenuType.GENERIC_9x3, 1, golem.getInventory(), c, 3);
    }

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        world = (ServerLevel) BrainTestStubs.stubWorld();
        golem = GPlayer.create(world, new Vec3(5.5, 64, 5.5));
        source = new SimpleContainer(27);
        dest = new SimpleContainer(27);
    }

    @Test
    void oldStockStaysUntouchedWhenTakingAndPlacing() {
        // 背包前槽已有 64 泥土（同物旧库存），本趟从铜箱取 16
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
        source.setItem(0, new ItemStack(Items.DIRT, 64));
        var m = menu(source);
        int[] r = PlayerCopperGolemSortGoal.takeViaMenu(m, golem, new int[]{-1, -1});
        assertEquals(16, r[0], "本趟 16");
        assertEquals(48, source.getItem(0).getCount(), "源箱 64-16=48");
        // 放货：目标箱同物 64（可合并 0 空间）+ 空槽 → 只投本趟 16 到空槽，旧库存 64 原样
        dest.setItem(0, new ItemStack(Items.DIRT, 64));
        var dm = menu(dest);
        int placed = PlayerCopperGolemSortGoal.placeViaMenu(dm, golem, new ItemStack(Items.DIRT), 16);
        assertEquals(16, placed, "只投本趟 16");
        assertEquals(64, golem.getInventory().getItem(0).getCount(), "背包旧库存 64 原样（合并读数被还原）");
        assertEquals(16, dest.getItem(1).getCount(), "本趟入目标箱空槽");
    }

    @Test
    void differentComponentsAreNotMixed() {
        // 同 Item 不同组件：附魔组 ≠ 普通组
        ItemStack enchanted = new ItemStack(Items.DIRT);
        enchanted.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("X"));
        golem.getInventory().setItem(0, enchanted);
        source.setItem(0, new ItemStack(Items.DIRT, 16)); // 普通组
        var m = menu(source);
        int[] r = PlayerCopperGolemSortGoal.takeViaMenu(m, golem, new int[]{-1, -1});
        assertEquals(16, r[0]);
        // 放货：carryItem=普通组 → 目标箱有附魔组不并、入空槽
        dest.setItem(0, enchanted.copy());
        var dm = menu(dest);
        int placed = PlayerCopperGolemSortGoal.placeViaMenu(dm, golem, new ItemStack(Items.DIRT), 16);
        assertEquals(16, placed, "异组件组不混投（入空槽）");
        assertEquals(0, golem.getInventory().getItem(1).getCount(), "普通组整组出（交付后玩家侧为空）");
        assertEquals(1, dest.getItem(0).getCount(), "附魔组不动");
        assertEquals(16, dest.getItem(1).getCount(), "普通组入目标箱空槽");
    }

    @Test
    void partialPlacementKeepsRemaining() {
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        dest.setItem(0, new ItemStack(Items.DIRT, 60)); // 只能再并 4
        for (int i = 1; i < 27; i++) {
            dest.setItem(i, new ItemStack(Items.DIRT, 64)); // 无空槽：真正"部分投递"场景
        }
        var dm = menu(dest);
        int placed = PlayerCopperGolemSortGoal.placeViaMenu(dm, golem, new ItemStack(Items.DIRT), 16);
        assertEquals(4, placed, "目标满：只并 4");
        assertEquals(0, dm.getCarried().getCount(), "余量经正常菜单点击退回货物槽，光标不残留");
        assertEquals(64, dest.getItem(0).getCount());
        assertEquals(12, golem.getInventory().getItem(0).getCount(), "未送出的 12 留回本趟货物槽");
    }
}
