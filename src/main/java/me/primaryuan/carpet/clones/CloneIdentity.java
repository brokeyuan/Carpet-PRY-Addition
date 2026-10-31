package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;


/**
 * 分身实例判定（M1 原型）：判断某玩家是否为分身组的"非主实例"。
 *
 * <p>主实例语义：PlayerList.playersByUUID 按后写覆盖，组内最后加入者
 * 是 UUID 单值查询（getPlayer/getPlayerByName 第一个匹配）的返回对象。
 * 非"主实例"的分身在存档写盘层跳过，防互覆。</p>
 */
public final class CloneIdentity {

    private CloneIdentity() {}

    /** 该玩家是否为分身组中非主实例（组内 cloneId 最小者为主实例） */
    public static boolean isNonPrimaryClone(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake)) {
            return false;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        if (group == null) {
            return false;
        }
        return group.isNonPrimary(player);
    }
}
