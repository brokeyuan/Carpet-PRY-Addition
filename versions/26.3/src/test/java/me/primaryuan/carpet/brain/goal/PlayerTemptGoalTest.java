package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TemptGoal 调度与续行锁（26.3/1.21.11 字节码对齐）：
 * calmDown = reducedTickDelay(100) = 50 次评估 ≈ 100 游戏刻（此前实现按游戏 tick
 * 自减、被选择器间隔放大到 ~200 tick 才重扫——回归防线）；canContinueToUse 末尾
 * 复跑 canUse 重扫最近持食物玩家：旧引诱者走远/收起食物/死亡即结束或切换；
 * 停走距离 2.5；canScare=false（猪）不受玩家动作吓跑。候选经 nearbyTemptCandidates
 * 缝注入，冷却重扫经 PlayerGoalSelector.tick 奇偶门驱动。
 */
class PlayerTemptGoalTest {

    /** 可注入候选的测试 Goal（选择/过滤逻辑本体保持真实执行） */
    static class TestTemptGoal extends PlayerTemptGoal {
        List<net.minecraft.world.entity.player.Player> candidates = List.of();
        int scanCalls;

        TestTemptGoal(BrainTestStubs.FakePlayer mob) {
            super(mob, ItemTags.PIG_FOOD, 1.2);
        }

        TestTemptGoal(BrainTestStubs.FakePlayer mob, net.minecraft.world.item.Item singleItem) {
            super(mob, null, singleItem, 1.2, false);
        }

        @Override
        protected net.minecraft.world.entity.player.Player findTempter() {
            this.scanCalls++;
            return super.findTempter();
        }

        @Override
        protected java.util.List<net.minecraft.world.entity.player.Player> nearbyTemptCandidates() {
            return this.candidates;
        }
    }

    private static BrainTestStubs.FakePlayer pig;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        // 纯 JVM 未装载数据包标签：给测试涉及的物品 Holder 挂上对应食物标签，
        // 使 stack.is(TagKey) 与服务端同语义
        BrainTestStubs.bindTag(net.minecraft.world.item.Items.CARROT.builtInRegistryHolder(), ItemTags.PIG_FOOD);
        BrainTestStubs.bindTag(net.minecraft.world.item.Items.POTATO.builtInRegistryHolder(), ItemTags.PIG_FOOD);
        BrainTestStubs.bindTag(net.minecraft.world.item.Items.BEETROOT.builtInRegistryHolder(), ItemTags.PIG_FOOD);
        pig = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
    }

    private static BrainTestStubs.FakePlayer tempterAt(double x, double z, ItemStack main) {
        BrainTestStubs.FakePlayer p = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(pig.level())
                .stubBody(new net.minecraft.world.phys.Vec3(x, 64, z));
        p.stubHeldItems(main, null);
        return p;
    }

    /** 耗尽防抖冷却（canUse 每 2 tick 自减一次，此处直呼等效 50 次评估） */
    private static void drainCooldown(PlayerTemptGoal goal) {
        for (int i = 0; i < 60; i++) {
            goal.canUse();
        }
    }

    @Test
    void calmDownIs50EvaluationsNot100Ticks() {
        TestTemptGoal goal = new TestTemptGoal(pig);
        goal.stop();
        assertEquals(50, readCalmDown(goal), "stop() 置 reducedTickDelay(100)=50 次评估");

        // 无候选场景：canUse 前 50 次走冷却自减分支（未达扫描），第 51 次进入扫描
        goal.candidates = List.of();
        int calls = 0;
        while (goal.scanCalls == 0 && calls < 200) {
            goal.canUse();
            calls++;
        }
        assertEquals(51, calls, "50 次评估自减冷却 + 第 51 次进入扫描分支");
        assertEquals(0, readCalmDown(goal), "50 次评估后 calmDown 归零");
    }

    @Test
    void selectorDrivenRescanAt100GameTicks() {
        PlayerGoalSelector selector = new PlayerGoalSelector();
        TestTemptGoal goal = new TestTemptGoal(pig);
        goal.candidates = List.of();
        selector.addGoal(4, goal);
        goal.stop(); // calmDown = 50 次评估

        int firstScanTick = -1;
        for (int tick = 0; tick < 200; tick++) {
            selector.tick(tick % 2 == 0); // 奇偶门（偶相完整评估）
            if (firstScanTick < 0 && goal.scanCalls > 0) {
                firstScanTick = tick; // 扫描分支 = 冷却耗尽（挂载首轮仍在冷却自减）
            }
        }
        assertEquals(100, firstScanTick,
                "评估只发生在偶相：50 次评估 × 2 tick = 恰第 100 tick 重扫（旧实现 ~200 tick）");
    }

    @Test
    void rescanPicksNearestHolderAndSwitches() {
        TestTemptGoal goal = new TestTemptGoal(pig);
        ItemStack carrot = new ItemStack(Items.CARROT);
        BrainTestStubs.FakePlayer far = tempterAt(8, 0, carrot);       // ≈7.5 格
        BrainTestStubs.FakePlayer near = tempterAt(3, 0, carrot);      // ≈2.5 格更近
        goal.candidates = List.of(far, near);

        assertTrue(goal.canUse());
        assertEquals(near, currentTempter(goal), "选最近的持食物玩家");

        // 近者收起食物：canContinueToUse 复跑 canUse 重扫 → 切换到远者
        near.stubHeldItems(null, null);
        assertTrue(goal.canContinueToUse());
        assertEquals(far, currentTempter(goal), "旧目标收物后重扫切换到最近持物者");
    }

    @Test
    void tempterOutOfRangeEndsRun() {
        TestTemptGoal goal = new TestTemptGoal(pig);
        BrainTestStubs.FakePlayer close = tempterAt(5, 0, new ItemStack(Items.POTATO));
        goal.candidates = List.of(close);
        assertTrue(goal.canUse());
        assertTrue(goal.canContinueToUse());

        // 走出 10 格范围（TEMPT_RANGE）且世界再无候选：重扫 → 结束
        close.stubBody(new net.minecraft.world.phys.Vec3(30.5, 64, 0.5));
        goal.candidates = List.of();
        assertFalse(goal.canContinueToUse(), "引诱者走远后引诱结束");
        goal.stop();
        assertNull(currentTempter(goal), "stop 清引诱者并进防抖冷却");
        assertEquals(50, readCalmDown(goal));
    }

    @Test
    void deadTempterIsNotEligible() {
        TestTemptGoal goal = new TestTemptGoal(pig);
        BrainTestStubs.FakePlayer dead = tempterAt(4, 0, new ItemStack(Items.BEETROOT));
        dead.alive = false;
        goal.candidates = List.of(dead);
        assertFalse(goal.canUse(), "死亡候选不入选（isAlive 过滤真实执行）");
    }

    @Test
    void pigCanScareIsFalse() {
        // 猪装配 canScare=false：玩家在 6 格内跑动也不中止（原版猪口径）
        TestTemptGoal goal = new TestTemptGoal(pig);
        BrainTestStubs.FakePlayer mover = tempterAt(2, 0, new ItemStack(Items.CARROT));
        mover.stubBody(new net.minecraft.world.phys.Vec3(2.0, 64, 1.5)); // 与锚点不同 → 玩家在动
        goal.candidates = List.of(mover);
        assertTrue(goal.canUse());
        assertTrue(goal.canContinueToUse(), "canScare=false：玩家动作不中止");
    }

    @Test
    void carrotOnAStickSingleItemPredicate() {
        // 猪的第二条引诱：只认胡萝卜钓竿（单件谓词），普通胡萝卜不触发
        TestTemptGoal stickGoal = new TestTemptGoal(pig, Items.CARROT_ON_A_STICK);
        BrainTestStubs.FakePlayer plainCarrot = tempterAt(5, 0, new ItemStack(Items.CARROT));
        stickGoal.candidates = List.of(plainCarrot);
        assertFalse(stickGoal.canUse(), "普通胡萝卜不触发钓竿引诱");
        drainCooldown(stickGoal);

        BrainTestStubs.FakePlayer rod = tempterAt(5, 0, new ItemStack(Items.CARROT_ON_A_STICK));
        stickGoal.candidates = List.of(rod);
        assertTrue(stickGoal.canUse(), "胡萝卜钓竿触发引诱");
        assertEquals(rod, currentTempter(stickGoal));
    }

    @Test
    void flagsMoveAndLook() {
        TestTemptGoal goal = new TestTemptGoal(pig);
        assertEquals(java.util.EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK),
                goal.getFlags(), "原版 TemptGoal 旗标 MOVE+LOOK");
    }

    private static int readCalmDown(TestTemptGoal goal) {
        return (Integer) BrainTestStubs.readField(goal, PlayerTemptGoal.class, "calmDown");
    }

    private static Object currentTempter(TestTemptGoal goal) {
        return BrainTestStubs.readField(goal, PlayerTemptGoal.class, "tempter");
    }
}
