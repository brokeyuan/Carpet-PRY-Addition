package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerGroupHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;

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

/**
 * 卫道士模式脑（装配器）：26.3 字节码核实的原版装配。
 *
 * <p>目标链 = 反击+警报（pri 1，原版 {@code setAlertOthers(Raider)}——脑间等价
 * 为掠夺者/卫道士假人）→ 玩家（pri 2，mustSee，FOLLOW_RANGE 12）→ 村民
 * （pri 3，mustSee）/ 铁傀儡（pri 3）。行为链 = 近战 pri 5
 * （{@code MeleeAttackGoal(1.0, false)}）→ 漫游 pri 8（0.6）→ 看人 pri 9。
 * 规避 Creaking（pri 1）1.21.4+ 编入。</p>
 *
 * <p>不做（原版有、移植跳过）：破门/开门（方块交互 = 身体）、
 * {@code HoldGroundAttackGoal}（袭击巡逻队上下文）、Johnny 彩蛋
 * （{@code VindicatorJohnnyAttackGoal}，袭击上下文）。</p>
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
        // 反击 + 警报掠夺者/卫道士假人（原版 setAlertOthers(Raider) 的脑间等价）
        this.targetSelector.addGoal(1, new PlayerGroupHurtByTargetGoal(
                this.prowler, Set.of("pillager", "vindicator")));
        // pri 2 玩家（范围 = 原版 FOLLOW_RANGE 12，26.3 字节码：Vindicator
        // 显式覆写 12，与蛮兵同值）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 12.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // pri 3 村民 / 铁傀儡（原版均 mustSee）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 12.0, 10, true,
                v -> v.isAlive()));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 12.0, 10, true,
                g -> g.isAlive()));
        // pri 1 规避 Creaking（原版 AvoidEntityGoal<Creaking>(8, 1.0, 1.2)；
        // Creaking 1.21.4+）
        //#if MC >= 12104
        this.goalSelector.addGoal(1, new PlayerAvoidEntityGoal<>(
                this.prowler, net.minecraft.world.entity.monster.creaking.Creaking.class, 8.0F, 1.0, 1.2));
        //#endif
        // 行为：近战（原版 MeleeAttackGoal pri 5）
        this.goalSelector.addGoal(5, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 漫游（原版 RandomStrollGoal pri 8，速度 0.6）
        this.goalSelector.addGoal(8, new PlayerRandomStrollGoal(this.prowler, 0.6));
        // 空闲看人（原版 LookAtPlayerGoal pri 9，15 格）
        this.goalSelector.addGoal(9, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 15.0F));
    }
}
