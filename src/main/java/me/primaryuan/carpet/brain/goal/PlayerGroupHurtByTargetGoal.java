package me.primaryuan.carpet.brain.goal;

import java.util.Set;

import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.brain.PryMob;

/**
 * 带群体警报的"被打反击"目标（← 原版 {@code HurtByTargetGoal.setAlertOthers} 的
 * 脑间等价实现；溺尸/僵尸猪灵的群体仇恨）。
 *
 * <p>锁定攻击者的同时，把仇恨广播给指定脑模式的邻近假人——原版扫描框为
 * {@code inflate(跟随距离, 10, 跟随距离)}（溺尸/僵尸猪灵跟随距离 35），移植版
 * 统一取 20 格水平 / 10 格垂直。只唤醒"当前没有攻击目标"的同伴（原版
 * {@code alertOthers} 跳过已锁目标个体）；玩家类攻击者沿用全局门禁
 * （创造/旁观/和平难度不传播）。脑间广播零额外实体：目标经
 * {@code setTarget} 直写同伴的攻击目标字段，同伴的近战 Goal 自然接管追击。</p>
 */
public class PlayerGroupHurtByTargetGoal extends PlayerHurtByTargetGoal {

    /** 被警报的脑子模式（如溺尸警报 drowned + zombiepiglin） */
    private final Set<String> alertModes;

    public PlayerGroupHurtByTargetGoal(PryMob mob, Set<String> alertModes) {
        super(mob, null);
        this.alertModes = alertModes;
    }

    @Override
    public void start() {
        super.start();
        if (this.pendingTarget != null) {
            BrainManager.alertOthers(this.mob, this.pendingTarget, this.alertModes);
        }
    }
}
