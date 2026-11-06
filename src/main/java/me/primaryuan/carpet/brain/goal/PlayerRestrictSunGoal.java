package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.EnumSet;

/**
 * 移植版避日寻路开关目标（← 原版 {@code RestrictSunGoal}，骷髅基座 pri 2）。
 *
 * <p>与 {@link PlayerFleeSunGoal} 是两个独立决策（原版同构）：</p>
 * <ul>
 *   <li>本 Goal：<b>不要求着火</b>——白天亮、头盔为空即开启寻路器的避日开关
 *       （{@code GroundPathNavigation.setAvoidSun}），让 A* 在荫蔽中把路径截断
 *       到露天节点之前，负责"日常路线避开阳光"；</li>
 *   <li>FleeSun：着火后才触发的一次性逃生，负责"已经烧起来的撤离"。</li>
 * </ul>
 *
 * <p>原版 canUse 三条（26.3 字节码）：{@code isBrightOutside}、头盔为空、
 * 具备地面寻路器；无旗标（EnumSet 空集）——与移动类 Goal 并行运行。
 * start 置 avoidSun=true，stop 恢复 false。</p>
 */
public class PlayerRestrictSunGoal extends PlayerGoal {

    private final PryMob mob;

    public PlayerRestrictSunGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(PlayerGoal.Flag.class));
        this.setInterval(1); // 原版无内部节流：每 2 tick 评估一次
    }

    @Override
    public boolean canUse() {
        if (!this.mob.levelIsBrightOutside()) {
            return false;
        }
        return this.mob.asLiving().getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    @Override
    public void start() {
        this.mob.getNavigation().setAvoidSun(true);
    }

    @Override
    public void stop() {
        this.mob.getNavigation().setAvoidSun(false);
    }
}
