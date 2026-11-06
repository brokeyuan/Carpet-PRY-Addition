package me.primaryuan.carpet.handler.fakegames;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 同批淘汰：先全部改状态，再按输入顺序回调。 */
class EliminationBatchTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void marksAllBeforeInvokingCallbacks() {
        GameMember a = new GameMember(A);
        GameMember b = new GameMember(B);
        Map<UUID, GameMember> members = Map.of(A, a, B, b);
        StringBuilder statesAtCallback = new StringBuilder();
        List<UUID> callbackOrder = new java.util.ArrayList<>();

        List<GameMember> result = EliminationBatch.apply(members, List.of(A, B),
                "reason", member -> {
                    statesAtCallback.append(member.id.equals(A) ? a.state : b.state).append(':');
                    callbackOrder.add(member.id);
                });

        assertEquals(List.of(a, b), result);
        assertEquals("ELIMINATED:ELIMINATED:", statesAtCallback.toString());
        assertEquals(List.of(A, B), callbackOrder);
    }

    @Test
    void ignoresDuplicatesAndNonActiveMembers() {
        GameMember a = new GameMember(A);
        GameMember b = new GameMember(B);
        b.state = MemberState.LEFT;
        Map<UUID, GameMember> members = Map.of(A, a, B, b);
        int[] callbacks = {0};

        List<GameMember> result = EliminationBatch.apply(members,
                List.of(A, A, B, UUID.randomUUID()), "reason", member -> callbacks[0]++);

        assertEquals(List.of(a), result);
        assertEquals(1, callbacks[0]);
        assertEquals(MemberState.ELIMINATED, a.state);
        assertEquals(MemberState.LEFT, b.state);
    }

    @Test
    void listenerMayMutateSourceMapDuringCallbacks() {
        GameMember a = new GameMember(A);
        GameMember b = new GameMember(B);
        Map<UUID, GameMember> members = new LinkedHashMap<>();
        members.put(A, a);
        members.put(B, b);

        // 模拟玩法 onPlayerLeave 在回调中删除自己的冻结表条目
        assertDoesNotThrow(() -> EliminationBatch.apply(members, List.of(A, B),
                "reason", member -> members.remove(member.id)));
        assertTrue(members.isEmpty());
    }

    @Test
    void allViolatorsEndWithNoActiveMember() {
        GameMember a = new GameMember(A);
        GameMember b = new GameMember(B);
        Map<UUID, GameMember> members = Map.of(A, a, B, b);
        EliminationBatch.apply(members, List.of(A, B), "reason", member -> { });
        assertTrue(MemberRules.activeCount(members.values()) == 0);
    }
}
