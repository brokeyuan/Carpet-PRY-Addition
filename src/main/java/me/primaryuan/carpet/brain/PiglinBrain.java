package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 260102
//$$ import me.primaryuan.carpet.brain.goal.PlayerSpearAttackGoal;
//#endif
//#if MC >= 12111
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
//#else
//$$ import net.minecraft.world.entity.animal.Chicken;
//$$ import net.minecraft.world.entity.monster.WitherSkeleton;
//$$ import net.minecraft.world.entity.monster.ZombifiedPiglin;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 猪灵模式脑（装配器）：敌视不穿金装的玩家，对金装玩家保持中立。
 *
 * <p>目标选择直接复用原版判定 {@code PiglinAi#isWearingSafeArmor(LivingEntity)}
 * （1.21~1.21.1 叫 {@code isWearingGold}，1.21.3 起更名，语义同为"全身盔甲
 * 均为金质/猪灵可接受装备"）——与原版猪灵的"看装行事"完全一致：</p>
 * <ul>
 *   <li>不穿金装的玩家 → 敌对，近战追击（原版猪灵徒手/持金剑均近战）；</li>
 *   <li>穿金装的玩家 → 中立，不主动索敌；</li>
 *   <li>被谁打都还手（{@code HurtByTargetGoal}，优先级 1，无论对方穿什么）；</li>
 *   <li>无事随机漫步。</li>
 * </ul>
 * <p><b>武器决定战斗方式（对齐 Wiki：金剑近战/弩远程/金矛冲锋）</b>：三个
 * 行为 Goal 并存、靠 canUse 的持械判定自然互斥——持矛（26.1.2+，{@code
 * PlayerSpearAttackGoal}）优先，其次持弩（{@code PlayerRangedAttackGoal}，
 * 射击节奏≈2 秒/发、射程 8 格、射击时不左右移动），徒手/金剑走近战兜底；
 * 更换手中武器后下一评估周期自动切换战斗方式。</p>
 *
 * <p><b>其余 Wiki 对齐</b>：和平难度下不与玩家敌对（目标谓词门控）；立刻
 * 敌对 16 格内的凋灵骷髅；非敌对态主动远离僵尸猪灵（{@code PlayerAvoidEntityGoal}）；
 * 被谁打都还手（HurtBy，即"被攻击后群体反击"的单体版）。不做（红线/范围）：
 * 僵尸化（身体转化）、以物易物与拾取装备（物品系统状态机）、灵魂火方块规避
 * （无方块感知规避引擎）、疣猪兽 30 秒群攻冷却（群体协调）。</p>
 */
public class PiglinBrain extends PlayerBrainController {

    public PiglinBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "piglin";
    }

    /** Wiki：和平难度中成年猪灵不会与玩家敌对 */
    private boolean isPeaceful() {
        return this.player.level().getDifficulty() == Difficulty.PEACEFUL;
    }

    @Override
    protected void assemble() {
        // 被打反击：无论攻击者穿什么（与原版猪灵被激怒一致，大范围敌对的落地方案）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 敌视不穿金装的玩家（原版判定：全身均为猪灵可接受盔甲才算中立；
        // 1.21~1.21.1 叫 isWearingGold，1.21.3+ 更名 isWearingSafeArmor）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !this.isPeaceful()
                        //#if MC >= 12103
                        && !PiglinAi.isWearingSafeArmor(p)));
                        //#else
                        //$$ && !PiglinAi.isWearingGold(p)));
                        //#endif
        // 立刻敌对 16 格内的凋灵骷髅（Wiki：成年猪灵会立刻主动攻击）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, WitherSkeleton.class, 16.0, 10, true,
                w -> w.isAlive()));
        // 行为：
        // pri 0 远离僵尸猪灵（非敌对态的天敌规避；战斗激活时被更高优先级抢占）
        this.goalSelector.addGoal(0, new PlayerAvoidEntityGoal<>(
                this.prowler, ZombifiedPiglin.class, 12.0F, 0.9, 1.35));
        // pri 1 金矛冲锋（26.1.2+；Wiki：金矛的冲锋攻击，拉开距离后再冲）
        //#if MC >= 260102
//$$         this.goalSelector.addGoal(1, new PlayerSpearAttackGoal(this.prowler, 1.0));
        //#endif
        // pri 1 弩远程：射击不左右移动（Wiki：猪灵射箭时不左右移动，与弓类不同）；
        // canUse 要求主手是弩（含持弩上弦状态机），与矛/近战按武器自然互斥
        this.goalSelector.addGoal(1, new PlayerRangedAttackGoal(
                this.prowler, 1.0, 20, 8.0F, Items.CROSSBOW, 25, false));
        // pri 2 金剑/徒手近战兜底（持矛/弩时被上两者抢占；换武器自动切换）
        this.goalSelector.addGoal(2, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 空闲漫游
        this.goalSelector.addGoal(3, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
