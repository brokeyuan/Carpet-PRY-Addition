package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.handler.fakegames.games.HotPotatoGame;
import me.primaryuan.carpet.handler.fakegames.games.TagGame;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话级行为：同批淘汰顺序、离线成员回调、加入资格与等待期清理。 */
class GameSessionEliminationTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void batchEliminationUpdatesAllStatesBeforeCallbacks() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;

        session.eliminateBatch(List.of(fixture.players.get(1), fixture.players.get(2)),
                "carpetprimaryuan.fakegames.reason.hazard", "grass_block");

        assertEquals(MemberState.ELIMINATED, fixture.member(session, 1).state);
        assertEquals(MemberState.ELIMINATED, fixture.member(session, 2).state);
        assertEquals(MemberState.ACTIVE, fixture.member(session, 0).state);
        assertEquals(1, logic.endCount);
        assertEquals(2, logic.leaves.size());
        for (Leave leave : logic.leaves) {
            assertEquals(1, leave.activeAtCallback);
            assertEquals(2, leave.eliminatedStatesAtCallback);
        }
        assertEquals("end", logic.events.get(logic.events.size() - 1));
    }

    @Test
    void allViolatorsProduceDrawNotATraversalOrderWinner() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;

        session.eliminateBatch(List.of(fixture.players.get(0), fixture.players.get(1),
                fixture.players.get(2)), "carpetprimaryuan.fakegames.reason.hazard");

        assertEquals(0, session.activeCount());
        assertEquals(3, logic.leaves.size());
        for (Leave leave : logic.leaves) {
            assertEquals(0, leave.activeAtCallback);
            assertEquals(3, leave.eliminatedStatesAtCallback);
        }
        assertEquals(1, logic.endCount);
        assertEquals("end", logic.events.get(logic.events.size() - 1));
    }

    @Test
    void singleEliminationKeepsGameRunning() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;

        session.eliminate(fixture.players.get(1), "carpetprimaryuan.fakegames.reason.hazard");

        assertEquals(SessionPhase.RUNNING, session.phase);
        assertEquals(2, session.activeCount());
        assertEquals(1, logic.leaves.size());
        assertEquals(0, logic.endCount);
    }

    @Test
    void offlineMemberIsRemovedAndCallbackGetsNullPlayer() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;
        fixture.online.remove(fixture.players.get(1).getUUID());

        session.removePlayer(fixture.players.get(1), "carpetprimaryuan.fakegames.left_death");

        assertEquals(MemberState.LEFT, fixture.member(session, 1).state);
        assertEquals(1, logic.leaves.size());
        assertTrue(logic.leaves.get(0).playerWasNull);
        assertEquals(SessionPhase.RUNNING, session.phase);
    }

    @Test
    void joinRejectionChecksAliveSpectatorDimensionAndRegion() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 2);
        GameSession session = fixture.session(new RecordingLogic());
        StubPlayer player = fixture.players.get(1);

        player.dead = true;
        assertEquals("join_dead", session.joinRejection(player));
        player.dead = false;
        player.spectator = true;
        assertEquals("join_spectator", session.joinRejection(player));
        player.spectator = false;
        player.stubLevel = fixture.otherLevel;
        assertEquals("join_dimension", session.joinRejection(player));
        player.stubLevel = fixture.level;
        setPosition(player, 1000, 64, 0);
        assertEquals("join_region", session.joinRejection(player));
        setPosition(player, 0, 64, 0);
        assertNull(session.joinRejection(player));
    }

    @Test
    @SuppressWarnings("unchecked")
    void frozenMovementBatchDoesNotMutateIterationTable() throws Exception {
        Fixture fixture = new Fixture(GameType.TAG, 5);
        TagGame tag = new TagGame();
        GameSession session = fixture.session(tag);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;

        Field ghostsField = TagGame.class.getDeclaredField("ghosts");
        ghostsField.setAccessible(true);
        ((List<UUID>) ghostsField.get(tag)).add(fixture.players.get(0).getUUID());
        Field firstGhostField = TagGame.class.getDeclaredField("firstGhost");
        firstGhostField.setAccessible(true);
        firstGhostField.set(tag, fixture.players.get(0).getUUID());

        Field frozenField = TagGame.class.getDeclaredField("frozen");
        frozenField.setAccessible(true);
        Map<UUID, Vec3> frozen = (Map<UUID, Vec3>) frozenField.get(tag);
        frozen.put(fixture.players.get(1).getUUID(), new Vec3(1, 64, 0));
        frozen.put(fixture.players.get(2).getUUID(), new Vec3(2, 64, 0));
        setPosition(fixture.players.get(1), 10, 64, 0);
        setPosition(fixture.players.get(2), 10, 64, 0);

        Method check = TagGame.class.getDeclaredMethod("checkFrozenMovement", GameSession.class);
        check.setAccessible(true);
        assertDoesNotThrow(() -> check.invoke(tag, session));
        assertEquals(MemberState.ELIMINATED, fixture.member(session, 1).state);
        assertEquals(MemberState.ELIMINATED, fixture.member(session, 2).state);
        assertTrue(frozen.isEmpty());
    }

    @Test
    void hotPotatoPassesOnlyByEffectiveAttackAndBlocksImmediateBackPass() throws Exception {
        Fixture fixture = new Fixture(GameType.HOT_POTATO, 3);
        HotPotatoGame potato = new HotPotatoGame();
        GameSession session = fixture.session(potato);
        fixture.joinAll(session);
        session.phase = SessionPhase.RUNNING;
        setPosition(fixture.players.get(0), 0, 64, 0);
        setPosition(fixture.players.get(1), 1, 64, 0);
        setPosition(fixture.players.get(2), 10, 64, 0);

        Field holderField = HotPotatoGame.class.getDeclaredField("holder");
        holderField.setAccessible(true);
        holderField.set(potato, fixture.players.get(0).getUUID());
        Field lockField = HotPotatoGame.class.getDeclaredField("passLockTicks");
        lockField.setAccessible(true);
        lockField.setInt(potato, 0);

        assertTrue(potato.onAttack(session, fixture.players.get(0), fixture.players.get(1)));
        assertEquals(fixture.players.get(1).getUUID(), holderField.get(potato));

        // 刚传出的上一名持有者不能被立即传回
        assertTrue(potato.onAttack(session, fixture.players.get(1), fixture.players.get(0)));
        assertEquals(fixture.players.get(1).getUUID(), holderField.get(potato));

        // 其他合法目标仍然可以接收
        assertTrue(potato.onAttack(session, fixture.players.get(1), fixture.players.get(2)));
        assertEquals(fixture.players.get(2).getUUID(), holderField.get(potato));
    }

    @Test
    void beginRunningRevalidatesAndCancelsWhenBelowMinimum() throws Exception {
        Fixture fixture = new Fixture(GameType.CHARADES, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        fixture.online.remove(fixture.players.get(1).getUUID());
        session.phase = SessionPhase.COUNTDOWN;
        session.phaseTicks = GameSession.COUNTDOWN_TICKS;

        session.tick(null);

        assertEquals(SessionPhase.WAITING, session.phase);
        assertEquals(2, session.activeCount());
        assertEquals(MemberState.LEFT, fixture.member(session, 1).state);
    }

    @Test
    void waitingTickRemovesOfflineActiveMemberById() throws Exception {
        Fixture fixture = new Fixture(GameType.GRASS_IS_LAVA, 3);
        RecordingLogic logic = new RecordingLogic();
        GameSession session = fixture.session(logic);
        fixture.joinAll(session);
        fixture.online.remove(fixture.players.get(2).getUUID());

        for (int i = 0; i < 10; i++) {
            session.tick(null);
        }

        assertEquals(MemberState.LEFT, fixture.member(session, 2).state);
        assertEquals(2, session.activeCount());
        assertEquals(1, logic.leaves.size());
        assertTrue(logic.leaves.get(0).playerWasNull);
    }

    private record Leave(UUID id, int activeAtCallback, long eliminatedStatesAtCallback,
                         boolean playerWasNull) {}

    private static final class RecordingLogic implements GameLogic {
        final List<String> events = new ArrayList<>();
        final List<Leave> leaves = new ArrayList<>();
        int endCount;

        @Override
        public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
            long eliminatedStates = session.allMembers().stream()
                    .filter(member -> member.state == MemberState.ELIMINATED)
                    .count();
            leaves.add(new Leave(playerId, session.activeCount(), eliminatedStates, player == null));
            events.add("leave");
        }

        @Override
        public void onEnd(GameSession session) {
            endCount++;
            events.add("end");
        }
    }

    private static final class Fixture {
        final AirLevel level = unsafe(AirLevel.class);
        final AirLevel otherLevel = unsafe(AirLevel.class);
        final GameType type;
        final List<StubPlayer> players = new ArrayList<>();
        final Map<UUID, ServerPlayer> online = new LinkedHashMap<>();

        Fixture(GameType type, int count) throws Exception {
            this.type = type;
            for (int i = 0; i < count; i++) {
                StubPlayer player = unsafe(StubPlayer.class);
                player.id = UUID.randomUUID();
                player.stubLevel = level;
                setPosition(player, i, 64, 0);
                players.add(player);
                online.put(player.id, player);
            }
        }

        GameSession session(GameLogic logic) {
            return new GameSession(type, players.get(0), logic,
                    id -> online.get(id));
        }

        void joinAll(GameSession session) {
            for (StubPlayer player : players) {
                assertTrue(session.addPlayer(player));
            }
        }

        GameMember member(GameSession session, int index) {
            return session.member(players.get(index).id);
        }
    }

    private static final class StubPlayer extends ServerPlayer {
        UUID id;
        ServerLevel stubLevel;
        boolean dead;
        boolean spectator;

        @SuppressWarnings("unused")
        private StubPlayer() { super(null, null, null, null); }

        @Override public UUID getUUID() { return id; }
        @Override public ServerLevel level() { return stubLevel; }
        @Override public BlockPos blockPosition() { return BlockPos.ZERO; }
        @Override public boolean isAlive() { return !dead; }
        @Override public boolean isDeadOrDying() { return dead; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean onGround() { return true; }
        @Override public Component getName() { return Component.literal("p-" + id.toString().substring(0, 4)); }
        @Override public void sendSystemMessage(Component message) { }
    }

    @SuppressWarnings("unused")
    private static final class AirLevel extends ServerLevel {
        private AirLevel() { super(null, null, null, null, null, null, false, 0L, null, false); }
        @Override public void broadcastEntityEvent(net.minecraft.world.entity.Entity entity, byte event) { }
    }

    private static void setPosition(ServerPlayer player, double x, double y, double z) throws Exception {
        Field field = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        field.setAccessible(true);
        field.set(player, new Vec3(x, y, z));
    }

    @SuppressWarnings("unchecked")
    private static <T> T unsafe(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        return (T) unsafe.allocateInstance(type);
    }
}
