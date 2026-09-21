package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 僵尸模式脑（装配器）：原版近战追击 AI——自动追逐最近的存活玩家（含其它假人，
 * 创造/旁观者不可见不可选），进入玩家原生攻击距离后用原生 {@code attack()} 挥砍；
 * 遇障碍/台阶由移动控制器原生跳跃。
 *
 * <p>装配 = 目标选择（{@code PlayerNearestAttackableTargetGoal}）+ 行为
 * （{@code PlayerMeleeAttackGoal}），全部是移植版原版 Goal，算法 1:1。</p>
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
        // 被打反击：谁打我锁谁（原版 HurtByTargetGoal，优先级 1 压过常规索敌）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标：最近的存活玩家（视野内 mustSee；10 tick 节流搜索）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // 行为：近战追击（速度 1.0 = 正常行走）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 空闲漫游（原版僵尸同样挂 RandomStrollGoal）：丢目标后散步而非原地挂机
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}