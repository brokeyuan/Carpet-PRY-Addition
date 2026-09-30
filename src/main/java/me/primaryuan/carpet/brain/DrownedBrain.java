package me.primaryuan.carpet.brain;

import java.util.Set;

import me.primaryuan.carpet.brain.goal.PlayerGroupHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerTridentAttackGoal;
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 溺尸模式脑（装配器）：26.3 字节码核实的原版装配（addBehaviourGoals 全量）。
 *
 * <p>目标链 = 反击+群体警报（pri 1，原版 {@code HurtByTargetGoal.setAlertOthers}
 * 警报溺尸与僵尸猪灵两类）→ 玩家（pri 2，mustSee）→ 村民（pri 3，透墙）/
 * 铁傀儡 / 美西螈（pri 3）→ 幼年海龟（pri 5）。行为链 = 持三叉戟投掷与近战
 * 同位 pri 2（原版双 Goal 并列、持械判定自然分流：持戟投掷、空手/持剑近战，
 * 插入序先评三叉戟）→ 漫游 pri 7。</p>
 *
 * <p><b>投掷零凭空造物</b>：{@link PlayerTridentAttackGoal} 蓄力 10 tick 后
 * {@code releaseUsingItem()} 走原生 {@code TridentItem.releaseUsing}——掷出的
 * 就是玩家主手的真三叉戟（忠诚回收/耐久扣除全原生）。</p>
 *
 * <p>不做（原版有、移植跳过）：寻水/寻滩/上浮三 Goal（水中环境 Goal，
 * 假人无游泳导航引擎；落水沿用玩家原生浮沉物理）。</p>
 */
public class DrownedBrain extends PlayerBrainController {

    public DrownedBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "drowned";
    }

    @Override
    protected void assemble() {
        // 反击 + 群体警报（原版：同时唤醒溺尸与僵尸猪灵两类同伴）
        this.targetSelector.addGoal(1, new PlayerGroupHurtByTargetGoal(
                this.prowler, Set.of("drowned", "zombiepiglin")));
        // pri 2 玩家（mustSee）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // pri 3 村民（mustSee=false：透墙定位，与原版僵尸基座同款）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 20.0, 10, false,
                v -> v.isAlive() && !v.isBaby()));
        // pri 3 铁傀儡
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 20.0, 10, true,
                g -> g.isAlive()));
        // pri 3 美西螈（原版与溺尸互为猎物关系）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Axolotl.class, 16.0, 10, true,
                a -> a.isAlive()));
        // pri 5 幼年海龟
        this.targetSelector.addGoal(5, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 20.0, 10, true,
                t -> t.isBaby()));
        // 行为：持三叉戟投掷（pri 2）与近战兜底（pri 3）。原版两者同为 pri 2、
        // 靠"同 tick 全量评估 + 插入序"分胜负；移植版有冷却间隔层（近战 4 tick
        // 早于三叉戟 40 tick 成为候选），同优先级会永久互斥——故近战降一档：
        // 持戟时投掷抢占（2<3），掷出后（主手空）近战接管，可观察行为与原版一致
        this.goalSelector.addGoal(2, new PlayerTridentAttackGoal(this.prowler));
        this.goalSelector.addGoal(3, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 漫游（原版 RandomStrollGoal pri 7）
        this.goalSelector.addGoal(7, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
