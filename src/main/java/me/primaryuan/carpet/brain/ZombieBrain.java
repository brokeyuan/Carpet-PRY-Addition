package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerGroupHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
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

import java.util.Set;

/**
 * 僵尸模式脑（装配器）：26.3 字节码核实的原版装配（registerGoals +
 * addBehaviourGoals 全量；1.21.11 结构一致，见 tasks 核对记录）。
 *
 * <p>目标链（targetSelector）＝ 反击+群体警报（pri 1，原版
 * {@code HurtByTargetGoal.setAlertOthers(ZombifiedPiglin)}——被打时唤醒僵尸猪灵
 * 假人）→ 玩家（pri 2，mustSee；范围 = 原版 FOLLOW_RANGE 35，戴僵尸头减半
 * 17.5——26.3 经 {@code MOB_VISIBILITY} 组件实现，移植版以双 Goal 表达同结果）
 * → 村民/流浪商人（pri 3，mustSee=false：透过方块定位）→ 铁傀儡（pri 3，
 * mustSee）→ 幼年海龟（pri 5，interval 10，mustSee；选择器 = 幼年且不在水中，
 * 原版 {@code BABY_ON_LAND_SELECTOR}）。</p>
 *
 * <p>行为链（goalSelector）＝ 长矛刺击（26.1.2+，pri 2，原版
 * {@code SpearUseGoal(1.0, 10.0F, 2.0F)}）→ 近战（pri 3，原版
 * {@code ZombieAttackGoal(1.0, false)}——视线丢失即收手）→ 漫游（pri 7，原版
 * {@code WaterAvoidingRandomStrollGoal(1.0)}）→ 看人/随机张望（pri 8，
 * 原版 LookAtPlayer/RandomLookAround 同位）。寻路 malus：原版
 * {@code PathType.WATER} 默认 8.0，移植版默认同值。</p>
 *
 * <p>不做（原版有、移植跳过，docs 披露）：破门/毁蛋（方块交互 = 身体）、
 * {@code MoveThroughVillageGoal}（pri 6，无 POI/村庄查询引擎）、
 * 增援生成（凭空造物实体）、僵尸化转化（身体转化）、26.x 头颅组件
 * {@code MOB_VISIBILITY}（假人不带组件，用双 Goal 表达减半范围）。</p>
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
        // 被打反击并唤醒僵尸猪灵同伴（原版 HurtByTargetGoal.setAlertOthers 同款）
        this.targetSelector.addGoal(1, new PlayerGroupHurtByTargetGoal(
                this.prowler, Set.of("zombiepiglin")));
        // pri 2 玩家（mustSee，范围 = 原版 FOLLOW_RANGE 35（26.3 字节码：Zombie.createAttributes 显式覆写 35；继承链 drowned/babyzombie 同值））：
        // 戴僵尸头的玩家追踪距离减半（26.3：MOB_VISIBILITY 0.5 × 35 = 17.5），
        // 用两个并列 pri 2 的 goal 表达：同旗标互斥，按插入序先评"未戴头"者
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 35.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 17.5, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        // pri 3 村民/流浪商人（mustSee=false：原版僵尸可透过方块定位村民）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 35.0, 10, false,
                v -> v.isAlive() && !v.isBaby()));
        // pri 3 铁傀儡
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 35.0, 10, true,
                g -> g.isAlive()));
        // pri 5 幼年海龟（原版最低优先级目标；BABY_ON_LAND_SELECTOR =
        // 幼年且不在水中；mustSee=true 的新目标视线门与移植版一致）
        this.targetSelector.addGoal(5, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 35.0, 10, true,
                t -> t.isBaby() && !t.isInWater()));
        // 长矛刺击（26.1.2+）：与原版 26.x 僵尸同款优先级 2（SpearUseGoal 1.0，
        // 交战 10 格 / 蓄力 2.0 系数）；1.21.x 无长矛物品，此 Goal 不编入
        //#if MC >= 260102
//$$         this.goalSelector.addGoal(2, new PlayerSpearAttackGoal(this.prowler, 1.0));
        //#endif
        // 行为：近战追击（原版 ZombieAttackGoal(1.0, false)——失视线即收手，
        // 目标归 TARGET 旗标 Goal 管理）
        this.goalSelector.addGoal(3, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 空闲漫游（原版 WaterAvoidingRandomStrollGoal pri 7）
        this.goalSelector.addGoal(7, new PlayerRandomStrollGoal(this.prowler, 1.0));
        // 空闲看人/随机张望（原版 LookAtPlayerGoal / RandomLookAroundGoal，pri 8）
        this.goalSelector.addGoal(8, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new PlayerRandomLookAroundGoal(this.prowler));
    }
}
