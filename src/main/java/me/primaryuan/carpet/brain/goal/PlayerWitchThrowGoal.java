package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.BrainTeams;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.entity.LivingEntity;

/**
 * 女巫投掷目标（← 原版 {@code RangedAttackGoal(1.0, 60, 10.0F)} +
 * {@code Witch.performRangedAttack} 决策树，26.3 字节码核实）。
 *
 * <p><b>原版决策树（26.3 performRangedAttack 字节码）</b>：目标为 Raider 的
 * 援助分支不适用于本模式的玩家目标；玩家目标：距离 ≥8 且无缓慢 → 缓慢；
 * 目标血量 ≥8 且无中毒 → 中毒；距离 ≤3 且无虚弱且 25% 掷 → 虚弱；否则伤害。
 * （旧注释把中毒条件写成 &lt;8，与字节码相反。）</p>
 *
 * <p><b>节拍</b>：原版 RangedAttackGoal 首 tick 排期 60t，之后每 60t 出手一次；
 * 持续 moveTo 目标直到进入 10 格且连续看见 5t，再看 30°/tick 转头。这里完整
 * 复刻持续接近/瞄准/出手/冷却；出手前复核存活、同维、非友军、视线与射程。
 * 药水只来自背包真实库存，原生右键投掷，不生成弹射物。</p>
 */
public class PlayerWitchThrowGoal extends PlayerGoal {

    /** 原版决策树常量（26.3 performRangedAttack 字节码） */
    private static final double SLOWNESS_MIN_DIST = 8.0;
    private static final float POISON_MIN_TARGET_HEALTH = 8.0F;
    private static final double WEAKNESS_MAX_DIST = 3.0;
    private static final float WEAKNESS_CHANCE = 0.25F;
    /** 原版 Witch 的 RangedAttackGoal(1.0, 60, 10.0F)：射程 10、间隔 60t */
    private static final double THROW_RANGE = 10.0;
    private static final double THROW_RANGE_SQ = THROW_RANGE * THROW_RANGE;
    private static final int ATTACK_INTERVAL_TICKS = 60;
    /** 原版 RangedAttackGoal 的 seeTime 门槛：连续看见 5 tick 才驻足投掷 */
    private static final int SEE_TIME_THRESHOLD = 5;

    protected final PryMob mob;
    /** 本次持续交战锁定的目标 */
    private LivingEntity target;
    /** 距下次出手的 tick（-1 = 本轮尚未排期，对齐 RangedAttackGoal 初始值） */
    private int attackTime = -1;
    /** 连续看见目标的 tick 数 */
    private int seeTime;

    public PlayerWitchThrowGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(1);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = this.mob.getTarget();
        if (!this.isValidTarget(candidate) || this.selfUsingItem()) {
            return false;
        }
        // 决策只挑“对外攻击药水”；自我治疗由 DrinkGoal 负责，绝不因自己
        // 低血把治疗/再生喷溅药当成对敌默认弹。
        Holder<Potion> wanted = this.decidePotion(candidate);
        return wanted != null && this.findPotionStack(wanted) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.isValidTarget(this.mob.getTarget());
    }

    @Override
    public void start() {
        this.target = this.mob.getTarget();
        this.attackTime = -1;
        this.seeTime = 0;
        if (this.target != null) {
            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        }
    }

    @Override
    public void tick() {
        LivingEntity current = this.mob.getTarget();
        if (!this.isValidTarget(current)) {
            return;
        }
        this.target = current;
        double distSq = this.mob.distanceToSqr(current);
        boolean seen = this.hasLineOfSight(current);
        this.seeTime = seen ? this.seeTime + 1 : 0;

        // 接近：超出射程或尚未稳定看见 → 持续走向目标；否则驻足
        if (distSq > THROW_RANGE_SQ || this.seeTime < SEE_TIME_THRESHOLD) {
            this.mob.getNavigation().moveTo(current, 1.0);
        } else {
            this.mob.getNavigation().stop();
        }
        this.mob.getLookControl().setLookAt(current, 30.0F, 30.0F);

        // 节拍：首 tick 排期 60t（原版 attackTime<0 分支），之后每 60t 才允许
        // 一次出手；任何路径都不会每 2 tick 重开并耗药。
        if (--this.attackTime == 0) {
            this.tryThrow(current, distSq, seen);
        } else if (this.attackTime < 0) {
            this.attackTime = ATTACK_INTERVAL_TICKS;
        }
    }

    /** 出手前复核目标存活/同维/非友军/视线/射程，再按真实库存使用原生投掷 */
    private void tryThrow(LivingEntity current, double distSq, boolean seen) {
        if (!this.isValidTarget(current) || !seen || distSq > THROW_RANGE_SQ) {
            return;
        }
        Holder<Potion> wanted = this.decidePotion(current);
        if (wanted == null) {
            return; // 缺药：不出手、不假装消耗
        }
        ItemStack stack = this.findPotionStack(wanted);
        if (stack == null) {
            return;
        }
        if (!this.equipPotion(stack)) {
            return;
        }
        // 瞄准实际更新到玩家朝向后才使用物品（弹道由视线决定）
        this.mob.lookAt(current, 180.0F, 180.0F);
        if (this.mob.pryUseItemInHand(InteractionHand.MAIN_HAND)) {
            this.attackTime = ATTACK_INTERVAL_TICKS;
        }
    }

    @Override
    public void stop() {
        if (this.mob.isUsingItem()) {
            this.mob.stopUsingItem();
        }
        this.mob.getNavigation().stop();
        this.target = null;
        this.attackTime = -1;
        this.seeTime = 0;
    }

    /** 决策树测试缝（走生产 decidePotion） */
    Holder<Potion> decideForTest(LivingEntity target) {
        return this.decidePotion(target);
    }

    /**
     * 原版 26.3 performRangedAttack 决策树（玩家目标不进入 Raider 援助分支）：
     * 距离 ≥8 且无缓慢 → 缓慢；目标血量 ≥8 且无中毒 → 中毒；
     * 距离 ≤3 且无虚弱且 25% 掷 → 虚弱；否则伤害。只返回真实库存内可投类型。
     */
    private Holder<Potion> decidePotion(LivingEntity target) {
        double dist = Math.sqrt(this.mob.distanceToSqr(target));
        if (dist >= SLOWNESS_MIN_DIST
                && !this.targetHasEffect(target, net.minecraft.world.effect.MobEffects.SLOWNESS)
                && this.hasPotionStack(Potions.SLOWNESS)) {
            return Potions.SLOWNESS;
        }
        if (this.targetHealth(target) >= POISON_MIN_TARGET_HEALTH
                && !this.targetHasEffect(target, net.minecraft.world.effect.MobEffects.POISON)
                && this.hasPotionStack(Potions.POISON)) {
            return Potions.POISON;
        }
        if (dist <= WEAKNESS_MAX_DIST
                && !this.targetHasEffect(target, net.minecraft.world.effect.MobEffects.WEAKNESS)
                && this.mob.getRandom().nextFloat() < WEAKNESS_CHANCE
                && this.hasPotionStack(Potions.WEAKNESS)) {
            return Potions.WEAKNESS;
        }
        return this.hasPotionStack(Potions.HARMING) ? Potions.HARMING : null;
    }

    /** 目标合法性：非自己、存活、同维、非友军、玩家非创造/旁观 */
    private boolean isValidTarget(LivingEntity candidate) {
        if (candidate == null || candidate == this.mob.asLiving()
                || !candidate.isAlive() || candidate.level() != this.mob.level()) {
            return false;
        }
        if (candidate instanceof net.minecraft.world.entity.player.Player p
                && (p.isSpectator() || p.isCreative())) {
            return false;
        }
        return !BrainTeams.isTeamFriendly(this.mob.asLiving(), candidate);
    }

    /** 把指定药水选中到手（真实背包槽；不凭空生成） */
    private boolean equipPotion(ItemStack stack) {
        var inv = this.mob.asPlayer().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                if (i < net.minecraft.world.entity.player.Inventory.getSelectionSize()) {
                    pryEquipSlot(inv, i);
                } else {
                    inv.pickSlot(i);
                }
                return true;
            }
        }
        return false;
    }

    /** 使用状态缝（26.3 走数据同步器，桩世界不可读 → 测试覆写） */
    protected boolean selfUsingItem() {
        return this.mob.asLiving().isUsingItem();
    }

    /** 视线读取缝（桩世界射线不可达 → 测试覆写） */
    protected boolean hasLineOfSight(LivingEntity target) {
        return this.mob.getSensing().hasLineOfSight(target);
    }

    /** 目标生命读取缝（26.3 生命走数据同步器） */
    protected float targetHealth(LivingEntity target) {
        return target.getHealth();
    }

    /** 自身生命读取缝（保留测试覆写面；投掷决策不再使用自身血量） */
    protected float selfHealth() {
        return this.mob.asLiving().getHealth();
    }

    /** 目标药效查询缝（26.3 走数据同步器） */
    protected boolean targetHasEffect(LivingEntity target, Holder<net.minecraft.world.effect.MobEffect> effect) {
        return target.hasEffect(effect);
    }

    /** 背包内是否持有指定药水的喷溅/滞留药水（真实库存） */
    private ItemStack findPotionStack(Holder<Potion> potion) {
        var inv = this.mob.asPlayer().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.SplashPotionItem)
                    && !(stack.getItem() instanceof net.minecraft.world.item.LingeringPotionItem)) {
                continue;
            }
            PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()
                    && contents.potion().get().is(potion)) {
                return stack;
            }
        }
        return null;
    }

    private boolean hasPotionStack(Holder<Potion> potion) {
        return this.findPotionStack(potion) != null;
    }
}
