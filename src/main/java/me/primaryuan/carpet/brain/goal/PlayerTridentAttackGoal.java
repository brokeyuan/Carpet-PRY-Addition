package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 移植版三叉戟投掷目标（← 原版 {@code DrownedTridentAttackGoal}，26.3 字节码：
 * 速度 1.0 / 评估间隔 40 tick / 射程 10 格）。
 *
 * <p>移动逼近、风筝走位、视线节流全部复用 {@link PlayerRangedAttackGoal}
 * 的弓类状态机，仅换两处弹药语义：</p>
 * <ul>
 *   <li>三叉戟无独立弹药（{@code getProjectile} 恒空）——弹药门改为"主手三叉戟
 *       耐久完好"；</li>
 *   <li>蓄力门槛 10 tick（原版 {@code TridentItem.THROW_THRESHOLD_TIME}，26.3
 *       字节码 {@code if_icmpge 10}），满蓄后 {@code releaseUsingItem()} 触发原生
 *       {@code TridentItem.releaseUsing}——被掷出的就是玩家主手的真三叉戟
 *       （忠诚回收/耐久扣除/命中伤害全原生），零凭空造物。</li>
 * </ul>
 * <p>激流三叉戟在陆地 {@code use()} 会拒绝起手（原版仅水中/雨天可掷），
 * {@code startUsingItem} 后未进入使用态即进入 40 tick 冷却，等落水/下雨再试。</p>
 */
public class PlayerTridentAttackGoal extends PlayerRangedAttackGoal {

    public PlayerTridentAttackGoal(PryMob mob) {
        super(mob, 1.0, 40, 10.0F, Items.TRIDENT, 10, true);
    }

    @Override
    protected boolean hasAmmo(ItemStack weapon) {
        return weapon.getDamageValue() < weapon.getMaxDamage();
    }
}
