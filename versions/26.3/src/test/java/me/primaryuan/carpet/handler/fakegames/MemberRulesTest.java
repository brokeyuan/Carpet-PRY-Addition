package me.primaryuan.carpet.handler.fakegames;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 成员资格与最后存活者规则。 */
class MemberRulesTest {

    @Test
    void joinRejectionOrderAndResult() {
        assertNull(MemberRules.joinRejection(true, true, false, true, true));
        assertEquals("join_too_late", MemberRules.joinRejection(false, true, false, true, true));
        assertEquals("join_dead", MemberRules.joinRejection(true, false, false, true, true));
        assertEquals("join_spectator", MemberRules.joinRejection(true, true, true, true, true));
        assertEquals("join_dimension", MemberRules.joinRejection(true, true, false, false, true));
        assertEquals("join_region", MemberRules.joinRejection(true, true, false, true, false));
    }

    @Test
    void soleActiveOnlyWhenExactlyOne() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        GameMember ma = new GameMember(a);
        GameMember mb = new GameMember(b);
        mb.state = MemberState.LEFT;
        assertEquals(a, MemberRules.soleActive(Map.of(a, ma, b, mb)));
        ma.state = MemberState.ELIMINATED;
        assertNull(MemberRules.soleActive(Map.of(a, ma, b, mb)));
    }

    @Test
    void activeCountIgnoresLeftAndEliminated() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        GameMember ma = new GameMember(a);
        GameMember mb = new GameMember(b);
        GameMember mc = new GameMember(c);
        mb.state = MemberState.ELIMINATED;
        mc.state = MemberState.LEFT;
        assertEquals(1, MemberRules.activeCount(java.util.List.of(ma, mb, mc)));
    }
}
