package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputFlags;

/**
 * 移动输入状态控制器：把逐 Tick 输入掩码差量落到 FakeDriver。
 * 只在状态变化时更新对应动作，避免逐 Tick 重建导致 Carpet 动作被反复初始化。
 */
public final class InputStateController
{
    private int appliedMask = 0;
    private boolean jumpApplied;
    private boolean sneakApplied;
    private boolean sprintApplied;

    public int appliedMask()
    {
        return appliedMask;
    }

    /** 应用当前 Tick 的输入掩码与三个独立开关，返回是否发生任何变化 */
    public boolean apply(FakeDriver driver, int mask)
    {
        boolean changed = false;
        if (mask != appliedMask)
        {
            driver.setMovementMask(mask);
            appliedMask = mask;
            changed = true;
        }
        boolean jump = (mask & InputFlags.JUMP) != 0;
        if (jump != jumpApplied)
        {
            driver.setJump(jump);
            jumpApplied = jump;
            changed = true;
        }
        boolean sneak = (mask & InputFlags.SNEAK) != 0;
        if (sneak != sneakApplied)
        {
            driver.setSneaking(sneak);
            sneakApplied = sneak;
            changed = true;
        }
        boolean sprint = (mask & InputFlags.SPRINT) != 0;
        if (sprint != sprintApplied)
        {
            driver.setSprinting(sprint);
            sprintApplied = sprint;
            changed = true;
        }
        return changed;
    }

    /**
     * 无条件把当前掩码完整落到驱动（含全零）：外部假人接管时清掉旧动作后，
     * 首帧即使全是 0 也必须真正生效，不能依赖差量判断。
     */
    public void forceApply(FakeDriver driver, int mask)
    {
        driver.setMovementMask(mask);
        driver.setJump((mask & InputFlags.JUMP) != 0);
        driver.setSneaking((mask & InputFlags.SNEAK) != 0);
        driver.setSprinting((mask & InputFlags.SPRINT) != 0);
        appliedMask = mask;
        jumpApplied = (mask & InputFlags.JUMP) != 0;
        sneakApplied = (mask & InputFlags.SNEAK) != 0;
        sprintApplied = (mask & InputFlags.SPRINT) != 0;
    }

    /** 释放状态（不调用 driver，由调用方统一 releaseAll），重置差量基准 */
    public void reset()
    {
        appliedMask = 0;
        jumpApplied = false;
        sneakApplied = false;
        sprintApplied = false;
    }
}
