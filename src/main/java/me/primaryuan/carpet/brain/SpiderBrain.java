package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 蜘蛛模式脑（装配器）：白天中立/夜间敌对。
 *
 * <p>与僵尸模式的差别：目标选择谓词里加入 {@code !level().isDay()} 门控——
 * 白天无攻击目标（转为随机漫步），夜晚才能锁定玩家；目标随昼夜切换自然
 * 激活/停用（选择器按谓词复核，不会 add/remove 抖动）。</p>
 */
public class SpiderBrain extends PlayerBrainController {

    public SpiderBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "spider";
    }

    @Override
    protected void assemble() {
        // 只在夜间（外界不明亮）把玩家当作目标；白天该目标 canUse 恒 false
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !this.prowler.levelIsBrightOutside()));
        // 夜间高速追击（1.2 → 疾跑），白天漫步无所事事
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.2, true));
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 0.6));
    }
}