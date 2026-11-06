package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEventType;

/**
 * 左键攻击执行器：把左键事件族落到 FakeDriver，维护"持续挖掘/攻击"持有状态。
 * 语义（对当前准星目标重试，与录像目标无关）：
 * - DIG_START → 持续左键保持（Carpet continuous 会在每 Tick 对当前准星挖方块/按冷却攻击实体）
 * - DIG_FINISH / DIG_ABORT → 释放持续左键
 * - ATTACK_ENTITY / 挥空 SWING → 单次左键尝试（不干扰持有状态）
 * 事件不存在时（无目标）也照常下发尝试，不等待结果、不验证成功。
 *
 * 逻辑持有(held)与驱动已应用(applied)分离：暂停只停物理动作，恢复时按 held 重建，
 * 避免"暂停期间 releaseAll 后恢复不再继续挖掘"。
 */
public final class AttackPlaybackController
{
    /** 逻辑上仍应保持左键（暂停不清） */
    private boolean held;
    /** 驱动当前确实处于持续左键状态 */
    private boolean applied;

    public boolean holdActive()
    {
        return held;
    }

    public void handle(FakeDriver driver, InputEventType type, int hand)
    {
        switch (type)
        {
            case DIG_START -> {
                if (!held)
                {
                    held = true;
                    applied = driver.startAttackHold();
                }
            }
            case DIG_FINISH, DIG_ABORT -> release(driver);
            case ATTACK_ENTITY -> driver.attackOnce();
            case SWING -> {
                // 伴随挖掘/攻击的挥动不额外下发；仅当无其他左键事件时作为挥空处理
                if (!held)
                {
                    driver.attackOnce();
                }
            }
            default -> {}
        }
    }

    /** 暂停：只停驱动侧动作，保留逻辑持有 */
    public void suspend(FakeDriver driver)
    {
        if (applied)
        {
            driver.stopAttackHold();
            applied = false;
        }
    }

    /** 恢复：逻辑上仍按住时重建持续动作（不重发瞬时事件） */
    public void rebuild(FakeDriver driver)
    {
        if (held && !applied)
        {
            applied = driver.startAttackHold();
        }
    }

    /** 结束/停止：释放并清除逻辑持有 */
    public void release(FakeDriver driver)
    {
        suspend(driver);
        held = false;
    }
}
