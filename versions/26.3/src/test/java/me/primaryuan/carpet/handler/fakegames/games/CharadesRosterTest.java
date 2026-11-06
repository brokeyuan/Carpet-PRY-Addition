package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.MemberState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 你演我猜表演名单只包含 ACTIVE 成员，退出者不会被重新加入。 */
class CharadesRosterTest {

    @Test
    void leftAndEliminatedMembersAreNotPerformers() {
        UUID active = UUID.randomUUID();
        UUID left = UUID.randomUUID();
        UUID eliminated = UUID.randomUUID();
        GameMember a = new GameMember(active);
        GameMember l = new GameMember(left);
        l.state = MemberState.LEFT;
        GameMember e = new GameMember(eliminated);
        e.state = MemberState.ELIMINATED;

        List<UUID> performers = CharadesGame.activePerformers(List.of(a, l, e));

        assertEquals(List.of(active), performers);
    }

    @Test
    void rejoinedActiveMemberAppearsOnce() {
        UUID id = UUID.randomUUID();
        GameMember member = new GameMember(id);
        member.state = MemberState.LEFT;
        member.state = MemberState.ACTIVE;

        assertEquals(List.of(id), CharadesGame.activePerformers(List.of(member)));
    }

    @Test
    void orderFollowsMemberOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(List.of(b, a),
                CharadesGame.activePerformers(List.of(new GameMember(b), new GameMember(a))));
    }
}
