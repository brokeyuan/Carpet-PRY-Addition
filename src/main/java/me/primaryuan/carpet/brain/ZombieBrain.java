package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 260102
//$$ // 26.1.2 起原版加入长矛（动能武器）；根 src 裸代码须为 1.21.11 方言，
//$$ // 故 26.x 专属 import/goal 用 //$$ 行标注
//$$ import me.primaryuan.carpet.brain.goal.PlayerSpearAttackGoal;
//#endif
//#if MC >= 12111
// 1.21.11 起铁傀儡/海龟/村民类迁入子包（rootNode 方言 = 裸行）
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 僵尸模式脑（装配器）：原版近战追击 AI——自动追逐最近的存活玩家（含其它假人，
 * 创造/旁观者不可见不可选），进入玩家原生攻击距离后用原生 {@code attack()} 挥砍；
 * 遇障碍/台阶由移动控制器原生跳跃。
 *
 * <p>装配 = 目标选择（{@code PlayerNearestAttackableTargetGoal}）+ 行为
 * （{@code PlayerMeleeAttackGoal}），全部是移植版原版 Goal，算法 1:1。</p>
 *
 * <p><b>长矛支持（26.1.2+）</b>：与原版 26.x 僵尸同款装配——主手持长矛
 * （动能武器）时由 {@code PlayerSpearAttackGoal}（优先级 2，与原版 SpearUseGoal
 * 同位）接管：接近→举矛蓄力冲刺→刺中（原版动能判定）→后撤循环；
 * 近战 Goal 在优先级 3 兜底（对应原版 ZombieAttackGoal 在 pri 3）。
 * 挥砍路径天然支持任何武器——玩家原生 {@code attack()} 的伤害取自主手物品属性。</p>
 */
public class ZombieBrain extends PlayerBrainController {

    public ZombieBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "zombie";
    }

    @Override
    protected void assemble() {
        // 被打反击：谁打我锁谁（原版 HurtByTargetGoal，优先级 1 压过常规索敌）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标（对齐原版僵尸目标选择器，优先级数值越小越优先）：
        // pri 2 玩家（mustSee）——戴僵尸头的玩家追踪距离减半（wiki：35→17.5），
        // 用两个并列 pri 2 的 goal 表达：同旗标互斥，按插入序先评"未戴头"者
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 10.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        // pri 3 村民/流浪商人（mustSee=false：原版僵尸可透过方块定位村民）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 20.0, 10, false,
                v -> v.isAlive() && !v.isBaby()));
        // pri 4 铁傀儡
        this.targetSelector.addGoal(4, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 20.0, 10, true,
                g -> g.isAlive()));
        // pri 5 幼年海龟（原版最低优先级目标；isBaby 排除成年海龟）
        this.targetSelector.addGoal(5, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 20.0, 10, true,
                t -> t.isBaby()));
        // 长矛刺击（26.1.2+）：主手持矛时接管，与原版 26.x 僵尸同款优先级 2；
        // 1.21.x 无长矛物品，此 Goal 不编入，近战直接兜底
        //#if MC >= 260102
//$$         this.goalSelector.addGoal(2, new PlayerSpearAttackGoal(this.prowler, 1.0));
        //#endif
        // 行为：近战追击（原版僵尸持矛时 ZombieAttackGoal 在 pri 3 兜底，同款）
        this.goalSelector.addGoal(3, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 空闲漫游（原版僵尸同样挂 RandomStrollGoal）：丢目标后散步而非原地挂机
        this.goalSelector.addGoal(4, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}