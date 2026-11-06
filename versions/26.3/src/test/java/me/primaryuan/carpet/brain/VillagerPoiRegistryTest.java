package me.primaryuan.carpet.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 村民 POI 占用登记表锁（原版 PoiManager 占用语义的自建等价）：
 * 床/工作站容量 1（他人登记失败）、本人幂等；释放（Goal stop/卸载）、
 * 失效（方块被破坏）、停服清空。变异验证：容量判定移除 → 容量用例失败。
 */
class VillagerPoiRegistryTest {

    private Level world;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        VillagerPoiRegistry.clear();
        world = BrainTestStubs.stubWorld();
    }

    @Test
    void bedAndWorkstationCapacityOne() {
        BlockPos bed = new BlockPos(4, 64, 4);
        assertTrue(VillagerPoiRegistry.claim(a, world, bed, VillagerPoiRegistry.Kind.BED));
        assertTrue(VillagerPoiRegistry.isOccupied(world, bed));
        assertFalse(VillagerPoiRegistry.claim(b, world, bed, VillagerPoiRegistry.Kind.BED),
                "容量 1：其他村民登记失败（原版床位单占用）");
        assertTrue(VillagerPoiRegistry.claim(a, world, bed, VillagerPoiRegistry.Kind.BED),
                "本人重复登记幂等成功");

        assertFalse(VillagerPoiRegistry.isOccupied(world, new BlockPos(9, 64, 9)),
                "未登记点不视为占用");
    }

    @Test
    void meetingPointUnlimited() {
        BlockPos bell = new BlockPos(0, 64, 0);
        assertTrue(VillagerPoiRegistry.claim(a, world, bell, VillagerPoiRegistry.Kind.MEETING));
        assertTrue(VillagerPoiRegistry.claim(b, world, bell, VillagerPoiRegistry.Kind.MEETING),
                "集合点不限占用（原版 MEETING poi 容量语义）");
    }

    @Test
    void releaseAllOnDetach() {
        assertTrue(VillagerPoiRegistry.claim(a, world, new BlockPos(1, 64, 1), VillagerPoiRegistry.Kind.BED));
        assertTrue(VillagerPoiRegistry.claim(a, world, new BlockPos(2, 64, 2), VillagerPoiRegistry.Kind.WORKSTATION));
        assertTrue(VillagerPoiRegistry.releaseAll(a), "卸载释放至少一处");
        assertFalse(VillagerPoiRegistry.isOccupied(world, new BlockPos(1, 64, 1)));
        assertFalse(VillagerPoiRegistry.isOccupied(world, new BlockPos(2, 64, 2)));
        assertFalse(VillagerPoiRegistry.releaseAll(a), "无占用再释放返回 false");
    }

    @Test
    void invalidateOnBlockChange() {
        BlockPos bed = new BlockPos(5, 64, 5);
        assertTrue(VillagerPoiRegistry.claim(a, world, bed, VillagerPoiRegistry.Kind.BED));
        VillagerPoiRegistry.invalidate(world, bed);
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed), "点失效清空占用（床被破坏）");
        assertTrue(VillagerPoiRegistry.claim(b, world, bed, VillagerPoiRegistry.Kind.BED),
                "失效后他人可重新登记");
    }

    @Test
    void clearOnServerStop() {
        assertTrue(VillagerPoiRegistry.claim(a, world, new BlockPos(3, 64, 3), VillagerPoiRegistry.Kind.BED));
        VillagerPoiRegistry.clear();
        assertEquals(0, VillagerPoiRegistry.claimCount(), "停服清空全部登记");
        assertFalse(VillagerPoiRegistry.isOccupied(world, new BlockPos(3, 64, 3)));
    }
}
