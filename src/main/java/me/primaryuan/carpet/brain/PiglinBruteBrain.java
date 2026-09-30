package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 猪灵蛮兵模式脑（装配器）：恒敌对近战——无视金装、无远程、不捡拾。
 *
 * <p>原版蛮兵为 Brain 系统驱动（{@code PiglinBruteAi}），GoalSelector 优先级
 * 无法 1:1 对照，按 Wiki 行为骨架等价装配：反击（pri 1）+ 玩家（pri 2，
 * 无金装豁免——蛮兵见人就打，与普通猪灵的"看装行事"相反）+ 近战（pri 2，
 * {@code doHurtTarget} 走玩家原生攻击管线）+ 漫游（pri 4）。和平难度由
 * 玩家目标门禁统一拦截（与原版怪物和平不索敌一致）。</p>
 *
 * <p>不做：猎疣猪兽（群体狩猎协调）、以物易物/拾取（蛮兵本就不捡拾）。</p>
 */
public class PiglinBruteBrain extends PlayerBrainController {

    public PiglinBruteBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "piglinbrute";
    }

    @Override
    protected void assemble() {
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // pri 2 玩家：无金装豁免（蛮兵恒敌对）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // 行为：近战兜底
        this.goalSelector.addGoal(2, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 漫游
        this.goalSelector.addGoal(4, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
