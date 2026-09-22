package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;

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
 * <p>不做物物交换（barter）与猎杀 hoglin——保持战斗 AI 的最小完整闭环。</p>
 */
public class PiglinBrain extends PlayerBrainController {

    public PiglinBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "piglin";
    }

    @Override
    protected void assemble() {
        // 被打反击：无论攻击者穿什么（与原版猪灵被激怒一致）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 敌视不穿金装的玩家（原版判定：全身均为猪灵可接受盔甲才算中立；
        // 1.21~1.21.1 叫 isWearingGold，1.21.3+ 更名 isWearingSafeArmor）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        //#if MC >= 12103
                        && !PiglinAi.isWearingSafeArmor(p)));
                        //#else
                        //$$ && !PiglinAi.isWearingGold(p)));
                        //#endif
        // 行为：近战追击（1.0）+ 空闲漫游
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
