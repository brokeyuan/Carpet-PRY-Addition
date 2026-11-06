package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.server.level.ServerPlayer;

/**
 * 假游戏攻击合法性：只判断“原版近战是否真的能命中”，不做宽泛距离放行。
 *
 * <p>1.21.11 起客户端/服务端已有按主手物品与目标碰撞箱计算攻击距离的入口；
 * 更早版本用实体交互范围属性与目标碰撞箱到眼睛的距离。两条路径都要求目标
 * 处于当前攻击距离内，避免用固定 6 格代替合法近战判定。</p>
 */
public final class FakeGameCombat {

    private FakeGameCombat() {}

    public static boolean isWithinAttackRange(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null || attacker == victim) {
            return false;
        }
        //#if MC >= 260102
        //$$ return attacker.isWithinAttackRange(attacker.getMainHandItem(), victim.getBoundingBox(), 0.0);
        //#elseif MC >= 12111
        return attacker.isWithinAttackRange(victim.getBoundingBox(), 0.0);
        //#else
        //$$ double range = attacker.getAttributeValue(
        //$$         net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        //$$ return victim.getBoundingBox().distanceToSqr(attacker.getEyePosition()) <= range * range;
        //#endif
    }
}
