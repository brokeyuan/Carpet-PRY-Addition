package me.primaryuan.carpet.handler.playerHead;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 铁砧换肤状态机：改名、超时与失败后迟到结果必须失效。 */
class HeadRenameSessionTest {

    private static HeadSkin skin(String name) {
        return new HeadSkin(UUID.randomUUID(), name, "value-" + name, null);
    }

    @Test void lateResultAfterRenameIsRejected() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        session.beginQuery();
        int alphaRevision = session.revision();

        session.revise("Beta");
        session.beginQuery();
        int betaRevision = session.revision();

        assertFalse(session.complete(alphaRevision, skin("Alpha")), "旧名称结果不得覆盖新输入");
        assertFalse(session.ready());
        assertTrue(session.blocksPickup(), "未就绪时禁止领取");
        assertTrue(session.complete(betaRevision, skin("Beta")));
        assertTrue(session.ready());
        assertFalse(session.blocksPickup());
        assertEquals("Beta", session.skin().name());
    }

    @Test void failureRejectsLateSuccess() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        session.beginQuery();
        assertTrue(session.fail(session.revision()));
        assertFalse(session.complete(session.revision(), skin("Alpha")));
        assertTrue(session.blocksPickup());
    }

    @Test void resultBeforeQueryStartIsRejected() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        assertFalse(session.complete(session.revision(), skin("Alpha")), "防抖阶段不得接受结果");
    }

    @Test void staleFailureDoesNotAffectCurrentRevision() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        session.beginQuery();
        int alphaRevision = session.revision();
        session.revise("Beta");
        session.beginQuery();
        assertFalse(session.fail(alphaRevision));
        assertTrue(session.complete(session.revision(), skin("Beta")));
        assertTrue(session.ready());
    }

    @Test void reuseSkipsQuery() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        session.reuse(skin("Alpha"));
        assertTrue(session.ready());
        assertFalse(session.blocksPickup());
    }

    @Test void skinWithoutTextureIsNotAccepted() {
        HeadRenameSession session = new HeadRenameSession();
        session.revise("Alpha");
        session.beginQuery();
        assertFalse(session.complete(session.revision(), new HeadSkin(null, "Alpha", null, null)));
        assertFalse(session.ready());
    }
}
