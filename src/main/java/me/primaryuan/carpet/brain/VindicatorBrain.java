package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 卫道士模式脑（装配器）：26.3 字节码核实的原版装配。
 *
 * <p>目标链 = 反击（pri 1）→ 玩家（pri 2，mustSee）→ 村民 / 铁傀儡（pri 3）。
 * 行为链 = 近战 pri 5（{@code MeleeAttackGoal(1.0, false)}）→ 漫游 pri 8。</p>
 *
 * <p>不做（原版有、移植跳过）：破门/开门（方块交互 = 身体）、
 * {@code HoldGroundAttackGoal}（袭击巡逻队状态，假人无袭击上下文）、
 * Johnny 彩蛋（全体敌对 Goal，由 {@code "Johnny"} 自定义名触发的攻击一切行为）。</p>
 */
public class VindicatorBrain extends PlayerBrainController {

    public VindicatorBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "vindicator";
    }

    @Override
    protected void assemble() {
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // pri 2 玩家
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // pri 3 村民 / 铁傀儡（原版均 mustSee）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 20.0, 10, true,
                v -> v.isAlive()));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 20.0, 10, true,
                g -> g.isAlive()));
        // 行为：近战（原版 MeleeAttackGoal pri 5）
        this.goalSelector.addGoal(5, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 漫游（原版 RandomStrollGoal pri 8）
        this.goalSelector.addGoal(8, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
