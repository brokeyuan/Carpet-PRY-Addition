package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerFleeSunGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRestrictSunGoal;
//#if MC >= 12105
import net.minecraft.world.entity.animal.wolf.Wolf;
//#else
//$$ import net.minecraft.world.entity.animal.Wolf;
//#endif
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 骷髅模式脑（装配器）：原版远程/近战双形态 AI，零凭空造物。
 *
 * <p><b>索敌与攻击方式分离（原版同构）</b>：目标选择不挑武器——
 * {@code AbstractSkeleton} 的玩家/铁傀儡/海龟目标链从不看手上是什么；武器形态
 * 由 {@code reassessWeaponGoal} 决定：持弓时装远程 Goal（pri 4）、卸下时换近战
 * {@code MeleeAttackGoal(1.2, false)}（同位 4）。原版在 setItemSlot/出生时重评，
 * 移植版在每 tick 前比对持弓状态（玩家主手可随时切换，原生装备事件不触发），
 * 形态切换即时生效。持剑/空手时照常索敌并近战接敌。</p>
 *
 * <p><b>零凭空造物（红线的落地方案）</b>：不手动实例化任何箭。拉弓蓄力走原生
 * {@code Player.startUsingItem}，满弦（{@link PlayerRangedAttackGoal} 蓄力 20 tick）
 * 后调用原生 {@code releaseUsingItem()} 触发 {@code BowItem.releaseUsing} 流程——
 * 由原版逻辑扣除背包真实箭矢、用原生弹道射出并广播生成包，客户端拉弓/飞行动画
 * 免费获得，服务端权威。（注意 {@code stopUsingItem()} 是静默收弓不发射，勿混用）
 * 背包无箭时不射（进入重试等待），不凭空补充。</p>
 *
 * <p><b>避日双决策（原版同构）</b>：RestrictSun（pri 2，不要求着火）开寻路避日
 * 开关；FleeSun（pri 3）着火后找躲避点撤离。晒伤本体属身体（玩家不被日照点燃，
 * 如实披露），假人仅在受火焰等真实伤害着火时触发逃生。</p>
 *
 * <p><b>风筝走位</b>：目标进入射程（15 格）后站定射击，过近由 strafe 拉开
 * （对应原版骷髅绕着目标拉距离）。</p>
 */
public class SkeletonBrain extends PlayerBrainController {

    /** 武器形态两 Goal（reassessWeaponGoal 动态换装，恒只装其一） */
    private final PlayerRangedAttackGoal rangedGoal;
    private final PlayerMeleeAttackGoal meleeGoal;

    /** 武器装配三态：NONE=尚未装配（挂脑初始），装配后只会在 MELEE/RANGED 间切换 */
    private enum WeaponForm { NONE, MELEE, RANGED }

    private WeaponForm weaponForm = WeaponForm.NONE;

    public SkeletonBrain(ServerPlayer player) {
        super(player);
        this.rangedGoal = new PlayerRangedAttackGoal(this.prowler, 1.0, 40, 15.0F);
        this.meleeGoal = new PlayerMeleeAttackGoal(this.prowler, 1.2, false);
    }

    @Override
    public String modeKey() {
        return "skeleton";
    }

    @Override
    protected void assemble() {
        // 目标链（原版 AbstractSkeleton.registerGoals 同位，均不看武器）：
        // 被打反击（HurtByTargetGoal pri 1）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标：最近的存活玩家（原版 NearestAttackableTargetGoal(Player, mustSee) pri 2；
        // 范围 = 原版 FOLLOW_RANGE 16（26.3 字节码：Mob.createMobAttributes 基座 16，该生物未覆写））
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true, this.playerTargetPredicate()));
        // pri 3 铁傀儡与幼年海龟（原版目标链基座同位）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 16.0, 10, true,
                g -> g.isAlive()));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 16.0, 10, true,
                t -> t.isBaby() && !t.isInWater()));
        // 行为链（原版同位）：RestrictSun 2 / FleeSun 3 / 规避狼 3 / 武器形态 4 /
        // 漫游 5 / 看人 6 / 张望 6
        this.goalSelector.addGoal(2, new PlayerRestrictSunGoal(this.prowler));
        this.goalSelector.addGoal(3, new PlayerFleeSunGoal(this.prowler));
        this.goalSelector.addGoal(3, new PlayerAvoidEntityGoal<>(
                this.prowler, Wolf.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(5, new PlayerRandomStrollGoal(this.prowler, 1.0));
        this.goalSelector.addGoal(6, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new PlayerRandomLookAroundGoal(this.prowler));
        // 挂脑强制完成首次武器装配：NONE 态不参与"无变化提前返回"，
        // 空手/持剑挂脑同样立即装上近战形态
        this.weaponForm = WeaponForm.NONE;
        this.reassessWeaponGoal();
    }

    @Override
    public void tick() {
        // 原版 reassessWeaponGoal 由 setItemSlot/出生触发；玩家主手可每 tick 变化，
        // 移植版在决策前比对持弓状态（无变化时零开销），换武器形态即时切换
        this.reassessWeaponGoal();
        super.tick();
    }

    /**
     * 玩家索敌谓词：只做身份/存活/模式门，<b>不看武器</b>（原版同构——目标选择与
     * 攻击方式分离，持剑/空手照常索敌并近战接敌）。
     */
    protected java.util.function.Predicate<Player> playerTargetPredicate() {
        return p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    /**
     * 原版 AbstractSkeleton.reassessWeaponGoal：移除两种形态后按持弓情况装回
     * 同位 4 的其一——持弓装远程（攻击间隔按难度：移植版固定 40 tick，披露
     * 原版硬核 20 的差异），否则装近战 MeleeAttackGoal(1.2, false)。
     * protected 供离线测试直驱（换武器形态的成员管理）。
     */
    protected void reassessWeaponGoal() {
        WeaponForm desired = this.prowler.isHolding(stack -> stack.is(Items.BOW))
                ? WeaponForm.RANGED
                : WeaponForm.MELEE;
        if (desired == this.weaponForm) {
            return; // 形态无变化：保持既有 Goal，不重置攻击过程
        }
        if (desired == WeaponForm.RANGED) {
            this.goalSelector.removeGoal(this.meleeGoal);
            this.goalSelector.addGoal(4, this.rangedGoal);
        } else {
            this.goalSelector.removeGoal(this.rangedGoal);
            this.goalSelector.addGoal(4, this.meleeGoal);
        }
        this.weaponForm = desired;
    }
}
