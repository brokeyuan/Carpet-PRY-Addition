package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BegGoal 行为锁（26.3/1.21.11 字节码对齐）：
 * playerHoldingInteresting = stack.is(Items.BONE) || wolf.isFood(stack)（两版均无
 * 驯服门——骨头与狼食物都算）；8 格内最近候选；lookTime = adjustedTickDelay
 * (40+next(40))（非 rUE 每 2 tick 递减 1 次 → 时长 40~80 游戏刻）；候选收物/
 * 出范围即结束；纯 LOOK 旗标。变异验证：移除 isInteresting 的骨头分支 → 骨头用例失败。
 */
class PlayerBegGoalTest {

    static class TestBegGoal extends PlayerBegGoal {
        List<net.minecraft.world.entity.player.Player> candidates = List.of();

        TestBegGoal(BrainTestStubs.FakePlayer mob) {
            super(mob);
        }

        @Override
        protected java.util.List<net.minecraft.world.entity.player.Player> nearbyBegCandidates() {
            return this.candidates;
        }
    }

    private static BrainTestStubs.FakePlayer wolf;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        // 纯 JVM 未装载数据包标签：牛肉挂 #wolf_food（服务端装载后同语义）
        BrainTestStubs.bindTag(net.minecraft.world.item.Items.BEEF.builtInRegistryHolder(), ItemTags.WOLF_FOOD);
        wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
    }

    private static BrainTestStubs.FakePlayer holderAt(double x, double z, ItemStack main, ItemStack off) {
        BrainTestStubs.FakePlayer p = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(wolf.level())
                .stubBody(new net.minecraft.world.phys.Vec3(x, 64, z));
        p.stubHeldItems(main, off);
        return p;
    }

    @Test
    void boneMakesWolfBeg() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer bone = holderAt(4, 0, new ItemStack(Items.BONE), null);
        goal.candidates = List.of(bone);
        assertTrue(goal.canUse(), "持骨头的玩家触发乞食（1.21.11/26.3 无驯服门）");
    }

    @Test
    void wolfFoodMakesWolfBeg() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer meat = holderAt(4, 0, null, new ItemStack(Items.BEEF));
        goal.candidates = List.of(meat);
        assertTrue(goal.canUse(), "副手持狼食物（#wolf_food 含牛肉）触发乞食");
    }

    @Test
    void nonFoodDoesNotTrigger() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer stone = holderAt(4, 0, new ItemStack(Items.STONE), null);
        goal.candidates = List.of(stone);
        assertFalse(goal.canUse(), "非骨头/狼食物不触发");
    }

    @Test
    void nearestHolderWins() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer far = holderAt(7, 0, new ItemStack(Items.BONE), null);
        BrainTestStubs.FakePlayer near = holderAt(2, 0, new ItemStack(Items.BEEF), null);
        goal.candidates = List.of(far, near);
        assertTrue(goal.canUse());
        assertEquals(near, currentBegger(goal), "最近的持物玩家入选");
    }

    @Test
    void lookTimeLifecycle() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer holder = holderAt(3, 0, new ItemStack(Items.BONE), null);
        goal.candidates = List.of(holder);
        assertTrue(goal.canUse());
        goal.start();

        int lookTime = readLookTime(goal);
        assertTrue(lookTime >= 20 && lookTime <= 40,
                "lookTime = adjustedTickDelay(40+next(40)) ∈ [20,40] 次推进（实际 " + lookTime + "）");
        assertTrue(goal.canContinueToUse(), "未耗尽且持物 → 续行");

        // 每 tick() 递减 1 次（非 rUE，每 2 游戏刻 1 次）：耗尽后结束
        for (int i = 0; i < lookTime; i++) {
            goal.tick();
        }
        assertFalse(goal.canContinueToUse(), "注视时长耗尽 → 结束");
    }

    @Test
    void endsWhenFoodPutAwayOrOutOfRange() {
        TestBegGoal goal = new TestBegGoal(wolf);
        BrainTestStubs.FakePlayer holder = holderAt(3, 0, new ItemStack(Items.BONE), null);
        goal.candidates = List.of(holder);
        assertTrue(goal.canUse());
        goal.start();

        holder.stubHeldItems(null, null);
        assertFalse(goal.canContinueToUse(), "收起食物 → 结束");

        holder.stubHeldItems(new ItemStack(Items.BONE), null);
        holder.stubBody(new net.minecraft.world.phys.Vec3(20.5, 64, 0.5));
        assertFalse(goal.canContinueToUse(), "超出 8 格 → 结束（原版 distanceToSqr > lookDistance²）");
    }

    @Test
    void flagsLookOnly() {
        TestBegGoal goal = new TestBegGoal(wolf);
        assertEquals(java.util.EnumSet.of(PlayerGoal.Flag.LOOK), goal.getFlags(),
                "纯 LOOK 旗标——与移动类 Goal 并行，被战斗/跟随抢占");
    }

    private static Object currentBegger(TestBegGoal goal) {
        return BrainTestStubs.readField(goal, PlayerBegGoal.class, "player");
    }

    private static int readLookTime(TestBegGoal goal) {
        return (Integer) BrainTestStubs.readField(goal, PlayerBegGoal.class, "lookTime");
    }
}
