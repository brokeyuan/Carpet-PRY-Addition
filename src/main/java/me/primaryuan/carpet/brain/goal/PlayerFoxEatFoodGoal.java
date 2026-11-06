package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 狐狸进食目标（← 原版狐狸持物进食行为）：玩家真正饥饿且背包有 FOOD
 * 语义的食物时，经玩家原生使用流程进食（手持 + {@code startUsingItem} 32 tick，
 * 消耗/饱和度/音效全走原版）。不凭空恢复食物。
 *
 * <p>“生命未满”不等于“能吃”：判断走 {@code Player.canEat(false)}（饥饿值
 * 或 canAlwaysEat）。药水等只有 CONSUMABLE 的饮用物不是普通食物，必须有
 * FOOD 组件。一轮进食结束/使用失败即让出 MOVE，由选择器下一轮重新评估。</p>
 */
public class PlayerFoxEatFoodGoal extends PlayerGoal {

    protected final PryMob mob;

    public PlayerFoxEatFoodGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    /** 玩家可进食条件缝（桩世界食物数据不可达 → 测试覆写） */
    protected boolean canEatNow() {
        return this.mob.asPlayer().canEat(false);
    }

    /** 使用状态缝（26.3 走数据同步器，桩世界不可读 → 测试覆写） */
    protected boolean selfUsingItem() {
        return this.mob.asLiving().isUsingItem();
    }

    /** 取消使用缝（桩世界使用状态不可达 → 测试覆写） */
    protected void stopUsingItem() {
        this.mob.stopUsingItem();
    }

    /** 开始使用缝（桩世界使用状态不可达 → 测试覆写） */
    protected void beginUsingItem() {
        this.mob.startUsingItem(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean canUse() {
        if (this.selfUsingItem() || !this.canEatNow()) {
            return false; // 饥饿满/不能进食：生命未满也不吃
        }
        return this.findFood() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // 只维持“本轮正在使用”的进食；完成/失败立即结束，背包还有食物也不能
        // 让本 Goal 永久占用 MOVE。
        return this.selfUsingItem();
    }

    @Override
    public void start() {
        ItemStack food = this.findFood();
        if (food == null) {
            return;
        }
        var inv = this.mob.asPlayer().getInventory();
                // 选中到手（与农民同款玩家语义）；主手使用
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == food) {
                if (i < net.minecraft.world.entity.player.Inventory.getSelectionSize()) {
                    pryEquipSlot(inv, i);
                } else {
                    inv.pickSlot(i);
                }
                break;
            }
        }
        this.beginUsingItem();
    }

    @Override
    public void tick() {
        // 使用进度由原版 LivingEntity.useTick 驱动；吃满自动结算
        if (!this.selfUsingItem()) {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        if (this.selfUsingItem()) {
            this.stopUsingItem(); // 静默收起（被打断不结算）
        }
    }

    /** 只认 FOOD 语义（普通食物；CONSUMABLE 还包含药水等饮用物） */
    private ItemStack findFood() {
        var inv = this.mob.asPlayer().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                return stack;
            }
        }
        return null;
    }
}
