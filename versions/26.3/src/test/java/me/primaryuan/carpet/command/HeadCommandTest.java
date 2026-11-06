package me.primaryuan.carpet.command;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** /head 可见性：规则与权限缺一不可，控制台不因非玩家来源绕过权限。 */
class HeadCommandTest {
    @BeforeAll static void bootstrap() { TestRegistries.bootstrap(); }

    @AfterEach void restore() {
        CarpetPrimaryuanSettings.universalHead = false;
        CarpetPrimaryuanSettings.memorialHead = false;
    }

    @Test void hiddenWhenRuleDisabled() {
        assertFalse(HeadCommand.rootVisible(source(PermissionSet.ALL_PERMISSIONS)));
        assertFalse(HeadCommand.universalVisible(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test void visibleToAdminWhenRuleEnabled() {
        CarpetPrimaryuanSettings.universalHead = true;
        assertTrue(HeadCommand.rootVisible(source(PermissionSet.ALL_PERMISSIONS)));
        assertTrue(HeadCommand.universalVisible(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test void hiddenFromNonAdmin() {
        CarpetPrimaryuanSettings.universalHead = true;
        assertFalse(HeadCommand.rootVisible(source(PermissionSet.NO_PERMISSIONS)));
        assertFalse(HeadCommand.universalVisible(source(PermissionSet.NO_PERMISSIONS)));
    }

    @Test void memorialVisibilityFollowsItsOwnRule() {
        CarpetPrimaryuanSettings.memorialHead = true;
        assertTrue(HeadCommand.rootVisible(source(PermissionSet.ALL_PERMISSIONS)),
                "规则开启时根命令可见");
        assertTrue(HeadCommand.memorialVisible(source(PermissionSet.ALL_PERMISSIONS)));
        assertFalse(HeadCommand.universalVisible(source(PermissionSet.ALL_PERMISSIONS)),
                "通用头颅未开启时其子命令仍隐藏");
        assertFalse(HeadCommand.memorialVisible(source(PermissionSet.NO_PERMISSIONS)),
                "非管理员不可见");
        CarpetPrimaryuanSettings.memorialHead = false;
        assertFalse(HeadCommand.rootVisible(source(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test void memorialValidationAcceptsCompleteArchive() {
        assertEquals(HeadCommand.Validation.OK,
                HeadCommand.validateMemorial("Notch", "纪念名称", "周年活动", "2026-11-07", "张三", "说明"));
    }

    @Test void memorialValidationRejectsBadFields() {
        assertEquals(HeadCommand.Validation.BAD_SOURCE,
                HeadCommand.validateMemorial("", "名", "活动", "2026-11-07", "张三", ""));
        assertEquals(HeadCommand.Validation.BAD_NAME,
                HeadCommand.validateMemorial("Notch", "", "活动", "2026-11-07", "张三", ""));
        assertEquals(HeadCommand.Validation.BAD_EVENT,
                HeadCommand.validateMemorial("Notch", "名", "", "2026-11-07", "张三", ""));
        assertEquals(HeadCommand.Validation.BAD_RECIPIENT,
                HeadCommand.validateMemorial("Notch", "名", "活动", "2026-11-07", "", ""));
        assertEquals(HeadCommand.Validation.BAD_NOTE,
                HeadCommand.validateMemorial("Notch", "名", "活动", "2026-11-07", "张三", "x".repeat(101)));
    }

    @Test void dateMustBeExplicitAndReal() {
        assertTrue(HeadCommand.validDate("2026-11-07"));
        assertTrue(HeadCommand.validDate("2024-02-29"), "闰日合法");
        assertFalse(HeadCommand.validDate("2023-02-29"), "非闰年拒绝");
        assertFalse(HeadCommand.validDate("2026-02-30"));
        assertFalse(HeadCommand.validDate("2026-1-1"), "必须两位月日");
        assertFalse(HeadCommand.validDate("2026/11/07"));
        assertEquals(HeadCommand.Validation.BAD_DATE,
                HeadCommand.validateMemorial("Notch", "名", "活动", "2026-02-30", "张三", ""));
    }

    @Test void greedyNoteLosesOneQuoteLayer() {
        assertEquals("感谢一路同行", HeadCommand.unquote("\"感谢一路同行\""));
        assertEquals("say \"hi\"", HeadCommand.unquote("\"say \\\"hi\\\"\""));
        assertEquals("plain note", HeadCommand.unquote("plain note"));
        assertEquals("\"unbalanced", HeadCommand.unquote("\"unbalanced"));
    }

    private static CommandSourceStack source(PermissionSet permissions) {
        CommandSource output = new CommandSource() {
            @Override public void sendSystemMessage(Component message) {}
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CommandSourceStack(output, Vec3.ZERO, Vec2.ZERO, null, permissions,
                (net.minecraft.server.MinecraftServer) null, null);
    }
}
