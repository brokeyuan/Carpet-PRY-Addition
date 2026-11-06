package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实床 BlockState 方向锁（round5 #1）：
 * 使用真实 RED_BED 方块状态（16 个状态位完整）走生产 probeBed/isOtherHalfStillBed/
 * isOtherHalfOf——四个 FACING × HEAD/FOOT 双向（FOOT→head 规范化、HEAD→foot 完整性、
 * getSleepingPos 床头或床尾都算本人占用）、缺另一半拒绝、旁边另一张床不冒充床尾。
 * <b>不覆写任何生产方向计算</b>——只把 BlockState 提供到 world.getBlockState 缝。
 */
class PlayerBedRealStateTest {

    /** 真实 BlockState 世界桩：只实现 getBlockState（生产方向计算全保留） */
    static class RealStateSleepGoal extends PlayerSleepInBedGoal {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        VillagerSchedule.Activity activity = VillagerSchedule.Activity.REST;

        RealStateSleepGoal(BrainTestStubs.FakePlayer mob) {
            super(mob);
        }

        void placeBed(BlockPos head, BlockPos foot, Direction facing, boolean occupied) {
            // 真实床方块状态：FACING 从 FOOT 指向 HEAD（原版 setPlacedBy 语义），
            // PART=HEAD/FOOT，OCCUPIED 按参数
            BlockState headState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                    .setValue(AbstractBedBlock.FACING, facing)
                    .setValue(AbstractBedBlock.PART, BedPart.HEAD)
                    .setValue(AbstractBedBlock.OCCUPIED, occupied);
            BlockState footState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                    .setValue(AbstractBedBlock.FACING, facing)
                    .setValue(AbstractBedBlock.PART, BedPart.FOOT)
                    .setValue(AbstractBedBlock.OCCUPIED, false);
            this.states.put(head, headState);
            this.states.put(foot, footState);
        }

        @Override
        protected VillagerSchedule.Activity currentActivity() {
            return this.activity;
        }

        @Override
        protected List<BlockPos> nearbyBedCandidates() {
            return List.copyOf(this.states.keySet());
        }

        /** 仅读块缝：真实 BlockState（方向/PART/配对判断全走生产） */
        @Override
        protected BlockState readBedState(Level level, BlockPos pos) {
            return this.states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        /** 模拟自己已睡（getSleepingPos 可为床头或床尾，由用例指定） */
        java.util.Optional<BlockPos> selfSleepingPos = java.util.Optional.empty();

        void markSelfAsleepAt(BlockPos pos) {
            this.selfSleepingPos = java.util.Optional.of(pos);
        }

    }

    @BeforeAll
    static void boot() {
        TestRegistries.bootstrap();
        // 纯 JVM 未装载数据包标签：给真实床方块 Holder 挂 #minecraft:beds，
        // 使生产 bedTag() 的 is(TagKey) 与服务端同语义（同 BrainTestStubs.damageType 模式）
        var bed = net.minecraft.world.level.block.Blocks.BED
                .pick(net.minecraft.world.item.DyeColor.RED)
                .builtInRegistryHolder();
        BrainTestStubs.bindTag(bed, net.minecraft.tags.BlockTags.BEDS);
    }

    private RealStateSleepGoal goal;
    private BrainTestStubs.FakePlayer villager;

    private BrainTestStubs.FakePlayer villagerNear(Vec3 pos) {
        var world = BrainTestStubs.stubWorld();
        return BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(world)
                .stubBody(pos);
    }

    @AfterEach
    void tearDown() {
        VillagerPoiRegistry.clear();
    }

    /**
     * 四个 FACING 的床头→床尾映射验证（经生产 getConnectedDirection 计算，
     * 世界桩仅提供 BlockState——通过覆写 probeBed 的世界入口不可能，故直接
     * 驱动生产 isOtherHalfStillBed 的 level 参数：桩 Level 读块 NPE 不可行。
     * 真实驱动方案：本测试覆写 nearbyBedCandidates + 用最小 Level 不可行，
     * 因此对生产方向计算的验证走"同缝不同数据"：把生产 getConnectedDirection
     * 经 isOtherHalfStillBed 的真实 BlockState 直测（不覆写该方法），
     * Level 桩提供 getBlockState 由 stubWorld 无方块读——改用
     * RealStateSleepGoal.states 表 + 覆写 probeBed 仅供 nearby 扫描是不够的。
     * 最终方案：isOtherHalfStillBed(level, head) 的 level 参数仅用于 getBlockState，
     * 而桩 Level 的 getBlockState 会 NPE——所以这里用一个只覆写 getBlockState
     * 世界观的最小测试替身不可行，退而验证生产方法在真实 BlockState 下的
     * 纯函数行为：直接构造 BlockState 并调用生产 getConnectedDirection 等价路径。
     */
    @Test
    void connectedDirectionOnRealHeadStatePointsToFoot() {
        // 生产方向计算验证：HEAD 状态的 getConnectedDirection 指向床尾
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState headState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                    .setValue(AbstractBedBlock.FACING, facing)
                    .setValue(AbstractBedBlock.PART, BedPart.HEAD)
                    .setValue(AbstractBedBlock.OCCUPIED, false);
            // 生产使用的同一 API 调用（PlayerSleepInBedGoal.bedConnectedDirection）。
            // 原版 setPlacedBy 字节码：HEAD 位于 pos.relative(FACING)——FACING 从 FOOT
            // 指向 HEAD，故 HEAD 状态的 getConnectedDirection = FACING.getOpposite()
            // （内部已反转，返回值即指向床尾）
            Direction dir = callBedConnectedDirection(headState);
            BlockPos head = new BlockPos(4, 64, 4);
            BlockPos expectedFoot = head.relative(facing.getOpposite());
            assertEquals(expectedFoot, head.relative(dir),
                    "FACING=" + facing + "：床头状态的连接方向应指向床尾（facing 反向）");
            // 修复核验：dir 必须恰为 facing.getOpposite()（旧缺陷多加一层 getOpposite
            // 时 dir 会变成 facing 本身，期望床尾坐标随即不匹配——上方断言已击杀）
            assertEquals(facing.getOpposite(), dir,
                    "FACING=" + facing + "：生产 bedConnectedDirection(HEAD) 应为 FACING 的反向（无双重反转）");
        }
    }

    @Test
    void connectedDirectionOnFootStatePointsBackToHead() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState footState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                    .setValue(AbstractBedBlock.FACING, facing)
                    .setValue(AbstractBedBlock.PART, BedPart.FOOT)
                    .setValue(AbstractBedBlock.OCCUPIED, false);
            Direction dir = callBedConnectedDirection(footState);
            BlockPos foot = new BlockPos(4, 64, 4);
            // FOOT 状态：getConnectedDirection 返回 FACING 本身——FOOT 沿 facing
            // 指向 HEAD（原版 setPlacedBy 语义），probeBed 对 FOOT 入参经此映射床头
            assertEquals(facing, dir, "FACING=" + facing + "：床尾状态返回 facing（指向床头）");
        }
    }

    /** 经生产 PlayerSleepInBedGoal 的私有静态 bedConnectedDirection 反射调用 */
    private static Direction callBedConnectedDirection(BlockState state) {
        try {
            var m = PlayerSleepInBedGoal.class.getDeclaredMethod("bedConnectedDirection", BlockState.class);
            m.setAccessible(true);
            return (Direction) m.invoke(null, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 全链真实状态驱动：生产 probeBed/isOtherHalfStillBed/isOtherHalfOf 全部
     * 走真实 BlockState——通过覆写附近候选 + 覆写"世界读块"仅此一处（Level 桩
     * getBlockState 不可用），读块缝收口在 probeBed 与 isOtherHalfStillBed 共享的
     * 单一 stateSupplier 上（RealStateSleepGoal 覆写两者，但**方向计算保留生产**）。
     */
    @Test
    void fullChainRealStateFourFacingsFootNearHeadNormalization() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VillagerPoiRegistry.clear();
            var world = BrainTestStubs.stubWorld();
            BlockPos head = new BlockPos(4, 64, 4);
            // 原版 setPlacedBy：HEAD 在 facing 方向 → foot 在反方向
            BlockPos foot = head.relative(facing.getOpposite());
            // 村民站床尾旁（离床尾更近）
            var v = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubWorld(world)
                    .stubBody(new Vec3(foot.getX() + 0.5, 64, foot.getZ() + 1.5));
            RealStateSleepGoal g = new RealStateSleepGoal(v) {
                @Override
                protected VillagerSchedule.Activity currentActivity() {
                    return VillagerSchedule.Activity.REST; // 桩世界日程固化为 rest 段
                }

                @Override
                protected BlockState readBedState(Level level, BlockPos pos) {
                    // 仅读块缝：真实 BlockState；方向计算全部走生产
                    return this.states.getOrDefault(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            };
            g.placeBed(head, foot, facing, false);
            assertTrue(g.canUse(), "FACING=" + facing + "：rest 段激活");
            assertEquals(head, BrainTestStubs.readField(g, PlayerSleepInBedGoal.class, "pendingBed"),
                    "FACING=" + facing + "：床尾候选规范化到床头");
            g.start();
            assertEquals(head, BrainTestStubs.readField(g, PlayerSleepInBedGoal.class, "claimedBed"),
                    "FACING=" + facing + "：认领床头");
            assertTrue(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, head),
                    "FACING=" + facing + "：按床头登记");
        }
    }


    static net.minecraft.world.level.block.state.properties.EnumProperty<Direction> facingProperty() {
        return net.minecraft.world.level.block.AbstractBedBlock.FACING;
    }

    static net.minecraft.world.level.block.state.properties.EnumProperty<BedPart> bedPartProperty() {
        //#if MC >= 260300
        //$$ return net.minecraft.world.level.block.AbstractBedBlock.PART;
        //#else
        return net.minecraft.world.level.block.BedBlock.PART;
        //#endif
    }

    static Direction bedConnectedDirection(BlockState state) {
        //#if MC >= 260300
        //$$ return net.minecraft.world.level.block.AbstractBedBlock.getConnectedDirection(state);
        //#else
        return net.minecraft.world.level.block.BedBlock.getConnectedDirection(state);
        //#endif
    }

    @Test
    void missingFootHalfRejectsCandidate() {
        // 床头存在、床尾缺失：生产 isOtherHalfStillBed 完整性检查拒绝
        // （仅 readBedState 提供真实 BlockState，方向/PART/配对判断全走生产）
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        BlockPos head = new BlockPos(4, 64, 4);
        var v = villagerNear(new Vec3(4.5, 64, 5.5));
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        // 只放床头（north：HEAD 在 facing 反方向 = south 即 (4,64,3)？——
        // 原版 setPlacedBy：HEAD 在 facing 方向。north 时 HEAD=(4,64,3)。
        // 本用例直接以 (4,64,4) 为床头、FACING=south（foot=(4,64,4)？不——
        // 统一：head=(4,64,4)、facing=south → foot=head.relative(south.opposite())=(4,64,3)。
        // 为简明用 facing=north：head=(4,64,4) 为床头态、正确床尾=(4,64,5) 缺失。
        g.states.put(head, realBedState(Direction.NORTH, BedPart.HEAD, false));
        assertFalse(g.canUse(), "床尾缺失：候选被拒（双格床完整性）");
        g.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, head), "未认领");
    }

    @Test
    void gapOccupiedByOtherBedHeadRejects() {
        // A 床头的正确床尾位置放另一张床的 HEAD：PART 不匹配 → 配对检查拒绝
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        BlockPos headA = new BlockPos(4, 64, 4);
        BlockPos gap = headA.relative(Direction.NORTH.getOpposite()); // A 的正确床尾位
        var v = villagerNear(new Vec3(4.5, 64, 5.5));
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        g.states.put(headA, realBedState(Direction.NORTH, BedPart.HEAD, false));
        g.states.put(gap, realBedState(Direction.NORTH, BedPart.HEAD, false)); // 另一床的 HEAD
        assertFalse(g.canUse(), "缺口位置是另一床的 HEAD（PART 不匹配）：不能冒充 A 的床尾");
        g.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, headA), "未认领");
    }

    @Test
    void gapOccupiedByMismatchedFacingFootRejects() {
        // A 床头的床尾位放 east 床的 FOOT：FACING 不一致 → 双向配对失败拒绝
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        BlockPos headA = new BlockPos(4, 64, 4);
        BlockPos gap = headA.relative(Direction.NORTH.getOpposite());
        var v = villagerNear(new Vec3(4.5, 64, 5.5));
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        g.states.put(headA, realBedState(Direction.NORTH, BedPart.HEAD, false));
        g.states.put(gap, realBedState(Direction.EAST, BedPart.FOOT, false));
        assertFalse(g.canUse(), "朝向不匹配的 FOOT：双向配对失败拒绝");
        g.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, headA), "未认领");
    }

    @Test
    void adjacentBedsEachRegisterOwnHead() {
        // 两张完整床相邻：A 头完整性只认 A 尾（双向配对校验），互不冒充
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        BlockPos headA = new BlockPos(4, 64, 4);
        BlockPos footA = headA.relative(Direction.NORTH.getOpposite());
        BlockPos headB = new BlockPos(4, 64, 6);
        BlockPos footB = headB.relative(Direction.NORTH.getOpposite());
        var v = villagerNear(new Vec3(4.5, 64, 3.5)); // 站 A 床头旁
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        g.placeBed(headA, footA, Direction.NORTH, false);
        g.placeBed(headB, footB, Direction.NORTH, false);
        assertTrue(g.canUse(), "A 床完整：邻床 B 不影响");
        assertEquals(headA, BrainTestStubs.readField(g, PlayerSleepInBedGoal.class, "pendingBed"),
                "A 按自身床头登记（非 B 的任何一半）");
        g.start();
        assertTrue(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, headA));
        assertFalse(VillagerPoiRegistry.isOccupied(world, headB), "B 床未被 A 占用");

        var v2 = villagerNear(new Vec3(4.5, 64, 6.5)); // 站 B 床头旁
        RealStateSleepGoal g2 = new RealStateSleepGoal(v2);
        g2.placeBed(headA, footA, Direction.NORTH, false);
        g2.placeBed(headB, footB, Direction.NORTH, false);
        assertTrue(g2.canUse());
        assertEquals(headB, BrainTestStubs.readField(g2, PlayerSleepInBedGoal.class, "pendingBed"),
                "B 按自身床头登记（A 占用不影响 B）");
    }

    @Test
    void occupiedByRealPlayerNotStolen() {
        VillagerPoiRegistry.clear();
        var world = BrainTestStubs.stubWorld();
        BlockPos head = new BlockPos(4, 64, 4);
        BlockPos foot = head.relative(Direction.NORTH.getOpposite());
        var v = villagerNear(new Vec3(4.5, 64, 5.5));
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        g.placeBed(head, foot, Direction.NORTH, true); // OCCUPIED=true（真人已睡）
        assertFalse(g.canUse(), "OCCUPIED=true 的床不可选（不抢真人/原版村民）");
        g.start();
        assertFalse(VillagerPoiRegistry.isClaimedBy(v.getUUID(), world, head), "未认领");
    }

    @Test
    void selfOccupiedMaintainsNotEvictedFootAndHead() {
        // 本人已睡：OCCUPIED=true 但 getSleepingPos 命中床头或床尾 → 自己的有效占用
        // （原版 startSleeping 记录实际躺下格，两半任一命中即本人）
        VillagerPoiRegistry.clear();
        BlockPos head = new BlockPos(4, 64, 4);
        BlockPos foot = head.relative(Direction.NORTH.getOpposite());
        var world = BrainTestStubs.stubWorld();
        var v = SleepStub.create(world, new Vec3(4.5, 64, 4.5));
        RealStateSleepGoal g = new RealStateSleepGoal(v);
        g.placeBed(head, foot, Direction.NORTH, false);
        assertTrue(g.canUse());
        g.start();
        // 身体侧写 OCCUPIED=true + 自己睡在床尾（原版记录实际躺下格）
        g.states.put(head, realBedState(Direction.NORTH, BedPart.HEAD, true));
        v.selfSleepingPos = java.util.Optional.of(foot);
        assertTrue(g.canContinueToUse(), "本人已睡在床尾 → 续行维持，不误判他人占用");
        // 睡在床头同样识别
        v.selfSleepingPos = java.util.Optional.of(head);
        assertTrue(g.canContinueToUse(), "本人已睡在床头 → 续行维持");
        // 他人占用（无睡眠记录）→ 误判防护
        v.selfSleepingPos = java.util.Optional.empty();
        assertFalse(g.canContinueToUse(), "非本人占用（无睡眠记录）→ 结束");
    }

        /** 睡眠可编程的假人桩：pry 缝覆写（桩无 entityData，原生睡眠面不可达） */
    static class SleepStub extends BrainTestStubs.FakePlayer {
        java.util.Optional<BlockPos> selfSleepingPos = java.util.Optional.empty();

        static SleepStub create(net.minecraft.world.level.Level world, Vec3 pos) {
            SleepStub p = BrainTestStubs.allocate(SleepStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "uuid", id);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.selfSleepingPos = java.util.Optional.empty(); // Unsafe 跳过初始化器：补默认值
            p.stubWorld(world);
            p.stubBody(pos);
            return p;
        }

        @Override
        public boolean pryIsSleeping() {
            return this.selfSleepingPos.isPresent();
        }

        @Override
        public java.util.Optional<BlockPos> pryGetSleepingPos() {
            return this.selfSleepingPos;
        }

        @Override
        public void pryStopSleepInBed(boolean updateSleeping, boolean sendMessage) {
            this.selfSleepingPos = java.util.Optional.empty();
        }
    }

    static Direction bedConnectedDirectionForProbe(BlockState state) {
        try {
            var m = PlayerSleepInBedGoal.class.getDeclaredMethod("bedConnectedDirection", BlockState.class);
            m.setAccessible(true);
            return (Direction) m.invoke(null, state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static BlockState realBedState(Direction facing, BedPart part, boolean occupied) {
        return Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                .setValue(AbstractBedBlock.FACING, facing)
                .setValue(AbstractBedBlock.PART, part)
                .setValue(AbstractBedBlock.OCCUPIED, occupied);
    }

    static net.minecraft.world.level.block.state.properties.BooleanProperty occupiedProperty() {
        //#if MC >= 260300
        //$$ return net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED;
        //#else
        return net.minecraft.world.level.block.BedBlock.OCCUPIED;
        //#endif
    }

    static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> bedTagForProbe() {
        return net.minecraft.tags.BlockTags.BEDS;
    }
}
