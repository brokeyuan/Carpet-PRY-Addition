package me.primaryuan.carpet.clones;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;

/**
 * 分身实例判定（M1 原型）：判断某玩家是否为分身组的"非主实例"。
 *
 * <p>主实例语义：PlayerList.playersByUUID 按后写覆盖，组内最后加入者
 * 是 UUID 单值查询（getPlayer/getPlayerByName 第一个匹配）的返回对象。
 * 非"主实例"的分身在存档写盘层跳过，防互覆。</p>
 */
public final class CloneIdentity {

    private CloneIdentity() {}

    /** 该玩家是否为分身组中非主实例（playersByUUID 登记者不是它） */
    public static boolean isNonPrimaryClone(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake)) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (!CloneAllowlist.isAllowed(uuid)) {
            return false;
        }
        // ServerPlayer#getServer 在 1.21.10+ 映射不存在，统一走 level().getServer()
        List<ServerPlayer> players = player.level().getServer().getPlayerList().getPlayers();
        int sameUuid = 0;
        for (ServerPlayer p : players) {
            if (p.getUUID().equals(uuid)) {
                sameUuid++;
            }
        }
        return sameUuid > 1;
    }
}
