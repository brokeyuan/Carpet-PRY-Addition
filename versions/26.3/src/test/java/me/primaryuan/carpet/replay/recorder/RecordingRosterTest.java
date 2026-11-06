package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 准备页/邀请/录制中迟加入/退出的纯逻辑边界 */
class RecordingRosterTest {

    private static RecordingRoster roster() {
        return new RecordingRoster(UUID.randomUUID(), "owner", "Owner", "测试");
    }

    @Test
    void defaultRosterOnlySelectsSelf() {
        RecordingRoster r = roster();
        assertTrue(r.selfSelected());
        assertTrue(r.hasJoinedParticipant());
        assertEquals(1, r.joinedCount());
        assertFalse(r.settingsLocked(), "未邀请前可以改设置");
    }

    @Test
    void duplicatePrepareReusesTaskId() {
        UUID id = UUID.randomUUID();
        RecordingRoster a = new RecordingRoster(id, "owner", "Owner", "测试");
        RecordingRoster b = new RecordingRoster(id, "owner", "Owner", "测试");
        assertEquals(a.taskId, b.taskId, "准备任务身份用稳定 UUID，不用名称");
    }

    @Test
    void startClearsPendingInvitesAndOnlyJoinsAccepted() {
        RecordingRoster r = roster();
        assertEquals(RecordingRoster.InviteResult.OK, r.invite("u1", "A", false));
        UUID token = r.get("u1").token;
        assertNotNull(token);
        assertEquals(RecordingRoster.InviteResult.OK, r.accept("u1", token, false));
        assertEquals(RecordingRoster.State.ACCEPTED, r.get("u1").state);
        assertEquals(RecordingRoster.InviteResult.OK, r.invite("u2", "B", false));
        // 开始：等待回复的 u2 失效，已同意的 u1 加入
        assertEquals(RecordingRoster.InviteResult.OK, r.start());
        assertNull(r.get("u2"), "准备阶段未回复的邀请在开始时失效");
        assertEquals(RecordingRoster.State.JOINED, r.get("u1").state);
        assertEquals(2, r.joinedCount());
    }

    @Test
    void onlySelfCanStartWithSelf() {
        RecordingRoster r = roster();
        r.invite("u1", "A", false); // 等待回复不阻止开始
        assertEquals(RecordingRoster.InviteResult.OK, r.start());
        assertEquals(1, r.joinedCount());
    }

    @Test
    void noParticipantCannotStart() {
        RecordingRoster r = roster();
        r.removeSelf();
        assertEquals(RecordingRoster.InviteResult.NO_SLOT, r.start());
    }

    @Test
    void reInviteReplacesTokenAndOldButtonFails() {
        RecordingRoster r = roster();
        r.invite("u1", "A", false);
        UUID first = r.get("u1").token;
        r.invite("u1", "A", false);
        UUID second = r.get("u1").token;
        assertNotEquals(first, second, "重新邀请必须换发独立令牌");
        assertEquals(RecordingRoster.InviteResult.OLD_TOKEN, r.accept("u1", first, false));
        assertEquals(RecordingRoster.InviteResult.OK, r.accept("u1", second, false));
    }

    @Test
    void inviteTokenBoundToTaskAndInvitee() {
        RecordingRoster r = roster();
        r.invite("u1", "A", false);
        UUID token = r.get("u1").token;
        assertEquals(RecordingRoster.InviteResult.NOT_FOUND, r.accept("u2", token, false),
                "令牌不能批准其他人的邀请");
        assertEquals(RecordingRoster.InviteResult.NOT_FOUND, r.deny("u2", token));
    }

    @Test
    void adminDirectAddSkipsConsentAndCannotLeave() {
        RecordingRoster r = roster();
        assertEquals(RecordingRoster.InviteResult.OK, r.invite("u1", "A", true));
        assertEquals(RecordingRoster.State.JOINED, r.get("u1").state);
        assertEquals(ReplayParticipant.JoinMethod.ADMIN, r.get("u1").joinMethod);
        assertEquals(RecordingRoster.InviteResult.NOT_ALLOWED, r.leave("u1"),
                "管理员直接添加者不能主动退出");
        assertEquals(RecordingRoster.InviteResult.NOT_ALLOWED, r.leave("owner"),
                "创作者也不能通过普通退出入口退出");
    }

    @Test
    void invitedParticipantCanLeaveAndRejoinOnlyByNewInvite() {
        RecordingRoster r = roster();
        r.invite("u1", "A", false);
        r.accept("u1", r.get("u1").token, false);
        r.start();
        assertEquals(RecordingRoster.InviteResult.OK, r.leave("u1"));
        assertEquals(RecordingRoster.State.LEFT, r.get("u1").state);
        assertEquals(RecordingRoster.InviteResult.OK, r.invite("u1", "A", false),
                "退出后再次参与须重新邀请");
        assertEquals(RecordingRoster.State.INVITED, r.get("u1").state);
    }

    @Test
    void inviteDuringRecordingImmediatelyJoinsAfterAccept() {
        RecordingRoster r = roster();
        r.start();
        r.invite("u1", "A", false);
        assertEquals(RecordingRoster.InviteResult.OK, r.accept("u1", r.get("u1").token, true));
        assertEquals(RecordingRoster.State.JOINED, r.get("u1").state);
        assertEquals(2, r.joinedCount());
    }

    @Test
    void settingsLockAfterInviteButNotSoloStart() {
        RecordingRoster r = roster();
        r.setDurationTicks(1200);
        r.setAccess(ReplayAccess.PRIVATE);
        assertEquals(1200, r.durationTicks());
        assertEquals(ReplayAccess.PRIVATE, r.access());
        r.start();
        assertFalse(r.settingsLocked(), "只录自己时未确认任何邀请，无需锁定");
        r.setDurationTicks(2400);
        assertEquals(2400, r.durationTicks());

        RecordingRoster other = roster();
        other.invite("u1", "A", false);
        assertTrue(other.settingsLocked(), "邀请发出后锁定同意范围");
    }

    @Test
    void maxParticipantsEnforced() {
        RecordingRoster r = roster();
        for (int i = 0; i < me.primaryuan.carpet.replay.common.ReplayLimits.MAX_PARTICIPANTS - 1; i++) {
            assertEquals(RecordingRoster.InviteResult.OK, r.invite("u" + i, "P" + i, true));
        }
        assertEquals(RecordingRoster.InviteResult.NO_SLOT,
                r.invite("overflow", "Overflow", true));
    }
}
