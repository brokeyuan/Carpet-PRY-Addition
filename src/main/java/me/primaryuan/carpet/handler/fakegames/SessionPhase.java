package me.primaryuan.carpet.handler.fakegames;

/** 会话阶段：等待加入 → 倒计时 → 进行中 → 已结束。 */
public enum SessionPhase {
    WAITING,
    COUNTDOWN,
    RUNNING,
    FINISHED
}
