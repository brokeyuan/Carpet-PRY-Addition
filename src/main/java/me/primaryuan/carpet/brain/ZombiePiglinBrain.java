package me.primaryuan.carpet.brain;

import java.util.Set;

import me.primaryuan.carpet.brain.goal.PlayerGroupHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 260102
//$$ import me.primaryuan.carpet.brain.goal.PlayerSpearAttackGoal;
//#endif
import net.minecraft.server.level.ServerPlayer;

/**
 * 僵尸猪灵模式脑（装配器）：中立——被打才反击，并唤醒周围同类群体仇恨。
 *
 * <p>装配按 26.3 字节码核实（addBehaviourGoals）：长矛（26.1.2+）pri 1 /
 * 近战 pri 2（{@code ZombieAttackGoal(1.0, false)}）/ 漫游 pri 7。
 * 原版的目标链经 PersistentAnger 中立仇恨系统驱动（被伤 → 持续愤怒 400~800
 * tick → {@code alertOthers} 群体广播）；移植版无愤怒状态机，等价取其行为骨架：
 * 仅"被打反击 + 群体警报"两个来源可建立仇恨（{@link PlayerGroupHurtByTargetGoal}，
 * 只警报 zombiepiglin 同类），无常规索敌——不被激怒绝不主动攻击。</p>
 *
 * <p>不做：僵尸化转化（身体转化）、愤怒时长衰减（状态机细节）。</p>
 */
public class ZombiePiglinBrain extends PlayerBrainController {

    public ZombiePiglinBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "zombiepiglin";
    }

    @Override
    protected void assemble() {
        // 反击 + 群体警报（原版愤怒广播的同类版；玩家攻击者受和平/创造/旁观门禁）
        this.targetSelector.addGoal(1, new PlayerGroupHurtByTargetGoal(
                this.prowler, Set.of("zombiepiglin")));
        // 长矛刺击（26.1.2+，原版 SpearUseGoal pri 1）；1.21.x 不编入
        //#if MC >= 260102
//$$         this.goalSelector.addGoal(1, new PlayerSpearAttackGoal(this.prowler, 1.0));
        //#endif
        // 近战（原版 ZombieAttackGoal pri 2）
        this.goalSelector.addGoal(2, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 漫游（原版 WaterAvoidingRandomStrollGoal pri 7）
        this.goalSelector.addGoal(7, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
