package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 掠夺者模式脑（装配器）：弩版远程 AI，零凭空造物。
 *
 * <p>与骷髅模式共用 {@link PlayerRangedAttackGoal}（武器参数化为
 * {@code Items.CROSSBOW}、上弦 25 tick，对齐原版弩的蓄力时长）。射击同样走
 * 原生 {@code startUsingItem → releaseUsingItem} 流程——由原版
 * {@code CrossbowItem.releaseUsing} 扣除背包箭矢并射出。</p>
 *
 * <p><b>激活条件（收弩熄火）</b>：目标选择与远程 Goal 均按
 * {@code isHolding(Items.CROSSBOW)} 每 tick 动态判定——挂脑时不要求已持弩，
 * 中途收弩即熄火，重新持弩自动恢复。</p>
 */
public class PillagerBrain extends PlayerBrainController {

    public PillagerBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "pillager";
    }

    @Override
    protected void assemble() {
        // 收弩熄火由谓词/远程 Goal 的 canUse 动态判定（此刻无弩也照常装配，
        // 之后拿到弩立即生效——避免"先挂脑后给装备"时序导致的空脑子）
        // 被打反击（原版掠夺者同样挂 HurtByTargetGoal）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标：最近的存活玩家；谓词里额外要求假人此刻仍持弩（收弩即熄火）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 24.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && this.prowler.isHolding(s -> s.is(Items.CROSSBOW))));
        // 行为：原版远程 Goal（弩：上弦 25 tick、射击间隔 20 tick、射程 8 格）
        // ★ 零凭空造物：releaseUsingItem → 原生 CrossbowItem.releaseUsing 射出背包箭矢 ★
        this.goalSelector.addGoal(1, new PlayerRangedAttackGoal(
                this.prowler, 1.0, 20, 8.0F, Items.CROSSBOW, 25));
        // 空闲漫游（原版掠夺者同样挂 RandomStrollGoal）
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
