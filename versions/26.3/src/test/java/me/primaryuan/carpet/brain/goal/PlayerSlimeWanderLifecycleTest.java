package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 史莱姆空闲跳行生命周期锁（round4 #2）：
 * 起跳→腾空→落地→等待→再次起跳的完整周期经生产 Goal + 选择器自然推进
 * （不反射置零 jumpDelay）；等待未耗尽时落地不立即重跳；方向未到期不换；
 * 空闲/追击切换后恢复。变异验证：start 清零恢复 → 等待跨周期用例失败。
 */
class PlayerSlimeWanderLifecycleTest {

    /** 身体可控的史莱姆桩：onGround 由测试切换，jumpFromGround 触发离地 */
    static class SlimeStub extends BrainTestStubs.FakePlayer {
        boolean ground = true;
        int jumpCalls;
        int moveCalls;



        static SlimeStub create(net.minecraft.world.level.Level world, Vec3 pos) {
            SlimeStub p = BrainTestStubs.allocate(SlimeStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "uuid", id);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.ground = true; // Unsafe 分配跳过初始化器：默认在地面
            p.stubWorld(world);
            p.stubBody(pos);
            return p;
        }

        @Override
        public boolean onGround() {
            return this.ground;
        }

        @Override
        public void jumpFromGround() {
            this.jumpCalls++;
            this.ground = false; // 起跳即离地（模拟物理）
        }
    }

    private net.minecraft.world.level.Level world;
    private PlayerGoalSelector selector;
    private PlayerSlimeWanderGoal goal;
    private SlimeStub slime;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        world = BrainTestStubs.stubWorld();
        slime = SlimeStub.create(world, new Vec3(0.5, 64, 0.5));
        goal = new PlayerSlimeWanderGoal(slime);
        selector = new PlayerGoalSelector();
        selector.addGoal(1, goal);
    }

    @Test
    void fullJumpCycleWithWaitNotConsumedInstantly() {
        // 可控随机源：nextInt 恒 0 → jumpDelay 恒 10（确定性时序）
        BrainTestStubs.putField(slime, net.minecraft.world.entity.Entity.class, "random",
                new net.minecraft.util.RandomSource() {
                    @Override
                    public net.minecraft.util.RandomSource fork() {
                        return this;
                    }

                    @Override
                    public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void setSeed(long seed) {
                    }

                    @Override
                    public int nextInt() {
                        return 0;
                    }

                    @Override
                    public int nextInt(int bound) {
                        return 0;
                    }

                    @Override
                    public long nextLong() {
                        return 0;
                    }

                    @Override
                    public boolean nextBoolean() {
                        return true;
                    }

                    @Override
                    public float nextFloat() {
                        return 0.0F;
                    }

                    @Override
                    public double nextDouble() {
                        return 0.0;
                    }

                    @Override
                    public double nextGaussian() {
                        return 0.0;
                    }
                });
        // 完整 tick 启动：起跳（可控源 → jumpDelay 恒 10）
        selector.tick(true);
        assertEquals(1, slime.jumpCalls, "首拍起跳");
        int delayAfterJump = goal.jumpDelayForTest();
        assertEquals(10, delayAfterJump,
                "起跳拍同拍不递减等待（onGround 门在：重掷 10 不被打折——门移除变异即 9）");
        assertFalse(slime.onGround(), "起跳后离地");

        // 腾空期（round5 补）：完整 tick 起跳后紧随的轻量 tick 走生产 tick，
        // 但 onGround 门挂起等待——不递减、不输出第二次移动
        slime.ground = false;
        int delayInAir = goal.jumpDelayForTest();
        int jumpsBefore0 = slime.jumpCalls;
        selector.tick(true);  // 完整 tick（腾空：canContinueToUse=false → 清理移出）
        selector.tick(false); // 轻量 tick：rUE 仍推进 tick，但 onGround 门 return
        assertEquals(delayInAir, goal.jumpDelayForTest(), "腾空期等待挂起不递减");
        assertEquals(jumpsBefore0, slime.jumpCalls, "腾空期不输出起跳");

        // 落地恢复：等待从剩余值继续消耗（不立即重跳——清零变异在此击杀）
        slime.ground = true;
        int jumpsBefore = slime.jumpCalls;
        int landedDelay = goal.jumpDelayForTest();
        assertTrue(landedDelay >= 10, "起跳时重掷的等待 ≥10，落地时仍有剩余（实测 " + landedDelay + "）");
        selector.tick(true); // 重启 Goal：等待消耗 1（清零版此拍立即重跳）
        assertEquals(jumpsBefore, slime.jumpCalls, "落地首拍不立即重跳（等待跨周期保留）");
        assertTrue(goal.jumpDelayForTest() == landedDelay - 1,
                "完整 tick 等待 -1（实测 " + goal.jumpDelayForTest() + "，应为 " + (landedDelay - 1) + "）");
        // 地面上的轻量 tick：rUE → 等待仍递减 1（round5 #2 核心断言）
        selector.tick(false);
        assertEquals(landedDelay - 2, goal.jumpDelayForTest(),
                "地面轻量 tick 等待也 -1（rUE 每 tick 递减；隔 tick 递减即旧缺陷）");
        selector.tick(false);
        assertEquals(landedDelay - 3, goal.jumpDelayForTest(), "连续地面轻量 tick 继续递减");
        // 方向时钟不被 rUE 加倍：重掷周期按奇偶分频
        int rt0 = readNextRandomize();
        selector.tick(true);
        selector.tick(false);
        int rt1 = readNextRandomize();
        assertTrue(rt1 <= rt0, "方向时钟单调推进（rUE 不加倍重掷）");

        // 持续驱动直到再次起跳：确认完整周期可达
        int guard = 0;
        while (slime.jumpCalls == 1 && guard++ < 200) {
            selector.tick(true);
            slime.ground = true; // 模拟每次 tick 后已落地（等待逐拍消耗）
            selector.tick(false);
        }
        assertTrue(slime.jumpCalls >= 2, "等待耗尽后再次起跳（完整周期）");
    }

    @Test
    void directionPersistsAcrossLandingUntilReschedule() {
        selector.tick(true); // 首跳 + 首次方向
        float dir1 = goal.chosenDegreesForTest();
        int randTime1 = readNextRandomize();

        // 落地重启：方向时钟不重置（start 不清零）；奇偶分频——两拍完整 tick 恰好
        // 消耗 1 次评估（rUE 下方向重掷不加速）
        slime.ground = false;
        slime.ground = true;
        selector.tick(true);
        selector.tick(true);
        assertEquals(randTime1 - 1, readNextRandomize(),
                "方向时钟跨周期递减（两拍完整 tick 消耗 1 次评估——rUE 不加倍）");
        float dir2 = goal.chosenDegreesForTest();
        if (readNextRandomize() > 0) {
            assertEquals(dir1, dir2, "方向未到重掷期限：不换方向");
        }

        // 把方向时钟推到 0：后续 tick 内重掷出新方向（相位由奇偶门决定，
        // 循环驱动直到方向变化——重掷必然发生在某次 true 相）
        float before = goal.chosenDegreesForTest();
        int guard = 0;
        while (goal.chosenDegreesForTest() == before && guard++ < 200) {
            BrainTestStubs.putInt(goal, PlayerSlimeWanderGoal.class, "nextRandomizeTime", 1);
            slime.ground = true;
            selector.tick(true);
        }
        assertNotEquals(before, goal.chosenDegreesForTest(), "到期重掷方向");
    }

    @Test
    void idleChaseSwitchingResumes() {
        // 空闲启动起跳
        selector.tick(true);
        assertEquals(1, slime.jumpCalls);
        BrainTestStubs.TargetMob target = BrainTestStubs.TargetMob.create().stubWorld(world);
        target.putPosition(slime.position().add(2, 0, 0));
        slime.stubTarget(target); // 追击接管（wander canUse=false 让位）

        // 目标出现：选择器路径下 goal 会被完整 tick 清理移出（canUse=false），
        // 目标门在生命周期内不可达——直接驱动 goal.tick() 验证防御性门本身：
        // onGround=true + 目标存在 → tick 直接 return（不重掷/不递减/不起跳）
        slime.ground = true;
        int jumpsWithTarget = slime.jumpCalls;
        int delayBeforeTarget = goal.jumpDelayForTest();
        int randBeforeTarget = readNextRandomize();
        goal.tick();
        assertEquals(jumpsWithTarget, slime.jumpCalls, "目标门：不输出起跳");
        assertEquals(delayBeforeTarget, goal.jumpDelayForTest(), "目标门：等待不递减");
        assertEquals(randBeforeTarget, readNextRandomize(), "目标门：方向时钟不推进");

        // 空闲 Goal 被停（或自然让位）：状态保留
        goal.stop();
        slime.ground = true;
        int delayBefore = goal.jumpDelayForTest();

        // 目标消失 → 空闲恢复：从剩余等待继续（不清零）
        slime.stubTarget(null);
        selector.tick(true);
        assertTrue(slime.jumpCalls == 1 || goal.jumpDelayForTest() != 0 || delayBefore == 0,
                "空闲恢复：等待从剩余值继续或耗尽起跳");
        int guard = 0;
        while (slime.jumpCalls == 1 && guard++ < 200) {
            slime.ground = true;
            selector.tick(true);
        }
        assertTrue(slime.jumpCalls >= 2, "切回空闲后正常恢复跳行");
    }

    private int readNextRandomize() {
        return (Integer) BrainTestStubs.readField(goal, PlayerSlimeWanderGoal.class, "nextRandomizeTime");
    }
}
