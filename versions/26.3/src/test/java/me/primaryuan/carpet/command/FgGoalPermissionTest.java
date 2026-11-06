package me.primaryuan.carpet.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /fg goal 权限：发起者或管理员，且必须在会话维度与区域内。 */
class FgGoalPermissionTest {

    @Test
    void hostInsideRegionAllowed() {
        assertTrue(FgCommand.canConfigureGoal(true, false, true));
    }

    @Test
    void adminInsideRegionAllowed() {
        assertTrue(FgCommand.canConfigureGoal(false, true, true));
    }

    @Test
    void nonHostMemberDenied() {
        assertFalse(FgCommand.canConfigureGoal(false, false, true));
    }

    @Test
    void outsideRegionDeniedEvenForHostOrAdmin() {
        assertFalse(FgCommand.canConfigureGoal(true, false, false));
        assertFalse(FgCommand.canConfigureGoal(false, true, false));
    }
}
