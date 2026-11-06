package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 狼跟随主人锁（26.3/1.21.11 FollowOwnerGoal(1.0, 10F, 2F) 字节码对齐）：
 * startDistance=10 是启动阈值（distanceToSqr >= 100 才 canUse）、stopDistance=2
 * 是结束阈值（canContinueToUse 须 !isDone && distanceToSqr > 4）——旧实现
 * "超过 2 格就启动 + 0.6 近距慢速分档"均无原版依据；start/stop 临时置 0/还原
 * 水路 malus（原版 setPathfindingMalus(WATER, 0) 同款）。
 */
class PlayerFollowOwnerGoalTest {

    static class TestFollowGoal extends PlayerFollowOwnerGoal {
        BrainTestStubs.FakePlayer ownerOverride;

        TestFollowGoal(BrainTestStubs.FakePlayer mob, UUID ownerUuid) {
            super(mob, ownerUuid, 1.0);
        }

        @Override
        protected net.minecraft.server.level.ServerPlayer resolveOwner() {
            return this.ownerOverride;
        }
    }

    private BrainTestStubs.FakePlayer wolf;
    private BrainTestStubs.FakePlayer owner;
    private TestFollowGoal goal;
    private BrainTestStubs.RecordingNavigation nav;

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        var level = BrainTestStubs.stubWorld();
        wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(level)
                .stubBody(new Vec3(0.5, 64, 0.5));
        owner = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID()).stubWorld(level);
        goal = new TestFollowGoal(wolf, owner.getUUID());
        goal.ownerOverride = owner;
        nav = new BrainTestStubs.RecordingNavigation(wolf);
        BrainTestStubs.putField(wolf.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
    }

    private void ownerAt(double dx, double dz) {
        owner.stubBody(new Vec3(0.5 + dx, 64, 0.5 + dz));
    }

    @Test
    void startThresholdIsTenBlocksNotTwo() {
        ownerAt(1.5, 0); // ≈1.5 格：旧实现此处启动，原版不启动
        assertFalse(goal.canUse(), "2~10 格之间不启动跟随（原版 startDistance=10 启动阈值）");

        ownerAt(5, 0);
        assertFalse(goal.canUse(), "5 格不启动");

        ownerAt(10, 0);
        assertTrue(goal.canUse(), "恰 10 格启动（原版 distanceToSqr >= 100 含端点）");

        ownerAt(15, 0);
        assertTrue(goal.canUse(), "15 格启动");
    }

    @Test
    void stopThresholdIsTwoBlocks() {
        ownerAt(12, 0);
        assertTrue(goal.canUse());
        goal.start();
        nav.done = false; // 模拟跟随行进中

        ownerAt(3, 0);
        assertTrue(goal.canContinueToUse(), "3 格 > 2 → 继续跟随");

        ownerAt(1.5, 0);
        assertFalse(goal.canContinueToUse(), "≤2 格 → 停止（原版 stopDistance² = 4）");

        ownerAt(12, 0);
        nav.done = true;
        assertFalse(goal.canContinueToUse(), "路径走完 → 结束（原版 !isDone 门）");
    }

    @Test
    void waterMalusTemporarilyZeroDuringFollow() {
        ownerAt(12, 0);
        assertTrue(goal.canUse());
        double before = wolf.fields().getWaterMalus();
        goal.start();
        assertEquals(0.0, wolf.fields().getWaterMalus(), "跟随期间水路 malus 置 0（原版跟主人下水）");
        goal.stop();
        assertEquals(before, wolf.fields().getWaterMalus(), "stop 还原原值");
    }

    @Test
    void constantSpeedNoTiers() {
        ownerAt(12, 0);
        assertTrue(goal.canUse());
        goal.start();
        goal.tick(); // timeToRecalcPath 0→-1 → 首 tick 即建路（原版同款）
        goal.tick(); // 重算倒计时 5 → 4，不再建路
        assertEquals(1, nav.moveToCalls, "重算节奏：首 tick 建路后进入 adjustedTickDelay(10) 倒计时");
        assertEquals(1.0, nav.lastSpeed, "跟随恒速 1.0——无 0.6 近距慢速分档（旧实现缺陷）");
        assertEquals(owner, nav.lastMoveTarget, "直奔主人");
    }

    @Test
    void ownerDeadOrOfflineStopsFollow() {
        ownerAt(12, 0);
        assertTrue(goal.canUse());
        goal.ownerOverride = null; // 下线/换世界
        assertFalse(goal.canUse(), "主人不在 → 不跟随");
        owner.alive = false;
        goal.ownerOverride = owner;
        assertFalse(goal.canUse(), "主人死亡 → 不跟随");
    }

    @Test
    void flagsMoveAndLook() {
        assertEquals(java.util.EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK),
                goal.getFlags(), "原版 FollowOwnerGoal 旗标 MOVE+LOOK");
    }
}
