package me.primaryuan.carpet.mixins.rule.playerHeadSkin;

import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 重发剩余冷却，不重置服务端计时。 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
    @Accessor("cooldowns")
    Map<?, ?> pry$headSkinCooldowns();

    @Accessor("tickCount")
    int pry$headSkinTickCount();
}
