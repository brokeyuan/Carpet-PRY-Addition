package me.primaryuan.carpet.brain;

import java.util.EnumSet;
import java.util.Set;

/**
 * 移植版原版 {@code Goal} 的基类（org.spongepowered 无关，纯语义复刻）。
 *
 * <p>原版 {@link net.minecraft.world.entity.ai.goal.Goal} 是纯虚基类，定义
 * {@code canUse/canContinueToUse/start/stop/tick} 生命周期与 4 个行为旗标
 * （MOVE/LOOK/JUMP/TARGET），并由 {@code GoalSelector} 按"旗标互斥 + 优先级"
 * 驱动。这里逐字段复刻，唯一差异是把旗标枚举改成我们自己的 {@link Flag}
 * （避免引用 {@code Goal.Flag} 跨版本漂移）；其它语义（冷却间隔、状态机）原样保留。</p>
 *
 * <p>冷却（interval）：原版 Goal 默认 20 tick 才允许重新评估一次 {@code canUse}，
 * 用于抑制高频目标扫描。这里用计数器实现，语义等价。</p>
 */
public abstract class PlayerGoal {

    /** 默认评估间隔（tick）：与原版 Goal 一致 */
    private static final int DEFAULT_INTERVAL = 20;

    /** 行为旗标：MOVE=自主移动 / LOOK=自主转头 / JUMP=自主跳跃 / TARGET=自行选择攻击目标 */
    public enum Flag { MOVE, LOOK, JUMP, TARGET }

    private final EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    private int interval = DEFAULT_INTERVAL;
    private int cooldown = 0;

    /** 每 tick 核心判定：本目标此刻是否应当激活 */
    public abstract boolean canUse();

    /** 已激活的目标继续维持的条件（原版默认复用 canUse） */
    public boolean canContinueToUse() {
        return this.canUse();
    }

    /** 目标被选择器激活时的钩子（装配/清仓/清零通常在这里做） */
    public void start() {
    }

    /** 目标被选择器停用时的钩子 */
    public void stop() {
    }

    /** 激活期间每 tick 推进 */
    public void tick() {
    }

    /** 设置行为旗标（用于互斥调度） */
    public void setFlags(EnumSet<Flag> flags) {
        this.flags.clear();
        this.flags.addAll(flags);
    }

    public Set<Flag> getFlags() {
        return this.flags;
    }

    /**
     * 冷却门控：选择器每 tick 调用；冷却未到 0 返回 false 并递减，
     * 冷却归零后恢复 true 并重置冷却（原版 Goal 的 interval 语义）。
     */
    public boolean canStart() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = this.interval;
        return true;
    }

    /** 设置评估间隔；间隔越小决策越灵敏（原版 goals 常用 10~60） */
    public void setInterval(int ticks) {
        this.interval = ticks;
    }

    /** 立即清除冷却（供装配/切换模式时使用，避免"新脑上路要空转 20 tick"） */
    public void resetInterval() {
        this.cooldown = 0;
    }
}