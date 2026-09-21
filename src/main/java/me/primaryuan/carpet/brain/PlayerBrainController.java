package me.primaryuan.carpet.brain;

import net.minecraft.server.level.ServerPlayer;

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

    /** 被接管的假人本体（原生玩家身份） */
    protected final ServerPlayer player;
    /** 同一个体的"生物假面"（Mixin 注入，供移植 Goal 调用） */
    protected final PryMob prowler;
    /** 行为目标选择器（MOVE/LOOK 等）—— 子类 assemble() 里装配目标 */
    protected final PlayerGoalSelector goalSelector;
    /** 攻击目标选择器（TARGET 旗标目标） */
    protected final PlayerGoalSelector targetSelector;

    protected PlayerBrainController(ServerPlayer player) {
        this.player = player;
        this.prowler = (PryMob) (Object) player;
        this.goalSelector = this.prowler.getGoalSelector();
        this.targetSelector = this.prowler.getTargetSelector();
    }

    /** 模式标识（zombie / skeleton / pillager / irongolem / spider / wolf / villager / enderman） */
    public abstract String modeKey();

    /** 挂载时的目标装配：子类往 goalSelector / targetSelector 里 addGoal */
    protected void assemble() {
    }

    /** 每 tick 的 AI 决策与运动驱动入口（在 ServerPlayer.tick() 头部被调用） */
    public void tick() {
        this.targetSelector.tick();
        this.goalSelector.tick();
        this.prowler.getNavigation().tick();
        this.prowler.getMoveControl().tick();
        this.prowler.getLookControl().tick();
    }

    /** 挂载钩子：BrainManager.attach 时调用（先清后装，幂等） */
    public void onAttach() {
        this.goalSelector.removeAllGoals();
        this.targetSelector.removeAllGoals();
        this.prowler.setTarget(null);
        this.assemble();
    }

    /** 卸载钩子：清空目标/移动输入，假人恢复静止木桩，无任何残留 */
    public void onDetach() {
        this.goalSelector.removeAllGoals();
        this.targetSelector.removeAllGoals();
        this.prowler.setTarget(null);
        this.prowler.getNavigation().stop();
        this.player.zza = 0.0F;
        this.player.xxa = 0.0F;
        this.player.setSprinting(false);
    }
}