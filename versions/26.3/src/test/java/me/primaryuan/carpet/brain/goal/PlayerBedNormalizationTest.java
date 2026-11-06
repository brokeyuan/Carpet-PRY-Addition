package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 床头规范化锁（round4 #1）：
 * probeBed 按传入坐标真实建模双格床（FOOT→head 映射），findFreeBed 规范化去重；
 * 离床尾更近时选中床尾、最终按床头认领与入睡；两假人各从一端发现同一床只有
 * 一个占用；入睡后连续完整/轻量 tick 维持睡眠不醒睡；真人已睡不抢；
 * 床毁/唤醒/换脑释放。变异验证：findFreeBed 去重移除 → 两端用例失败。
 */
class PlayerBedNormalizationTest {

    /** 可编程床世界桩：pos → 真实床 BlockState（FACING 从床尾指向床头） */
    static class BedWorld {
        final java.util.Map<BlockPos, BlockState> states = new java.util.HashMap<>();

        void addBed(BlockPos head, BlockPos foot, boolean occupied) {
            // FACING 方向 = foot → head（原版 setPlacedBy：HEAD 在 facing 方向）
            Direction facing = directionFromTo(foot, head);
            this.states.put(head, realBedState(facing, BedPart.HEAD, occupied));
            this.states.put(foot, realBedState(facing, BedPart.FOOT, false));
        }

        BlockState stateAt(BlockPos pos) {
            return this.states.get(pos);
        }

        static Direction directionFromTo(BlockPos from, BlockPos to) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (from.relative(d).equals(to)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("not adjacent: " + from + " -> " + to);
        }

        static BlockState realBedState(Direction facing, BedPart part, boolean occupied) {
            return net.minecraft.world.level.block.Blocks.BED
                    .pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                    .setValue(PlayerBedNormalizationTest.bedPartProperty(), part)
                    .setValue(PlayerBedNormalizationTest.facingProperty(), facing)
                    .setValue(PlayerBedNormalizationTest.occupiedProperty(), occupied);
        }
    }

    static class TestSleepGoal extends PlayerSleepInBedGoal {
        final BedWorld world;
        List<BlockPos> candidates = List.of();
        VillagerSchedule.Activity activity = VillagerSchedule.Activity.REST;

        TestSleepGoal(BrainTestStubs.FakePlayer mob, BedWorld world) {
            super(mob);
            this.world = world;
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return this.activity;
        }

        @Override
        protected List<BlockPos> nearbyBedCandidates() {
            return this.candidates;
        }

        /**
         * 真实 BlockState 驱动：world 桩仅提供方块状态（含 FACING/PART/OCCUPIED），
         * 床头映射与完整性检查走生产 bedConnectedDirection——不覆写生产方向计算。
         */
        @Override
        protected BedInfo probeBed(net.minecraft.world.level.Level level, BlockPos pos) {
            BlockState st = this.world.stateAt(pos);
            if (st == null) {
                return BedInfo.NOT_BED;
            }
            if (!st.hasProperty(PlayerBedNormalizationTest.bedPartProperty())) {
                return BedInfo.NOT_BED;
            }
            BlockPos head = st.getValue(PlayerBedNormalizationTest.bedPartProperty()) == BedPart.HEAD
                    ? pos.immutable()
                    : pos.relative(PlayerBedNormalizationTest.bedConnectedDirection(st));
            BlockState headSt = this.world.stateAt(head);
            boolean occ = headSt != null && headSt.getValue(PlayerBedNormalizationTest.occupiedProperty());
            return new BedInfo(true, head, occ);
        }

        @Override
        protected boolean isOtherHalfStillBed(net.minecraft.world.level.Level level, BlockPos head) {
            // 生产方向计算：床尾 = head + bedConnectedDirection(HEAD state)（无 getOpposite）
            BlockState st = this.world.stateAt(head);
            if (st == null || !st.hasProperty(PlayerBedNormalizationTest.bedPartProperty())) {
                return false;
            }
            BlockPos foot = head.relative(PlayerBedNormalizationTest.bedConnectedDirection(st));
            return this.world.stateAt(foot) != null;
        }

    }

    /** 睡眠可编程的村民桩 */
    static class Sleeper extends BrainTestStubs.FakePlayer {
        boolean sleeping;
        BlockPos sleptAt;
        int wakeCalls;

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
            this.wakeCalls++;
        }

        @Override
        public java.util.Optional<BlockPos> getSleepingPos() {
            return this.sleeping ? java.util.Optional.of(this.sleptAt) : java.util.Optional.empty();
        }
    }

    private net.minecraft.world.level.Level world;
    private BedWorld beds;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        VillagerPoiRegistry.clear();
        world = BrainTestStubs.stubWorld();
        beds = new BedWorld();
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    /** 床：床头 (4,64,4)，床尾 (5,64,4) */
    private static final BlockPos HEAD = new BlockPos(4, 64, 4);
    private static final BlockPos FOOT = new BlockPos(5, 64, 4);

    @Test
    void nearerFootStillClaimsAndSleepsAtHead() {
        beds.addBed(HEAD, FOOT, false);
        // 村民站床尾旁（离床尾更近）——扫描原始候选给床尾
        Sleeper v = Sleeper.create(world, new Vec3(5.5, 64, 5.5));
        TestSleepGoal goal = new TestSleepGoal(v, beds);
        goal.candidates = List.of(FOOT); // 扫描返回床尾坐标

        assertTrue(goal.canUse(), "rest 段激活");
        Object pending = BrainTestStubs.readField(goal, PlayerSleepInBedGoal.class, "pendingBed");
        assertEquals(HEAD, pending, "离床尾更近 → 仍规范化到床头暂存");
        goal.start();
        assertEquals(HEAD, BrainTestStubs.readField(goal, PlayerSleepInBedGoal.class, "claimedBed"),
                "认领坐标 = 床头");
        assertTrue(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, HEAD), "按床头登记");
        assertFalse(VillagerPoiRegistry.isOccupied(world, FOOT), "床尾不是独立登记键");

        // 走到床尾旁（≤2.5 格）→ 入睡；getSleepingPos 返回实际入睡格（生产传 claimedBed=床头）
        goal.tick();
        assertTrue(v.sleeping, "原生入睡生效");
        assertEquals(HEAD, v.sleptAt, "睡眠位置 = 床头（与登记键一致）");

        // 连续完整/轻量 tick：维持睡眠，不醒睡
        for (int tick = 0; tick < 40; tick++) {
            goal.tick(); // 睡眠分支直接 return
        }
        assertTrue(v.sleeping, "40 tick 后仍维持睡眠（无反复醒睡）");
        assertEquals(0, v.wakeCalls);
    }

    @Test
    void twoVillagersFromOppositeHalvesSingleOccupant() {
        beds.addBed(HEAD, FOOT, false);
        Sleeper a = Sleeper.create(world, new Vec3(4.5, 64, 5.5)); // 站床头侧
        Sleeper b = Sleeper.create(world, new Vec3(5.5, 64, 5.5)); // 站床尾侧
        TestSleepGoal goalA = new TestSleepGoal(a, beds);
        TestSleepGoal goalB = new TestSleepGoal(b, beds);
        goalA.candidates = List.of(HEAD); // A 从床头发现
        goalB.candidates = List.of(FOOT); // B 从床尾发现

        assertTrue(goalA.canUse());
        goalA.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(a.getUUID(), world, HEAD), "A 按床头占用");

        // B 规范化后同键 → 容量满 → canUse false（满点不暂存）
        assertFalse(goalB.canUse(), "同一床头资源：B 从床尾发现也不可认领");
        goalB.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(b.getUUID(), world, HEAD), "B 无占用");
    }

    @Test
    void occupiedByRealPlayerNotStolen() {
        beds.addBed(HEAD, FOOT, true); // 真人/原版村民已睡（OCCUPIED=true）
        Sleeper v = Sleeper.create(world, new Vec3(4.5, 64, 5.5));
        TestSleepGoal goal = new TestSleepGoal(v, beds);
        goal.candidates = List.of(HEAD);
        assertFalse(goal.canUse(), "OCCUPIED=true 的床不可选（不抢真人/原版村民）");
        goal.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, HEAD));
        assertFalse(v.sleeping, "未入睡");
    }

    @Test
    void selfOccupiedBedMaintainsNotEvicted() {
        beds.addBed(HEAD, FOOT, false);
        Sleeper v = Sleeper.create(world, new Vec3(4.5, 64, 5.5));
        TestSleepGoal goal = new TestSleepGoal(v, beds);
        goal.candidates = List.of(HEAD);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertTrue(v.sleeping);
        // 模拟原版 startSleeping 写 OCCUPIED=true（身体侧真实状态）
        beds.states.put(HEAD, BedWorld.realBedState(
                BedWorld.directionFromTo(FOOT, HEAD), BedPart.HEAD, true));
        // 续行：本人已睡 → 自己的有效占用，不误判他人
        assertTrue(goal.canContinueToUse(), "本人已睡在该床 → 续行维持");
        goal.tick();
        assertTrue(v.sleeping, "tick 维持（自我识别，不自我驱逐）");
    }

    @Test
    void bedDestroyedWakesAndReleases() {
        beds.addBed(HEAD, FOOT, false);
        Sleeper v = Sleeper.create(world, new Vec3(4.5, 64, 5.5));
        TestSleepGoal goal = new TestSleepGoal(v, beds);
        goal.candidates = List.of(HEAD);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertTrue(v.sleeping);

        beds.states.remove(HEAD); // 床头被破坏
        assertFalse(goal.canContinueToUse(), "床失效 → 结束");
        goal.stop();
        assertFalse(v.sleeping, "唤醒");
        assertFalse(VillagerPoiRegistry.isOccupied(world, HEAD), "释放登记");
    }

    @Test
    void detachReleasesAndWakes() {
        beds.addBed(HEAD, FOOT, false);
        Sleeper v = Sleeper.create(world, new Vec3(4.5, 64, 5.5));
        TestSleepGoal goal = new TestSleepGoal(v, beds);
        goal.candidates = List.of(HEAD);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick();
        assertTrue(v.sleeping);

        goal.stop(); // 换脑/下线/死亡路径
        assertFalse(v.sleeping, "卸载唤醒");
        assertFalse(VillagerPoiRegistry.isOccupied(world, HEAD), "卸载释放");
        assertEquals(1, v.wakeCalls);
    }


    /** 生产 bedConnectedDirection 反射（测试经生产方向计算，不覆写） */
    static Direction bedConnectedDirection(BlockState state) {
        try {
            var m = PlayerSleepInBedGoal.class.getDeclaredMethod("bedConnectedDirection", BlockState.class);
            m.setAccessible(true);
            return (Direction) m.invoke(null, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static net.minecraft.world.level.block.state.properties.EnumProperty<BedPart> bedPartProperty() {
        //#if MC >= 260300
        //$$ return net.minecraft.world.level.block.AbstractBedBlock.PART;
        //#else
        return net.minecraft.world.level.block.BedBlock.PART;
        //#endif
    }

    static net.minecraft.world.level.block.state.properties.EnumProperty<Direction> facingProperty() {
        return net.minecraft.world.level.block.AbstractBedBlock.FACING;
    }

    static net.minecraft.world.level.block.state.properties.BooleanProperty occupiedProperty() {
        //#if MC >= 260300
        //$$ return net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED;
        //#else
        return net.minecraft.world.level.block.BedBlock.OCCUPIED;
        //#endif
    }
}
