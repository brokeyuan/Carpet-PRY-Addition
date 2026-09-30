package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerFlopGoal;
import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomSwimGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/**
 * 鱼模式脑（装配器）：鳕鱼/鲑鱼/热带鱼同款行为（原版三者 AI 无差异，共用
 * AbstractFish 装配，26.3 字节码：恐慌 pri 0 / 规避玩家 pri 2 / 水中游动 pri 4）。
 *
 * <p>特征 = 离水扑腾：原版写在 aiStep（不经 Goal 调度），移植版以最高优先级
 * Goal 表达"离水压过一切移动"——被冲上岸只会原地扑腾，不会迈步逃跑（原版
 * 水生寻路在陆地算不出路径，行为一致）；水中则游动、受惊逃窜、见真人玩家
 * 靠近就躲（原版鱼类 8 格内避人）。水中上浮/下潜由移动控制器的游泳层翻译：
 * 跳跃输入走原版 {@code aiStep → jumpInLiquid(WATER)} 上浮路径（等价真人按住
 * 跳跃键），下潜交给自然沉降，水平仍是玩家原生行走输入。</p>
 *
 * <p>不做（引擎边界，docs 披露）：深水自由三维巡航——A* 以"可站立格"为节点，
 * 中层水无支撑不成节点，游动沿水底并借台阶式路径上浮；河豚毒刺/鱿鱼喷墨
 * 等身体附伤本就不做。</p>
 */
public class FishBrain extends PlayerBrainController {

    /** 憋气阈值（剩余空气 tick）：低于即进入换气上浮（默认 300，留 1/3 呼吸余量） */
    private static final int BREATH_LOW = 100;
    /** 换气完成阈值：回满到此值才恢复水下活动（滞回防抖） */
    private static final int BREATH_OK = 280;

    /** 是否在换气上浮中 */
    private boolean breathing;

    public FishBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "fish";
    }

    @Override
    public void tick() {
        if (this.breathing) {
            // 换气期独占移动链：跳过目标/行为/寻路（游动 Goal 每 tick 重发水下游动
            // 指令，会覆盖上浮指令——实测憋气指令永远抢不过，鱼溺死在池底），
            // 只原地垂直上浮（目标点高于脚下 → 移动控制器置跳跃输入 → 原版
            // jumpInLiquid(WATER) 上浮路径），眼部出水后自然回气
            this.prowler.getNavigation().stop();
            this.prowler.getLookControl().clearLook();
            this.prowler.getMoveControl().setWantedPosition(
                    this.player.getX(), this.player.getY() + 1.5, this.player.getZ(), 1.0);
            this.prowler.getMoveControl().tick();
            if (this.player.getAirSupply() >= BREATH_OK || this.player.isDeadOrDying()) {
                this.breathing = false;
            }
            return;
        }
        super.tick();
        if (this.player.getAirSupply() < BREATH_LOW
                && this.player.isEyeInFluid(FluidTags.WATER)) {
            this.breathing = true;
        }
    }

    @Override
    protected void assemble() {
        // 离水扑腾（最高优先级：原版 aiStep 级行为，离水时压过恐慌与游动）
        this.goalSelector.addGoal(0, new PlayerFlopGoal(this.prowler));
        // 恐慌（原版 pri 0；降一档让位于扑腾——离水的鱼不迈步逃命）
        this.goalSelector.addGoal(1, new PlayerPanicGoal(this.prowler));
        // 规避玩家（原版 pri 2：8 格内避人，走 1.6/疾 1.4——Goal 层内 7 格内加速）
        this.goalSelector.addGoal(2, new PlayerAvoidEntityGoal<>(
                this.prowler, Player.class, 8.0F, 1.6, 1.4));
        // 水中随机游动（原版 pri 4 FishSwimGoal）
        this.goalSelector.addGoal(4, new PlayerRandomSwimGoal(this.prowler, 0.8));
    }
}
