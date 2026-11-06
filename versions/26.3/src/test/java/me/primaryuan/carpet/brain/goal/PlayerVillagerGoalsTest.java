package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import me.primaryuan.carpet.brain.VillagerBrain;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 村民日程 Goal 锁（经 PlayerGoalSelector 奇偶门驱动）：
 * 日程段激活对应 Goal（WORK→工作站登记到位 / MEET→集合点 / REST→床位）；
 * 遇险打断（恐慌 MOVE 抢占 → 占用释放）→ 险情结束日程恢复（重新登记）；
 * POI 失效（方块被破坏）→ 释放登记；换脑/下线/死亡 onDetach 释放全部占用并唤醒。
 * 变异验证：删释放路径 → 打断后仍占用用例失败；删日程门 → 换段用例失败。
 */
class PlayerVillagerGoalsTest {

    /** 环境可编程的村民桩（日程/睡眠/持物全桩） */
    static class VillagerStub extends BrainTestStubs.FakePlayer {
        VillagerSchedule.Activity activity = VillagerSchedule.Activity.IDLE;
        boolean sleeping;
        BlockPos sleptAt;
        int wakeCalls;

        static VillagerStub create(Level level) {
            VillagerStub p = BrainTestStubs.allocate(VillagerStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, Entity.class, "uuid", id);
            BrainTestStubs.putField(p, Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.stubWorld(level);
            return p;
        }

        @Override
        public boolean isBaby() {
            return false; // 假人村民无繁殖系统，恒成年
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
            this.wakeCalls++;
        }
    }

    static class TestWorkGoal extends PlayerGotoPoiGoal {
        List<BlockPos> candidates = List.of();
        boolean poiBlock = true;

        TestWorkGoal(BrainTestStubs.FakePlayer mob) {
            super(mob, VillagerSchedule.Activity.WORK, 0.5);
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return ((VillagerStub) this.mob.asLiving()).activity;
        }

        @Override
        protected List<BlockPos> nearbyPoiCandidates() {
            return this.candidates;
        }

        @Override
        protected boolean isPoiBlock(net.minecraft.world.level.Level level, BlockPos pos) {
            return this.poiBlock;
        }
    }

    static class TestSleepGoal extends PlayerSleepInBedGoal {
        List<BlockPos> candidates = List.of();
        boolean bedBlock = true;

        TestSleepGoal(BrainTestStubs.FakePlayer mob) {
            super(mob);
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return ((VillagerStub) this.mob.asLiving()).activity;
        }

        @Override
        protected List<BlockPos> nearbyBedCandidates() {
            return this.candidates;
        }

        @Override
        protected BedInfo probeBed(net.minecraft.world.level.Level level, BlockPos pos) {
            return this.bedBlock
                    ? new BedInfo(true, pos, false)
                    : BedInfo.NOT_BED;
        }

        @Override
        protected boolean isOtherHalfStillBed(net.minecraft.world.level.Level level, BlockPos head) {
            return this.bedBlock; // 桩建模：单点床（probeBed 恒真时床尾同真）
        }
    }

    private Level world;
    private VillagerStub villager;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        VillagerPoiRegistry.clear();
        world = BrainTestStubs.stubWorld();
        villager = VillagerStub.create(world);
        villager.stubBody(new Vec3(0.5, 64, 0.5));
        // 桩世界无方块读写（DIMENSIONS 注册表纯 JVM 不可达）：注入录制导航器
        BrainTestStubs.putField(villager.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", new BrainTestStubs.RecordingNavigation(villager));
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    @Test
    void workGoalClaimsAndArrivesDuringWorkTime() {
        TestWorkGoal goal = new TestWorkGoal(villager);
        BlockPos station = new BlockPos(3, 64, 3);
        goal.candidates = List.of(station);
        villager.activity = VillagerSchedule.Activity.WORK;

        assertTrue(goal.canUse(), "工作时段 + 空闲工作站 → 激活");
        assertFalse(VillagerPoiRegistry.isOccupied(world, station),
                "canUse 只暂存候选（登记表零副作用——幽灵占位的回归防线）");
        goal.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, station),
                "start 复核后提交占用");
        villager.stubBody(new Vec3(3.5, 64, 3.5)); // 走到工作站旁
        goal.tick();
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, station), "到位站定仍持有占用");

        // 换段（9000 后 meet）：选择器清理路径 = canContinueToUse false → stop → 释放
        villager.activity = VillagerSchedule.Activity.MEET;
        assertFalse(goal.canContinueToUse(), "日程离开 WORK 段 → 结束");
        goal.stop(); // 选择器在清理分支调用的同一释放路径
        assertFalse(VillagerPoiRegistry.isOccupied(world, station), "结束释放占用");
    }

    @Test
    void poiDestroyedReleasesClaim() {
        TestWorkGoal goal = new TestWorkGoal(villager);
        BlockPos station = new BlockPos(3, 64, 3);
        goal.candidates = List.of(station);
        villager.activity = VillagerSchedule.Activity.WORK;
        assertTrue(goal.canUse());
        goal.start();

        goal.poiBlock = false; // 工作站被破坏
        assertFalse(goal.canContinueToUse(), "POI 失效 → 结束");
        goal.stop();
        assertFalse(VillagerPoiRegistry.isOccupied(world, station), "失效释放登记（不留幽灵占用）");
    }

    @Test
    void panicPreemptsWorkThenScheduleResumes() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        TestWorkGoal work = new TestWorkGoal(villager);
        BlockPos station = new BlockPos(3, 64, 3);
        work.candidates = List.of(station);
        villager.activity = VillagerSchedule.Activity.WORK;

        // 恐慌 Goal：目的地缝注入、导航可控
        BrainTestStubs.RecordingNavigation nav = new BrainTestStubs.RecordingNavigation(villager);
        BrainTestStubs.putField(villager.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        var panic = new PlayerPanicGoal(villager, 0.5) {
            @Override
            protected net.minecraft.core.BlockPos panicDestination() {
                return new net.minecraft.core.BlockPos(8, 64, 8);
            }
        };
        selector.addGoal(0, panic);
        selector.addGoal(3, work);

        // 第一拍：恐慌未触发，工作激活
        selector.tick(true);
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, station), "无险情 → 工作 Goal 登记");

        // 遇险（player_attack ∈ PANIC_CAUSES）：恐慌抢占，工作释放
        var attack = new net.minecraft.world.damagesource.DamageSource(
                BrainTestStubs.damageType("player_attack",
                        net.minecraft.tags.DamageTypeTags.PANIC_CAUSES,
                        net.minecraft.tags.DamageTypeTags.IS_PLAYER_ATTACK));
        villager.stubLastDamage(attack, 0L);
        selector.tick(true);
        assertFalse(VillagerPoiRegistry.isOccupied(world, station),
                "恐慌抢占 → 工作 Goal stop → 占用释放");
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE), "恐慌在跑（MOVE 旗标持有者已换人）");

        // 险情结束（伤害窗口外 + 恐慌路径走完）：日程恢复，工作重新登记。
        // 工作 Goal 的扫描冷却为 20 次评估（≈40 tick）——循环驱动到它重评估
        villager.stubLastDamage(null, 0L);
        nav.done = true;
        boolean resumed = false;
        for (int tick = 0; tick < 60 && !resumed; tick++) {
            selector.tick(tick % 2 == 0);
            resumed = VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, station);
        }
        assertTrue(resumed, "遇险打断后日程恢复 → 工作重新登记");
    }

    @Test
    void sleepGoalLifecycleInRestTime() {
        TestSleepGoal goal = new TestSleepGoal(villager);
        BlockPos bed = new BlockPos(2, 64, 2);
        goal.candidates = List.of(bed);
        villager.activity = VillagerSchedule.Activity.REST;

        assertTrue(goal.canUse(), "rest 段激活");
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed),
                "canUse 只暂存候选（登记表零副作用）");
        goal.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), world, bed),
                "start 复核后提交占用");
        villager.stubBody(new Vec3(2.5, 64, 2.5)); // 走到床边（≤2.5 格）
        goal.tick();
        assertTrue(villager.sleeping, "到位 → 原生 startSleeping 躺下");
        assertTrue(goal.canContinueToUse(), "入睡后维持");

        // 天亮（日程离开 rest）→ 唤醒 + 释放
        villager.activity = VillagerSchedule.Activity.IDLE;
        assertFalse(goal.canContinueToUse(), "天亮 → 结束");
        goal.stop();
        assertFalse(villager.sleeping, "stop 原生唤醒");
        assertEquals(1, villager.wakeCalls);
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed), "醒来释放床位");
    }

    @Test
    void bedDestroyedWakesAndReleases() {
        TestSleepGoal goal = new TestSleepGoal(villager);
        BlockPos bed = new BlockPos(2, 64, 2);
        goal.candidates = List.of(bed);
        villager.activity = VillagerSchedule.Activity.REST;
        assertTrue(goal.canUse());
        goal.start();
        villager.stubBody(new Vec3(2.5, 64, 2.5));
        goal.tick();
        assertTrue(villager.sleeping);

        goal.bedBlock = false; // 床被破坏
        assertFalse(goal.canContinueToUse(), "床失效 → 结束");
        goal.stop();
        assertFalse(villager.sleeping, "床被破坏唤醒村民");
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed));
    }

    @Test
    void detachReleasesAllAndWakes() throws Exception {
        me.primaryuan.carpet.brain.VillagerBrain brain = new me.primaryuan.carpet.brain.VillagerBrain(villager);
        var assemble = me.primaryuan.carpet.brain.VillagerBrain.class
                .getDeclaredMethod("assemble");
        assemble.setAccessible(true);
        assemble.invoke(brain);
        TestSleepGoal goal = new TestSleepGoal(villager);
        BlockPos bed = new BlockPos(2, 64, 2);
        goal.candidates = List.of(bed);
        villager.activity = VillagerSchedule.Activity.REST;
        assertTrue(goal.canUse());
        goal.start();
        villager.stubBody(new Vec3(2.5, 64, 2.5));
        goal.tick();
        assertTrue(villager.sleeping);

        brain.onDetach(); // 换脑/下线/死亡
        assertFalse(VillagerPoiRegistry.isOccupied(world, bed), "卸载释放全部占用（不留幽灵床位）");
        assertFalse(villager.sleeping, "卸载唤醒");
    }

    @Test
    void workGoalSkipsForeignOccupiedStation() {
        TestWorkGoal goal = new TestWorkGoal(villager);
        BlockPos taken = new BlockPos(3, 64, 3);
        villager.activity = VillagerSchedule.Activity.WORK;
        assertTrue(VillagerPoiRegistry.claim(UUID.randomUUID(), world, taken, VillagerPoiRegistry.Kind.WORKSTATION));
        goal.candidates = List.of(taken);
        assertFalse(goal.canUse(), "工作站被他人占用 → 本村民不登记不前往");
        assertNull(currentPoi(goal));
    }

    private static Object currentPoi(TestWorkGoal goal) {
        return BrainTestStubs.readField(goal, PlayerGotoPoiGoal.class, "pendingPoi");
    }
}
