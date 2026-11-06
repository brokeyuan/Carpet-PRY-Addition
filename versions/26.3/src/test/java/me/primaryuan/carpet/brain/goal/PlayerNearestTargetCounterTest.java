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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享调度逐计数锁（round3 #6/#7/#8 的回归防线）：
 * Nearest——原版构造器 reducedTickDelay(randomInterval)：param 10 → 5 次评估
 * （≈10 游戏 tick 起搜索窗）；rUE 掷骰在 canUse。RandomLookAround——rUE、
 * 递减在 tick、canContinueToUse 只判断（20~39 游戏刻）。SlimeAttack——
 * jumpDelay=nextInt(20)+10、激怒 ÷3、rUE。全部经 PlayerGoalSelector.tick
 * 奇偶门以真实游戏 tick 驱动。变异验证见变异日志（折半丢失/递减移位/固定 12 均被击杀）。
 */
class PlayerNearestTargetCounterTest {

    private BrainTestStubs.FakePlayer mob;
    private PlayerGoalSelector selector;
    private PlayerNearestAttackableTargetGoal<BrainTestStubs.FakePlayer> goal;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        var world = BrainTestStubs.stubWorld();
        mob = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(world)
                .stubBody(new Vec3(0.5, 64, 0.5));
        // 固定随机源：掷骰频率断言不应因 JVM 随机序列波动
        BrainTestStubs.putField(mob, net.minecraft.world.entity.Entity.class, "random",
                net.minecraft.util.RandomSource.create(12345L));
        selector = new PlayerGoalSelector();
        goal = new PlayerNearestAttackableTargetGoal<>(mob, BrainTestStubs.FakePlayer.class,
                16.0, 10, false, p -> p != mob && p.isAlive()) {
            @Override
            protected BrainTestStubs.FakePlayer findBestTarget() {
                return null; // 桩世界无实体查询：只测掷骰频率（scanCalls 缝在 findBestTarget 之前）
            }
        };
        selector.addGoal(1, goal);
    }

    /** 数 canUse 内扫描调用次数（findBestTarget 前的掷骰通过才会扫描） */
    private int scanCount() {
        try {
            var f = PlayerNearestAttackableTargetGoal.class.getDeclaredField("scanCalls");
            f.setAccessible(true);
            return (Integer) f.get(goal);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void randomIntervalIsHalvedInConstructor() throws Exception {
        var f = PlayerNearestAttackableTargetGoal.class.getDeclaredField("randomInterval");
        f.setAccessible(true);
        assertEquals(5, (Integer) f.get(goal),
                "原版构造器 reducedTickDelay(param=10)=5 次评估（≈10 游戏 tick），非直传 10");
    }

    @Test
    void selfTargetIsRejectedCentrally() {
        PlayerNearestAttackableTargetGoal<BrainTestStubs.FakePlayer> selfGoal =
                new PlayerNearestAttackableTargetGoal<>(mob, BrainTestStubs.FakePlayer.class,
                        16.0, 10, false, p -> true) {
                    @Override
                    protected BrainTestStubs.FakePlayer findBestTarget() {
                        return (BrainTestStubs.FakePlayer) mob; // 唯一候选 = 自己
                    }
                };
        assertFalse(selfGoal.canUse(), "共享索敌 Goal 必须拒绝自己");
    }

    @Test
    void diceGatesScanWithVanillaRate() {
        // 100 游戏 tick = 50 次评估；randomInterval=5 → 期望 ~10 次真正扫描。
        // 变异（未折半，interval=10）下期望 ~5 次——统计窗内可分
        for (int tick = 0; tick < 100; tick++) {
            selector.tick(tick % 2 == 0);
        }
        int scans = scanCount();
        assertTrue(scans >= 6 && scans <= 16,
                "1/5 次评估掷骰：50 次评估 → 10±4 次扫描（实测 " + scans + "）");
    }
}
