package me.primaryuan.carpet.replay.common;

/**
 * 输入轨道事件：第 tick 个录制 Tick 内第 seq 个事件（seq 从 0 起，按到达顺序）。
 * 只记录操作意图；b/c 与 d 为诊断信息（d 可为 BlockPos.asLong 打包坐标或实体 id），播放时不得依赖。
 * 字段约定：
 * SELECT_SLOT: a=槽位0~8
 * SWING: hand
 * ATTACK_ENTITY: d=实体id（诊断）
 * DIG_START/DIG_FINISH/DIG_ABORT: d=诊断方块坐标（BlockPos.asLong）
 * USE_DOWN: a=目标类型（0方块/1实体/2空气），d=诊断（方块打包坐标或实体id）
 * USE_UP: hand
 * DROP_ITEM: hand，a=1整组/0单个
 * SWAP_HANDS: 无
 */
public final class InputEvent
{
    public final int tick;
    public final int seq;
    public final InputEventType type;
    /** 手：0 主手 1 副手；-1 无关 */
    public byte hand = -1;
    public int a;
    public int b;
    public int c;
    /** 诊断用长整型（打包坐标或实体 id） */
    public long d;

    public InputEvent(int tick, int seq, InputEventType type)
    {
        this.tick = tick;
        this.seq = seq;
        this.type = type;
    }

    public InputEvent withHand(int hand)
    {
        this.hand = (byte) hand;
        return this;
    }
}
