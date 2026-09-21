package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerStareAggroGoal;
import net.minecraft.server.level.ServerPlayer;

/**
 * 末影人模式脑（装配器）：被凝视激怒 + 疾跑扑击。
 *
 * <p>装配：</p>
 * <ul>
 *   <li>targetSelector 优先级 1 —— 凝视激怒（{@code PlayerStareAggroGoal}：
 *       谁盯着看就锁定谁，并调用原生 {@code setSprinting(true)}）；</li>
 *   <li>goalSelector 优先级 1 —— 原版近战 Goal（{@code PlayerMeleeAttackGoal}，
 *       速度 1.2 → 疾跑追击，移动控制会自动维持原生疾跑标志）。</li>
 * </ul>
 *
 * <p>与原版末影人的差异：不做传送（红线：绝不直接修改坐标），
 * 激怒后表现为"盯着你疾跑冲脸"。</p>
 */
public class EndermanBrain extends PlayerBrainController {

    public EndermanBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "enderman";
    }

    @Override
    protected void assemble() {
        // 被谁盯上就打谁（凝视算法 = 原版 isStaredAt 的逐行移植）
        this.targetSelector.addGoal(1, new PlayerStareAggroGoal(this.prowler));
        // 疾跑近战扑击（速度 1.2 → 移动控制自动挂原生疾跑）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.2, true));
    }
}
