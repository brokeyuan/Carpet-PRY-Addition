package me.primaryuan.carpet.brain.goal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 狐狸采莓（七期 P4）：成熟采摘、未成熟不采、丛保留（右键 use 流程）。
 */
class PlayerFoxBerryTest {

    static class FoxPlayer extends BrainTestStubs.FakePlayer {
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        static FoxPlayer create(ServerLevel world, Vec3 pos) {
            FoxPlayer p = BrainTestStubs.FakePlayer.createFakeOf(FoxPlayer.class, UUID.randomUUID());
            p.blocks = new HashMap<>();
            p.stubWorld(world);
            p.stubBody(pos);
            return p;
        }

        @Override
        public boolean pryUseItemOnBlock(BlockPos pos, net.minecraft.world.InteractionHand hand) {
            this.useItemCalls.add(pos);
            BlockState s = this.blocks.get(pos);
            if (s != null && s.getBlock() instanceof SweetBerryBushBlock) {
                this.blocks.put(pos, s.setValue(SweetBerryBushBlock.AGE, 1));
            }
            return true;
        }
    }

    static class TestFoxGoal extends PlayerFoxEatBerriesGoal {
        final FoxPlayer fp;

        TestFoxGoal(FoxPlayer mob) {
            super(mob);
            this.fp = mob;
        }

        @Override
        protected BlockState readStateAt(Level level, BlockPos pos) {
            BlockState s = this.fp.blocks.get(pos);
            return s != null ? s : Blocks.STONE.defaultBlockState();
        }
    }

    private ServerLevel world;
    private FoxPlayer fox;
    private TestFoxGoal goal;
    private BlockPos bush;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        world = (ServerLevel) BrainTestStubs.stubWorld();
        fox = FoxPlayer.create(world, new Vec3(5.5, 64, 5.5));
        BrainTestStubs.putField(fox.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", new BrainTestStubs.RecordingNavigation(fox));
        goal = new TestFoxGoal(fox);
        bush = new BlockPos(6, 64, 5);
    }

    @Test
    void harvestsOnlyMatureBushes() {
        fox.blocks.put(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, 1));
        assertFalse(goal.canUse(), "未成熟浆果丛（age=1）不采");

        fox.blocks.put(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, 3));
        // 扫描节流：gameTime 推进过 40t 再评（canUse 的重扫间隔门）
        var levelData = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(world, net.minecraft.world.level.Level.class, "levelData");
        levelData.setGameTime(100);
        assertTrue(goal.canUse(), "成熟浆果丛（age=3）可采");
        goal.tick(); // 走向丛
        levelData.setGameTime(200);
        fox.stubBody(new Vec3(6.5, 64, 5.5));
        goal.tick(); // 到位采摘
        assertEquals(List.of(bush), fox.useItemCalls, "玩家右键采摘");
        assertEquals(1, fox.blocks.get(bush).getValue(SweetBerryBushBlock.AGE),
                "丛保留且回退 age=1（原版 use 语义）");
    }
}
