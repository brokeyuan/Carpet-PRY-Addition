package me.primaryuan.carpet.replay.script;

/**
 * 假人驱动端口：脚本引擎对 Carpet ActionPack 的唯一访问面。
 * 实现层负责把调用落到当前版本 Carpet 的 EntityPlayerActionPack 上；
 * 单测用桩实现验证状态机，不经 MC。
 * 严禁在实现之外出现传送、寻路、位置纠正类操作。
 */
public interface FakeDriver
{
    /** 依据掩码位（InputFlags）设置前进/后退/左移/右移；实现须映射到 Carpet 的 setForward/setStrafing 语义 */
    void setMovementMask(int mask);

    void setJump(boolean jump);

    void setSneaking(boolean sneak);

    void setSprinting(boolean sprint);

    /** 按绝对 yaw/pitch 设置视角 */
    void look(float yaw, float pitch);

    /** 快捷栏槽位 0~8（实现负责转换为 Carpet 的 1~9 参数） */
    void setSlot(int slot);

    /** 开始持续左键（挖方块/攻击实体由 Carpet 按当前准星决定）。返回是否成功启动 */
    boolean startAttackHold();

    /** 结束持续左键 */
    void stopAttackHold();

    /** 单次左键尝试（挥空或点击当前目标）。返回是否成功下发 */
    boolean attackOnce();

    /** 开始持续右键（持续使用：拉弓/进食/盾牌）。返回是否成功启动 */
    boolean startUseHold(int hand);

    /** 结束持续右键 */
    void stopUseHold(int hand);

    /** 单次右键尝试（放置/对方块/对实体）。返回是否成功下发 */
    boolean useOnce(int hand);

    /** 丢弃当前选中槽物品（hand 仅保留录制语义；Carpet 槽位参数恒为当前槽） */
    void dropItem(int hand, boolean all);

    /** 主副手交换 */
    void swapHands();

    /** 假人是否仍然有效（未死亡/未卸载） */
    boolean isValid();

    /**
     * 每轮开始（非首轮）回调。仅在脚本明确配置"每轮回到起点"时执行轮间位置重置；
     * 轮内永远不做任何位置干预。
     *
     * @return 是否成功（false 时脚本停止并反馈，不静默继续）
     */
    default boolean onNewRound()
    {
        return true;
    }

    /**
     * 轮间位置重置：传送到指定维度与位置（含朝向）。仅由 onNewRound 在轮间调用。
     * @return 是否成功
     */
    default boolean teleportTo(String dimKey, double x, double y, double z, float yaw, float pitch)
    {
        return false;
    }

    /** 释放全部脚本控制动作（stopAll），恢复假人自由 */
    void releaseAll();

    /**
     * 下线假人：仅由调度器在本会话 ownsFake（假人为本脚本自动召唤）时调用；
     * 外部绑定的假人绝不走此路径。
     */
    default void takeOffline()
    {
    }
}
