package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下落沿口"可通行"语义的判据面：isPassableCell 读 getCollisionShape 双空，
 * 判据 = 本格与头顶格碰撞箱为空。桩世界无方块读写（DIMENSIONS 层注册表
 * 数据包驱动、纯 JVM 不可达），本用例锁定判定式的输入语义——真实方块状态的
 * 碰撞行为；谓词对世界的接线由 boot check 承接。
 */
class BrainNavigationGeometryTest {

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
    }

    private static boolean collisionAt(BlockState state) {
        // 与 PlayerPathNavigation.isPassableCell 相同的读法：
        // getBlockState(...).getCollisionShape(level, pos).isEmpty() 的方块侧语义
        return !state.isAir() && state.getCollisionShape(null, net.minecraft.core.BlockPos.ZERO).isEmpty();
    }

    @Test
    void airIsOpenLedge() {
        // 开放沿口（空气）：碰撞为空 → isPassableCell 放行；无支撑 → isWalkableCell 拒绝
        BlockState air = Blocks.AIR.defaultBlockState();
        assertTrue(air.getCollisionShape(null, net.minecraft.core.BlockPos.ZERO).isEmpty(),
                "空气无碰撞箱（沿口开放判据）");
    }

    @Test
    void stoneBlocksPassage() {
        // 堵口沿口（实体方块）：碰撞非空 → isPassableCell 拒绝
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertFalse(stone.getCollisionShape(null, net.minecraft.core.BlockPos.ZERO).isEmpty(),
                "石头有碰撞箱（堵口判据）");
    }

    @Test
    void fenceShapeBlocksPassage() {
        // 栅栏/墙等部分碰撞同样非空（堵口判据覆盖非整格方块）
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        assertFalse(fence.getCollisionShape(null, net.minecraft.core.BlockPos.ZERO).isEmpty(),
                "栅栏有碰撞箱（堵口判据覆盖部分碰撞）");
    }
}
