package me.primaryuan.carpet.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标选择器（忠实复刻原版 {@code GoalSelector} 的调度语义，零 Profiler/零版本依赖）。
 *
 * <p>原版每个生物的脑子里有两个 {@code GoalSelector}：{@code goalSelector} 负责
 * 移动/行为目标，{@code targetSelector} 负责攻击目标选择（通常挂 TARGET 旗标）。
 * 调度规则（26.3/1.21.11/1.21 字节码逐版核实，三版一致）：</p>
 *
 * <ol>
 *   <li>{@code Mob.serverAiStep} 以 {@code (tickCount + id) % 2} 奇偶门分两档：
 *       <b>完整 tick</b>（偶数）＝清理（对运行中目标查 canContinueToUse）→
 *       goalUpdate（评估未运行目标 canUse 并启动）→ 推进全部运行中目标；
 *       <b>轻量 tick</b>（奇数）＝只推进 requiresUpdateEveryTick 的运行中目标；</li>
 *   <li>启动顺序按优先级从高到低（数值小优先），需通过 {@link PlayerGoal#canStart()}
 *       冷却门控且 {@code canUse()} 成立，且旗标与当前运行中目标<b>不冲突</b>；
 *       更高优先级候选可抢占运行中的低优先级目标。</li>
 * </ol>
 *
 * <p>这样 6 种模式里的"近战 vs 跟随 vs 漫步 vs 逃跑"天然互斥，不会出现
 * 两个逻辑同时改 zza/xxa 互相打架的"人格分裂"。{@code reducedTickDelay} 换算
 * 依据完整 tick 的评估节奏（每 2 tick 评估一次）。</p>
 */
public class PlayerGoalSelector {

    /** 目标 → 包装（保持插入顺序，addGoal 时按需 setPriority） */
    private final Map<PlayerGoal, WrappedGoal> goals = new LinkedHashMap<>();
    /** 当前激活的目标（原版 availableGoals 语义） */
    private final List<WrappedGoal> runningGoals = new ArrayList<>();

    public PlayerGoalSelector() {
    }

    /** 以给定优先级注册一个目标；优先级数值越小越优先（原版 addGoal 语义） */
    public void addGoal(int priority, PlayerGoal goal) {
        this.goals.computeIfAbsent(goal, g -> new WrappedGoal(g)).setPriority(priority);
    }

    /** 是否有持有 MOVE 旗标的目标正在运行（移动控制粘滞保险的判定用） */
    public boolean hasRunningMoveGoal() {
        return this.hasRunningFlag(PlayerGoal.Flag.MOVE);
    }

    /** 是否有持有指定旗标的目标正在运行（视线粘滞清理 / 目标仲裁用） */
    public boolean hasRunningFlag(PlayerGoal.Flag flag) {
        for (WrappedGoal wrapped : this.runningGoals) {
            if (wrapped.goal.getFlags().contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /** 清空全部目标并 stop 所有激活中的目标（挂载/卸载脑时先调） */
    public void removeAllGoals() {
        for (WrappedGoal wrapped : this.runningGoals) {
            wrapped.stop();
        }
        this.runningGoals.clear();
        this.goals.clear();
    }

    /** 移除一个已注册目标（对齐原版 removeGoal：运行中则先 stop 再移出） */
    public void removeGoal(PlayerGoal goal) {
        WrappedGoal wrapped = this.goals.remove(goal);
        if (wrapped != null && wrapped.running) {
            wrapped.stop();
            this.runningGoals.remove(wrapped);
        }
    }

    /** 某 Goal 是否已注册在册（武器形态换装测试与装配自检用） */
    boolean isRegistered(PlayerGoal goal) {
        return this.goals.containsKey(goal);
    }


    /**
     * 每 tick 调度入口（BrainManager 驱动，位于玩家 travel 之前）。
     *
     * @param fullTick 完整评估 tick（奇偶门，见类注）：true 才做清理 + 候选评估，
     *                 两种 tick 都推进运行中目标（轻量 tick 仅限 rUE 目标）
     */
    public void tick(boolean fullTick) {
        if (fullTick) {
            // goalCleanup：先于 goalUpdate（原版同序）；canContinueToUse 每 2 tick 查一次
            Iterator<WrappedGoal> it = this.runningGoals.iterator();
            while (it.hasNext()) {
                WrappedGoal wrapped = it.next();
                if (!wrapped.goal.canContinueToUse()) {
                    wrapped.stop();
                    it.remove();
                }
            }
            // goalUpdate：候选按优先级升序；要求冷却通过；旗标冲突时允许
            // 更高优先级（数值更小）的新目标抢占运行中的低优先级目标
            List<WrappedGoal> candidates = new ArrayList<>();
            for (WrappedGoal wrapped : this.goals.values()) {
                if (!wrapped.running && wrapped.goal.canStart()) {
                    candidates.add(wrapped);
                }
            }
            candidates.sort(Comparator.comparingInt(w -> w.priority));
            for (WrappedGoal wrapped : candidates) {
                this.tryStart(wrapped);
            }
        }
        // tickRunningGoals：完整 tick 全部推进；轻量 tick 只推进 requiresUpdateEveryTick
        for (WrappedGoal wrapped : this.runningGoals) {
            if (fullTick || wrapped.goal.requiresUpdateEveryTick()) {
                wrapped.goal.tick();
            }
        }
    }

    /**
     * 尝试启动一个候选目标（原版 GoalSelector 顺序：旗标可替换性先于 canUse——
     * canUse 可能带实体扫描乃至全局副作用，被旗标挡住的候选不得触发它；
     * 被抢占目标在候选确认能启动后才停止，不出现"先停旧行为再发现新行为不能启动"）。
     *
     * @return 是否成功启动
     */
    private boolean tryStart(WrappedGoal candidate) {
        // 先判旗标兼容性与抢占资格（不调用 canUse）
        List<WrappedGoal> preempted = null;
        for (WrappedGoal running : this.runningGoals) {
            if (!conflicts(running, candidate)) {
                continue;
            }
            if (running.priority <= candidate.priority) {
                return false; // 运行中的目标优先级不低于候选：排队等待，不抢占
            }
            if (preempted == null) {
                preempted = new ArrayList<>();
            }
            preempted.add(running);
        }
        // 资格通过才评估 canUse（可带实体扫描/A* 等开销）
        if (!candidate.goal.canUse()) {
            return false;
        }
        if (preempted != null) {
            for (WrappedGoal running : preempted) {
                running.stop();
                this.runningGoals.remove(running);
            }
        }
        candidate.start();
        this.runningGoals.add(candidate);
        return true;
    }

    /** 两个目标的旗标是否相交（互斥判定） */
    private boolean conflicts(WrappedGoal a, WrappedGoal b) {
        for (PlayerGoal.Flag flag : a.goal.getFlags()) {
            if (b.goal.getFlags().contains(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 目标包装：priority + running 状态（对齐原版 WrappedGoal 的最小形态）。
     * start/stop 统一走这里的钩子——原版同样把 running 标志封装在包装层，
     * 任何停止路径漏复位都会让候选过滤（!running）把该 Goal 永久排除，
     * 表现为"每个 Goal 一次会话只能自然结束一次"（战斗/跟随/漫步全部一次性）。
     */
    private static final class WrappedGoal {
        final PlayerGoal goal;
        int priority;
        boolean running;

        WrappedGoal(PlayerGoal goal) {
            this.goal = goal;
        }

        void setPriority(int priority) {
            this.priority = priority;
        }

        void start() {
            this.running = true;
            this.goal.start();
        }

        void stop() {
            this.running = false;
            this.goal.stop();
        }
    }
}