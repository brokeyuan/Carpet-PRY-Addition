package me.primaryuan.carpet.replay.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 公开/私有访问、管理权与观看实例控制分离 */
class ReplayPermissionsTest {

    @Test
    void publicIsVisibleToEveryoneButOnlyOwnerManages() {
        assertTrue(ReplayPermissions.canView(ReplayAccess.PUBLIC, "owner", "viewer", false));
        assertFalse(ReplayPermissions.canManage("owner", "viewer", false));
        assertTrue(ReplayPermissions.canManage("owner", "owner", false));
    }

    @Test
    void privateIsOwnerAndAdminOnly() {
        assertFalse(ReplayPermissions.canView(ReplayAccess.PRIVATE, "owner", "viewer", false));
        assertTrue(ReplayPermissions.canView(ReplayAccess.PRIVATE, "owner", "owner", false));
        assertTrue(ReplayPermissions.canView(ReplayAccess.PRIVATE, "owner", "admin", true));
        assertTrue(ReplayPermissions.canManage("owner", "admin", true));
    }

    @Test
    void participantDoesNotGainManageBecauseRecorded() {
        // 权限函数只认 owner/admin，参与者身份不参与管理判断
        assertFalse(ReplayPermissions.canManage("owner", "participant", false));
        assertFalse(ReplayPermissions.canControl("viewer", "other-viewer", false));
    }

    @Test
    void playbackControlBelongsToViewerInstance() {
        assertTrue(ReplayPermissions.canControl("viewer", "viewer", false));
        assertFalse(ReplayPermissions.canControl("viewer", "other", false));
        assertTrue(ReplayPermissions.canControl("viewer", "admin", true));
    }
}
