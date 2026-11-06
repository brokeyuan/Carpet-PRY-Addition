package me.primaryuan.carpet.mixins.rule.playerHead;

import me.primaryuan.carpet.handler.playerHead.HeadData;
import me.primaryuan.carpet.handler.playerHead.HeadItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版头颅掉落表只复制 profile / note_block_sound / custom_name，PRY 档案
 * 保存在 custom_data 中会丢失。这里只给玩家头颅补回本模组子标签并重建
 * Lore，不替换掉落表、不改数量，也不影响其它方块与普通头颅。
 */
@Mixin(CopyComponentsFunction.class)
public abstract class CopyComponentsFunctionMixin {
    @Inject(method = "run", at = @At("RETURN"), cancellable = true)
    private void pry$carryHeadData(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty() || !result.is(Items.PLAYER_HEAD)) return;
        BlockEntity blockEntity = pry$blockEntity(context);
        if (!(blockEntity instanceof SkullBlockEntity)) return;
        HeadData data = HeadData.read(blockEntity.components().get(DataComponents.CUSTOM_DATA));
        if (data == null) return;
        data.write(result);
        HeadItems.applyLore(result, data);
        if (result.get(DataComponents.PROFILE) == null) {
            HeadItems.applySkin(result, data.skin());
        }
        cir.setReturnValue(result);
    }

    private static BlockEntity pry$blockEntity(LootContext context) {
        //#if MC >= 260300
        //$$ return context.getOptional(LootContextParams.BLOCK_ENTITY);
        //#elseif MC >= 12103
        return context.hasParameter(LootContextParams.BLOCK_ENTITY)
                ? context.getParameter(LootContextParams.BLOCK_ENTITY) : null;
        //#else
        //$$ return context.hasParam(LootContextParams.BLOCK_ENTITY)
        //$$         ? context.getParam(LootContextParams.BLOCK_ENTITY) : null;
        //#endif
    }
}
