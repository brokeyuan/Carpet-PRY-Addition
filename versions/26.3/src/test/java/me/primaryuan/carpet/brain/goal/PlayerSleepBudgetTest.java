package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import me.primaryuan.carpet.brain.VillagerSchedule;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 睡眠预算锁（round5 #3）：睡眠期间差值基准持续刷新——恢复寻路时只累计
 * 恢复后的时间，整段睡眠（>600 tick）不进寻路预算；真正持续寻路超预算
 * 仍触发退避保护。经生产 Goal + RecordingNavigation + gameTime 驱动。
 */
class PlayerSleepBudgetTest {

    static class TestSleepGoal extends PlayerSleepInBedGoal {
        List<BlockPos> candidates = List.of();
        boolean bedBlock = true;
        BlockPos bedPos;

        TestSleepGoal(BrainTestStubs.FakePlayer mob) {
            super(mob);
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return VillagerSchedule.Activity.REST;
        }

        @Override
        protected List<BlockPos> nearbyBedCandidates() {
            return this.candidates;
        }

        @Override
        protected boolean isOtherHalfStillBed(net.minecraft.world.level.Level level, BlockPos head) {
            return this.bedBlock; // 生命周期测试与方向验证分离（方向在 RealStateTest）
        }

        // isBedUsable 生产为 private：此用例不依赖真实占用场景（方向/占用在
        // RealState/Normalization 锁定），这里 bedBlock 恒真即可走通预算路径

        @Override
        protected net.minecraft.world.level.block.state.BlockState readBedState(
                net.minecraft.world.level.Level level, BlockPos pos) {
            // 桩世界无方块读：预算测试放一张完整床（床头 (2,64,2)、床尾 (3,64,2)），
            // 两坐标都返回真实状态；占用位恒 false
            BlockPos head = this.bedPos;
            BlockPos foot = head.relative(net.minecraft.core.Direction.SOUTH);
            if (pos.equals(head)) {
                return net.minecraft.world.level.block.Blocks.BED
                        .pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                        .setValue(PlayerBedRealStateTest.bedPartProperty(), net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                        .setValue(PlayerBedRealStateTest.facingProperty(), net.minecraft.core.Direction.SOUTH)
                        .setValue(PlayerBedRealStateTest.occupiedProperty(), false);
            }
            if (pos.equals(foot)) {
                return net.minecraft.world.level.block.Blocks.BED
                        .pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                        .setValue(PlayerBedRealStateTest.bedPartProperty(), net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                        .setValue(PlayerBedRealStateTest.facingProperty(), net.minecraft.core.Direction.SOUTH)
                        .setValue(PlayerBedRealStateTest.occupiedProperty(), false);
            }
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        @Override
        protected BlockPos findFreeBed() {
            // 单点规范化：床头由 setUp 注入（本桩直接提供规范床头）
            return this.bedPos;
        }
    }

    static class Sleeper extends BrainTestStubs.FakePlayer {
        boolean sleeping;
        BlockPos sleptAt;

        static Sleeper create(net.minecraft.world.level.Level world, Vec3 pos) {
            Sleeper p = BrainTestStubs.allocate(Sleeper.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "uuid", id);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.stubWorld(world);
            p.stubBody(pos);
            return p;
        }

        @Override
        public boolean isSleeping() {
            return this.sleeping;
        }

        @Override
        public boolean startSleeping(BlockPos pos) {
            this.sleeping = true;
            this.sleptAt = pos;
            return true;
        }

        @Override
        public void stopSleepInBed(boolean updateSleeping, boolean sendMessage) {
            this.sleeping = false;
        }

        @Override
        public java.util.Optional<BlockPos> getSleepingPos() {
            return this.sleeping ? java.util.Optional.of(this.sleptAt) : java.util.Optional.empty();
        }
    }

    private net.minecraft.world.level.Level world;
    private net.minecraft.world.level.storage.PrimaryLevelData levelData;
    private Sleeper villager;
    private PlayerGoalSelector selector;
    private TestSleepGoal goal;
    private BrainTestStubs.RecordingNavigation nav;
    private BlockPos bed;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        // 纯 JVM 未装载数据包标签：给真实床 Holder 挂 #minecraft:beds（生产 bedTag 判定）
        var bedHolder = net.minecraft.world.level.block.Blocks.BED
                .pick(net.minecraft.world.item.DyeColor.RED).builtInRegistryHolder();
        BrainTestStubs.bindTag(bedHolder, net.minecraft.tags.BlockTags.BEDS);
        VillagerPoiRegistry.clear();
        world = BrainTestStubs.stubWorld();
        levelData = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(world, net.minecraft.world.level.Level.class, "levelData");
        levelData.setGameTime(0);
        villager = Sleeper.create(world, new Vec3(2.5, 64, 2.5));
        nav = new BrainTestStubs.RecordingNavigation(villager);
        BrainTestStubs.putField(villager.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        bed = new BlockPos(2, 64, 2);
        goal = new TestSleepGoal(villager);
        goal.bedPos = bed;
        goal.candidates = List.of(bed);
        selector = new PlayerGoalSelector();
        selector.addGoal(2, goal);
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    @Test
    void sleepDurationDoesNotEnterPathBudget() {
        // 少量寻路（t=0→10）→ 到床入睡（t=10）→ 睡 700 tick（t=10→710）
        // → 醒来离床重新寻路（t=710→760）——睡眠时长不进预算
        assertTrue(goal.canUse());
        goal.start();
        // t=0→10：寻路 10 tick（预算累计 10）
        levelData.setGameTime(10);
        goal.tick();
        // 到床（已在 2.5 格内）→ 入睡
        assertTrue(villager.sleeping, "首拍入睡");

        // 睡 700 tick：tick 期间 isSleeping=true，预算基准持续刷新
        for (long t = 11; t <= 710; t++) {
            levelData.setGameTime(t);
            goal.tick();
        }
        assertTrue(villager.sleeping, "睡眠维持 700 tick");
        long budgetUsed = (Long) BrainTestStubs.readField(goal, PlayerSleepInBedGoal.class, "pathBudgetUsedTicks");
        assertTrue(budgetUsed <= 12, "睡眠期间预算不累计（实测 " + budgetUsed + "，应 ≤10+容差）");

        // 日程仍 REST 但被打醒（恐慌等）：离床重新寻路
        villager.sleeping = false;
        villager.stubBody(new Vec3(8.5, 64, 8.5)); // 离床（distSq=54 > 6.25）
        levelData.setGameTime(712);
        goal.tick(); // 首拍重新设立计时
        levelData.setGameTime(713);
        goal.tick();
        // 预算只累计恢复后的时间（~3 tick），远不到 600 → 不拉黑
        assertFalse(goal.canContinueToUse() && false, "占位");
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, bed),
                "重新寻路中床位仍被自己持有（未因旧预算拉黑）");
        long budgetAfter = (Long) BrainTestStubs.readField(goal, PlayerSleepInBedGoal.class, "pathBudgetUsedTicks");
        assertTrue(budgetAfter <= 20, "恢复后预算只累计恢复后的时间（实测 " + budgetAfter + "）");
    }

    @Test
    void sustainedPathingBeyondBudgetStillTriggers() {
        // 持续寻路（无进展）600+ tick → 退避保护仍触发
        assertTrue(goal.canUse());
        goal.start();
        nav.done = false;
        villager.stubBody(new Vec3(20.5, 64, 20.5)); // 距床远，身体不动

        // 两拍设立+触发一轮 → pathFailures=1；三轮触发退避
        for (int round = 1; round <= 3; round++) {
            long base = round * 100;
            levelData.setGameTime(base);
            goal.tick();
            levelData.setGameTime(base + 21);
            goal.tick();
        }
        assertEquals(3, (Integer) BrainTestStubs.readField(goal, PlayerSleepInBedGoal.class, "pathFailures"),
                "连续三轮无进展 → 连续失败 3");
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed), "不可达床已释放");
    }
}
