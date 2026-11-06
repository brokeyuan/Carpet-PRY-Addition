package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerFoxEatBerriesGoal;
import me.primaryuan.carpet.brain.goal.PlayerFoxEatFoodGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.rabbit.Rabbit;

/**
 * 狐狸模式脑（装配器）：采莓 + 进食 + 小猎物捕食 + 规避，昼间休整（姿态披露）。
 *
 * <p>采莓/进食契约见两个 Goal（26.3 结构核实）。猎物 = 鸡/兔（原版狐狸猎物
 * 子集；水生猎物不在玩家寻路能力内，如实披露）；扑击 = 玩家普通跳跃+近战
 * （不写特殊腾空速度，披露差异）；昼间休息姿态（原版躺卧）玩家身体无对应
 * 姿态，不伪造睡眠成功。</p>
 */
public class FoxBrain extends PlayerBrainController {

    public FoxBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "fox";
    }

    @Override
    protected void assemble() {
        // 被打反击（原版 HurtByTarget 语义）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 猎物：鸡/兔（原版猎物子集）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(this.prowler, Chicken.class, 12.0, true, c -> true));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(this.prowler, Rabbit.class, 12.0, true, r -> true));
        // 近战（扑击以普通跳跃+近战表达）
        this.goalSelector.addGoal(3, new PlayerMeleeAttackGoal(this.prowler, 1.2, true));
        // 采莓（MOVE）
        this.goalSelector.addGoal(4, new PlayerFoxEatBerriesGoal(this.prowler));
        // 规避狼（原版狐狸怕狼）
        this.goalSelector.addGoal(1, new PlayerAvoidEntityGoal<>(this.prowler,
                net.minecraft.world.entity.animal.wolf.Wolf.class, 8.0F, 1.0, 1.3));
        // 进食（饥饿时用真实食物）
        this.goalSelector.addGoal(5, new PlayerFoxEatFoodGoal(this.prowler));
        this.goalSelector.addGoal(6, new PlayerRandomStrollGoal(this.prowler, 0.6));
    }
}
