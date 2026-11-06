package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F08：狐狸进食按玩家实际饥饿/可进食条件判断，只认 FOOD 组件；
 * 完成/失败即让出 MOVE，背包仍有食物也不能永久续行。
 */
class PlayerFoxEatFoodTest {

    static class FoxPlayer extends BrainTestStubs.FakePlayer {
        static FoxPlayer create(ServerLevel world, Vec3 pos) {
            FoxPlayer p = BrainTestStubs.FakePlayer.createFakeOf(FoxPlayer.class, UUID.randomUUID());
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            // 走真实 Player.canEat：能力 + FoodData 字段（Unsafe 跳过初始化器）
            BrainTestStubs.putField(p, Player.class, "abilities", new Abilities());
            BrainTestStubs.putField(p, Player.class, "foodData", new FoodData());
            p.getFoodData().setFoodLevel(20);
            return p;
        }
    }

    static class TestFoxGoal extends PlayerFoxEatFoodGoal {
        final FoxPlayer fox;
        boolean using;

        TestFoxGoal(FoxPlayer mob) {
            super(mob);
            this.fox = mob;
        }

        @Override
        protected boolean selfUsingItem() {
            return this.using;
        }

        @Override
        protected void beginUsingItem() {
            this.using = true;
        }

        @Override
        protected void stopUsingItem() {
            this.using = false; // 桩不写 entityData
        }
    }

    private FoxPlayer fox;
    private TestFoxGoal goal;
    private PlayerGoalSelector selector;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        ServerLevel world = (ServerLevel) BrainTestStubs.stubWorld();
        fox = FoxPlayer.create(world, new Vec3(0.5, 64, 0.5));
        fox.getInventory().setItem(9, new ItemStack(Items.BREAD, 2));
        goal = new TestFoxGoal(fox);
        selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
    }

    @Test
    void fullHungerMeansNoEatingEvenAtLowHealth() {
        fox.getFoodData().setFoodLevel(20);
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "饥饿满时生命未满也不能吃（真实 Player.canEat 门）");
    }

    @Test
    void potionIsNotFood() {
        fox.getInventory().setItem(9, ItemStack.EMPTY);
        fox.getInventory().setItem(10, new ItemStack(Items.POTION));
        fox.getFoodData().setFoodLevel(5);
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "药水只有 CONSUMABLE，不是 FOOD，不能当普通食物");
    }

    @Test
    void eatingEndsAndReleasesMoveEvenWithMoreFood() {
        fox.getFoodData().setFoodLevel(5);
        selector.tick(true);
        assertTrue(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE));
        assertEquals(Items.BREAD, fox.getMainHandItem().getItem(), "选中真实食物到手");

        // 模拟原版 32t 吃完：使用结束、饥饿已恢复，背包仍有第二块面包
        goal.using = false;
        fox.getFoodData().setFoodLevel(20);
        selector.tick(true); // 选择器清理 canContinueToUse=false → stop，且 canUse 不再重启
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "吃完立即结束，背包还有食物不能永久续行");
        int bread = 0;
        for (int i = 0; i < fox.getInventory().getContainerSize(); i++) {
            if (fox.getInventory().getItem(i).is(Items.BREAD)) {
                bread += fox.getInventory().getItem(i).getCount();
            }
        }
        assertEquals(2, bread, "剩余食物仍在背包（换槽后位置可变）");
    }

    @Test
    void useFailureAlsoEndsRound() {
        fox.getFoodData().setFoodLevel(5);
        selector.tick(true);
        assertTrue(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE));
        goal.using = false;
        fox.getFoodData().setFoodLevel(20);
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "使用失败/被打断同样让出 MOVE");
    }
}
