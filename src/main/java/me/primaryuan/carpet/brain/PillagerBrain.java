package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerGroupHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;

import java.util.Set;
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
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
 *
 * <p>装配按 26.3 字节码：{@code RangedCrossbowAttackGoal(1.0, 8F)} pri 3 /
 * 漫游 pri 8（0.6）/ 看人 pri 9；目标链 = 反击+警报（pri 1，
 * {@code setAlertOthers(Raider)}——脑间等价为掠夺者/卫道士假人）→ 玩家
 * （pri 2，mustSee，FOLLOW_RANGE 32）→ 村民（pri 3，透墙）/ 铁傀儡。
 * 规避 Creaking（pri 1）1.21.4+ 编入。不做：{@code HoldGroundAttackGoal}
 * （袭击巡逻队上下文）、开门（身体）。</p>
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
        // 被打反击 + 警报掠夺者/卫道士假人（原版 setAlertOthers(Raider) 的脑间等价）
        this.targetSelector.addGoal(1, new PlayerGroupHurtByTargetGoal(
                this.prowler, Set.of("pillager", "vindicator")));
        // 追击目标：最近的存活玩家（范围 = 原版 FOLLOW_RANGE 32，26.3 字节码：
        // Pillager.createAttributes 显式覆写 32）；谓词里额外要求假人此刻
        // 仍持弩（收弩即熄火）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 32.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && this.prowler.isHolding(s -> s.is(Items.CROSSBOW))));
        // pri 3 村民（透墙）与铁傀儡（原版 Pillager 目标链，26.3 字节码：
        // NearestAttackableTarget<AbstractVillager>(mustSee=false) / <IronGolem>(mustSee)）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 32.0, 10, false,
                v -> v.isAlive()));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 32.0, 10, true,
                g -> g.isAlive()));
        // pri 1 规避 Creaking（原版 AvoidEntityGoal<Creaking>(8, 1.0, 1.2)；
        // Creaking 1.21.4+）
        //#if MC >= 12104
        this.goalSelector.addGoal(1, new PlayerAvoidEntityGoal<>(
                this.prowler, net.minecraft.world.entity.monster.creaking.Creaking.class, 8.0F, 1.0, 1.2));
        //#endif
        // 行为：原版远程 Goal（弩：上弦 25 tick、射击间隔 20 tick、射程 8 格），
        // 与原版 RangedCrossbowAttackGoal 同位 pri 3
        // ★ 零凭空造物：releaseUsingItem → 原生 CrossbowItem.releaseUsing 射出背包箭矢 ★
        this.goalSelector.addGoal(3, new PlayerRangedAttackGoal(
                this.prowler, 1.0, 20, 8.0F, Items.CROSSBOW, 25));
        // 空闲漫游（原版 RandomStrollGoal pri 8，速度 0.6）
        this.goalSelector.addGoal(8, new PlayerRandomStrollGoal(this.prowler, 0.6));
        // 空闲看人（原版 LookAtPlayerGoal pri 9，15 格）
        this.goalSelector.addGoal(9, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 15.0F));
    }
}
