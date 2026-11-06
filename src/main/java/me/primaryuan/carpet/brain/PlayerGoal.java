package me.primaryuan.carpet.brain;

import net.minecraft.util.Mth;

import java.util.EnumSet;
import java.util.Set;

/**
 * 移植版原版 {@code Goal} 的基类（org.spongepowered 无关，纯语义复刻）。
 *
 * <p>原版 {@link net.minecraft.world.entity.ai.goal.Goal} 是纯虚基类，定义
 * {@code canUse/canContinueToUse/start/stop/tick} 生命周期与 4 个行为旗标
 * （MOVE/LOOK/JUMP/TARGET），并由 {@code GoalSelector} 按"旗标互斥 + 优先级"
 * 驱动。这里逐字段复刻，唯一差异是把旗标枚举改成我们自己的 {@link Flag}
 * （避免引用 {@code Goal.Flag} 跨版本漂移）。</p>
 *
 * <p><b>interval 层语义（26.3/1.21.11/1.21 字节码逐版核实）</b>：原版
 * {@code Mob.serverAiStep} 以 {@code (tickCount + id) % 2} 奇偶门驱动选择器——
 * 偶数 tick 才做清理 + goalUpdate（评估未运行目标的 canUse），奇数 tick 只推进
 * {@code requiresUpdateEveryTick} 的运行中目标。{@code reducedTickDelay(n)}=
 * ceil(n/2) 正是把"游戏 tick"换算成"评估次数"的换算（每 2 tick 评估一次）。
 * 本层的 interval 与原版 Goal 内部自带节流（Nearest 的 randomInterval 掷骰、
 * LookAt 的 2% 概率掷骰、Tempt 的 calmDown）一一对应：interval=N 表示"N 次
 * 评估（≈2N tick）放行一次"，确定性替代原版的几何分布掷骰（均值相同）。
 * 默认 interval=1（原版无内部节流的 Goal 每 2 tick 评估一次）。</p>
 */
public abstract class PlayerGoal {

    /** 默认评估间隔（评估次数）：1 = 每 2 tick 评估一次（原版默认节奏） */
    private static final int DEFAULT_INTERVAL = 1;

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
     * 冷却门控：选择器在每个"完整评估 tick"对未运行目标调用；冷却未到 0 返回
     * false 并递减，冷却归零后放行评估（canUse 是否通过都消耗本次评估——失败须
     * 等下一段间隔）。interval 以"评估次数"计（每 2 tick 评估一次），与原版
     * Goal 内部节流同口径。
     */
    public boolean canStart() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = this.interval - 1;
        return true;
    }

    /** 设置评估间隔（评估次数，1 = 每 2 tick 评估一次） */
    public void setInterval(int evaluations) {
        this.interval = Math.max(1, evaluations);
    }

    /** 失败重试用：清空本目标冷却，下一完整评估 tick 即可重评（不触发全量扫描风暴） */
    /**
     * 玩家式"选物到手"：快捷栏槽直接切选中（数字键），背包槽 pickSlot 交换
     * （原版数字键悬停交换语义）。Inventory 选中 setter 在 1.21.5 改名。
     */
    protected static void pryEquipSlot(net.minecraft.world.entity.player.Inventory inv, int slot) {
        //#if MC >= 12105
        inv.setSelectedSlot(slot);
        //#elseif MC >= 12103
        //$$ inv.setSelectedHotbarSlot(slot);
        //#else
        //$$ inv.selected = slot; // 1.21~1.21.1 为公开字段
        //#endif
    }

    protected void expediteNextEvaluation() {
        this.cooldown = 0;
    }

    /**
     * 运行中目标是否每 tick 推进 {@code tick()}（原版 {@code Goal.requiresUpdateEveryTick}）。
     * 非 rUE 目标的 tick()/canContinueToUse 同样只在"完整评估 tick"（每 2 tick）执行，
     * 内部计时需按 adjustedTickDelay 口径折半。原版覆写 rUE 的 Goal：MeleeAttack、
     * RangedBowAttack、RandomLookAround（26.3 字节码核实）。
     */
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    /** 原版 Goal.reducedTickDelay：游戏 tick 数 → 评估次数（每 2 tick 评估一次，向上取整） */
    protected static int reducedTickDelay(int ticks) {
        return Mth.positiveCeilDiv(ticks, 2);
    }

    /** 静态版（匿名/常量初始化场景使用，语义同上） */
    public static int reducedTickDelayStatic(int ticks) {
        return Mth.positiveCeilDiv(ticks, 2);
    }

    /** 原版 Goal.adjustedTickDelay：rUE 目标按原值计，否则按评估次数折算 */
    protected int adjustedTickDelay(int ticks) {
        return this.requiresUpdateEveryTick() ? ticks : reducedTickDelay(ticks);
    }
}