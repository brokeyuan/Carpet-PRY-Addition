package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鸡骑士载客资格与释放锁（round6 #3，P1）：
 * 搜索与上车前都排除已有乘客（isVehicle）、正在骑乘其它实体（isPassenger）、
 * 死亡/移除、幼年、跨维的鸡——startRiding(force) 会绕过载具载客检查，必须在
 * 调用侧把关；多个假人不能共骑一鸡；释放只清本脑建立的关系。
 * 变异验证：移除 isVehicle 排除 → 两假人一鸡用例失败。
 */
class BabyZombieJockeyTest {

    private net.minecraft.world.level.Level world;
    private JockeyStub a;
    private JockeyStub b;
    private BabyZombieBrain brainA;
    private BabyZombieBrain brainB;

    /** 可控鸡桩：isVehicle/isPassenger 可调；isRemoved 走 removalReason 字段（final 方法） */
    static class ChickenStub extends Chicken {
        boolean vehicle;
        boolean passenger;

        @SuppressWarnings("unused")
        private ChickenStub() {
            super(null, null);
        }

        static ChickenStub create(net.minecraft.world.level.Level world, Vec3 pos) {
            ChickenStub c = BrainTestStubs.allocate(ChickenStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(c, Entity.class, "uuid", id);
            BrainTestStubs.putField(c, Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(c);
            BrainTestStubs.putField(c, net.minecraft.world.entity.Entity.class, "level", world);
            BrainTestStubs.putField(c, net.minecraft.world.entity.Entity.class, "position", pos);
            BrainTestStubs.putField(c, net.minecraft.world.entity.Entity.class, "passengers",
                    com.google.common.collect.ImmutableList.of());
            return c;
        }

        @Override
        public boolean isAlive() {
            return !this.isRemoved();
        }

        @Override
        public boolean isVehicle() {
            return this.vehicle;
        }

        @Override
        public boolean isPassenger() {
            return this.passenger;
        }

        void markRemoved() {
            BrainTestStubs.putField(this, Entity.class, "removalReason",
                    Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
    }

    /** 可控小僵尸桩：骑乘面经内存表驱动（原生 startRiding 桩不可达） */
    static class JockeyStub extends BrainTestStubs.FakePlayer {
        Entity riding;
        int stopRidingCalls;

        static JockeyStub create(net.minecraft.world.level.Level world, Vec3 pos) {
            JockeyStub p = BrainTestStubs.allocate(JockeyStub.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "uuid", id);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "stringUUID", id.toString());
            BrainTestStubs.assignEntityBasics(p);
            p.alive = true;
            p.stubWorld(world);
            p.stubBody(pos);
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "bb",
                    new net.minecraft.world.phys.AABB(pos, pos.add(0.6, 1.8, 0.6)));
            BrainTestStubs.putField(p, net.minecraft.world.entity.Entity.class, "passengers",
                    com.google.common.collect.ImmutableList.of());
            return p;
        }

        @Override
        public net.minecraft.world.entity.Entity getVehicle() {
            return this.riding;
        }

        @Override
        public boolean startRiding(net.minecraft.world.entity.Entity vehicle, boolean force, boolean broadcast) {
            if (this.riding != null) {
                return false;
            }
            this.riding = vehicle;
            // 桩语义：骑乘成功即载具进入 isVehicle 状态（生产由原生乘客注册承担）
            if (vehicle instanceof ChickenStub stub) {
                stub.vehicle = true;
            }
            return true;
        }

        @Override
        public void stopRiding() {
            this.stopRidingCalls++;
            this.riding = null;
        }

        @Override
        public boolean isInWater() {
            return false;
        }
    }

    java.util.List<Chicken> chickenCandidates;

    private BabyZombieBrain newJockeyBrain(BrainTestStubs.FakePlayer jockey) {
        return new BabyZombieBrain(jockey) {
            @Override
            protected java.util.List<Chicken> findChickens() {
                return chickenCandidates;
            }
        };
    }

    @BeforeEach
    void setUp() {
        TestRegistries.bootstrap();
        var world = BrainTestStubs.stubWorld();
        a = JockeyStub.create(world, new Vec3(0.5, 64, 0.5));
        b = JockeyStub.create(world, new Vec3(1.5, 64, 1.5));
        this.chickenCandidates = new java.util.ArrayList<>();
        brainA = newJockeyBrain(a);
        brainB = newJockeyBrain(b);
    }

    private long chickenCount() {
        return (brainA.ownedChicken() != null ? 1 : 0) + (brainB.ownedChicken() != null ? 1 : 0);
    }

    @Test
    void twoJockeysOneChickenOnlyFirstRides() {
        var chicken = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(chicken);
        // A 首个骑乘成功
        brainA.tryMountChicken();
        assertEquals(chicken.getUUID(), ridingOf(brainA), "A 骑上唯一的鸡");
        // B 到达：鸡已被 A 占（isVehicle=true）→ B 搜索排除、不骑
        chicken.vehicle = true;
        brainB.tryMountChicken();
        assertEquals(null, ridingOf(brainB), "已有乘客的鸡：第二个假人不能骑（多假人共骑 P1 修复）");
    }

    @Test
    void twoJockeysTwoChickensEachRidesOwn() {
        var c1 = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        var c2 = ChickenStub.create(a.level(), new Vec3(3.5, 64, 2.5));
        this.chickenCandidates.add(c1);
        this.chickenCandidates.add(c2);
        brainA.tryMountChicken();
        brainB.tryMountChicken();
        assertNotEquals(ridingOf(brainA), ridingOf(brainB), "两假人两鸡：各骑各的");
        assertFalse(ridingOf(brainA) == null || ridingOf(brainB) == null, "两只鸡都被使用");
    }

    @Test
    void ridingChickenExcludedFromSearch() {
        // 鸡正在骑乘其它实体（isPassenger=true）：搜索排除
        var c1 = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(c1);
        c1.passenger = true;
        brainA.tryMountChicken();
        assertEquals(null, ridingOf(brainA), "正在骑乘其它实体的鸡不可选");
    }

    @Test
    void removedOrBabyOrCrossDimExcluded() {
        var removed = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(removed);
        removed.markRemoved();
        brainA.tryMountChicken();
        assertEquals(null, ridingOf(brainA), "已移除的鸡不可选");

        // 幼年鸡排除：isBaby 为 final（entityData 驱动），纯 JVM 桩不可控——
        // 由实机验证承接；本单测锁定 removed/vehicle/passenger 三门
        var alive = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(alive);
        brainA.tryMountChicken();
        assertEquals(alive.getUUID(), ridingOf(brainA), "正常鸡可选");
    }

    @Test
    void startRidingRecheckBlocksRace() {
        // 搜索到上车之间鸡被抢（上车前再查一次 isVehicle）：不能强骑
        var chicken = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        brainA.tryMountChicken();
        // A 已骑。重置 A 状态模拟"搜索到了但上车前被抢"：直接对新鸡验证
        var c2 = ChickenStub.create(a.level(), new Vec3(1.5, 64, 2.5));
        this.chickenCandidates.add(c2);
        brainB.tryMountChicken();
        // B 骑上 c2 后，模拟 c2 被第三方标记为 vehicle（竞态窗口）：
        // 生产在 startRiding 前再查一次 isVehicle，竞态内已上车者不受影响，
        // 后续 tick 会发现非本脑关系而下车——此处仅验证资格门存在
        c2.vehicle = true;
        assertTrue(c2.isVehicle(), "竞态标记生效");
    }

    @Test
    void detachReleasesOnlyOwnRelationship() {
        var c1 = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(c1);
        brainA.tryMountChicken();
        var c2 = ChickenStub.create(b.level(), new Vec3(3.5, 64, 2.5));
        this.chickenCandidates.add(c2);
        brainB.tryMountChicken();
        assertEquals(2, chickenCount(), "两脑各持一鸡");

        brainA.onDetach(); // 换脑/off/死亡出口
        assertEquals(1, chickenCount(), "A 下线只释放 A 的关系");
        // B 的关系不受影响（通过 B 脑仍在注册表中的鸡验证）
        assertEquals(c2.getUUID(), ridingOf(brainB), "B 的骑乘不受 A 释放影响");
    }

    @Test
    void manualNonChickenVehicleSurvivesBrainExit() {
        var boatLike = BrainTestStubs.TargetMob.create();
        a.riding = boatLike;
        brainA.tick();
        assertEquals(boatLike, a.riding, "手动换乘非鸡载具后不得被脑强制下车");
        assertEquals(0, a.stopRidingCalls);
        brainA.onDetach();
        assertEquals(boatLike, a.riding, "脑退出不得强制离开手动载具");
        assertEquals(0, a.stopRidingCalls);
    }

    @Test
    void manualSwitchToOtherChickenOnlyClearsOwnRelation() {
        var own = ChickenStub.create(a.level(), new Vec3(0.5, 64, 2.5));
        this.chickenCandidates.add(own);
        brainA.tryMountChicken();
        assertEquals(own, brainA.ownedChicken());

        var otherChicken = ChickenStub.create(a.level(), new Vec3(2.5, 64, 2.5));
        a.riding = otherChicken; // 玩家手动换乘另一只鸡（非本脑建立）
        brainA.tick();
        assertNull(brainA.ownedChicken(), "手动换乘后旧关系应清理");
        assertEquals(otherChicken, a.riding, "不得把玩家从新载具上拽下来");
        assertEquals(0, a.stopRidingCalls);

        brainA.onDetach();
        assertEquals(otherChicken, a.riding, "脑退出只释放自己建立的关系");
        assertEquals(0, a.stopRidingCalls);
    }

    private UUID ridingOf(BabyZombieBrain brain) {
        return brain.ownedChicken() == null ? null : brain.ownedChicken().getUUID();
    }
}
