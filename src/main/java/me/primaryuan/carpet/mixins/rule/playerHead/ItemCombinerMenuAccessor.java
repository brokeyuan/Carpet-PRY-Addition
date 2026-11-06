package me.primaryuan.carpet.mixins.rule.playerHead;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 铁砧会话需要区分服务端玩家与客户端本地预测菜单。 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("player")
    Player pry$player();
}
