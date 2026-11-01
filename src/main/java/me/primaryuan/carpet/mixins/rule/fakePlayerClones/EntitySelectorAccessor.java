package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.commands.arguments.selector.EntitySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EntitySelector.maxResults 访问器：区分单实体语义（==1，give/clear 等的
 * player() 参数）与复数选择器（>1）——分身去重只应作用于前者。
 */
@Mixin(EntitySelector.class)
public interface EntitySelectorAccessor {

    @Accessor("maxResults")
    int pry$maxResults();

}
