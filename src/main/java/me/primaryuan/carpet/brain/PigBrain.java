package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;

/**
 * 猪模式脑（装配器）：完全中立，被打就跑，闲时散步。
 *
 * <p>对齐原版猪：无攻击目标（targetSelector 空置，永不主动索敌）、
 * 被伤害/着火时恐慌逃跑（{@code PanicGoal}）、无事随机漫步
 * （{@code RandomStrollGoal}）。与村民模式的差别：不专门规避僵尸
 * （原版猪不会像村民那样主动远离僵尸，僵尸来打它时它才跑）。</p>
 */
public class PigBrain extends PlayerBrainController {

    public PigBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "pig";
    }

    @Override
    protected void assemble() {
        // 被打/着火 → 恐慌狂奔（100 tick 时间盒内有效）
        this.goalSelector.addGoal(0, new PlayerPanicGoal(this.prowler));
        // 无事 → 随机散步
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 0.6));
    }
}
