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

    @AfterEach void restore() { CarpetPrimaryuanSettings.universalHead = false; }

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
