package me.primaryuan.carpet.brain.goal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 铜傀儡分类目标（七期 P2）：取货上限 16 且超出部分放回源箱；目标箱 =
 * 空箱或含同物组（isSameItem）；两假人不共用同一容器；无目标进 140t 冷却。
 * 世界输入（方块表 + 容器表 + 真 ChestMenu）由测试提供，判定与点击序列走生产。
 */
class PlayerCopperGolemSortTest {

    static class WorldPlayer extends BrainTestStubs.FakePlayer {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, SimpleContainer> containers = new HashMap<>();

        static WorldPlayer create(ServerLevel world, Vec3 pos) {
            WorldPlayer p = BrainTestStubs.FakePlayer.createFakeOf(WorldPlayer.class, UUID.randomUUID());
            p.blocks = new HashMap<>();
            p.containers = new HashMap<>();
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            return p;
        }
    }

    private ServerLevel world;
    private WorldPlayer golem;
    private TestSortGoal goal;
    private PlayerGoalSelector selector;
    private BlockPos copperChest;
    private BlockPos plainChest;

    /** 26.3 的铜箱是风化集合：取未风化变体（注册表 holder 用变体块） */
    private static Block copperChestBlock() {
        return Blocks.COPPER_CHEST.weathering()
                .pick(net.minecraft.world.level.block.WeatheringCopper.WeatherState.UNAFFECTED);
    }

    private static BlockState copperChestState() {
        return copperChestBlock().defaultBlockState();
    }

    static class TestSortGoal extends PlayerCopperGolemSortGoal {
        final WorldPlayer wp;

        TestSortGoal(WorldPlayer mob) {
            super(mob);
            this.wp = mob;
        }

        @Override
        protected BlockState readStateAt(net.minecraft.world.level.Level level, BlockPos pos) {
            BlockState s = this.wp.blocks.get(pos);
            return s != null ? s : Blocks.STONE.defaultBlockState();
        }

        @Override
        protected boolean isChunkLoaded(net.minecraft.world.level.Level level, BlockPos pos) {
            return true; // 桩世界无 chunkSource：全部视为已加载
        }

        @Override
        protected net.minecraft.world.Container containerAt(
                net.minecraft.world.level.Level level, BlockPos pos) {
            return this.wp.containers.get(pos);
        }

        final List<BlockPos> opened = new ArrayList<>();
        final List<BlockPos> closed = new ArrayList<>();
        boolean menuOpen;
        long menuOpenSince = Long.MIN_VALUE;
        long menuCloseAt = Long.MIN_VALUE;
        boolean withinRange = true;
        /** 前 N 次开箱返回失败（模拟封顶/遮挡） */
        int openFailLimit;
        int openFailCount;

        @Override
        protected boolean withinInteractionRange(BlockPos pos) {
            return this.withinRange; // 桩世界无属性表：距离门由生命周期测试单独断言
        }

        @Override
        protected AbstractContainerMenu openChestMenu(BlockPos pos) {
            // 桩环境无网络层：以真 ChestMenu 模拟玩家右键打开并保持会话
            if (this.openFailCount < this.openFailLimit) {
                this.openFailCount++;
                return null; // 模拟封顶/遮挡等真实开箱失败
            }
            var menu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                    this.player.getInventory(), this.wp.containers.get(pos), 3);
            this.player.containerMenu = menu;
            this.opened.add(pos);
            this.menuOpen = true;
            this.menuOpenSince = this.wp.level().getGameTime();
            return menu;
        }

        @Override
        protected void closeChestSession() {
            if (this.menuOpen) {
                this.menuOpen = false;
                this.menuCloseAt = this.wp.level().getGameTime();
                this.closed.add(BrainTestStubs.readField(this,
                        PlayerCopperGolemSortGoal.class, "openMenuTarget") instanceof BlockPos pos ? pos : null);
                this.player.containerMenu = this.player.inventoryMenu;
            }
            super.closeChestSession();
        }
    }

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        BrainTestStubs.bindTag(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.wrapAsHolder(copperChestBlock()),
                BlockTags.COPPER_CHESTS);
        world = (ServerLevel) BrainTestStubs.stubWorld();
        golem = WorldPlayer.create(world, new Vec3(5.5, 64, 5.5));
        BrainTestStubs.putField(golem.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", new BrainTestStubs.RecordingNavigation(golem));
        goal = new TestSortGoal(golem);
        selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
        copperChest = new BlockPos(4, 64, 4);
        plainChest = new BlockPos(6, 64, 4);
        golem.blocks.put(copperChest, copperChestState());
        golem.blocks.put(plainChest, Blocks.CHEST.defaultBlockState());
        golem.containers.put(copperChest, new SimpleContainer(27));
        golem.containers.put(plainChest, new SimpleContainer(27));
    }

    @AfterEach
    void tearDown() {
        PlayerCopperGolemSortGoal.releaseBrain(golem);
    }

    private void setGameTime(long t) {
        var ld = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(world, net.minecraft.world.level.Level.class, "levelData");
        ld.setGameTime(t);
    }

    private long testTime;

    private void setTestTime(long t) {
        this.testTime = t;
        setGameTime(t);
    }

    private void tickTest() {
        setGameTime(++this.testTime);
        selector.tick(true);
    }

    private void tickAfterCooldown() {
        long cooldown = (Long) BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "cooldownUntil");
        setTestTime(Math.max(this.testTime, cooldown) + 1);
        selector.tick(true);
    }

    private int totalInInventory(net.minecraft.world.item.Item item) {
        int n = 0;
        var inv = golem.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    @Test
    void takesFromCopperChestCappedAtSixteen() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        var menu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(copperChest), 3);
        int[] r = PlayerCopperGolemSortGoal.takeViaMenu(menu, golem, new int[]{-1, -1});
        assertEquals(16, r[0], "单趟携带上限 16（原版 min(count,16)）");
        assertEquals(48, golem.containers.get(copperChest).getItem(0).getCount(),
                "超出 16 的部分经右键逐件放回源箱（64-16=48）");
        assertEquals(16, totalInInventory(Items.DIRT), "光标余量经玩家背包槽入包");
    }

    @Test
    void takesSmallStackWhole() {
        golem.containers.get(copperChest).setItem(3, new ItemStack(Items.DIRT, 9));
        var menu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(copperChest), 3);
        int[] r = PlayerCopperGolemSortGoal.takeViaMenu(menu, golem, new int[]{-1, -1});
        assertEquals(9, r[0], "小整组全取");
        assertTrue(golem.containers.get(copperChest).getItem(3).isEmpty(), "源槽清空");
        assertEquals(9, totalInInventory(Items.DIRT));
    }

    @Test
    void placesIntoEmptyChestAndMergesSameItem() {
        // 空目标箱：整组入首个空槽
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        var emptyMenu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(plainChest), 3);
        PlayerCopperGolemSortGoal.placeViaMenu(emptyMenu, golem, new ItemStack(Items.DIRT), 16);
        assertEquals(16, golem.containers.get(plainChest).getItem(0).getCount(), "空箱整组放入");
        assertTrue(golem.getInventory().getItem(0).isEmpty(), "背包货物清空");

        // 同物组合并：全新箱内 50 土（上限 64）→ 并入 14 至 64，余 2 入首个空槽
        golem.containers.put(plainChest, new SimpleContainer(27));
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        golem.containers.get(plainChest).setItem(2, new ItemStack(Items.DIRT, 50));
        var mergeMenu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(plainChest), 3);
        PlayerCopperGolemSortGoal.placeViaMenu(mergeMenu, golem, new ItemStack(Items.DIRT), 16);
        assertEquals(64, golem.containers.get(plainChest).getItem(2).getCount(),
                "并入同物组至最大堆叠（isSameItemSameComponents）");
        assertEquals(2, golem.containers.get(plainChest).getItem(0).getCount(),
                "余量入首个空箱槽（原版 addItemsToContainer 空槽语义）");
        assertEquals(0, totalInInventory(Items.DIRT), "背包货物清空");
    }

    @Test
    void destinationsAreCandidatesCheckedAfterOpening() {
        // 源：铜箱是候选；是否还有货必须开箱后判断，不在搜索阶段读内容
        assertEquals(copperChest, goal.findSource(world), "空铜箱也是源候选");

        // 无本趟货物：无目标
        assertNull(goal.findDestination(world), "无携带物不寻目标");

        // 携货后：异物箱/同物箱/空箱都是候选，开箱后才分类
        goal.setCarryForTest(new ItemStack(Items.DIRT), 16);
        golem.containers.get(plainChest).setItem(0, new ItemStack(Items.STONE, 10));
        assertEquals(plainChest, goal.findDestination(world), "含异物的箱仍是候选");
        golem.containers.get(plainChest).setItem(0, new ItemStack(Items.DIRT, 10));
        assertEquals(plainChest, goal.findDestination(world), "含同物的箱是候选");
        golem.containers.get(plainChest).setItem(0, ItemStack.EMPTY);
        assertEquals(plainChest, goal.findDestination(world), "空箱是候选");
    }

    @Test
    void twoGolemsDoNotShareContainer() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        assertEquals(copperChest, goal.findSource(world));
        // 经真实选择器启动：SEEK_SOURCE → 登记 → TRAVEL_SOURCE
        selector.tick(true);
        WorldPlayer golemB = WorldPlayer.create(world, new Vec3(7.5, 64, 5.5));
        golemB.blocks = golem.blocks;         // 共享世界表
        golemB.containers = golem.containers;
        BrainTestStubs.putField(golemB.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", new BrainTestStubs.RecordingNavigation(golemB));
        TestSortGoal goalB = new TestSortGoal(golemB);
        assertNull(goalB.findSource(world), "铜箱被其他铜傀儡脑占用 → 不可再选");
        PlayerCopperGolemSortGoal.releaseBrain(golem);
        assertEquals(copperChest, goalB.findSource(world), "释放登记后可再选");
    }

    @Test
    void partialMergeRecordsOnlyActualInventoryDelta() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 16));
        for (int i = 0; i < 36; i++) {
            golem.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 60)); // 同物旧库存：只剩 4 空间
        var menu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(copperChest), 3);
        int[] r = PlayerCopperGolemSortGoal.takeViaMenu(menu, golem, new int[]{-1, -1});
        assertEquals(4, r[0], "只记真实并入的 4，不把合并后 64 当成本趟 16");
        assertEquals(12, golem.containers.get(copperChest).getItem(0).getCount(), "未并入的 12 退回源槽");
        assertEquals(64, golem.getInventory().getItem(0).getCount(), "旧库存 60 + 本趟 4");
        assertEquals(0, menu.getCarried().getCount(), "光标无残留");
    }

    @Test
    void placeOnlyTripAmountPreservesPrivateStockInSameSlot() {
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 64)); // 私人 60 + 本趟 4
        golem.containers.get(plainChest).setItem(0, new ItemStack(Items.DIRT, 60));
        for (int i = 1; i < 27; i++) {
            golem.containers.get(plainChest).setItem(i, new ItemStack(Items.STONE, 64));
        }
        var menu = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), golem.containers.get(plainChest), 3);
        int placed = PlayerCopperGolemSortGoal.placeViaMenu(menu, golem,
                new ItemStack(Items.DIRT), 4, 0);
        assertEquals(4, placed, "只投本趟 4");
        assertEquals(60, golem.getInventory().getItem(0).getCount(), "同槽私人 60 原样保留");
        assertEquals(64, golem.containers.get(plainChest).getItem(0).getCount());
        assertEquals(0, menu.getCarried().getCount());
    }

    @Test
    void stopWithCargoResumesSeekDestWithoutCargoResumesSource() throws Exception {
        goal.setCarryForTest(new ItemStack(Items.DIRT), 16);
        selector.tick(true); // 先经选择器启动（有货 → SEEK_DEST）
        selector.removeGoal(goal); // 选择器暂停路径调用 stop
        assertEquals("SEEK_DEST", BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "phase").toString(), "有货暂停恢复寻目的地");
        setGameTime(100); // 推进过 stop 留下的下一次扫描节流
        selector.addGoal(1, goal);
        selector.tick(true);
        assertTrue(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "canUse 允许恢复阶段");

        selector.removeGoal(goal);
        goal.setCarryForTest(ItemStack.EMPTY, 0);
        selector.addGoal(1, goal);
        selector.tick(true); // 无货启动/归一 SEEK_SOURCE
        selector.removeGoal(goal);
        assertEquals("SEEK_SOURCE", BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "phase").toString(), "无货暂停恢复寻来源");
    }

    @Test
    void runningSeekDestHonorsNoTargetCooldown() {
        goal.setCarryForTest(new ItemStack(Items.DIRT), 16);
        golem.blocks.put(plainChest, Blocks.STONE.defaultBlockState()); // 无目标箱
        setGameTime(1000);
        selector.tick(true); // SEEK_DEST 无目标：140t 冷却
        var nav = (BrainTestStubs.RecordingNavigation) golem.getNavigation();
        int before = nav.moveToCalls;

        golem.blocks.put(plainChest, Blocks.CHEST.defaultBlockState());
        setGameTime(1001);
        selector.tick(true);
        assertEquals(before, nav.moveToCalls, "冷却未到，运行中的 SEEK_DEST 不得隔 tick 重扫/起步");
        setGameTime(1140);
        selector.tick(true);
        assertTrue(nav.moveToCalls > before, "140t 后才重新寻目的地");
    }

    @Test
    void menuIdentityUsesRealContainerAndDoubleChestMembership() {
        BlockPos halfA = new BlockPos(10, 64, 10);
        BlockPos halfB = new BlockPos(11, 64, 10);
        SimpleContainer a = new SimpleContainer(27);
        SimpleContainer b = new SimpleContainer(27);
        a.setItem(0, new ItemStack(Items.DIRT, 3));
        b.setItem(0, new ItemStack(Items.STONE, 7));
        golem.containers.put(halfA, a);
        golem.containers.put(halfB, b);
        CompoundContainer compound = new CompoundContainer(a, b);
        ChestMenu doubleMenu = new ChestMenu(MenuType.GENERIC_9x6, 1,
                golem.getInventory(), compound, 6);
        assertTrue(goal.menuIsForTarget(doubleMenu, halfA), "双箱任一半的真实容器身份都匹配");
        assertTrue(goal.menuIsForTarget(doubleMenu, halfB));

        SimpleContainer sameContentOther = new SimpleContainer(27);
        sameContentOther.setItem(0, new ItemStack(Items.DIRT, 3));
        ChestMenu wrong = new ChestMenu(MenuType.GENERIC_9x3, 1,
                golem.getInventory(), sameContentOther, 3);
        assertFalse(goal.menuIsForTarget(wrong, halfA), "容量/首槽相同也不能冒充目标容器");
    }

    @Test
    void doubleChestHalvesCanonicalizeToSingleIdentity() {
        BlockPos left = new BlockPos(2, 64, 2);
        BlockPos right = new BlockPos(3, 64, 2);
        golem.blocks.put(left, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, ChestType.LEFT));
        golem.blocks.put(right, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        assertEquals(goal.canonicalContainerPos(world, left),
                goal.canonicalContainerPos(world, right), "双箱两半归一");
    }

    @Test
    void noDestinationEntersCooldown() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        // 携带物在主手（原版携带位），但场上没有可接收的箱子
        golem.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        golem.getInventory().setSelectedSlot(0);
        golem.blocks.put(plainChest, Blocks.STONE.defaultBlockState()); // 目标箱移除
        selector.tick(true); // SEEK_SOURCE → TRAVEL_SOURCE
        selector.tick(true); // TRAVEL 分支推进导航
        golem.stubBody(new Vec3(4.5, 64, 4.5)); // 到位
        for (int i = 0; i < 61; i++) {
            selector.tick(true); // 驻箱 60t → 取货
        }
        // 取到了货；目标箱没了 → SEEK_DEST 无果 → 运行中冷却
        var nav = (BrainTestStubs.RecordingNavigation) golem.getNavigation();
        int before = nav.moveToCalls;
        selector.tick(true);
        assertEquals(before, nav.moveToCalls, "无匹配目标进入冷却，运行中的 SEEK_DEST 不重扫");
    }
    private void arriveAndOpen(BlockPos pos) {
        // 不匹配箱会先开箱→关箱→进入已访问，之后才轮到下一候选
        for (int i = 0; i < 300; i++) {
            tickTest();
            var phase = BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "phase");
            var openTarget = BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "openMenuTarget");
            if ("INTERACT_DEST".equals(phase.toString()) && pos.equals(openTarget)) {
                return;
            }
        }
        fail("未打开目标箱: " + pos
                + " phase=" + BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "phase")
                + " target=" + BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "target")
                + " cooldown=" + BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "cooldownUntil")
                + " nextScan=" + BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "nextScanTime")
                + " visited=" + BrainTestStubs.readField(goal, PlayerCopperGolemSortGoal.class, "visited")
                + " gameTime=" + world.getGameTime());
    }

    private void runOpenSession(BlockPos pos) {
        long start = (Long) BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "openMenuStartedGameTime");
        for (long t = start; t < start + 60; t++) {
            setTestTime(t + 1);
            selector.tick(true);
            if (t == start + 30) {
                assertTrue(goal.menuOpen, "检查期间菜单必须保持打开");
                assertEquals(pos, BrainTestStubs.readField(goal,
                        PlayerCopperGolemSortGoal.class, "openMenuTarget"), "会话仍属于本目标箱");
            }
        }
    }

    @Test
    void checksMismatchedBoxesByOpeningThenPlacesEmptyBox() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        BlockPos stoneBox = new BlockPos(6, 64, 4);
        BlockPos wheatBox = new BlockPos(8, 64, 4);
        BlockPos emptyBox = new BlockPos(10, 64, 4);
        golem.blocks.put(stoneBox, Blocks.CHEST.defaultBlockState());
        golem.blocks.put(wheatBox, Blocks.CHEST.defaultBlockState());
        golem.blocks.put(emptyBox, Blocks.CHEST.defaultBlockState());
        golem.containers.put(stoneBox, new SimpleContainer(27));
        golem.containers.put(wheatBox, new SimpleContainer(27));
        golem.containers.put(emptyBox, new SimpleContainer(27));
        golem.containers.get(stoneBox).setItem(0, new ItemStack(Items.STONE, 16));
        golem.containers.get(wheatBox).setItem(0, new ItemStack(Items.WHEAT, 16));
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(1000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        assertTrue(goal.menuOpen, "来源箱已打开");
        runOpenSession(copperChest);
        assertFalse(goal.menuOpen, "取货后关闭本脑会话");
        assertEquals(16, totalInInventory(Items.DIRT), "本趟取货 16");

        tickAfterCooldown(); // SEEK_DEST 最近候选 A（石箱）
        golem.stubBody(new Vec3(6.5, 64, 4.5));
        arriveAndOpen(stoneBox);
        runOpenSession(stoneBox);
        assertFalse(goal.menuOpen, "不匹配箱开箱检查后关箱");
        assertEquals(16, golem.containers.get(stoneBox).getItem(0).getCount(), "不匹配箱未被投放");

        tickAfterCooldown(); // B（麦箱）
        golem.stubBody(new Vec3(8.5, 64, 4.5));
        arriveAndOpen(wheatBox);
        runOpenSession(wheatBox);
        assertEquals(16, golem.containers.get(wheatBox).getItem(0).getCount(), "B 未被投放");

        tickAfterCooldown(); // C（空箱）
        golem.stubBody(new Vec3(10.5, 64, 4.5));
        arriveAndOpen(emptyBox);
        runOpenSession(emptyBox);
        assertEquals(16, golem.containers.get(emptyBox).getItem(0).getCount(), "空箱接收本趟泥土");
        assertEquals(0, totalInInventory(Items.DIRT), "本趟无残留");

        assertEquals(List.of(copperChest, stoneBox, wheatBox, emptyBox), goal.closed,
                "逐箱开箱检查，来源会话与每个检查会话都只关一次");
    }

    @Test
    void matchingBoxStopsFurtherChecksAndFullBoxContinues() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        BlockPos emptyBox = new BlockPos(6, 64, 4);
        BlockPos laterBox = new BlockPos(10, 64, 4);
        golem.blocks.put(emptyBox, Blocks.CHEST.defaultBlockState());
        golem.blocks.put(laterBox, Blocks.CHEST.defaultBlockState());
        golem.containers.put(emptyBox, new SimpleContainer(27));
        golem.containers.put(laterBox, new SimpleContainer(27));
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(2000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        runOpenSession(copperChest);
        assertEquals(16, totalInInventory(Items.DIRT));

        tickAfterCooldown(); // SEEK_DEST 最近候选 = 空箱
        golem.stubBody(new Vec3(6.5, 64, 4.5));
        arriveAndOpen(emptyBox);
        runOpenSession(emptyBox);
        assertEquals(16, golem.containers.get(emptyBox).getItem(0).getCount());
        assertEquals(0, totalInInventory(Items.DIRT));
        assertFalse(goal.opened.contains(laterBox), "送完后不检查后续箱");

        // 第二趟：目的箱 27 槽全为同物 64 → 开箱确认无接收空间后继续下一箱
        for (int i = 0; i < 27; i++) {
            golem.containers.get(emptyBox).setItem(i, new ItemStack(Items.DIRT, 64));
        }
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(3000);
        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        runOpenSession(copperChest);
        assertEquals(16, totalInInventory(Items.DIRT));

        tickAfterCooldown(); // SEEK_DEST 仍选最近满箱
        golem.stubBody(new Vec3(6.5, 64, 4.5));
        arriveAndOpen(emptyBox);
        runOpenSession(emptyBox);
        assertEquals(64, golem.containers.get(emptyBox).getItem(0).getCount(), "满箱不投放");
        assertEquals(16, totalInInventory(Items.DIRT), "余货保留在傀儡身上");

        tickAfterCooldown(); // 满箱不开吞货，继续检查下一候选
        golem.stubBody(new Vec3(10.5, 64, 4.5));
        arriveAndOpen(laterBox);
        runOpenSession(laterBox);
        assertEquals(16, golem.containers.get(laterBox).getItem(0).getCount(), "满箱后继续检查下一箱");
        assertEquals(0, totalInInventory(Items.DIRT));
    }

    @Test
    void partialPlaceContinuesWithRemainingCargo() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 16));
        BlockPos partialBox = new BlockPos(6, 64, 4);
        BlockPos emptyBox = new BlockPos(10, 64, 4);
        golem.blocks.put(partialBox, Blocks.CHEST.defaultBlockState());
        golem.blocks.put(emptyBox, Blocks.CHEST.defaultBlockState());
        golem.containers.put(partialBox, new SimpleContainer(27));
        golem.containers.put(emptyBox, new SimpleContainer(27));
        golem.containers.get(partialBox).setItem(0, new ItemStack(Items.DIRT, 60));
        for (int i = 1; i < 27; i++) {
            golem.containers.get(partialBox).setItem(i, new ItemStack(Items.STONE, 64));
        }
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(4000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        runOpenSession(copperChest);
        assertEquals(16, totalInInventory(Items.DIRT));
        tickAfterCooldown(); // SEEK_DEST 找到部分箱
        arriveAndOpen(partialBox);
        runOpenSession(partialBox);
        assertEquals(64, golem.containers.get(partialBox).getItem(0).getCount(), "只并入 4");
        assertEquals(12, totalInInventory(Items.DIRT), "余货归属保留");
        tickAfterCooldown(); // SEEK_DEST 找到空箱
        golem.stubBody(new Vec3(10.5, 64, 4.5));
        arriveAndOpen(emptyBox);
        runOpenSession(emptyBox);
        assertEquals(12, golem.containers.get(emptyBox).getItem(0).getCount(), "余货送入下一箱");
        assertEquals(0, totalInInventory(Items.DIRT));
    }

    @Test
    void interruptionClosesOnlyOwnMenuSession() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(5000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        assertTrue(goal.menuOpen, "来源检查菜单打开");
        golem.blocks.put(copperChest, Blocks.STONE.defaultBlockState());
        tickTest();
        assertFalse(goal.menuOpen, "目标拆除时关闭本脑菜单");
        assertTrue(goal.closed.contains(copperChest), "关闭的是本目标会话");

        // 推远：重新成为候选并打开后，距离门关闭本脑会话
        golem.blocks.put(copperChest, copperChestState());
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        tickAfterCooldown();
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        assertTrue(goal.menuOpen);
        goal.withinRange = false;
        tickTest();
        assertFalse(goal.menuOpen, "离开交互距离时关闭本脑菜单");
        goal.withinRange = true;

        // 距离中断后铜箱仍在已访问集：先经一轮无候选 140t 冷却清空，再重开验证 brain off
        tickAfterCooldown();
        tickAfterCooldown();
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        assertTrue(goal.menuOpen);
        selector.removeGoal(goal);
        assertFalse(goal.menuOpen, "brain off 路径关闭本脑菜单");
    }

    /**
     * 满快捷栏全流程：开箱不换手（空手栈），取到的货落到背包槽并记对位置，
     * 放货按记录精确转移 16 件，私人库存（9 格圆石）原样保留。
     */
    @Test
    void fullHotbarTripKeepsPrivateStockAndPlacesExactCargo() {
        var inv = golem.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(8000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        tickTest(); // 第1刻开箱
        assertTrue(goal.menuOpen, "满快捷栏也能开箱");
        runOpenSession(copperChest);
        assertEquals(16, totalInInventory(Items.DIRT), "取到 16");
        int cargoSlot = goal.carrySlotForTest();
        assertTrue(cargoSlot >= 0 && cargoSlot < 9, "货物被选中到手（快捷栏槽），记录=" + cargoSlot);
        assertEquals(16, inv.getItem(cargoSlot).getCount(), "记录槽即货物实体所在槽");

        tickAfterCooldown(); // SEEK_DEST
        golem.stubBody(new Vec3(6.5, 64, 4.5));
        arriveAndOpen(plainChest);
        runOpenSession(plainChest);
        assertEquals(16, golem.containers.get(plainChest).getItem(0).getCount(), "按记录槽精确放 16");
        assertEquals(0, totalInInventory(Items.DIRT), "本趟货清空");
        assertEquals(9 * 64, totalInInventory(Items.COBBLESTONE), "私人圆石总量不变（只允许槽位交换）");
    }

    /** 开箱失败 3 次后必须退避换箱：不永久停在 INTERACT，且拉黑该位置 */
    @Test
    void repeatedOpenFailureBacksOffToNextCandidate() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        golem.blocks.put(plainChest, Blocks.STONE.defaultBlockState()); // 唯一目标打不开
        goal.openFailLimit = 99; // 模拟封顶/遮挡：一直开不出来
        golem.stubBody(new Vec3(4.5, 64, 4.5));
        setTestTime(6000);

        tickTest(); // SEEK_SOURCE
        tickTest(); // TRAVEL -> INTERACT
        for (int i = 0; i < 80; i++) {
            tickTest();
        }
        assertEquals("SEEK_SOURCE", BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "phase").toString(), "持续开箱失败后退避回到寻点");
        assertTrue(((Map<?, ?>) BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "blacklist")).containsKey(copperChest), "失败目标进入退避名单");
        assertEquals(0, goal.opened.size(), "从未成功打开");

        // 重试次数受限：不是每 tick 都撞同一箱子
        assertTrue(goal.openFailCount <= 4, "有限重试（实测 " + goal.openFailCount + " 次）");
    }

    /** 寻路失败有限重试：3 次内成功则继续前往，不拉黑 */
    @Test
    void moveFailureRetriesBeforeBlacklisting() {
        golem.containers.get(copperChest).setItem(0, new ItemStack(Items.DIRT, 64));
        var nav = (BrainTestStubs.RecordingNavigation) golem.getNavigation();
        nav.failNextMoves = 2;
        golem.stubBody(new Vec3(4.5, 64, 12.5)); // 远于到达距离
        setTestTime(7000);

        tickTest(); // SEEK_SOURCE → TRAVEL_SOURCE（beginTravel 的 moveTo 失败 1）
        tickTest(); // 重试（失败 2）
        setTestTime(7020);
        tickTest(); // 第 3 次成功
        assertEquals("TRAVEL_SOURCE", BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "phase").toString(), "重试成功后继续前往");
        assertFalse(((Map<?, ?>) BrainTestStubs.readField(goal,
                PlayerCopperGolemSortGoal.class, "blacklist")).containsKey(copperChest), "未拉黑");
    }

}
