package me.primaryuan.carpet.brain;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 假人"脑子"会话基类（策略模式；原 {@code FakePlayerBrain} 重构而来）。
 *
 * <p><b>只换脑子、不换身体</b>：假人始终是 {@code ServerPlayer}，血量/属性/
 * 背包/碰撞箱一律不动。本类的全部职责是把"生物式的决策结果"翻译成对玩家身体的
 * 最小操作——这一翻译由挂载的移植 Goal + 注入控制器完成：</p>
 *
 * <pre>
 *   tick() 驱动链（每 tick，早于本 tick 的 travel 消费移动输入）：
 *     targetSelector.tick()   → 攻击目标决策（写 getTarget/setTarget）
 *     goalSelector.tick()     → 行为目标决策（改导航/移动/视线/使用物品）
 *     navigation.tick()       → 沿 A* 路径推进，把下一节点交给 moveControl
 *     moveControl.tick()      → ★ 防鬼畜核心：移动指令 → 玩家原生 zza/xxa/yaw ★
 *     lookControl.tick()      → AI 视线 → 玩家原生 yaw/pitch
 * </pre>
 *
 * <p>装配/卸载都由 BrainManager 驱动：{@code attach} 时先清双选择器再
 * {@code assemble()}（各模式往里塞自己的目标）；{@code detach} 时清空全部
 * 选择器、目标与移动输入——假人立即恢复成"普通木桩"，无任何残留状态
 * （不生成实体，卸载即纯净）。</p>
 */
public abstract class PlayerBrainController {

    /**
     * 连续崩溃卸脑阈值（tick）：瞬时异常保活会话；连续崩溃到该值视为确定性
     * 故障，由 {@code BrainManager} 卸脑并清 keep（重挂必然复现，保活只会循环）。
     */
    public static final int MAX_CONSECUTIVE_CRASHES = 100;

    /** 被接管的假人本体（原生玩家身份） */
    protected final ServerPlayer player;
    /** 同一个体的"生物假面"（Mixin 注入，供移植 Goal 调用） */
    protected final PryMob prowler;
    /** 行为目标选择器（MOVE/LOOK 等）—— 子类 assemble() 里装配目标 */
    protected final PlayerGoalSelector goalSelector;
    /** 攻击目标选择器（TARGET 旗标目标） */
    protected final PlayerGoalSelector targetSelector;

    /** 连续崩溃计数（正常 tick 归零；BrainManager 限频记日志 + 阈值卸脑用） */
    private int consecutiveCrashes;

    protected PlayerBrainController(ServerPlayer player) {
        this.player = player;
        this.prowler = (PryMob) (Object) player;
        this.goalSelector = this.prowler.getGoalSelector();
        this.targetSelector = this.prowler.getTargetSelector();
    }

    /** 模式标识（zombie / skeleton / pillager / irongolem / spider / wolf / villager / enderman） */
    public abstract String modeKey();

    /**
     * 名牌后缀的 lang 键尾段（{@code mode_} 之后的部分）。默认 = 模式名本身；
     * 有内部状态的模式（村民职业等）覆写后，状态变化时配合
     * {@link BrainManager#refreshSuffix} 刷新头顶后缀。
     */
    public String suffixLangKey() {
        return "mode_" + this.modeKey();
    }

    /** 挂载时的目标装配：子类往 goalSelector / targetSelector 里 addGoal */
    protected void assemble() {
    }

    /** 每 tick 的 AI 决策与运动驱动入口（在 ServerPlayer.tick() 头部被调用） */
    public void tick() {
        // 奇偶门对齐原版 Mob.serverAiStep：(tickCount + id) % 2 == 0 时做完整评估
        // （清理 + goalUpdate + 推进全部运行目标），否则只推进 rUE 目标。注入点在
        // ServerPlayer.tick() HEAD，baseTick 尚未自增——本 tick 的等效 tickCount
        // 是 tickCount + 1。原版对前 2 tick 的奇数相全量放行（tickCount > 1 门），
        // 一并复刻：挂脑最初 2 tick 全量评估，保证"先挂脑后给装备"立即生效
        int effectiveTickCount = this.player.tickCount + 1;
        boolean fullTick = (effectiveTickCount + this.player.getId()) % 2 == 0
                || effectiveTickCount <= 1;
        this.targetSelector.tick(fullTick);
        this.clearStaleTarget();
        this.goalSelector.tick(fullTick);
        // 无 LOOK 旗标目标运行即清注视请求：goal 停止后残留的 wantPos 会让
        // 假人持续扭头盯住旧坐标（如敌人死亡的位置）
        if (!this.goalSelector.hasRunningFlag(PlayerGoal.Flag.LOOK)) {
            this.prowler.getLookControl().clearLook();
        }
        this.prowler.getNavigation().tick();
        this.prowler.getMoveControl().tick();
        this.prowler.getLookControl().tick();
        // 兜底保险：无任何 MOVE 旗标目标在运行且路径已走完时，强制移动控制
        // 复位为 WAIT——防止个别 goal 的 stop() 漏清导致 STRAFE/MOVE_TO 指令
        // 永久粘滞（假人"目标没了还朝固定方向蹭/原地冻结"）
        if (!this.goalSelector.hasRunningMoveGoal() && this.prowler.getNavigation().isDone()) {
            this.prowler.getMoveControl().setStop();
        }
    }

    /**
     * 陈旧攻击目标回收：目标死亡/移除/跨维度后，若没有任何 TARGET 旗标目标在跑
     * （其 canContinueToUse 本应清理并清字段），此处统一回收。
     *
     * <p>覆盖外部直写目标的路径（群体警报 {@code alertOthers}、狼仇恨同步、
     * 队伍撤销等）：这些目标不归任何选择器 Goal 管，引用死亡后近战 Goal 的
     * {@code canUse} 读到死目标恒 false——表现为"有脑无目标也不动"的粘滞态。</p>
     *
     * @return 是否回收了一个陈旧目标
     */
    boolean clearStaleTarget() {
        LivingEntity target = this.prowler.getTarget();
        if (target != null && !this.targetSelector.hasRunningFlag(PlayerGoal.Flag.TARGET)
                && (target.isRemoved() || !target.isAlive() || target.level() != this.player.level())) {
            this.prowler.setTarget(null);
            return true;
        }
        return false;
    }

    /**
     * 队伍变更后的撤销：当前攻击目标与改队者互为同队 → 立即清目标并停追击
     * （待执行攻击随目标为空自然停止，不需要重新挂脑）。改队双方都调用本方法
     * （由 {@code BrainManager.revokeTeamInvalidTargets} 遍历会话）。
     *
     * @return 是否撤销了一个目标
     */
    boolean revokeTeamTarget() {
        LivingEntity target = this.currentTarget();
        if (target != null && BrainTeams.isTeamFriendly(this.player, target)) {
            this.prowler.setTarget(null);
            this.prowler.getNavigation().stop();
            return true;
        }
        return false;
    }

    /** 当前攻击目标（诊断用：BrainManager 状态查询区分四态） */
    LivingEntity currentTarget() {
        return this.prowler.getTarget();
    }

    /**
     * 诊断视图（状态查询用）：区分"脑子被卸载 / 有脑无目标 / 有目标但导航失败 /
     * Goal 不再推进"四态——
     * <ul>
     *   <li>卸载：会话不存在（{@code BrainManager.diagnosticsOf} 返回 null）；</li>
     *   <li>无目标：{@code target == null}；</li>
     *   <li>导航失败：target 非空且 {@code navStuck}；</li>
     *   <li>Goal 停滞：target 非空、导航进行中但移动控制器 WAIT（有路径却没在走）。</li>
     * </ul>
     */
    public record Diagnostics(String targetName, Double targetDistance,
                              boolean navInProgress, boolean navStuck,
                              String moveOp, int consecutiveCrashes) {
    }

    /** 当前会话的诊断快照 */
    public Diagnostics diagnostics() {
        LivingEntity target = this.currentTarget();
        String targetName = target == null ? null : target.getName().getString();
        Double targetDistance = target == null ? null
                : Math.sqrt(this.prowler.distanceToSqr(target));
        return new Diagnostics(
                targetName,
                targetDistance,
                this.prowler.getNavigation().isInProgress(),
                this.prowler.getNavigation().isStuck(),
                this.prowler.getMoveControl().operation().name(),
                this.consecutiveCrashes);
    }

    /** 连续崩溃 tick 数（BrainManager 阈值卸脑判定用） */
    int consecutiveCrashes() {
        return this.consecutiveCrashes;
    }

    /** 正常完成一次 tick：崩溃计数归零 */
    void onTickHealthy() {
        this.consecutiveCrashes = 0;
    }

    /** tick 崩溃：计数递增（日志由 BrainManager 限频记录） */
    void onTickCrashed() {
        this.consecutiveCrashes++;
    }

    /** 挂载钩子：BrainManager.attach 时调用（先清后装，幂等） */
    public void onAttach() {
        this.goalSelector.removeAllGoals();
        this.targetSelector.removeAllGoals();
        this.prowler.setTarget(null);
        // 导航器装配基准复位：前一模式的水代价不得残留（如 drowned 0.0 →
        // zombie 应回默认 8.0）；模式装配如需覆写在其 assemble() 内执行
        this.prowler.fields().setWaterMalus(PlayerPathNavigation.DEFAULT_WATER_MALUS);
        this.assemble();
    }

    /**
     * 群体仇恨广播的落点：直写攻击目标字段（原版 {@code alertOther} 的
     * {@code setTarget} 语义）——同伴的近战/远程 Goal 读到非空目标即接管追击。
     */
    void alertAnger(LivingEntity attacker) {
        this.prowler.setTarget(attacker);
    }

    /** 卸载钩子：清空目标/移动输入，假人恢复静止木桩，无任何残留 */
    public void onDetach() {
        this.goalSelector.removeAllGoals();
        this.targetSelector.removeAllGoals();
        this.prowler.setTarget(null);
        this.prowler.getNavigation().stop();
        this.prowler.getLookControl().clearLook();
        this.player.zza = 0.0F;
        this.player.xxa = 0.0F;
        this.player.setSprinting(false);
    }
}