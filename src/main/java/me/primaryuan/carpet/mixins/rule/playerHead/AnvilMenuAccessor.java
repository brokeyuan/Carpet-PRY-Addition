package me.primaryuan.carpet.mixins.rule.playerHead;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取铁砧当前输入名称；查询未就绪时把花费归零。 */
@Mixin(AnvilMenu.class)
public interface AnvilMenuAccessor {
    @Accessor("itemName")
    String pry$itemName();

    @Accessor("cost")
    DataSlot pry$cost();
}
