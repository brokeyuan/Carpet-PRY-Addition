package me.primaryuan.carpet.handler.fakegames;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** 会话成员与加入资格的纯规则，便于独立验证。 */
public final class MemberRules {

    private MemberRules() {}

    public static int activeCount(Collection<GameMember> members) {
        int count = 0;
        for (GameMember member : members) {
            if (member.state == MemberState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    /** 仅有一名 ACTIVE 时返回其 UUID；0 人或多人返回 null（0 人由调用方判平局）。 */
    public static UUID soleActive(Map<UUID, GameMember> members) {
        UUID sole = null;
        for (GameMember member : members.values()) {
            if (member.state != MemberState.ACTIVE) {
                continue;
            }
            if (sole != null) {
                return null;
            }
            sole = member.id;
        }
        return sole;
    }

    /**
     * 加入资格：等待阶段、存活、非旁观、同维度、在区域内。
     * 返回拒绝原因的 lang 键片段，合格返回 null。
     */
    public static String joinRejection(boolean waiting, boolean alive, boolean spectator,
                                       boolean sameDimension, boolean inRegion) {
        if (!waiting) {
            return "join_too_late";
        }
        if (!alive) {
            return "join_dead";
        }
        if (spectator) {
            return "join_spectator";
        }
        if (!sameDimension) {
            return "join_dimension";
        }
        if (!inRegion) {
            return "join_region";
        }
        return null;
    }
}
