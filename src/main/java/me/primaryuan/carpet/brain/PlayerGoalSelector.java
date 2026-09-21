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
 * 调度规则（与原版一致）：</p>
 *
 * <ol>
 *   <li>每 tick 先推进<b>已激活</b>的目标：{@code canContinueToUse()} 成立则
 *       {@code tick()}，否则 {@code stop()} 并移除；</li>
 *   <li>再按优先级从高到低（数值小优先）尝试启动未激活目标：需通过
 *       {@link PlayerGoal#canStart()} 冷却门控且 {@code canUse()} 成立，
 *       且其旗标与当前运行中目标的旗标<b>不冲突</b>（两目标不会同时抢腿/枪眼/手）。</li>
 * </ol>
 *
 * <p>这样 6 种模式里的"近战 vs 跟随 vs 漫步 vs 逃跑"天然互斥，不会出现
 * 两个逻辑同时改 zza/xxa 互相打架的"人格分裂"。</p>
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

    /** 移除一个目标（若在激活中会先 stop） */
    public void removeGoal(PlayerGoal goal) {
        WrappedGoal wrapped = this.goals.remove(goal);
        if (wrapped != null && wrapped.running) {
            wrapped.goal.stop();
            this.runningGoals.remove(wrapped);
        }
    }

    /** 清空全部目标并 stop 所有激活中的目标（挂载/卸载脑时先调） */
    public void removeAllGoals() {
        for (WrappedGoal wrapped : this.runningGoals) {
            wrapped.goal.stop();
        }
        this.runningGoals.clear();
        this.goals.clear();
    }


    /** 每 tick 调度入口（BrainManager 驱动，位于玩家 travel 之前） */
    public void tick() {
        Iterator<WrappedGoal> it = this.runningGoals.iterator();
        while (it.hasNext()) {
            WrappedGoal wrapped = it.next();
            if (wrapped.goal.canContinueToUse()) {
                wrapped.goal.tick();
            } else {
                wrapped.goal.stop();
                it.remove();
            }
        }
        // 尝试启动新目标：优先级升序、要求冷却通过；旗标冲突时允许
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

    /**
     * 尝试启动一个候选目标（原版 GoalSelector 的旗标互斥 + 抢占语义）。
     * 缺少抢占时会出现"漫步中假人对眼前的敌人视而不见"——漫步（低优先级）
     * 未走完路径前，近战/恐慌（高优先级）一直被旗标互斥拦住。
     *
     * @return 是否成功启动
     */
    private boolean tryStart(WrappedGoal candidate) {
        // 先评估候选是否真要启动（canUse 通常带目标扫描/A* 等开销，
        // 顺序上必须在停止任何运行中目标之前）
        if (!candidate.goal.canUse()) {
            return false;
        }
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
        if (preempted != null) {
            for (WrappedGoal running : preempted) {
                running.goal.stop();
                running.running = false;
                this.runningGoals.remove(running);
            }
        }
        candidate.goal.start();
        candidate.running = true;
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

    /** 目标包装：priority + running 状态（对齐原版 WrappedGoal 的最小形态） */
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
    }
}