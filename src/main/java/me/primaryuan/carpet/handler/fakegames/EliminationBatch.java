package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 同批淘汰的两阶段提交：先把全部目标置为 ELIMINATED，再按输入顺序回调。
 * 回调看到的是完整更新后的成员状态，不会因遍历顺序产生“先到先赢”。
 */
public final class EliminationBatch {

    @FunctionalInterface
    public interface Listener {
        void onEliminated(GameMember member);
    }

    private EliminationBatch() {}

    /** 过滤重复、非 ACTIVE 成员；返回本批实际淘汰的成员（输入顺序）。 */
    public static List<GameMember> apply(Map<UUID, GameMember> members, Collection<UUID> ids,
                                         Component reason, Listener listener) {
        List<GameMember> targets = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            GameMember member = members.get(id);
            if (member != null && member.state == MemberState.ACTIVE && seen.add(id)) {
                targets.add(member);
            }
        }
        for (GameMember member : targets) {
            member.state = MemberState.ELIMINATED;
            member.eliminationReason = reason;
        }
        for (GameMember member : targets) {
            listener.onEliminated(member);
        }
        return targets;
    }

    /** 兼容旧调用：只带翻译键、不带格式参数。 */
    public static List<GameMember> apply(Map<UUID, GameMember> members, Collection<UUID> ids,
                                         String reasonKey, Listener listener) {
        List<GameMember> targets = apply(members, ids, Component.literal(reasonKey), listener);
        for (GameMember member : targets) {
            member.eliminationKey = reasonKey;
        }
        return targets;
    }
}
