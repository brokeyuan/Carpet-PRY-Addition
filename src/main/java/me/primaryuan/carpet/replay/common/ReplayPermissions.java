package me.primaryuan.carpet.replay.common;

import net.minecraft.server.level.ServerPlayer;

/**
 * 录像权限纯逻辑（可单测）：访问、管理、观看实例控制三者分离。
 * 公开录像对所有具备功能权限的玩家可访问；私有录像仅创作者与管理员。
 * 被录制者不会因参与而取得整份录像的管理权。
 */
public final class ReplayPermissions
{
    private ReplayPermissions() {}

    public static boolean canView(ReplayAccess access, String ownerUuid, String viewerUuid, boolean admin)
    {
        if (admin)
        {
            return true;
        }
        if (access == ReplayAccess.PUBLIC)
        {
            return true;
        }
        return ownerUuid != null && ownerUuid.equals(viewerUuid);
    }

    public static boolean canManage(String ownerUuid, String playerUuid, boolean admin)
    {
        return admin || (ownerUuid != null && ownerUuid.equals(playerUuid));
    }

    /** 观看实例只能由发起观看的玩家本人或管理员控制 */
    public static boolean canControl(String instanceViewerUuid, String playerUuid, boolean admin)
    {
        return admin || (instanceViewerUuid != null && instanceViewerUuid.equals(playerUuid));
    }

    public static boolean isAdmin(ServerPlayer player)
    {
        //#if MC >= 12111
        return player != null && net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        //#else
        //$$ return player != null && player.hasPermissions(2);
        //#endif
    }
}
