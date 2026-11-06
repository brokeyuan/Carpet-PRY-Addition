package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameLogic;
import me.primaryuan.carpet.handler.fakegames.GameSession;

/** 依赖或适配尚未完成的玩法不会进入创建流程；本类仅作防御性兜底。 */
public final class UnavailableGame implements GameLogic {
    @Override
    public void onStart(GameSession session) {
        session.cancel("cancel_unavailable");
    }
}
