package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * 猪灵拾取目标（Wiki 对齐）：主动搜寻附近的掉落物并走过去捡。
 *
 * <p><b>零凭空造物的关键</b>：本 Goal <b>只负责"走过去"</b>——假人是玩家实体，
 * 走到掉落物旁会触发玩家原生的碰撞拾取（{@code ItemEntity.playerTouch}），
 * 物品进入玩家背包的全过程由原版完成：拾取动画/音效/拾取提示、物品守恒，
 * AI 不搬运任何 ItemStack。金质物品优先（{@code ItemTags.PIGLIN_LOVED}，
 * 即 Wiki 的 #piglin_loved 列表），其余物品也捡。</p>
 *
 * <p>节流与门控（Wiki）：</p>
 * <ul>
 *   <li>被攻击后 20 秒（400 tick）内不再尝试捡拾（{@code getLastHurtByMobTimestamp}）；</li>
 *   <li>有战斗目标时不捡（战斗优先，被更高优先级 Goal 抢占或此处显式让位）；</li>
 *   <li>目标物品消失（被别人捡走/烧毁）则重新搜索。</li>
 * </ul>
 */
public class PlayerPickupItemsGoal extends PlayerGoal {

    /** 搜索半径（格） */
    private static final double SEARCH_RANGE_SQ = 12.0 * 12.0;
    /** 视为"已捡到"的距离：玩家碰撞拾取在边界盒外扩约 1 格内生效 */
    private static final double PICKED_UP_DIST_SQ = 1.5 * 1.5;

    private final PryMob mob;
    private ItemEntity targetItem;

    public PlayerPickupItemsGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(10);
    }

    /** Wiki：被玩家/生物攻击后 20 秒（400 tick）内不再尝试捡起物品 */
    private boolean recentlyHurt() {
        int since = this.mob.asLiving().tickCount - this.mob.asLiving().getLastHurtByMobTimestamp();
        return since < 400;
    }

    /** 最近的掉落物；金质物品（#piglin_loved）优先于普通物品，同组按距离 */
    private ItemEntity findNearestItem() {
        List<ItemEntity> items = this.mob.level().getEntitiesOfClass(ItemEntity.class,
                this.mob.getBoundingBox().inflate(Math.sqrt(SEARCH_RANGE_SQ), 4.0, Math.sqrt(SEARCH_RANGE_SQ)),
                e -> e.isAlive() && !e.isRemoved());
        if (items.isEmpty()) {
            return null;
        }
        // 排序：金质优先，其次距离（组合键）
        items.sort(Comparator
                .comparing((ItemEntity e) -> !e.getItem().is(ItemTags.PIGLIN_LOVED))
                .thenComparingDouble(e -> this.mob.distanceToSqr(e)));
        ItemEntity best = items.get(0);
        return this.mob.distanceToSqr(best) <= SEARCH_RANGE_SQ ? best : null;
    }

    @Override
    public boolean canUse() {
        if (this.recentlyHurt() || this.mob.getTarget() != null) {
            return false;
        }
        this.targetItem = this.findNearestItem();
        return this.targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 有敌人/被攻击时立即让位（战斗优先）
        if (this.mob.getTarget() != null || this.recentlyHurt()) {
            return false;
        }
        return this.targetItem != null && this.targetItem.isAlive()
                && !this.targetItem.isRemoved()
                && this.mob.distanceToSqr(this.targetItem) > PICKED_UP_DIST_SQ;
    }

    @Override
    public void start() {
        if (this.targetItem != null) {
            this.mob.getNavigation().moveTo(
                    this.targetItem.getX(), this.targetItem.getY(), this.targetItem.getZ(), 1.0);
        }
    }

    @Override
    public void stop() {
        // 走到即被原生拾取；导航停止清移动控制（防粘滞）
        this.targetItem = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        // 每 tick 维持导航（物品可能被水流推动/他人移动）
        if (this.targetItem != null && this.targetItem.isAlive()) {
            this.mob.getNavigation().moveTo(
                    this.targetItem.getX(), this.targetItem.getY(), this.targetItem.getZ(), 1.0);
        }
    }
}
