package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEventType;

/**
 * 右键使用执行器：对方块/对实体的单次交互与对空气的持续使用（拉弓/进食/盾牌）分别处理。
 * 语义：
 * - USE_DOWN(kind=BLOCK|ENTITY) → 单次右键尝试（按录制时的点击节奏逐次重试）
 * - USE_DOWN(kind=AIR) → 开始持续使用；USE_UP → 释放
 * - USE_UP.hand=-1 表示释放当前活动使用（真实录像的松弓/松手格式），两手逻辑状态一起清
 * 播放时对当前视线目标执行同类型输入，不使用录像中的旧坐标（诊断字段不参与）。
 *
 * 逻辑持有(held)与驱动已应用(applied)分离：暂停只停物理动作，恢复时按 held 重建。
 * Carpet 仅有单个 USE 通道，双手同时持有时只做一次物理释放，不宣称并行支持。
 */
public final class ItemUseController
{
    private final boolean[] held = new boolean[2];
    private final boolean[] applied = new boolean[2];

    public boolean holdActive(int hand)
    {
        return hand >= 0 && hand < 2 && held[hand];
    }

    public void handle(FakeDriver driver, InputEventType type, int hand, int targetKind)
    {
        switch (type)
        {
            case USE_DOWN -> {
                if (hand < 0 || hand > 1)
                {
                    return;
                }
                if (targetKind == InputEventType.USE_TARGET_AIR)
                {
                    if (held[hand])
                    {
                        // 重复按下视为先释放再按
                        stopPhysical(driver);
                    }
                    held[hand] = true;
                    applied[hand] = driver.startUseHold(hand);
                }
                else
                {
                    driver.useOnce(hand);
                }
            }
            case USE_UP -> {
                if (hand < 0)
                {
                    releaseAll(driver);
                }
                else
                {
                    stopHold(driver, hand);
                }
            }
            default -> {}
        }
    }

    /** 释放指定手的逻辑持有与物理动作 */
    public void stopHold(FakeDriver driver, int hand)
    {
        if (hand >= 0 && hand < 2)
        {
            if (applied[hand])
            {
                driver.stopUseHold(hand);
                applied[hand] = false;
            }
            held[hand] = false;
        }
    }

    /** 释放当前活动使用（USE_UP.hand=-1）：两手逻辑状态清空，物理通道只停一次 */
    public void releaseAll(FakeDriver driver)
    {
        stopPhysical(driver);
        held[0] = false;
        held[1] = false;
    }

    /** 暂停：只停驱动侧动作，保留逻辑持有 */
    public void suspend(FakeDriver driver)
    {
        stopPhysical(driver);
    }

    /** 恢复：逻辑上仍按住的使用重新开始（不恢复旧蓄力/材料） */
    public void rebuild(FakeDriver driver)
    {
        for (int hand = 0; hand < 2; hand++)
        {
            if (held[hand] && !applied[hand])
            {
                applied[hand] = driver.startUseHold(hand);
            }
        }
    }

    private void stopPhysical(FakeDriver driver)
    {
        if (applied[0] || applied[1])
        {
            // Carpet 只有一个 USE 通道：任意一手持有即调用一次停止
            driver.stopUseHold(0);
        }
        applied[0] = false;
        applied[1] = false;
    }
}
