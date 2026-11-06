package me.primaryuan.carpet.brain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.primaryuan.carpet.brain.goal.PlayerFarmerWorkGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 村民职业生命周期（八期）：初始 [村民]；认领堆肥桶 → [村民-农民]；
 * Goal 暂停（集合/睡觉/恐慌模拟）不清职业；岗位被拆 → 回 [村民]；
 * 讲台不占农民求职。有效性方块判定经缝注入（桩世界不可读）。
 */
class VillagerProfessionLifecycleTest {

    static class VPlayer extends BrainTestStubs.FakePlayer {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        boolean stationAlive = true;

        static VPlayer create(ServerLevel world, Vec3 pos) {
            VPlayer p = BrainTestStubs.FakePlayer.createFakeOf(VPlayer.class, UUID.randomUUID());
            p.blocks = new HashMap<>();
            p.stationAlive = true; // Unsafe 分配跳过字段初始化器
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory(); // 选择器推进到堆肥动作时需要真实背包
            return p;
        }
    }

    static class TestFarmerGoal extends PlayerFarmerWorkGoal {
        final VPlayer wp;
        List<BlockPos> candidates = List.of();

        TestFarmerGoal(VPlayer mob, java.util.function.Consumer<BlockPos> onClaimed,
                       java.util.function.BiPredicate<Level, BlockPos> isLongTerm) {
            super(mob, onClaimed, isLongTerm);
            this.wp = mob;
        }

        @Override
        protected me.primaryuan.carpet.brain.VillagerSchedule.Activity currentActivity() {
            return me.primaryuan.carpet.brain.VillagerSchedule.Activity.WORK;
        }

        @Override
        protected List<BlockPos> nearbyPoiCandidates() {
            return this.candidates;
        }

        @Override
        protected boolean isPoiBlock(Level level, BlockPos pos) {
            BlockState s = this.wp.blocks.get(pos);
            return s != null && s.is(Blocks.COMPOSTER);
        }

        @Override
        protected BlockState readStateAt(Level level, BlockPos pos) {
            BlockState s = this.wp.blocks.get(pos);
            return s != null ? s : Blocks.STONE.defaultBlockState();
        }

        @Override
        protected boolean mobGriefing(net.minecraft.server.level.ServerLevel level) {
            return false; // 桩世界无 gameRules：不启动农活出差，只验证岗位生命周期
        }
    }

    private ServerLevel world;
    private VPlayer villager;
    private VillagerBrain brain;
    private TestFarmerGoal goal;
    private PlayerGoalSelector selector;
    private BlockPos composter;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        me.primaryuan.carpet.brain.VillagerPoiRegistry.clear();
        world = (ServerLevel) BrainTestStubs.stubWorld();
        villager = VPlayer.create(world, new Vec3(5.5, 64, 6.5));
        brain = new VillagerBrain(villager) {
            @Override
            protected boolean isStationValid(net.minecraft.world.level.Level level, BlockPos pos) {
                return villager.stationAlive; // 桩世界不可读：注入岗位存活状态
            }
        };
        goal = new TestFarmerGoal(villager, brain::onComposterClaimedForTest, brain::isLongTermStation);
        composter = new BlockPos(5, 64, 5);
        villager.blocks.put(composter, Blocks.COMPOSTER.defaultBlockState());
        goal.candidates = List.of(composter);
        selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        me.primaryuan.carpet.brain.VillagerPoiRegistry.clear();
    }

    private void setGameTime(long t) {
        var ld = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(world, net.minecraft.world.level.Level.class, "levelData");
        ld.setGameTime(t);
    }

    /** 经真实选择器启动（最多 40 次完整评估，覆盖 GotoPoi 的 interval=20） */
    private void startViaSelector() {
        selector.addGoal(1, goal);
        for (int i = 0; i < 40 && !selector.hasRunningFlag(PlayerGoal.Flag.MOVE); i++) {
            selector.tick(true);
        }
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE), "选择器应启动工作 Goal");
    }

    /** 选择器暂停路径（removeGoal 内部调用 stop） */
    private void pauseViaSelector() {
        selector.removeGoal(goal);
    }

    private static void startOther(PlayerGoalSelector selector, TestFarmerGoal goal) {
        selector.addGoal(1, goal);
        for (int i = 0; i < 40 && !selector.hasRunningFlag(PlayerGoal.Flag.MOVE); i++) {
            selector.tick(true);
        }
    }

    @Test
    void initialSuffixIsVillagerThenFarmerAfterClaim() {
        assertEquals("mode_villager", brain.suffixLangKey(), "刚挂脑显示 [村民]");
        startViaSelector(); // 认领成功 → 回调登记岗位
        assertEquals(composter, goal.claimedStationPos());
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey(), "认领堆肥桶 → [村民-农民]");
    }

    @Test
    void goalPauseKeepsProfession() {
        startViaSelector();
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey());
        pauseViaSelector(); // 集合/睡觉/恐慌 = 选择器暂停路径
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", "mode_villager_farmer");
        assertEquals("mode_villager_farmer", brain.suffixLangKey(),
                "Goal 暂停不清除职业（岗位归属在控制器）");
    }

    @Test
    void brokenStationResetsProfession() {
        startViaSelector();
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey());
        villager.stationAlive = false; // 岗位被拆
        brain.refreshProfession();
        assertEquals("mode_villager", brain.suffixLangKey(), "岗位失效回 [村民]");
    }

    @Test
    void twoVillagersCannotHoldSameComposterAcrossPause() {
        // A 认领并进入农民；工作 Goal 因集合/睡眠暂停（选择器 removeGoal）不释放岗位
        startViaSelector();
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey());
        pauseViaSelector();
        brain.refreshProfession();
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(),
                world.dimension(), composter), "暂停不释放长期岗位");

        // B 在 A 暂停期间求职同一堆肥桶：容量被 A 持有 → 不能认领
        VPlayer other = VPlayer.create(world, new Vec3(6.5, 64, 6.5));
        other.blocks.put(composter, Blocks.COMPOSTER.defaultBlockState());
        VillagerBrain otherBrain = new VillagerBrain(other) {
            @Override
            protected boolean isStationValid(net.minecraft.world.level.Level level, BlockPos pos) {
                return true;
            }
        };
        TestFarmerGoal otherGoal = new TestFarmerGoal(other,
                otherBrain::onComposterClaimedForTest, otherBrain::isLongTermStation);
        otherGoal.candidates = List.of(composter);
        PlayerGoalSelector otherSelector = new PlayerGoalSelector();
        startOther(otherSelector, otherGoal);
        assertFalse(otherSelector.hasRunningFlag(PlayerGoal.Flag.MOVE),
                "同一岗位不能被第二个假人同时认领");
        assertEquals("mode_villager", otherBrain.suffixLangKey(), "B 仍是 [村民]");

        // A 恢复：自己的长期岗位仍可被自己选回
        startViaSelector();
        assertEquals(composter, goal.claimedStationPos());
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey());
        assertFalse(VillagerPoiRegistry.isClaimedBy(other.getUUID(),
                world.dimension(), composter), "B 从未取得岗位");
    }

    @Test
    void lecternDoesNotBlockComposterJob() {
        // 更近的讲台不在候选（堆肥桶专属求职）：work goal 只认堆肥桶
        BlockPos lectern = new BlockPos(4, 64, 4);
        villager.blocks.put(lectern, Blocks.LECTERN.defaultBlockState());
        goal.candidates = List.of(lectern, composter); // 讲台更近但非堆肥桶
        startViaSelector();
        assertEquals(composter, goal.claimedStationPos(), "讲台不占农民求职");
        brain.refreshProfession();
        assertEquals("mode_villager_farmer", brain.suffixLangKey());
    }
}
