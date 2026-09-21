package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 12111
// 1.21.11 起僵尸类迁入 monster.zombie 子包
import net.minecraft.world.entity.monster.zombie.Zombie;
//#else
//$$ import net.minecraft.world.entity.monster.Zombie;
//#endif
import net.minecraft.server.level.ServerPlayer;

/**
 * 村民模式脑（装配器）：无仇恨、随机漫步、遇僵尸反向逃跑。
 *
 * <p><b>没有仇恨</b>：不装配任何目标选择器（TargetSelector 空置），
 * 假人永远不会主动锁定攻击目标——对应村民"中立单位、不主动攻击"。</p>
 *
 * <p>行为栈（全部 MOVE 旗标，优先级升序，同刻只跑一个）：</p>
 * <ul>
 *   <li>优先级 0 —— 恐慌逃跑（{@code PlayerPanicGoal}）：被攻击/着火时朝反方向
 *       狂奔逃生，反应快（2 tick 重评）；</li>
 *   <li>优先级 1 —— 规避僵尸（{@code PlayerAvoidEntityGoal}）：僵尸进入 12 格
 *       视野即反向逃离，随距离拉远降速，这层接过恐慌的接力；</li>
 *   <li>优先级 2 —— 随机漫步（{@code PlayerRandomStrollGoal}）：无威胁时在出生点
 *       附近随便溜达（对应村民"在家附近闲逛"）。</li>
 * </ul>
 */
public class VillagerBrain extends PlayerBrainController {

    public VillagerBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "villager";
    }

    @Override
    protected void assemble() {
        // 没有被攻击 → 恐慌不激活；有僵尸逼近 → 规避接管；风平浪静 → 漫步兜底
        this.goalSelector.addGoal(0, new PlayerPanicGoal(this.prowler));
        // 规避僵尸：12 格生效、先走 0.9 后疾跑 1.35（越远甩得越快）
        this.goalSelector.addGoal(1, new PlayerAvoidEntityGoal<>(
                this.prowler, Zombie.class, 12.0F, 0.9, 1.35));
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 0.6));
    }
}