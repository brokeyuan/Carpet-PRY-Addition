package me.primaryuan.carpet.mixins.rule.playerHeadSkin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
    @Accessor("endTime")
    int pry$headSkinEndTime();
}
