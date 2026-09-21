package me.primaryuan.carpet.brain;

import net.minecraft.world.entity.LivingEntity;

/**
 * 玩家身上的"感知器"（零实体 token：对齐原版 {@code Sensing} 的被调用面，不创建任何对象）。
 *
 * <p>原版目标类大量调用 {@code this.mob.getSensing().hasLineOfSight(entity)} 做
 * 视野判定。玩家自带 {@code LivingEntity#hasLineOfSight(Entity)}（从眼部向目标
 * 做方块级射线，阻挡物判定与原版生物一致），这里直接转发，零成本且服务端权威。</p>
 */
public final class PlayerSensing {

    private final net.minecraft.world.entity.player.Player player;

    public PlayerSensing(net.minecraft.world.entity.player.Player player) {
        this.player = player;
    }

    /** 视线是否通达（null 目标一律不可见，防空指针） */
    public boolean hasLineOfSight(LivingEntity entity) {
        return entity != null && this.player.hasLineOfSight(entity);
    }
}