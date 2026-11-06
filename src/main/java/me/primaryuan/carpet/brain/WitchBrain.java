package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerWitchDrinkGoal;
import me.primaryuan.carpet.brain.goal.PlayerWitchThrowGoal;
import net.minecraft.server.level.ServerPlayer;

/**
 * 女巫模式脑（装配器）：真实库存药水的喝/投决策，无近战（原版女巫无近战）。
 *
 * <p>投掷决策树见 {@link PlayerWitchThrowGoal}（26.3 字节码对齐）；喝药条件
 * 见 {@link PlayerWitchDrinkGoal}。原版女巫"凭空合成药水弹射物/无限药水"的
 * 身体能力不映射：无药水 = 无攻击手段（文档披露）；伤害抗性等身体属性不
 * 附送。队伍语义：女巫不主动选中同队假人，但喷溅药水弹道仍可能波及同队
 * 邻近目标（原版弹道边界，docs 分别说明）。</p>
 */
public class WitchBrain extends PlayerBrainController {

    public WitchBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "witch";
    }

    @Override
    protected void assemble() {
        // 索敌：玩家 + 被打反击（原版女巫敌对口径）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(this.prowler,
                net.minecraft.world.entity.player.Player.class, 16.0, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // 喝药（MOVE，优先于投掷——保命先于输出）；投掷持续接近/瞄准/60t 节拍
        this.goalSelector.addGoal(2, new PlayerWitchDrinkGoal(this.prowler));
        this.goalSelector.addGoal(3, new PlayerWitchThrowGoal(this.prowler));
        this.goalSelector.addGoal(8, new PlayerRandomStrollGoal(this.prowler, 0.6));
        this.goalSelector.addGoal(9, new PlayerLookAtPlayerGoal(this.prowler,
                net.minecraft.world.entity.player.Player.class, 8.0F));
    }
}
