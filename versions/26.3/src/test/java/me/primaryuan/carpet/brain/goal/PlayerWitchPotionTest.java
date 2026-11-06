package me.primaryuan.carpet.brain.goal;

import java.util.List;
import java.util.UUID;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 女巫药水决策（七期 P5）：决策树顺序、真实库存消耗、缺药不假装。
 * 决策面直接驱动生产方法；投掷由实机验证弹道。
 */
class PlayerWitchPotionTest {

    static class WitchPlayer extends BrainTestStubs.FakePlayer {
        LivingEntity target;
        boolean targetEffectless = true;
        float selfHealthValue = 20.0F;

        static WitchPlayer create(ServerLevel world, Vec3 pos) {
            WitchPlayer p = BrainTestStubs.FakePlayer.createFakeOf(WitchPlayer.class, UUID.randomUUID());
            p.selfHealthValue = 20.0F; // Unsafe 分配跳过字段初始化器
            p.stubWorld(world);
            p.stubBody(pos);
            p.stubRealInventory();
            return p;
        }

        @Override
        public net.minecraft.world.entity.LivingEntity getTarget() {
            return this.target;
        }

        void stubHealth(float health) {
            this.selfHealthValue = health; // 26.3 生命走数据同步器：经 goal 缝注入
        }

        @Override
        public void setTarget(net.minecraft.world.entity.LivingEntity target) {
            this.target = target;
        }

        @Override
        public boolean pryUseItemInHand(net.minecraft.world.InteractionHand hand) {
            this.swingCalls++;
            ItemStack stack = this.getMainHandItem();
            if (!stack.isEmpty()) {
                stack.shrink(1); // 原生投掷的真实消耗（桩只扣主手栈）
            }
            return true;
        }
    }

    private ServerLevel world;
    private WitchPlayer witch;
    private PlayerWitchThrowGoal throwGoal;

    private static ItemStack potionOf(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    private static ItemStack drinkOf(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }

    private me.primaryuan.carpet.brain.BrainTestStubs.FakePlayer target(double dist) {
        var t = me.primaryuan.carpet.brain.BrainTestStubs.FakePlayer.createFake(UUID.randomUUID());
        t.stubWorld(world);
        t.stubBody(new Vec3(5.5 + dist, 64, 5.5));
        return t;
    }

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        world = (ServerLevel) BrainTestStubs.stubWorld();
        witch = WitchPlayer.create(world, new Vec3(5.5, 64, 5.5));
        BrainTestStubs.putField(witch.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", new BrainTestStubs.RecordingNavigation(witch));
        throwGoal = new TestThrowGoal(witch);
    }

    static class TestThrowGoal extends PlayerWitchThrowGoal {
        final WitchPlayer wp;

        TestThrowGoal(WitchPlayer mob) {
            super(mob);
            this.wp = mob;
        }

        @Override
        protected boolean targetHasEffect(LivingEntity target, Holder<net.minecraft.world.effect.MobEffect> effect) {
            return !this.wp.targetEffectless; // 26.3 走数据同步器：测试注入药效状态
        }

        @Override
        protected float selfHealth() {
            return this.wp.selfHealthValue;
        }

        float targetHealthValue = 20.0F;

        @Override
        protected float targetHealth(LivingEntity target) {
            return this.targetHealthValue; // 桩目标血量固定（数据同步器不可达）
        }

        @Override
        protected boolean hasLineOfSight(LivingEntity target) {
            return true; // 桩世界射线不可达
        }

        @Override
        protected boolean selfUsingItem() {
            return false; // 桩世界使用状态不可达
        }
    }

    @Test
    void noStockMeansNoThrow() {
        var t = target(5.0);
        witch.target = t;
        assertNull(throwGoal.decideForTest(t), "无真实库存：决策不投（不凭空出药水）");
    }

    @Test
    void harmsWhenStockedOtherwiseFallsThrough() {
        var t = target(5.0);
        witch.target = t;
        // 决策顺序：5 格无特殊条件 → 默认伤害药水；有库存 → HARMING
        witch.targetEffectless = true;
        witch.getInventory().setItem(9, potionOf(Potions.HARMING));
        var decided = throwGoal.decideForTest(t);
        assertTrue(decided != null && decided.is(Potions.HARMING), "默认决策 = 伤害药水");
        // 缓慢：距离 > 8 → SLOWNESS 优先
        var far = target(10.0);
        witch.getInventory().setItem(10, potionOf(Potions.SLOWNESS));
        assertTrue(throwGoal.decideForTest(far).is(Potions.SLOWNESS), "远距且无缓慢 → 缓慢药水");
        // 库存缺缓慢但有伤害：回落默认
        witch.getInventory().setItem(10, ItemStack.EMPTY);
        assertTrue(throwGoal.decideForTest(far).is(Potions.HARMING), "缺缓慢回落伤害");
    }

    @Test
    void drinksRealPotionWhenLowHealth() {
        var drinkGoal = new PlayerWitchDrinkGoal(witch) {
            @Override
            protected boolean selfUsingItem() {
                return false; // 26.3 使用状态走数据同步器：桩世界注入"未在使用"
            }

            @Override
            protected float selfHealth() {
                return witch.selfHealthValue; // 生命缝同上
            }

            @Override
            protected boolean selfOnFire() {
                return false; // 桩不可达：注入"未着火"
            }

            @Override
            protected boolean selfUnderWater() {
                return false; // 桩不可达：注入"不在水下"
            }

            @Override
            protected void beginUsingItem() {
                BrainTestStubs.putField(this.mob.asLiving(),
                        net.minecraft.world.entity.LivingEntity.class, "useItem",
                        this.mob.getMainHandItem()); // 直写使用物品字段（跳过同步器）
            }
        };
        var selector = new PlayerGoalSelector();
        selector.addGoal(1, drinkGoal);
        witch.stubHealth(20.0F);
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE), "满血不喝");
        witch.stubHealth(3.0F); // 低血但无药水：不假装喝
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "低血但无药水：不假装喝");
        witch.getInventory().setItem(9, drinkOf(Potions.HEALING));
        selector.tick(true);
        assertTrue(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "低血 + 有治疗药水 → 喝");
        assertTrue(!witch.getMainHandItem().isEmpty()
                && witch.getMainHandItem().getItem() == Items.POTION, "选中真实药水到手（玩家原生使用流程）");
        assertEquals(1, countPotionItems(), "start 只选中不消耗（消耗在饮用完成时由原版结算）");
    }

    @Test
    void selfLowHealthDoesNotThrowHealingPotion() {
        var t = target(5.0);
        witch.target = t;
        witch.stubHealth(2.0F);
        witch.getInventory().setItem(9, potionOf(Potions.HEALING));
        witch.getInventory().setItem(10, potionOf(Potions.REGENERATION));
        assertEquals(null, throwGoal.decideForTest(t), "自我低血不把治疗/再生喷溅药当对敌弹");
        var selector = new PlayerGoalSelector();
        selector.addGoal(1, throwGoal);
        selector.tick(true);
        assertFalse(selector.hasRunningFlag(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                "没有攻击药就不出手");
        assertEquals(0, witch.swingCalls);
    }

    @Test
    void poisonDecisionMatchesBytecodeHealthGate() {
        var t = target(5.0);
        witch.target = t;
        witch.getInventory().setItem(9, potionOf(Potions.POISON));
        witch.getInventory().setItem(10, potionOf(Potions.HARMING));
        witch.targetEffectless = true;
        var goal = new TestThrowGoal(witch);
        goal.targetHealthValue = 12.0F;
        assertTrue(goal.decideForTest(t).is(Potions.POISON), "目标血量 ≥8 → 中毒");
        goal.targetHealthValue = 4.0F;
        assertTrue(goal.decideForTest(t).is(Potions.HARMING), "目标血量 <8 → 不投中毒，回落伤害");
    }

    @Test
    void throwHasSixtyTickIntervalAndConsumesRealStock() {
        var t = target(5.0);
        witch.target = t;
        witch.stubHealth(20.0F);
        witch.getInventory().setItem(9, potionOf(Potions.HARMING)); // 1 组 1 个
        witch.getInventory().setItem(10, potionOf(Potions.HARMING));
        // 真实投掷消耗由玩家物品使用结算；桩直接扣主手数量并记录
        var goal = new TestThrowGoal(witch) {
            @Override
            protected boolean hasLineOfSight(LivingEntity target) {
                return true;
            }

            @Override
            protected float targetHealth(LivingEntity target) {
                return 20.0F;
            }
        };
        int before = countPotionItems();
        var selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
        for (int i = 0; i < 130; i++) {
            selector.tick(true);
        }
        assertEquals(2, witch.swingCalls, "130t 内按 60t 间隔只出手 2 次");
        assertEquals(before - 2, countPotionItems(), "每次出手真实消耗 1 瓶（缺药不假装）");
    }

    private int countPotionItems() {
        int n = 0;
        var inv = witch.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && (stack.getItem() == Items.POTION
                    || stack.getItem() == Items.SPLASH_POTION
                    || stack.getItem() == Items.LINGERING_POTION)) {
                n += stack.getCount();
            }
        }
        return n;
    }
}
