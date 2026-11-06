package me.primaryuan.carpet.handler.fakegames;

/** 玩家在本局中的状态。LEFT = 主动退出或因断线/真实死亡等客观原因退出。 */
public enum MemberState {
    ACTIVE,
    ELIMINATED,
    LEFT
}
