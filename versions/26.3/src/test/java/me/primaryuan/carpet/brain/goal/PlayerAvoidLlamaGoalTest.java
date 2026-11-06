package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 羊驼规避锁（26.3/1.21.11 Wolf$WolfAvoidEntityGoal 字节码对齐）：
 * canUse = AvoidEntityGoal 基座（24 格内最近羊驼 + 远离选点建路）&& 羊驼 &&
 * 未驯服（假人无驯服系统，按未驯服口径装配）&& llama.getStrength() >= nextInt(5)
 * （强度 5 恒触发、强度 0 仅 1/5）；start/tick 持续清空攻击目标；
 * canContinueToUse = 路径未完 && 威胁存活 && 距离 < maxDist²。
 * 变异验证：删强度掷骰 → 强度 0 的低频用例失败；删 clearTarget → 清目标用例失败。
 */
class PlayerAvoidLlamaGoalTest {

    /** 可编程羊驼桩（isAlive/getStrength 覆写绕开 data watcher） */
    static class StubLlama extends net.minecraft.world.entity.animal.equine.Llama {
        int strength = 1;

        @SuppressWarnings("unused")
        private StubLlama() {
            super(null, null);
        }

        static StubLlama create() {
            return BrainTestStubs.allocate(StubLlama.class);
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public int getStrength() {
            return this.strength;
        }
    }

    /** 缝注入版狼避羊驼 Goal（候选/选点/建路注入，条件链本体真实执行） */
    static class TestAvoidLlamaGoal extends PlayerAvoidEntityGoal<StubLlama> {
        List<StubLlama> candidates = List.of();
        BlockPos flee = new BlockPos(10, 64, 0);

        TestAvoidLlamaGoal(BrainTestStubs.FakePlayer mob) {
            super(mob, StubLlama.class, 24.0F, 1.5, 1.5,
                    llama -> llama.getStrength() >= mob.getRandom().nextInt(5), true);
        }

        @Override
        protected List<StubLlama> nearbyAvoidCandidates() {
            return this.candidates;
        }

        @Override
        protected BlockPos fleePoint(LivingEntity threat) {
            return this.flee;
        }

        @Override
        protected me.primaryuan.carpet.brain.Path createFleePath(BlockPos flee) {
            return new me.primaryuan.carpet.brain.Path(List.of(flee));
        }
    }

    private static BrainTestStubs.FakePlayer wolf;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new Vec3(0.5, 64, 0.5));
    }

    private static StubLlama llamaAt(double dx, int strength) {
        StubLlama llama = StubLlama.create();
        llama.strength = strength;
        BrainTestStubs.putField(llama, Entity.class, "level", wolf.level());
        BrainTestStubs.putField(llama, Entity.class, "position", wolf.position().add(dx, 0, 0));
        return llama;
    }

    @Test
    void strongLlamaAlwaysScaresWeakNeverGuaranteed() {
        TestAvoidLlamaGoal goal = new TestAvoidLlamaGoal(wolf);

        goal.candidates = List.of(llamaAt(5, 5));
        for (int i = 0; i < 30; i++) {
            assertTrue(goal.canUse(), "强度 5 >= nextInt(5) 恒成立 → 30 次评估全触发");
        }

        goal.candidates = List.of(llamaAt(5, 0));
        boolean everAvoided = false;
        boolean everStayed = false;
        for (int i = 0; i < 200; i++) {
            if (goal.canUse()) {
                everAvoided = true;
            } else {
                everStayed = true;
            }
        }
        assertTrue(everAvoided && everStayed,
                "强度 0：nextInt(5)==0 时才规避（1/5 概率面，200 次内两种结果都出现）");
    }

    @Test
    void distanceAndSelection() {
        TestAvoidLlamaGoal goal = new TestAvoidLlamaGoal(wolf);
        goal.candidates = List.of(llamaAt(30, 5)); // 30 格 > 24：不规避
        assertFalse(goal.canUse(), "24 格外不触发");

        goal.candidates = List.of(llamaAt(5, 5), llamaAt(2, 5)); // 最近者优先
        assertTrue(goal.canUse());
        assertEquals(2, distanceToAvoid(goal), 1.0E-6, "选最近的羊驼（原版 getNearestEntity）");
    }

    @Test
    void clearTargetWhileFleeing() {
        TestAvoidLlamaGoal goal = new TestAvoidLlamaGoal(wolf);
        goal.candidates = List.of(llamaAt(5, 5));
        assertTrue(goal.canUse());
        // 逃跑开始前锁了个目标（如同队误伤后的仇恨）
        BrainTestStubs.TargetMob enemy = BrainTestStubs.TargetMob.create().stubWorld(wolf.level());
        wolf.stubTarget(enemy);
        goal.start();
        assertNull(wolf.getTarget(), "start 清空攻击目标（原版 WolfAvoidEntityGoal.start）");
        goal.tick();
        assertNull(wolf.getTarget(), "tick 期间持续清空（原版 tick 同款）");
    }

    @Test
    void continueToUseGuards() {
        TestAvoidLlamaGoal goal = new TestAvoidLlamaGoal(wolf);
        StubLlama llama = llamaAt(5, 5);
        goal.candidates = List.of(llama);
        assertTrue(goal.canUse());
        goal.start();
        // start 装载了逃跑路径 → 路径未完、威胁存活、距离 <24 → 续行
        assertTrue(goal.canContinueToUse(), "路径未完且威胁在范围内 → 续行");

        // 逃出 24 格（maxDist）→ 原版距离门结束
        BrainTestStubs.putField(llama, Entity.class, "position", wolf.position().add(30, 0, 0));
        assertFalse(goal.canContinueToUse(), "逃出 maxDist → 结束（原版距离门）");
    }

    @Test
    void flagsAndPriorityContext() {
        TestAvoidLlamaGoal goal = new TestAvoidLlamaGoal(wolf);
        assertEquals(java.util.EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK),
                goal.getFlags(), "规避占用 MOVE+LOOK（原版 AvoidEntityGoal 同款）");
    }

    private static double distanceToAvoid(TestAvoidLlamaGoal goal) {
        LivingEntity toAvoid = (LivingEntity) BrainTestStubs.readField(goal, PlayerAvoidEntityGoal.class, "toAvoid");
        return wolf.position().distanceTo(toAvoid.position());
    }
}
