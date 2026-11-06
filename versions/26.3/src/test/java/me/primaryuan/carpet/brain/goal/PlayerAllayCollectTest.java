package me.primaryuan.carpet.brain.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 悦灵收集目标（七期 P3）：副手样品匹配（isSameItem + 药水内容守卫）、
 * 真实拾取、样品不交付、交付对象失效持有等待。世界输入由测试提供。
 */
class PlayerAllayCollectTest {

    static class AllayPlayer extends BrainTestStubs.FakePlayer {
        List<ItemEntity> worldItems;
        int dropCalls;

        /** 桩掉落物：getItem 走数据同步器（26.3），子类直写绕开 */
        static class StubItemEntity extends ItemEntity {
            ItemStack stack = ItemStack.EMPTY;

            StubItemEntity() {
                super(null, null);
            }

            @Override
            public ItemStack getItem() {
                return this.stack;
            }

            @Override
            public boolean isAlive() {
                return true;
            }
        }

        static StubItemEntity drop(Vec3 pos, ItemStack stack) {
            StubItemEntity e = BrainTestStubs.allocate(StubItemEntity.class);
            UUID id = UUID.randomUUID();
            BrainTestStubs.putField(e, Entity.class, "uuid", id);
            BrainTestStubs.putField(e, Entity.class, "stringUUID", id.toString());
            BrainTestStubs.putField(e, Entity.class, "position", pos);
            e.stack = stack;
            return e;
        }

        static AllayPlayer create(ServerLevel world, Vec3 pos) {
            AllayPlayer p = BrainTestStubs.FakePlayer.createFakeOf(AllayPlayer.class, UUID.randomUUID());
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            BrainTestStubs.putField(p, net.minecraft.world.entity.LivingEntity.class, "equipment",
                    new net.minecraft.world.entity.EntityEquipment());
            p.worldItems = List.of(); // Unsafe 分配跳过字段初始化器
            p.dropCalls = 0;
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "bb",
                    new net.minecraft.world.phys.AABB(pos, pos.add(0.6, 1.8, 0.6)));
            return p;
        }


        @Override
        public boolean pryUseItemOnBlock(BlockPos pos, net.minecraft.world.InteractionHand hand) {
            this.useItemCalls.add(pos);
            return true;
        }
    }

    static class TestAllayGoal extends PlayerAllayCollectGoal {
        final AllayPlayer ap;
        boolean deliverAtSelf;

        TestAllayGoal(AllayPlayer mob, UUID owner) {
            super(mob, owner);
            this.ap = mob;
        }

        @Override
        protected java.util.List<ItemEntity> collectItemEntities(
                net.minecraft.world.level.Level level, net.minecraft.world.phys.AABB box) {
            return this.ap.worldItems; // 26.3 ItemEntity 走数据同步器：测试注入掉落物表
        }

        @Override
        protected BlockStateLike readState(net.minecraft.world.level.Level level, BlockPos pos) {
            return new BlockStateLike(net.minecraft.world.level.block.Blocks.NOTE_BLOCK.defaultBlockState());
        }

        @Override
        protected Vec3 deliveryPoint(net.minecraft.world.level.Level level) {
            return this.deliverAtSelf ? this.mob.position() : super.deliveryPoint(level);
        }

        Vec3 lastDropTarget;

        @Override
        protected void dropToWorld(ItemStack stack, Vec3 targetPos) {
            this.ap.dropCalls++;
            this.lastDropTarget = targetPos;
        }
    }

    private ServerLevel world;
    private AllayPlayer allay;
    private TestAllayGoal goal;
    private PlayerGoalSelector selector;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        world = (ServerLevel) BrainTestStubs.stubWorld();
        allay = AllayPlayer.create(world, new Vec3(5.5, 64, 5.5));
        goal = new TestAllayGoal(allay, allay.getUUID());
        selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
    }

    @Test
    void matchesByItemNotNbt() {
        ItemStack sample = new ItemStack(Items.DIRT, 1);
        assertTrue(PlayerAllayCollectGoal.matchesSample(new ItemStack(Items.DIRT, 3), sample),
                "同物不同数量匹配（isSameItem）");
        assertFalse(PlayerAllayCollectGoal.matchesSample(new ItemStack(Items.STONE), sample),
                "不同物不匹配");
        // 药水：内容不一致不匹配（原版 hasNonMatchingPotion 守卫）；内容一致匹配
        ItemStack samplePotion = new ItemStack(Items.POTION);
        samplePotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(Potions.HEALING));
        ItemStack samePotion = new ItemStack(Items.POTION);
        samePotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(Potions.HEALING));
        assertTrue(PlayerAllayCollectGoal.matchesSample(samePotion, samplePotion),
                "同内容药水匹配");
        ItemStack otherPotion = new ItemStack(Items.POTION);
        otherPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(Potions.HARMING));
        assertFalse(PlayerAllayCollectGoal.matchesSample(otherPotion, samplePotion),
                "异内容药水不匹配");
    }

    @Test
    void noSampleMeansWait() {
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "副手无样品：明确等待");
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT)); // 副手样品
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "场上无匹配掉落物也无存货：等待");
    }

    @Test
    void findsWantedItemBySample() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT));
        allay.worldItems = List.of(
                AllayPlayer.drop(new Vec3(7.5, 64, 5.5), new ItemStack(Items.STONE, 1)),
                AllayPlayer.drop(new Vec3(9.5, 64, 5.5), new ItemStack(Items.DIRT, 5)));
        var nav = new BrainTestStubs.RecordingNavigation(allay);
        BrainTestStubs.putField(allay.fields(), me.primaryuan.carpet.brain.MobFields.class, "navigation", nav);
        selector.tick(true); // 真实选择器启动并推进
        assertTrue(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "有匹配掉落物：可用");
        assertEquals(9.5, nav.lastX, 0.01, "导航目标是匹配掉落物（非异物的 7.5）");
    }

    @Test
    void deliversInventoryOnlyAndKeepsSample() {
        allay.stubHeldItems(new ItemStack(Items.STONE, 1), new ItemStack(Items.DIRT));
        // 背包：2 组匹配物（交付最大组），样品在副手不参与
        allay.getInventory().setItem(9, new ItemStack(Items.DIRT, 3));
        allay.getInventory().setItem(10, new ItemStack(Items.DIRT, 8));
        allay.getInventory().setItem(11, new ItemStack(Items.STONE, 4)); // 非样品
        var nav = new BrainTestStubs.RecordingNavigation(allay);
        BrainTestStubs.putField(allay.fields(), me.primaryuan.carpet.brain.MobFields.class, "navigation", nav);
        goal.deliverAtSelf = true;
        selector.tick(true); // 真实选择器启动并交付
        assertEquals(1, allay.dropCalls, "交付一次");
        assertEquals(3, count(Items.DIRT), "交付最大匹配组 8（背包剩 3）");
        assertEquals(1, allay.getOffhandItem().getCount(), "副手样品不被消耗");
        assertEquals(4, count(Items.STONE), "非样品物品不被交付");
    }

    @Test
    void offhandExcessIsDeliverableButSampleStays() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 13)); // 26.3 拾取落入副手
        PlayerAllayCollectGoal.Deliverable d = goal.findDeliverableForTest();
        assertEquals(12, d.count(), "副手 13 = 1 样品 + 12 可交付");
        goal.dropForTest(d);
        assertEquals(1, allay.getOffhandItem().getCount(), "样品恒保留 1");
    }

    @Test
    void runningGoalHonorsDeliveryCooldownForLaterGroups() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1)); // 样品
        goal.deliverAtSelf = true;
        allay.getInventory().setItem(9, new ItemStack(Items.DIRT, 8));
        allay.getInventory().setItem(10, new ItemStack(Items.DIRT, 3));
        selector.tick(true); // 第一组真实交付（≤16、样品保留）
        assertEquals(1, allay.dropCalls, "第一组交付一次");
        assertEquals(3, count(Items.DIRT), "8 已交付，第二组 3 仍在（副手样品单列）");
        assertEquals(1, allay.getOffhandItem().getCount(), "样品未被消耗");
        selector.tick(true); // 第二组：240t 冷却内不得再交付
        assertEquals(1, allay.dropCalls, "运行中的多组货物也受交付冷却约束");
        assertEquals(3, count(Items.DIRT));
    }

    @Test
    void defaultOwnerDeliveryPointUsesExecutorWhenNoNoteBlock() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1)); // 副手样品
        UUID ownerId = UUID.randomUUID();
        BrainTestStubs.FakePlayer owner = BrainTestStubs.FakePlayer.createFake(ownerId)
                .stubWorld(world)
                .stubBody(new Vec3(10.5, 64, 10.5));
        world.getServer().getPlayerList().getPlayersByUUID().put(ownerId, owner);

        TestAllayGoal ownerGoal = new TestAllayGoal(allay, ownerId);
        Vec3 point = ownerGoal.deliveryPoint(world);

        assertNotNull(point, "无音符盒时回退到执行命令的玩家");
        assertEquals(10.5, point.x, 1.0E-9);
        assertEquals(64.0, point.y, 1.0E-9);
        assertEquals(10.5, point.z, 1.0E-9);
        assertEquals(1, allay.getOffhandItem().getCount(), "副手样品保留");
    }

    @Test
    void noteDeliveryPointIsAboveTheBlockNotInsideIt() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1));
        BlockPos note = new BlockPos(3, 64, 3);
        goal.noteHeard(note, world.dimension(), world.getGameTime() + 600);
        Vec3 point = goal.deliveryPoint(world);
        assertEquals(note.getY() + 1.0, point.y, 1.0E-9,
                "交付点必须在音符盒上方，不能落在方块内部被吞掉");
        assertEquals(note.getX() + 0.5, point.x, 1.0E-9);
    }

    @Test
    void deliveryDropsAtNotePointNotAtAllayFeet() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1)); // 样品 1
        allay.getInventory().setItem(9, new ItemStack(Items.DIRT, 2)); // 可交付 2
        BlockPos note = new BlockPos(5, 64, 5);
        goal.noteHeard(note, world.dimension(), world.getGameTime() + 600);
        allay.stubBody(new Vec3(5.5, 64, 5.5));
        selector.tick(true);
        assertEquals(1, allay.dropCalls);
        assertNotNull(goal.lastDropTarget);
        assertEquals(5.5, goal.lastDropTarget.x, 1.0E-9);
        assertEquals(65.0, goal.lastDropTarget.y, 1.0E-9);
        assertEquals(5.5, goal.lastDropTarget.z, 1.0E-9);
    }

    @Test
    void likedNoteBlockCarriesDimensionAndExpires() {
        long now = world.getGameTime();
        goal.noteHeard(new BlockPos(3, 64, 3), world.dimension(), now + 600);
        assertEquals(new BlockPos(3, 64, 3), goal.likedNoteBlockForTest());
        assertEquals(world.dimension(), goal.likedNoteBlockDimensionForTest());
        // 跨维（同坐标不同维度）立即失效
        goal.noteHeard(new BlockPos(3, 64, 3),
                net.minecraft.world.level.Level.NETHER, now + 600);
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1));
        allay.worldItems = List.of(AllayPlayer.drop(new Vec3(6.5, 64, 5.5), new ItemStack(Items.DIRT, 1)));
        selector.tick(true); // 真实选择器推进 tick，维度校验清除
        assertEquals(null, goal.likedNoteBlockForTest(), "维度不符的喜好点被清除");
    }

    @Test
    void jukeboxIsNotANoteBlock() {
        assertFalse(new PlayerAllayCollectGoal.BlockStateLike(
                net.minecraft.world.level.block.Blocks.JUKEBOX.defaultBlockState()).isNoteBlock(),
                "唱片机不是音符盒");
        assertTrue(new PlayerAllayCollectGoal.BlockStateLike(
                net.minecraft.world.level.block.Blocks.NOTE_BLOCK.defaultBlockState()).isNoteBlock());
    }

    @Test
    void singleOffhandSampleIsNeverDeliverable() {
        allay.stubHeldItems(ItemStack.EMPTY, new ItemStack(Items.DIRT, 1));
        assertEquals(null, goal.findDeliverableForTest(), "副手 1 个是样品，不是交付物");
    }

    private int count(net.minecraft.world.item.Item item) {
        int n = 0;
        var inv = allay.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }
}
