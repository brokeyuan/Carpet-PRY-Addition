package me.primaryuan.carpet.brain;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 战斗队伍核心语义：同队/异队/无队矩阵、参战身份门（真人与影子假人不串）、
 * 保留字、成员生命周期与改队撤销。
 */
class BrainTeamsTest {

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清表防串测 + 还原生产身份门
        var field = BrainTeams.class.getDeclaredField("TEAMS");
        field.setAccessible(true);
        ((java.util.Map<?, ?>) field.get(null)).clear();
        var gate = BrainTeams.class.getDeclaredField("combatantGate");
        gate.setAccessible(true);
        gate.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                BrainTeams::isFakeCombatant);
    }

    /** 测试身份门：桩（FakePlayer 继承 ServerPlayer）与裸假人都放行 */
    private static void openGateForStubs() throws Exception {
        var gate = BrainTeams.class.getDeclaredField("combatantGate");
        gate.setAccessible(true);
        gate.set(null, (java.util.function.Predicate<net.minecraft.world.entity.LivingEntity>)
                e -> e instanceof net.minecraft.server.level.ServerPlayer);
    }

    @Test
    void sameTeamIsFriendly() {
        EntityPlayerMPFake a = BrainTestStubs.rawFake(UUID.randomUUID());
        EntityPlayerMPFake b = BrainTestStubs.rawFake(UUID.randomUUID());
        BrainTeams.join(a.getUUID(), "red");
        BrainTeams.join(b.getUUID(), "red");
        assertTrue(BrainTeams.isTeamFriendly(a, b), "同队假人互为友军");
        assertTrue(BrainTeams.isTeamFriendly(b, a), "判定对称");
        assertFalse(BrainTeams.isTeamFriendly(a, a), "自身不作判定对象（调用方自排）");
    }

    @Test
    void differentTeamsAreNotFriendly() {
        EntityPlayerMPFake a = BrainTestStubs.rawFake(UUID.randomUUID());
        EntityPlayerMPFake b = BrainTestStubs.rawFake(UUID.randomUUID());
        BrainTeams.join(a.getUUID(), "red");
        BrainTeams.join(b.getUUID(), "blue");
        assertFalse(BrainTeams.isTeamFriendly(a, b), "异队是合法敌方");
    }

    @Test
    void unteamedIsNeverFriendly() {
        EntityPlayerMPFake a = BrainTestStubs.rawFake(UUID.randomUUID());
        EntityPlayerMPFake b = BrainTestStubs.rawFake(UUID.randomUUID());
        BrainTeams.join(a.getUUID(), "red");
        assertFalse(BrainTeams.isTeamFriendly(a, b), "对方无队 → 交战合法");
        assertFalse(BrainTeams.isTeamFriendly(b, a));
        // 两个都没入队 ≠ 同队
        assertFalse(BrainTeams.isTeamFriendly(b, BrainTestStubs.rawFake(UUID.randomUUID())),
                "双方无队不算友军");
    }

    @Test
    void realPlayersNeverCarryTeam() {
        // 真人（ServerPlayer 桩，非 EntityPlayerMPFake）即使 UUID 命中队伍表也不参战
        EntityPlayerMPFake fake = BrainTestStubs.rawFake(UUID.randomUUID());
        BrainTestStubs.TargetPlayer real = BrainTestStubs.TargetPlayer.create();
        BrainTeams.join(fake.getUUID(), "red");
        BrainTeams.join(real.getUUID(), "red");
        assertFalse(BrainTeams.isTeamFriendly(fake, real), "真人按原有生物行为处理，不获得队伍身份");
        assertFalse(BrainTeams.isTeamFriendly(real, fake));
        assertFalse(BrainTeams.isFakeCombatant(real));
        assertTrue(BrainTeams.isFakeCombatant(fake));
    }

    @Test
    void offIsReservedKeyword() {
        assertFalse(BrainTeams.isValidName("off"), "off 为退出关键字");
        assertFalse(BrainTeams.isValidName("OFF"), "保留字大小写不敏感");
        assertFalse(BrainTeams.isValidName(""));
        assertFalse(BrainTeams.isValidName(null));
        assertTrue(BrainTeams.isValidName("red"));
        assertTrue(BrainTeams.isValidName("红军"), "队名支持中文");
        assertTrue(BrainTeams.isValidName("Red Team"), "队名支持英文与空格");
    }

    @Test
    void joinLeaveAndKnownTeams() {
        UUID id = UUID.randomUUID();
        assertNull(BrainTeams.teamOf(id));
        assertFalse(BrainTeams.leave(id), "未入队退出返回 false");
        BrainTeams.join(id, "red");
        assertEquals("red", BrainTeams.teamOf(id));
        BrainTeams.join(id, "blue"); // 更换
        assertEquals("blue", BrainTeams.teamOf(id));
        assertTrue(BrainTeams.knownTeams().contains("blue"));
        assertTrue(BrainTeams.leave(id));
        assertNull(BrainTeams.teamOf(id));
    }

    @Test
    void teamChangeRevokesFriendlyTargets() throws Exception {
        openGateForStubs();
        BrainTestStubs.FakePlayer sessionOwner = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID());
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(sessionOwner);
        EntityPlayerMPFake mate = BrainTestStubs.rawFake(UUID.randomUUID());

        BrainTeams.join(sessionOwner.getUUID(), "red");
        BrainTeams.join(mate.getUUID(), "red");
        // 假设脑内锁定了队友（改队前是异队交战合法目标）
        sessionOwner.setTarget(mate);
        assertTrue(brain.revokeTeamTarget(), "同队目标应被撤销");
        assertNull(sessionOwner.getTarget(), "撤销后目标为空");

        // 异队目标不受影响
        BrainTeams.join(mate.getUUID(), "blue");
        sessionOwner.setTarget(mate);
        assertFalse(brain.revokeTeamTarget(), "异队目标保留");
        assertTrue(sessionOwner.getTarget() == mate, "目标保留原引用（Entity.equals 依赖实体 ID，桩用引用比较）");
    }

    @Test
    void teamSurvivesAcrossSessionsByKeyedUuid() {
        // 队伍按 UUID 记录、与会话无关：换脑/brain off/下线重上（同 UUID）均保留
        UUID id = UUID.randomUUID();
        BrainTeams.join(id, "red");
        assertEquals("red", BrainTeams.teamOf(id), "队伍与会话表独立，重挂脑不清除");
        assertTrue(BrainTeams.leave(id), "退出前确有队伍");
        assertNull(BrainTeams.teamOf(id));
    }

    @Test
    void joinWiresRevokeThroughBrainManager() throws Exception {
        openGateForStubs();
        // 战斗中改队场景：先异队互为合法目标，一方加入对方队伍 → 会话目标立即撤销
        BrainTestStubs.FakePlayer self = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID());
        BrainTestStubs.ScriptedController brain = new BrainTestStubs.ScriptedController(self);
        EntityPlayerMPFake mate = BrainTestStubs.rawFake(UUID.randomUUID());
        BrainTeams.join(self.getUUID(), "red");
        BrainTeams.join(mate.getUUID(), "blue");
        self.setTarget(mate);
        var field = BrainManager.class.getDeclaredField("BRAINS");
        field.setAccessible(true);
        ((java.util.Map<UUID, Object>) field.get(null)).put(self.getUUID(), brain);
        try {
            BrainTeams.join(mate.getUUID(), "red"); // mate 改入 red → 撤销 self 的目标
            assertNull(self.getTarget(), "改同队后 BrainManager 应撤销既有非法目标");
        } finally {
            ((java.util.Map<UUID, Object>) field.get(null)).clear();
        }
    }

    @Test
    void serverStoppingClearsTeams() {
        BrainTeams.init();
        UUID id = UUID.randomUUID();
        BrainTeams.join(id, "red");
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING
                .invoker().onServerStopping(null);
        assertNull(BrainTeams.teamOf(id), "服务器停止清空运行期队伍（不持久化）");
    }
}
