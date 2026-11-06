package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挂载失败回滚：装配（onAttach）或队伍应用（applyTeam）抛异常时，不得留下
 * 会话、keep、监听器/目标或本会话临时队；手动控制随会话摘除立即恢复。
 */
class BrainAttachRollbackTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @AfterEach
    void tearDown() throws Exception {
        BrainManager.detachAll();
        brainMap().clear();
        keepMap().clear();
        teamStates().clear();
    }

    @Test
    void assemblyFailureRollsBackSessionKeepAndDetachHook() throws Exception {
        UUID id = UUID.randomUUID();
        BrainTestStubs.FakePlayer fake = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        FailingController brain = new FailingController(fake);

        assertThrows(IllegalStateException.class,
                () -> BrainManager.installSession(fake, brain, "zombie", null, true),
                "装配异常必须继续上抛，不能伪装成功");

        assertFalse(brainMap().containsKey(id), "失败后无接管会话");
        assertFalse(keepMap().containsKey(id), "失败后不保留本次 keep");
        assertTrue(brain.detached, "回滚调用 onDetach 释放监听器/目标");
        assertTrue(brain.levelEmpty(), "失败后无临时队残留");
    }

    @Test
    void teamApplicationFailurePreservesOriginalTeamAndRemovesTemporaryTeam() throws Exception {
        UUID id = UUID.randomUUID();
        var level = BrainTestStubs.stubWorld();
        var server = level.getServer();
        FailingTeamScoreboard scoreboard = new FailingTeamScoreboard(server);
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        BrainTestStubs.putField(server, MinecraftServer.class, "scoreboard", scoreboard);

        BrainTestStubs.FakePlayer fake = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(level)
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        scoreboard.addPlayerToTeam(fake.getScoreboardName(), original);
        ZombieBrain brain = new ZombieBrain(fake);

        assertThrows(IllegalStateException.class,
                () -> BrainManager.installSession(fake, brain, "zombie", null, false));

        assertEquals(original, scoreboard.getPlayersTeam(fake.getScoreboardName()),
                "队伍应用失败后恢复挂载前归属");
        assertFalse(scoreboard.getPlayerTeams().stream()
                        .anyMatch(t -> t.getName().startsWith("pry_brain_")),
                "部分创建的临时队必须清理");
        assertFalse(brainMap().containsKey(id), "失败后无接管会话");
        assertNull(teamStates().get(id), "失败后无队伍恢复记录");
    }

    /** onAttach 注入失败；onDetach 记录是否被回滚调用 */
    static final class FailingController extends BrainTestStubs.ScriptedController {
        boolean detached;

        FailingController(BrainTestStubs.FakePlayer player) {
            super(player);
        }

        @Override
        public void onAttach() {
            throw new IllegalStateException("assemble failed");
        }

        @Override
        public void onDetach() {
            this.detached = true;
            super.onDetach();
        }

        boolean levelEmpty() {
            return this.player.level().getServer().getScoreboard().getPlayerTeams().stream()
                    .noneMatch(t -> t.getName().startsWith("pry_brain_"));
        }
    }

    /** 只对临时队注册失败：模拟 applyTeam 在部分建队后失败 */
    static final class FailingTeamScoreboard extends ServerScoreboard {
        FailingTeamScoreboard(MinecraftServer server) {
            super(server);
        }

        @Override
        public boolean addPlayerToTeam(String name, PlayerTeam team) {
            if (team.getName().startsWith("pry_brain_")) {
                throw new IllegalStateException("team apply failed");
            }
            return super.addPlayerToTeam(name, team);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, PlayerBrainController> brainMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("BRAINS");
        field.setAccessible(true);
        return (Map<UUID, PlayerBrainController>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> keepMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("KEEP");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static Map<UUID, ?> teamStates() throws Exception {
        Field field = BrainManager.class.getDeclaredField("TEAM_STATES");
        field.setAccessible(true);
        return (Map<UUID, ?>) field.get(null);
    }
}
