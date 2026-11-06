package me.primaryuan.carpet.replay.common;

/**
 * 输入轨道事件类型。只记录操作意图，目标字段一律是诊断信息，播放时不得用于定位结果。
 * 序号即文件格式中的 type 字节，只增不改，保证旧录像可读。
 */
public enum InputEventType
{
    /** 快捷栏切换，a=目标槽位 0~8 */
    SELECT_SLOT,
    /** 挥动手臂，h=手（0 主手 1 副手）。挥空、攻击、挖掘开始都可能伴随 */
    SWING,
    /** 左键点击实体（瞬间攻击意图），a=实体 id（诊断） */
    ATTACK_ENTITY,
    /** 开始挖掘，a/b/c=命中方块坐标（诊断） */
    DIG_START,
    /** 服务端判定挖掘完成（客户端 STOP_DESTROY_BLOCK），a/b/c=诊断坐标 */
    DIG_FINISH,
    /** 中止挖掘，a/b/c=诊断坐标 */
    DIG_ABORT,
    /** 右键按下（开始使用/交互），kind=目标类型，h=手，a/b/c=诊断方块坐标或实体 id */
    USE_DOWN,
    /** 右键释放（结束持续使用），h=手 */
    USE_UP,
    /** 丢弃物品，h=手，a=1 整组/0 单个 */
    DROP_ITEM,
    /** 主副手交换 */
    SWAP_HANDS,
    /** 打开容器等界面操作（仅诊断记录，不重放） */
    SCREEN_OPEN;

    /** 目标类型：对方块 / 对实体 / 对空气 */
    public static final byte USE_TARGET_BLOCK = 0;
    public static final byte USE_TARGET_ENTITY = 1;
    public static final byte USE_TARGET_AIR = 2;
}
