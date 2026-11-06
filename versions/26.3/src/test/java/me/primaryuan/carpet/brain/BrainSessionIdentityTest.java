package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F01：同 UUID 的两个实例交错下线。旧实例的 detach 不得拆掉新实例的脑、
 * keep 与计分板队伍；只有绑定实例自己的 detach 才释放。
 */
class BrainSessionIdentityTest {

    private static Method applyTeam;

    @BeforeAll
    static void setUp() throws Exception {
        TestRegistries.bootstrap();
        applyTeam = BrainManager.class.getDeclaredMethod("applyTeam",
                PlayerBrainController.class,
                net.minecraft.server.level.ServerPlayer.class, String.class);
        applyTeam.setAccessible(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        brainMap().clear();
        keepMap().clear();
        originalTeamMap().clear();
    }

    @Test
    void staleInstanceDetachLeavesNewSessionIntact() throws Exception {
        UUID id = UUID.randomUUID();
        BrainTestStubs.FakePlayer oldInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());
        BrainTestStubs.FakePlayer newInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());
        BrainTestStubs.ScriptedController newBrain = new BrainTestStubs.ScriptedController(newInstance);

        brainMap().put(id, newBrain);
        keepMap().put(id, newKeepRecord("zombie", null));
        applyTeam.invoke(null, newBrain, newInstance, "zombie");
        Scoreboard scoreboard = newInstance.level().getServer().getScoreboard();
        PlayerTeam newTeam = scoreboard.getPlayersTeam(newInstance.getScoreboardName());
        assertNotNull(newTeam, "新实例挂脑队伍已建立");

        assertFalse(BrainManager.detach(oldInstance), "旧实例 detach 不得命中新会话");
        assertEquals(newBrain, brainMap().get(id), "新实例脑子仍在");
        assertTrue(keepMap().containsKey(id), "旧实例 detach 不得清新实例 keep");
        assertNotNull(scoreboard.getPlayersTeam(newInstance.getScoreboardName()), "新实例队伍不得被拆");
        assertEquals(newTeam, scoreboard.getPlayersTeam(newInstance.getScoreboardName()));

        assertTrue(BrainManager.detach(newInstance, false), "绑定实例自己可释放");
        assertFalse(brainMap().containsKey(id));
        assertTrue(keepMap().containsKey(id), "自动卸载不传 clearKeep 时保留 keep");
        assertNull(scoreboard.getPlayersTeam(newInstance.getScoreboardName()), "释放时移除本会话队伍");
    }

    @Test
    void staleInstanceDetachDoesNotClearNewKeepEvenWhenCommandPath() throws Exception {
        UUID id = UUID.randomUUID();
        BrainTestStubs.FakePlayer oldInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());
        BrainTestStubs.FakePlayer newInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());
        brainMap().put(id, new BrainTestStubs.ScriptedController(newInstance));
        keepMap().put(id, newKeepRecord("zombie", null));

        // 旧实例的“命令路径”也不能按 UUID 拆新会话；显式 off 的 keep 清理
        // 属于玩家身份级语义，允许执行，但绝不能动新会话本身。
        assertFalse(BrainManager.detach(oldInstance, true));
        assertNotNull(brainMap().get(id), "新实例脑子仍在");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, PlayerBrainController> brainMap() throws Exception {
        var field = BrainManager.class.getDeclaredField("BRAINS");
        field.setAccessible(true);
        return (java.util.Map<UUID, PlayerBrainController>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, Object> keepMap() throws Exception {
        var field = BrainManager.class.getDeclaredField("KEEP");
        field.setAccessible(true);
        return (java.util.Map<UUID, Object>) field.get(null);
    }

    private static java.util.Map<UUID, ?> originalTeamMap() throws Exception {
        var field = BrainManager.class.getDeclaredField("TEAM_STATES");
        field.setAccessible(true);
        return (java.util.Map<UUID, ?>) field.get(null);
    }

    private static Object newKeepRecord(String mode, UUID owner) throws Exception {
        var ctor = Class.forName("me.primaryuan.carpet.brain.BrainManager$KeepRecord")
                .getDeclaredConstructor(String.class, UUID.class);
        ctor.setAccessible(true);
        return ctor.newInstance(mode, owner);
    }
}
