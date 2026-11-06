package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * 女巫喝药目标（← 原版 Witch.aiStep 的自我饮用分支，26.3 字节码核实）：
 * 眼在水中且无水下呼吸 → 水下呼吸；着火/火伤且无防火 → 防火；
 * 低血且有治疗/再生 → 治疗/再生。真实库存、玩家原生饮用（32t、消耗、效果
 * 全走原版）。只喝“饮用型”药水；喷溅/滞留在投掷目标里作为攻击药。
 */
public class PlayerWitchDrinkGoal extends PlayerGoal {

    /** 低血自救阈值（项目既有口径：血量 ≤4 视为危险） */
    private static final float LOW_HEALTH = 4.0F;

    protected final PryMob mob;

    public PlayerWitchDrinkGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    /** 开始使用缝（26.3 起使用状态写同步器，桩世界不可达 → 测试覆写直写字段） */
    protected void beginUsingItem() {
        this.mob.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /** 着火状态缝（桩不可读 → 测试覆写） */
    protected boolean selfOnFire() {
        return this.mob.asLiving().isOnFire();
    }

    /** 是否刚受火伤（原版 fire resistance 分支的 getLastDamageSource().is(IS_FIRE)） */
    protected boolean selfRecentlyFireDamaged() {
        var source = this.mob.asLiving().getLastDamageSource();
        return source != null && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
    }

    /** 眼部浸水（原版 isEyeInFluid(WATER)；桩不可读 → 测试覆写） */
    protected boolean selfUnderWater() {
        return this.mob.asLiving().isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
    }

    /** 自身生命缝（26.3 生命走数据同步器，桩世界不可读 → 测试覆写） */
    protected float selfHealth() {
        return this.mob.asLiving().getHealth();
    }

    /** 使用状态缝（26.3 走数据同步器，桩世界不可读 → 测试覆写） */
    protected boolean selfUsingItem() {
        return this.mob.asLiving().isUsingItem();
    }

    /** 自身药效查询缝（已有同效药不重复喝） */
    protected boolean selfHasEffect(Holder<net.minecraft.world.effect.MobEffect> effect) {
        return this.mob.asLiving().hasEffect(effect);
    }

    @Override
    public boolean canUse() {
        if (this.selfUsingItem()) {
            return false;
        }
        return this.findNeededPotion() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.selfUsingItem();
    }

    @Override
    public void start() {
        ItemStack stack = this.findNeededPotion();
        if (stack == null) {
            return;
        }
        var inv = this.mob.asPlayer().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) {
                if (i < net.minecraft.world.entity.player.Inventory.getSelectionSize()) {
                    pryEquipSlot(inv, i);
                } else {
                    inv.pickSlot(i);
                }
                break;
            }
        }
        this.beginUsingItem(); // 玩家原生饮用 32t，完成时由原版消耗并应用效果
    }

    @Override
    public void stop() {
        if (this.mob.isUsingItem()) {
            this.mob.stopUsingItem();
        }
    }

    /** 按原版优先级挑选当前真正需要且库存持有的饮用型药水 */
    private ItemStack findNeededPotion() {
        if (this.selfUnderWater()
                && !this.selfHasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) {
            ItemStack stack = this.findDrinkable(Potions.WATER_BREATHING, Potions.LONG_WATER_BREATHING);
            if (stack != null) {
                return stack;
            }
        }
        if ((this.selfOnFire() || this.selfRecentlyFireDamaged())
                && !this.selfHasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
            ItemStack stack = this.findDrinkable(Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE);
            if (stack != null) {
                return stack;
            }
        }
        if (this.selfHealth() <= LOW_HEALTH) {
            if (!this.selfHasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
                ItemStack regen = this.findDrinkable(Potions.REGENERATION, Potions.LONG_REGENERATION,
                        Potions.STRONG_REGENERATION);
                if (regen != null) {
                    return regen;
                }
            }
            return this.findDrinkable(Potions.HEALING, Potions.STRONG_HEALING);
        }
        return null;
    }

    /** 可饮用型药水（非喷溅/滞留：玩家身体喝的是饮用型药水） */
    private ItemStack findDrinkable(Holder<net.minecraft.world.item.alchemy.Potion>... wanted) {
        var inv = this.mob.asPlayer().getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.world.item.PotionItem)
                    || stack.getItem() instanceof net.minecraft.world.item.SplashPotionItem
                    || stack.getItem() instanceof net.minecraft.world.item.LingeringPotionItem) {
                continue;
            }
            PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) {
                continue;
            }
            var potion = contents.potion().get();
            for (Holder<net.minecraft.world.item.alchemy.Potion> candidate : wanted) {
                if (potion.is(candidate)) {
                    return stack;
                }
            }
        }
        return null;
    }
}
