package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
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
 * POI 寻路失败语义锁（round4 #4/#5）：
 * 连续失败序列——成功且有实际进展清零（失败→恢复→失败 ≠ 连续两次）；
 * moveTo 恒 true 但身体不前进 → gameTime 宽限（20 游戏刻边界）仍触发无进展
 * 保护；到位停留超预算不拉黑；stop/start 不继承旧计数。经生产 Goal +
 * RecordingNavigation 驱动，控制身体位置模拟进展。
 */
class PlayerPoiPathRecoveryTest {

    static class TestWorkGoal extends PlayerGotoPoiGoal {
        List<BlockPos> candidates = List.of();
        boolean poiBlock = true;
        /** 建路结果注入（null = 透传 RecordingNavigation 恒 true） */
        Boolean pathResult;

        TestWorkGoal(BrainTestStubs.FakePlayer mob) {
            super(mob, VillagerSchedule.Activity.WORK, 0.5);
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
        protected boolean isPoiBlock(net.minecraft.world.level.Level level, BlockPos pos) {
            return this.poiBlock;
        }

        // findFreePoi 不覆写：走生产黑名单/去重逻辑，候选经 nearbyPoiCandidates 注入
    }

    /** 可控导航桩：moveTo 结果可注入；isDone 可切换；"行进中"由测试建模 */
    static class ControllableNav extends BrainTestStubs.RecordingNavigation {
        public boolean done = true;
        /** moveTo 注入结果（null = 恒 true 的父类行为） */
        public Boolean moveToResult;

        ControllableNav(BrainTestStubs.FakePlayer player) {
            super(player);
        }

        @Override
        public boolean moveTo(double x, double y, double z, double speed) {
            if (this.moveToResult != null) {
                this.lastX = x; this.lastY = y; this.lastZ = z; this.lastSpeed = speed;
                this.moveToCalls++;
                return this.moveToResult;
            }
            return super.moveTo(x, y, z, speed);
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

        @Override
        public boolean isInProgress() {
            return !this.done;
        }
    }

    private net.minecraft.world.level.Level world;
    private BrainTestStubs.FakePlayer villager;
    private ControllableNav nav;
    private TestWorkGoal goal;
    private BlockPos station;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        VillagerPoiRegistry.clear();
        world = BrainTestStubs.stubWorld();
        villager = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(world)
                .stubBody(new Vec3(20.5, 64, 20.5));
        nav = new ControllableNav(villager);
        BrainTestStubs.putField(villager.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        station = new BlockPos(3, 64, 3);
        goal = new TestWorkGoal(villager);
        goal.candidates = List.of(station);
        // gameTime 推进由各用例经 PrimaryLevelData.setGameTime 控制
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

    private int pathFailures() {
        return (Integer) BrainTestStubs.readField(goal, PlayerGotoPoiGoal.class, "pathFailures");
    }

    @Test
    void failureThenProgressThenFailureIsNotConsecutive() {
        setGameTime(0);
        assertTrue(goal.canUse());
        goal.start();
        assertTrue(pathFailures() == 0);

        // 失败 1：建路 false（经导航桩注入）
        nav.moveToResult = false;
        BrainTestStubs.putInt(goal, PlayerGotoPoiGoal.class, "navigateCooldown", 1);
        goal.tick();
        assertEquals(1, pathFailures(), "第一次建路失败计 1");

        // 实际恢复：身体移动（进展检测清零连续序列）
        villager.stubBody(new Vec3(18.5, 64, 20.5));
        setGameTime(2);
        goal.tick();
        assertEquals(0, pathFailures(), "有实际进展 → 连续失败序列清零");

        // 失败 2：重新从 1 计（不是 2）
        nav.moveToResult = false;
        BrainTestStubs.putInt(goal, PlayerGotoPoiGoal.class, "navigateCooldown", 1);
        goal.tick();
        assertEquals(1, pathFailures(), "失败→恢复→失败 ≠ 连续两次失败");
        nav.moveToResult = null;
    }

    @Test
    void moveToTrueButNoProgressTriggersGrace() {
        setGameTime(0);
        assertTrue(goal.canUse());
        goal.start();
        nav.done = false; // "行进中"

        // moveTo 恒 true（RecordingNavigation 默认），身体不动 → 20 游戏刻宽限
        // （gameTime 差值 >= 20）后计失败：计时起点 t=1，t=20 差值 19 不触发，t=21 触发
        for (int t = 1; t <= 20; t++) {
            setGameTime(t);
            goal.tick();
        }
        assertEquals(0, pathFailures(), "差值 19（第 20 刻）：未达宽限，不计失败");
        setGameTime(21);
        goal.tick();
        assertEquals(1, pathFailures(), "差值 20（第 21 刻）：无进展宽限耗尽计失败");

        // 宽限中止（进展）后恢复：重新计时
        villager.stubBody(new Vec3(19.0, 64, 20.5));
        setGameTime(31);
        goal.tick();
        assertEquals(0, pathFailures(), "有进展清零");
        setGameTime(50);
        goal.tick();
        assertEquals(0, pathFailures(), "重新计时差值 19 仍不计");
        setGameTime(51);
        goal.tick();
        assertEquals(1, pathFailures(), "再次失败重新计时，差值 20 边界再触发");
    }

    @Test
    void arrivedStayBeyondBudgetDoesNotBlacklist() {
        setGameTime(0);
        assertTrue(goal.canUse());
        goal.start();

        // 走到位
        villager.stubBody(new Vec3(3.5, 64, 3.5));
        setGameTime(10);
        goal.tick();
        assertEquals(0, pathFailures());

        // 在位停留超过 600 tick（预算远超）——到位分支暂停预算，不触发退避
        setGameTime(10 + 700);
        for (int i = 0; i < 3; i++) {
            goal.tick();
        }
        assertEquals(0, pathFailures(), "到位停留不累计寻路预算");
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, station),
                "停留期间持有不受影响");

        // 短暂离开再回：不因旧预算立即拉黑（预算从离开后重新累计）
        villager.stubBody(new Vec3(6.0, 64, 6.0)); // 离开（distSq=18 > 4）
        setGameTime(720);
        goal.tick();
        assertEquals(0, pathFailures(), "离开后预算重新起算，无旧残留");
    }

    @Test
    void stopStartDoesNotInheritCounters() {
        setGameTime(0);
        assertTrue(goal.canUse());
        goal.start();
        nav.done = false;
        setGameTime(26); // 首拍：设立无进展计时（nps=26）
        goal.tick();
        setGameTime(46); // 差值 20 ≥ 20：宽限耗尽
        goal.tick();
        assertEquals(1, pathFailures());

        goal.stop(); // 清理本次状态
        setGameTime(100);
        assertTrue(goal.canUse());
        goal.start(); // 重启
        assertEquals(0, pathFailures(), "stop/start 不继承旧失败计数");
        // 无进展计时也复位：重新两拍（首拍设立 + 差值 19 不触发）
        nav.done = false;
        setGameTime(119);
        goal.tick();
        setGameTime(138); // 差值 19
        goal.tick();
        assertEquals(0, pathFailures(), "重启后宽限重新计时（差值 19 不计）");
    }

    @Test
    void unreachableFirstReachableSecondSwitchover() {
        // 生产按距离升序选点：villager(20.5,64,20.5) 离 second(10.5,64,10.5) 更近
        // → 首选 second；second 被连续无进展退避后自动轮转到 station
        BlockPos second = new BlockPos(10, 64, 10);
        goal.candidates = List.of(station, second);
        setGameTime(0);
        assertTrue(goal.canUse());
        assertEquals(second, BrainTestStubs.readField(goal, PlayerGotoPoiGoal.class, "pendingPoi"),
                "距离升序：先选更近的 second");
        goal.start();
        nav.done = false;
        nav.moveToResult = null; // moveTo 成功但身体不动 → 无进展
        for (int round = 1; round <= 3; round++) {
            long base = round * 100;
            setGameTime(base);
            goal.tick(); // 无进展计时启动
            setGameTime(base + 21);
            goal.tick(); // 差值 21 ≥ 20 → pathFailures++ → round3 触发退避
        }
        assertEquals(3, pathFailures(), "连续三轮无进展 → 连续失败 3");
        assertFalse(VillagerPoiRegistry.isOccupied(world, second), "不可达点已释放");
        // second 进黑名单 → 自动轮转到 station
        assertTrue(goal.canUse(), "轮转到可达点 → 激活");
        assertEquals(station, BrainTestStubs.readField(goal, PlayerGotoPoiGoal.class, "pendingPoi"),
                "首选退避后切换到次选 station");
    }

}
