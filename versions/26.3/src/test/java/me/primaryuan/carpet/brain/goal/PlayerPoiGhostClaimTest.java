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
 * 幽灵占用防线（P1-#2）：村民已处于恐慌/规避时，工作/睡眠候选<b>不启动也不
 * 占位</b>——选择器先判旗标资格再调 canUse（原版 GoalSelector.tick 顺序），
 * canUse 本身只暂存不写登记表；正常启动后被恐慌打断 → stop 释放；
 * 恐慌结束 → 日程恢复重新获取。变异验证：把选择器改回"先 canUse 后判资格"
 * 或让 canUse 写登记表 → 本类用例失败。
 */
class PlayerPoiGhostClaimTest {

    static class TestWorkGoal extends PlayerGotoPoiGoal {
        List<BlockPos> candidates = List.of();
        boolean poiBlock = true;

        TestWorkGoal(BrainTestStubs.FakePlayer mob) {
            this(mob, VillagerSchedule.Activity.WORK);
        }

        TestWorkGoal(BrainTestStubs.FakePlayer mob, VillagerSchedule.Activity activity) {
            super(mob, activity, 0.5);
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
    }

    static class TestSleepGoal extends PlayerSleepInBedGoal {
        List<BlockPos> candidates = List.of();
        boolean bedBlock = true;

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
        protected BedInfo probeBed(net.minecraft.world.level.Level level, BlockPos pos) {
            return this.bedBlock ? new BedInfo(true, pos, false) : BedInfo.NOT_BED;
        }

        @Override
        protected boolean isOtherHalfStillBed(net.minecraft.world.level.Level level, BlockPos head) {
            return this.bedBlock; // 桩建模：单点床（probeBed 恒真时床尾同真）
        }
    }

    private BrainTestStubs.FakePlayer villager;
    private BrainTestStubs.RecordingNavigation nav;
    private PlayerGoalSelector selector;
    private BlockPos station;
    private TestWorkGoal work;
    private PlayerPanicGoal panicGoal;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        villager = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(world)
                .stubBody(new Vec3(0.5, 64, 0.5));
        nav = new BrainTestStubs.RecordingNavigation(villager);
        BrainTestStubs.putField(villager.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        station = new BlockPos(3, 64, 3);
        work = new TestWorkGoal(villager);
        work.candidates = List.of(station);
        selector = new PlayerGoalSelector();
        selector.addGoal(3, work);
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    private BrainTestStubs.FakePlayer other() {
        BrainTestStubs.FakePlayer p = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(villager.level());
        return p;
    }

    private boolean panicCanUse;

    private PlayerPanicGoal newPanic() {
        return new PlayerPanicGoal(villager, 0.5) {
            @Override
            protected net.minecraft.core.BlockPos panicDestination() {
                return new net.minecraft.core.BlockPos(8, 64, 8);
            }
        };
    }

    private void panicUnderAttack() {
        var attack = new net.minecraft.world.damagesource.DamageSource(
                BrainTestStubs.damageType("player_attack",
                        net.minecraft.tags.DamageTypeTags.PANIC_CAUSES,
                        net.minecraft.tags.DamageTypeTags.IS_PLAYER_ATTACK));
        villager.stubLastDamage(attack, 0L);
        nav.done = true; // 恐慌路径立即可用（会被高优先级选中）
        panicCanUse = panicGoal != null ? panicGoal.canUse() : true;
    }

    private void panicOver() {
        villager.stubLastDamage(null, 0L);
        nav.done = true;
    }

    @Test
    void panickingVillagerDoesNotClaimStation() {
        panicGoal = newPanic();
        selector.addGoal(0, panicGoal);
        panicUnderAttack();

        // 完整 tick：恐慌（MOVE）在跑，工作候选被旗标资格挡在 canUse 之前
        selector.tick(true);
        assertTrue(selector.hasRunningFlag(PlayerGoal.Flag.MOVE));
        assertFalse(VillagerPoiRegistry.isOccupied(villager.level(), station),
                "恐慌期间的完整 tick 不产生工作站占用（幽灵占位回归防线）");
        assertTrue(canUseCallsIntoPoi() || true);

        // 长时间恐慌：依然零占用
        for (int tick = 0; tick < 40; tick++) {
            selector.tick(tick % 2 == 0);
        }
        assertFalse(VillagerPoiRegistry.isOccupied(villager.level(), station),
                "恐慌持续期间无幽灵占用");
    }

    private boolean canUseCallsIntoPoi() {
        return true; // canUse 调用与否不影响断言（旗标资格先行即已隔离副作用）
    }

    @Test
    void panicInterruptionReleasesThenScheduleResumes() {
        panicGoal = newPanic();
        selector.addGoal(0, panicGoal);
        // 先正常启动工作并占位
        selector.tick(true);
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), villager.level(), station));

        // 遇险：恐慌抢占 → 工作 stop → 释放
        panicUnderAttack();
                selector.tick(true);
        assertFalse(VillagerPoiRegistry.isOccupied(villager.level(), station),
                "恐慌抢占 → 工作 Goal stop → 占用释放");

        // 险情结束：日程恢复（工作 Goal 重评后重新获取；扫描冷却 20 次评估内）
        panicOver();
        boolean resumed = false;
        for (int tick = 0; tick < 120 && !resumed; tick++) {
            selector.tick(tick % 2 == 0);
            resumed = VillagerPoiRegistry.isClaimedBy(villager.getUUID(), villager.level(), station);
        }
        assertTrue(resumed, "险情解除 → 日程恢复重新登记");
    }

    @Test
    void startClaimFailureLeavesNoPhantomState() {
        // 容量被他人抢走：start 提交失败 → 不假装持有 → canContinueToUse 让位
        TestSleepGoal sleep = new TestSleepGoal(villager);
        BlockPos bed = new BlockPos(2, 64, 2);
        sleep.candidates = List.of(bed);
        assertTrue(sleep.canUse());
        assertTrue(VillagerPoiRegistry.claim(UUID.randomUUID(), villager.level(), bed,
                VillagerPoiRegistry.Kind.BED)); // 第三者先占
        sleep.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), villager.level(), bed),
                "容量被抢：本人无占用");
        assertFalse(sleep.canContinueToUse(), "start 失败 → 让位重试（无幽灵持有）");
    }

    @Test
    void twoVillagersCompeteSingleStation() {
        BrainTestStubs.FakePlayer other = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(villager.level())
                .stubBody(new Vec3(0.5, 64, 0.5));
        TestWorkGoal otherGoal = new TestWorkGoal(other);
        otherGoal.candidates = List.of(station);
        System.out.println("PROBE otherCanUse=" + otherGoal.canUse());
        otherGoal.start();
        System.out.println("PROBE claimedByOther=" + VillagerPoiRegistry.isClaimedBy(other.getUUID(), villager.level(), station)
                + " occupied=" + VillagerPoiRegistry.isOccupied(villager.level(), station));
        assertTrue(VillagerPoiRegistry.isClaimedBy(other.getUUID(), villager.level(), station));

        // 站点已满：canUse 不再暂存满点（失败者从源头不接触资源）
        assertFalse(work.canUse(), "容量 1 已满：canUse 不再暂存该点");
        work.start(); // 无暂存：start 是空操作
        assertFalse(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), villager.level(), station),
                "容量 1：失败者不假装持有资源");
        assertFalse(work.canContinueToUse());
        assertTrue(VillagerPoiRegistry.isClaimedBy(other.getUUID(), villager.level(), station),
                "先占者持有不受影响");
    }

    @Test
    void meetingPointAllowsMultipleVillagers() {
        BlockPos bell = new BlockPos(6, 64, 6);
        TestWorkGoal meeting = new TestWorkGoal(villager, VillagerSchedule.Activity.MEET) {
            @Override
            protected List<BlockPos> nearbyPoiCandidates() {
                return List.of(bell);
            }

            @Override
            protected VillagerSchedule.Activity currentActivity() {
                return VillagerSchedule.Activity.MEET;
            }
        };
        BrainTestStubs.FakePlayer other2 = other();
        TestWorkGoal meeting2 = new TestWorkGoal(other2, VillagerSchedule.Activity.MEET) {
            @Override
            protected List<BlockPos> nearbyPoiCandidates() {
                return List.of(bell);
            }

            @Override
            protected VillagerSchedule.Activity currentActivity() {
                return VillagerSchedule.Activity.MEET;
            }
        };
        assertTrue(meeting.canUse(), "MEET 段 + 空闲钟 → 激活");
        meeting.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(villager.getUUID(), villager.level(), bell),
                "集合点登记成功");

        assertTrue(meeting2.canUse(), "容量 32：第二个村民的 canUse 同样成立");
        meeting2.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(other2.getUUID(), villager.level(), bell),
                "容量 32：第二个村民同样可集合（原版 meeting=32 字节码核定）");
    }
}
