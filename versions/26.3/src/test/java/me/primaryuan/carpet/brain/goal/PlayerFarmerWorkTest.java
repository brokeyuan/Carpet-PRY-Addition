package me.primaryuan.carpet.brain.goal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 村民农民工作目标（七期 P1）：堆肥桶认领 = 农民职业面；成熟收割/未成熟不收；
 * 真实种子补种；堆肥保留每种 10 个、满级先取骨粉、等级 7 收手。世界输入
 * （方块表 + 右键推进堆肥等级）由测试提供，判定与动作编排全走生产 Goal。
 */
class PlayerFarmerWorkTest {

    /** 可变方块表世界 + 右键推进堆肥等级的桩玩家（世界行为归测试，判断走生产） */
    static class WorldPlayer extends BrainTestStubs.FakePlayer {
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        static WorldPlayer create(ServerLevel world, Vec3 pos) {
            WorldPlayer p = BrainTestStubs.FakePlayer.createFakeOf(WorldPlayer.class, UUID.randomUUID());
            p.blocks = new HashMap<>(); // Unsafe 分配跳过字段初始化器，显式建表
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            return p;
        }

        @Override
        public boolean pryUseItemOnBlock(BlockPos pos, InteractionHand hand) {
            this.useItemCalls.add(pos);
            // 原版堆肥右键：消耗 1 件手上物品（成功与否都消耗）
            this.getMainHandItem().shrink(1);
            BlockState s = this.blocks.get(pos);
            if (s != null && s.getBlock() == Blocks.COMPOSTER
                    && s.getValue(ComposterBlock.LEVEL) < ComposterBlock.MAX_LEVEL) {
                this.blocks.put(pos, s.setValue(ComposterBlock.LEVEL, s.getValue(ComposterBlock.LEVEL) + 1));
            }
            return true;
        }
    }

    private ServerLevel world;
    private WorldPlayer farmer;
    private TestFarmerGoal goal;
    private BlockPos composter;
    private BlockPos cropPos;

    /** 农民目标桩：世界方块读走表；活动段恒 WORK；候选注入 */
    static class TestFarmerGoal extends PlayerFarmerWorkGoal {
        final WorldPlayer wp;
        List<BlockPos> candidates = List.of();
        boolean griefing = true;

        TestFarmerGoal(WorldPlayer mob) {
            super(mob, null);
            this.wp = mob;
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return VillagerSchedule.Activity.WORK;
        }

        @Override
        protected List<BlockPos> nearbyPoiCandidates() {
            return this.candidates;
        }

        @Override
        protected boolean isPoiBlock(Level level, BlockPos pos) {
            BlockState s = this.wp.blocks.get(pos);
            return s != null && (s.is(Blocks.COMPOSTER) || s.is(Blocks.LECTERN));
        }

        @Override
        protected boolean mobGriefing(ServerLevel level) {
            return this.griefing; // 桩世界 gamerule 链不可达，测试注入规则开关
        }

        @Override
        protected BlockState readStateAt(Level level, BlockPos pos) {
            BlockState s = this.wp.blocks.get(pos);
            return s != null ? s : Blocks.STONE.defaultBlockState();
        }
    }

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        VillagerPoiRegistry.clear();
        // 纯 JVM 未装载数据包标签：给小麦种子挂 #minecraft:villager_plantable_seeds
        BrainTestStubs.bindTag(net.minecraft.world.item.Items.WHEAT_SEEDS.builtInRegistryHolder(),
                net.minecraft.tags.ItemTags.VILLAGER_PLANTABLE_SEEDS);
        world = (ServerLevel) BrainTestStubs.stubWorld();
        farmer = WorldPlayer.create(world, new Vec3(5.5, 64, 6.5));
        goal = new TestFarmerGoal(farmer);
        composter = new BlockPos(5, 64, 5);
        cropPos = new BlockPos(4, 64, 5);
        // 默认全石头；用例按需铺方块。默认已站在堆肥桶旁（distSq=1 ≤ 4）
        farmer.blocks.put(composter, Blocks.COMPOSTER.defaultBlockState());
        goal.candidates = List.of(composter);
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    private void setGameTime(long t) {
        var levelData = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(world, net.minecraft.world.level.Level.class, "levelData");
        levelData.setGameTime(t);
    }

    @Test
    void composterClaimMakesFarmerOtherStationsDoNot() {
        assertTrue(goal.canUse());
        goal.start();
        assertEquals(composter, goal.claimedStationPos(), "认领堆肥桶为工作站");
        assertTrue(goal.isFarmerStation(), "认领堆肥桶 → 农民站");
        assertTrue(goal.hasComposterClaim(), "堆肥桶占用由本人持有");

        // 其他工作方块不赋予农民职业
        BlockPos lectern = new BlockPos(8, 64, 8);
        goal.candidates = List.of(lectern);
        farmer.blocks.remove(composter);
        farmer.blocks.put(lectern, Blocks.LECTERN.defaultBlockState());
        goal.stop();
        assertTrue(goal.canUse(), "讲台候选可认领");
        goal.start();
        assertEquals(lectern, goal.claimedStationPos());
        assertFalse(goal.isFarmerStation(), "讲台不是农民站");
    }

    @Test
    void twoBotsCannotClaimSameComposter() {
        assertTrue(goal.canUse());
        goal.start();
        assertEquals(composter, goal.claimedStationPos());

        WorldPlayer farmerB = WorldPlayer.create(world, new Vec3(6.5, 64, 6.5));
        TestFarmerGoal goalB = new TestFarmerGoal(farmerB);
        goalB.candidates = List.of(composter);
        assertFalse(goalB.canUse(), "堆肥桶容量 1：第二个假人无候选可用");
        goalB.start();
        assertFalse(composter.equals(goalB.claimedStationPos()),
                "第二个假人不能重复占岗");
    }

    @Test
    void harvestsOnlyMatureCrops() {
        assertTrue(goal.canUse());
        goal.start();
        // 未成熟小麦：无效农位 → 不出差、不破坏
        farmer.blocks.put(cropPos, Blocks.WHEAT.defaultBlockState()
                .setValue(CropBlock.AGE, 3));
        setGameTime(100);
        goal.onArrivedAtStation();
        assertTrue(farmer.breakCalls.isEmpty(), "未成熟作物不收割");
        assertEquals(composter, goal.currentDestination(), "无有效农位时驻站");

        // 成熟小麦：出差到位 → 玩家原生破坏
        farmer.blocks.put(cropPos, Blocks.WHEAT.defaultBlockState()
                .setValue(CropBlock.AGE, 7));
        setGameTime(200);
        goal.onArrivedAtStation(); // 扫描出差
        assertEquals(cropPos, goal.currentDestination(), "成熟作物成为出差目标");
        farmer.stubBody(new Vec3(4.5, 64, 5.5)); // 走到目标 1 格内
        setGameTime(201);
        goal.tick(); // 到达 → onArrivedAtDestination → 破坏
        assertEquals(List.of(cropPos), farmer.breakCalls, "成熟作物经 pryBreakBlock 收割");
    }

    @Test
    void replantsWithRealSeedsOnly() {
        assertTrue(goal.canUse());
        goal.start();
        // 原位空气 + 下方耕地 + 无种子：不补种
        farmer.blocks.put(cropPos, Blocks.AIR.defaultBlockState());
        farmer.blocks.put(cropPos.below(), Blocks.FARMLAND.defaultBlockState());
        farmer.blocks.put(composter, Blocks.STONE.defaultBlockState()); // 避开堆肥分支
        setGameTime(300);
        goal.onArrivedAtStation(); // 出差到空气+耕地位
        farmer.stubBody(new Vec3(4.5, 64, 5.5));
        setGameTime(301);
        goal.tick();
        assertTrue(farmer.useItemCalls.isEmpty(), "无真实种子不凭空补种");

        // 给真实小麦种子后：手持种子右键耕地补种
        farmer.getInventory().setItem(10, new ItemStack(Items.WHEAT_SEEDS, 15));
        setGameTime(400);
        goal.onArrivedAtStation(); // 重选出差（同一农位）
        farmer.stubBody(new Vec3(4.5, 64, 5.5));
        setGameTime(401);
        goal.tick(); // 到位：空气+耕地+有种子 → 右键耕地补种
        assertEquals(List.of(cropPos.below()), farmer.useItemCalls,
                "手持真实种子右键耕地补种");
        assertTrue(farmer.breakCalls.isEmpty(), "空气位不触发破坏");
    }

    @Test
    void compostReservesTenPerTypeAndFillsWithRealClicks() {
        assertTrue(goal.canUse());
        goal.start();
        farmer.getInventory().setItem(10, new ItemStack(Items.WHEAT_SEEDS, 15));
        setGameTime(100);
        goal.onArrivedAtStation();
        // 15 个：保留 10 → 只投 5；右键 5 次，堆肥桶等级 0→5（桩世界推进）
        assertEquals(5, farmer.useItemCalls.size(), "每种保留 10 个，15 个只投 5");
        assertEquals(5, farmer.blocks.get(composter).getValue(ComposterBlock.LEVEL),
                "每次右键推进一级（原版 chance 管线由测试模拟）");
        assertEquals(10, countSeeds(), "保留 10 个种子在背包");
    }

    @Test
    void compostStopsAtLevelSevenAndExtractsWhenFull() {
        assertTrue(goal.canUse());
        goal.start();
        farmer.blocks.put(composter, Blocks.COMPOSTER.defaultBlockState()
                .setValue(ComposterBlock.LEVEL, 4));
        farmer.getInventory().setItem(10, new ItemStack(Items.WHEAT_SEEDS, 40));
        setGameTime(100);
        goal.onArrivedAtStation();
        // 4→7 需 3 次；到 7 收手（预算 20 内）
        assertEquals(3, farmer.useItemCalls.size(), "等级到 7 结束本轮投放");

        // 满级：先取骨粉（一拍一次）
        farmer.useItemCalls.clear();
        farmer.blocks.put(composter, Blocks.COMPOSTER.defaultBlockState()
                .setValue(ComposterBlock.LEVEL, ComposterBlock.MAX_LEVEL));
        setGameTime(200);
        goal.onArrivedAtStation();
        assertEquals(1, farmer.useItemCalls.size(), "满级只取骨粉一次");
    }

    private int countSeeds() {
        int n = 0;
        var inv = farmer.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WHEAT_SEEDS)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }
}
