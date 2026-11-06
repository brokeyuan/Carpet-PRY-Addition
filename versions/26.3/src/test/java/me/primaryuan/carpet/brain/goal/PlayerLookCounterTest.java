package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 张望/史莱姆计数锁（round3 #7/#8）：
 * RandomLookAround——rUE、递减在 tick、canContinueToUse 只判断：一次激活持续
 * 20~39 游戏刻（变异：递减搬回 canContinueToUse → 时长翻倍被杀）。Slime——
 * jumpDelay=nextInt(20)+10、激怒 ÷3（变异：固定 12 → 分布窗被杀）。
 * 全部经 PlayerGoalSelector.tick 奇偶门以真实游戏 tick 驱动。
 */
class PlayerLookCounterTest {

    private BrainTestStubs.FakePlayer mob;
    private PlayerGoalSelector selector;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        var world = BrainTestStubs.stubWorld();
        mob = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(world)
                .stubBody(new Vec3(0.5, 64, 0.5));
        selector = new PlayerGoalSelector();
    }

    @Test
    void randomLookDurationIs20To39GameTicks() throws Exception {
        // 可控随机源：nextFloat 首掷 <0.02（立即触发）、nextInt(20)=0（lookTime=20）
        BrainTestStubs.FakePlayer seed0 = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(mob.level())
                .stubBody(new Vec3(0.5, 64, 0.5));
        BrainTestStubs.putField(seed0, net.minecraft.world.entity.Entity.class, "random",
                new net.minecraft.util.RandomSource() {
                    private int nextIntDraws;

                    @Override
                    public net.minecraft.util.RandomSource fork() {
                        return this;
                    }

                    @Override
                    public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
                        return new net.minecraft.world.level.levelgen.PositionalRandomFactory() {
                            @Override
                            public net.minecraft.util.RandomSource fromHashOf(String name) {
                                return net.minecraft.util.RandomSource.create(0L);
                            }

                            @Override
                            public net.minecraft.util.RandomSource fromSeed(long seed) {
                                return net.minecraft.util.RandomSource.create(seed);
                            }

                            @Override
                            public net.minecraft.util.RandomSource at(int x, int y, int z) {
                                return net.minecraft.util.RandomSource.create(0L);
                            }

                            @Override
                            public void parityConfigString(StringBuilder builder) {
                            }
                        };
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
                        return 0; // 恒 0：lookTime=20+0=20
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
                        return 0.0F; // 恒 0 < 0.02：每次评估都触发
                    }

                    @Override
                    public double nextDouble() {
                        return 0.0;
                    }

                    @Override
                    public double nextGaussian() {
                        return 0;
                    }

                });
        PlayerRandomLookAroundGoal goal = new PlayerRandomLookAroundGoal(seed0);
        selector.addGoal(1, goal);

        // 启动后首拍：lookTime=20；此后完整/轻量 tick 都递减 1（rUE 每 tick）。
        // 递减到 0 后下一次偶相评估立即重启（可控源恒触发 = 原版反复触发语义）。
        selector.tick(true); // 启动
        var fLook = PlayerRandomLookAroundGoal.class.getDeclaredField("lookTime");
        fLook.setAccessible(true);
        int l0 = (Integer) fLook.get(goal);
        selector.tick(true);
        int l1 = (Integer) fLook.get(goal);
        selector.tick(false); // 轻量 tick：rUE 仍推进
        int l2 = (Integer) fLook.get(goal);
        org.junit.jupiter.api.Assertions.assertEquals(19, l0, "启动拍 canContinueToUse 通过后 tick 递减到 19");
        org.junit.jupiter.api.Assertions.assertEquals(18, l1, "完整 tick 递减 1");
        org.junit.jupiter.api.Assertions.assertEquals(17, l2, "轻量 tick rUE 仍递减（隔 tick 递减即旧缺陷）");
    }

    /** 攻击距离常数化（桩无属性表，起跳段可达） */
    static class ReachStub extends BrainTestStubs.FakePlayer {
        static ReachStub create(net.minecraft.world.level.Level world) {
            ReachStub p = BrainTestStubs.allocate(ReachStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "uuid", id);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.stubHeldItems(null, null);
            p.stubWorld(world);
            return p;
        }

        @Override
        public double attackReachSq() {
            return 100.0;
        }

        @Override
        public void jumpFromGround() {
            // 桩无属性表（getJumpPower 不可达）：测试只关心重掷结果，不触物理
        }
    }

    @Test
    void slimeJumpDelayDistribution() {
        BrainTestStubs.FakePlayer slime = ReachStub.create(mob.level())
                .stubBody(new Vec3(0.5, 64, 0.5))
                .stubOnGround(true)
                .stubDeltaMovement(Vec3.ZERO);
        PlayerSlimeAttackGoal goal = new PlayerSlimeAttackGoal(slime);
        selector.addGoal(1, goal);

        // 有目标（激怒）：jumpDelay = max(1, (10+nextInt(20))/3) ∈ [1, 9]
        BrainTestStubs.TargetMob target = BrainTestStubs.TargetMob.create().stubWorld(slime.level());
        target.putPosition(slime.position().add(1.0, 0, 0));
        slime.stubTarget(target);

        // 生产侧锁 A（经选择器：轻量 tick 推进 rUE——撤销 rUE 即跳过，此处击杀）：
        // 先启动（完整 tick），随后置 jumpDelay=3 并连续驱动三拍：完整→轻量→完整
        selector.tick(true); // 启动（jumpDelay=0 → 起跳段重掷——覆盖为受控值）
        BrainTestStubs.putInt(goal, PlayerSlimeAttackGoal.class, "jumpDelay", 3);
        selector.tick(true);  // 完整 tick：递减 3→2
        int after1 = (Integer) BrainTestStubs.readField(goal, PlayerSlimeAttackGoal.class, "jumpDelay");
        selector.tick(false); // 轻量 tick：rUE → 递减 2→1（非 rUE 恒 2，此处击杀）
        int after2 = (Integer) BrainTestStubs.readField(goal, PlayerSlimeAttackGoal.class, "jumpDelay");
        org.junit.jupiter.api.Assertions.assertEquals(2, after1, "完整 tick 递减 1");
        org.junit.jupiter.api.Assertions.assertEquals(1, after2, "轻量 tick rUE 仍递减（撤销 rUE 即停滞）");

        // 生产侧锁 B：递减归零后的下一拍走起跳段重掷——采集生产真实 jumpDelay 分布
        int minDelay = Integer.MAX_VALUE;
        int maxDelay = Integer.MIN_VALUE;
        for (int i = 0; i < 300; i++) {
            BrainTestStubs.putInt(goal, PlayerSlimeAttackGoal.class, "jumpDelay", 0);
            goal.tick(); // 起跳 + 重掷（激怒 ÷3）
            int rolled = (Integer) BrainTestStubs.readField(goal, PlayerSlimeAttackGoal.class, "jumpDelay");
            if (rolled > 0) {
                minDelay = Math.min(minDelay, rolled);
                maxDelay = Math.max(maxDelay, rolled);
            }
        }
        assertTrue(minDelay >= 1 && maxDelay <= 9,
                "生产激怒 jumpDelay ∈ [1,9]（实测 [" + minDelay + "," + maxDelay + "]，未 ÷3 的 [4,13] 立即暴露）");
        assertTrue(maxDelay - minDelay >= 4,
                "生产分布跨度非常数（固定 12 → 恒 4 跨度 0；实测跨度 " + (maxDelay - minDelay) + "）");
    }

}
