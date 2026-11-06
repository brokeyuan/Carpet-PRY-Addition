package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.network.chat.Component;

import java.util.UUID;

/** 一名参与者：游戏内状态与积分，不含任何真实玩家数据的引用。 */
public final class GameMember {
    public final UUID id;
    public MemberState state = MemberState.ACTIVE;
    /** 占点/射击/生存等按积分结算的游戏使用；其余游戏恒为 0 */
    public int score;
    /** 最近一次淘汰原因（已按当前语言翻译并带入格式参数），未淘汰为 null */
    public Component eliminationReason;
    /** 兼容旧状态页与测试的翻译键；新代码同时写入 eliminationReason */
    public String eliminationKey;

    public GameMember(UUID id) {
        this.id = id;
    }

    public void clearElimination() {
        eliminationReason = null;
        eliminationKey = null;
    }
}
