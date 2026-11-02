package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.level.ServerPlayer;

/**
 * 分身实例存档判定：只有组主实例写同一份 playerdata，避免多实例互覆。
 * 判断依据是组内成员身份（不是当前规则值），规则在运行中被关闭也不会
 * 让保护逻辑静默停用。
 */
public final class CloneIdentity {

    private CloneIdentity() {}

    public static boolean shouldSkipSave(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake)) {
            return false;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        if (group == null || !group.members().containsValue(player)) {
            return false;
        }
        return group.isExitSaved() || group.isRollingBack(player) || group.isNonPrimary(player);
    }
}
