package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 骷髅模式脑（装配器）：原版远程风筝 AI，零凭空造物。
 *
 * <p><b>激活条件（摘弓熄火）</b>：仅当假人主手持弓（{@link Items#BOW}）时
 * 整套目标链才可用——装配时主手无弓直接不装；装好后中途取下弓，由目标谓词
 * （{@code this.prowler.isHolding(...)}）在每 tick 复核时判定失败，目标被释放、
 * 远程 Goal 随即停摆，假人原地熄火。重新拿弓自动恢复。</p>
 *
 * <p><b>零凭空造物（红线的落地方案）</b>：不手动实例化任何箭。拉弓蓄力走原生
 * {@code Player.startUsingItem}，满弦（{@link PlayerRangedAttackGoal} 蓄力 20 tick）
 * 后调用原生 {@code releaseUsingItem()} 触发 {@code BowItem.releaseUsing} 流程——
 * 由原版逻辑扣除背包真实箭矢、用原生弹道射出并广播生成包，客户端拉弓/飞行动画
 * 免费获得，服务端权威。（注意 {@code stopUsingItem()} 是静默收弓不发射，勿混用）</p>
 *
 * <p><b>风筝走位</b>：目标进入射程（{@code PlayerRangedAttackGoal} 内建
 * attackRadius² 判定）后站定射击，其余时间保持寻路距离；距离过近时由远程 Goal
 * 的 {@code strafe} 反向平移拉开（对应原版骷髅绕着目标拉距离）。</p>
 */
public class SkeletonBrain extends PlayerBrainController {

    public SkeletonBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "skeleton";
    }

    @Override
    protected void assemble() {
        // 主手无弓：整个模式不装配任何目标（装好后中途取弓则由谓词动态熄火）
        if (!this.player.getMainHandItem().is(Items.BOW)) {
            return;
        }
        // 被打反击（原版骷髅同样挂 HurtByTargetGoal）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标：最近的存活玩家；谓词里额外要求假人此刻仍持弓（摘弓即熄火）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 24.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && this.prowler.isHolding(s -> s.is(Items.BOW))));
        // 行为：原版远程 Goal（速度 1.0、最短射击间隔 20 tick、攻击半径 8 格）
        // ★ 射箭零凭空造物：满弦后 releaseUsingItem() → 原生 BowItem.releaseUsing 射出 ★
        this.goalSelector.addGoal(1, new PlayerRangedAttackGoal(this.prowler, 1.0, 20, 8.0F));
        // 空闲漫游（原版骷髅同样挂 RandomStrollGoal）
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}