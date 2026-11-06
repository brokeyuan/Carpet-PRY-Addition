package me.primaryuan.carpet.brain;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 僵尸系 Goal × 战斗队伍：索敌维持与反击链的同队过滤（僵尸装配使用的共享
 * Goal 类组合验证）。索敌起始扫描与群体警报依赖世界实体查询，由 boot check
 * 与游戏内验证覆盖；本类锁定纯逻辑面。
 */
class ZombieTeamCombatTest {

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
    }

    @AfterEach
    void tearDown() throws Exception {
        var field = BrainTeams.class.getDeclaredField("TEAMS");
        field.setAccessible(true);
        ((java.util.Map<?, ?>) field.get(null)).clear();
    }

    @Test
    void targetGoalDropsSameTeamTarget() throws Exception {
        // 身份门对桩放行（生产为 EntityPlayerMPFake instanceof，矩阵测试见 BrainTeamsTest）
        var gate = BrainTeams.class.getDeclaredField("combatantGate");
        gate.setAccessible(true);
        gate.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                e -> e instanceof net.minecraft.server.level.ServerPlayer);
        try {
            BrainTestStubs.FakePlayer zombie = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubBody(new Vec3(0, 64, 0));
            BrainTestStubs.FakePlayer mate = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubBody(new Vec3(3, 64, 4));
            BrainTeams.join(zombie.getUUID(), "red");
            BrainTeams.join(mate.getUUID(), "red");

            // 僵尸玩家目标同款（队伍判定在链首，友军裁剪不触达桩无法提供的视线/难度查询；
            // 候选用 FakePlayer——isAlive 覆写恒 true，同队断言不依赖存活门短路）
            PlayerNearestAttackableTargetGoal<Player> goal =
                    new PlayerNearestAttackableTargetGoal<>(zombie, Player.class, 35.0, 10, false,
                            p -> p.isAlive() && !p.isSpectator() && !p.isCreative());

            // 同队目标：canContinueToUse → false（追击立即放弃）
            zombie.setTarget(mate);
            assertFalse(goal.canContinueToUse(), "同队目标不得维持追击");
            // 异队交战合法性由 BrainTeamsTest 矩阵覆盖（此处 canContinueToUse 的
            // 难度门需要世界上下文，由 boot check 与游戏内验证承接）
        } finally {
            var gate2 = BrainTeams.class.getDeclaredField("combatantGate");
            gate2.setAccessible(true);
            gate2.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                    BrainTeams::isFakeCombatant);
        }
    }

    @Test
    void wolfHateSyncIgnoresSameTeamTarget() throws Exception {
        // 狼仇恨同步（syncHate→biteable）同样受队伍过滤：主人打了同队假人，
        // 狼不锁定队友（FR-3 覆盖面：仇恨同步路径）。直测 biteable（反射），
        // 桩身份门开法与 targetGoalDropsSameTeamTarget 相同。
        var gate = BrainTeams.class.getDeclaredField("combatantGate");
        gate.setAccessible(true);
        gate.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                e -> e instanceof net.minecraft.server.level.ServerPlayer);
        try {
            BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubWorld(BrainTestStubs.stubWorld());
            // 同队/异队候选都用 FakePlayer 桩（参战身份 = ServerPlayer，过门）；
            // 候选目标经 biteable 只做身份/存活/队伍判定——队伍过滤先于一切
            // 玩家门禁（难度门需要 LevelData，由游戏内验证承接），
            // 移除过滤时同队断言直接失败
            BrainTestStubs.FakePlayer mate = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubWorld(wolf.level());
            BrainTeams.join(wolf.getUUID(), "red");
            BrainTeams.join(mate.getUUID(), "red");

            WolfBrain brain = (WolfBrain) allocateBrain(wolf, mate.getUUID());
            var biteable = WolfBrain.class.getDeclaredMethod("biteable",
                    net.minecraft.server.level.ServerPlayer.class, net.minecraft.world.entity.LivingEntity.class);
            biteable.setAccessible(true);
            assertNull(biteable.invoke(brain, wolf, mate), "同队假人不是合法扑咬候选");

            // 异队目标恢复合法候选（队伍过滤不误伤正常交战）
            BrainTeams.join(mate.getUUID(), "blue");
            Object result = biteable.invoke(brain, wolf, mate);
            assertTrue(result == mate, "异队目标保持合法候选");
        } finally {
            var gate2 = BrainTeams.class.getDeclaredField("combatantGate");
            gate2.setAccessible(true);
            gate2.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                    BrainTeams::isFakeCombatant);
        }
    }

    private static Object allocateBrain(BrainTestStubs.FakePlayer wolf, UUID owner) throws Exception {
        var ctor = WolfBrain.class.getDeclaredConstructor(
                net.minecraft.server.level.ServerPlayer.class, UUID.class);
        ctor.setAccessible(true);
        return ctor.newInstance(wolf, owner);
    }

    @Test
    void revengeIgnoresSameTeamAttacker() throws Exception {
        var gate = BrainTeams.class.getDeclaredField("combatantGate");
        gate.setAccessible(true);
        gate.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                e -> e instanceof net.minecraft.server.level.ServerPlayer);
        try {
            BrainTestStubs.FakePlayer zombie = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubWorld(BrainTestStubs.stubWorld()); // 难度门读 level().getDifficulty()
            BrainTestStubs.FakePlayer mate = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                    .stubWorld(zombie.level()); // attacker 同维门读 level()
            BrainTeams.join(zombie.getUUID(), "red");
            BrainTeams.join(mate.getUUID(), "red");

            PlayerHurtByTargetGoal goal = new PlayerHurtByTargetGoal(zombie, null);

            // 同队玩家攻击者：队伍门在玩家门禁之前（canUse 链首），不触达桩无法
            // 提供的 gameMode 查询即被拒绝
            zombie.stubHurtBy(mate);
            assertFalse(goal.canUse(), "同队误伤不触发复仇");

            // 异队玩家攻击者（门开着，难度门走 LevelData 夹具）→ 复仇合法
            BrainTeams.join(mate.getUUID(), "blue");
            assertTrue(goal.canUse(), "异队攻击者触发反击");
        } finally {
            var gate2 = BrainTeams.class.getDeclaredField("combatantGate");
            gate2.setAccessible(true);
            gate2.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                    BrainTeams::isFakeCombatant);
        }

    }
}
