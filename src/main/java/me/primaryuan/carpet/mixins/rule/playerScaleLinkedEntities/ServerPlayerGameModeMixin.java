package me.primaryuan.carpet.mixins.rule.playerScaleLinkedEntities;

import me.primaryuan.carpet.handler.playerScaleLinkedEntities.SpawnContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * playerScaleLinkedEntities 规则的上下文采集端。
 *
 * 服务端所有"玩家使用物品"都经 ServerPlayerGameMode 漏斗：
 * - useItem（手持右键：船、烟花、投掷物等）
 * - useItemOn（对方块右键：盔甲架、刷怪蛋、矿车、展示框、末影水晶等）
 * 在两者 HEAD/RETURN 之间把操作玩家记入 SpawnContext，供 ServerLevelMixin
 * 在实体真正添加时读取，从而只联动"玩家用物品直接生成"的实体；
 * 发射器、刷怪笼等非玩家路径不在漏斗内，天然不受影响。
 *
 * 方法按名注入（两方法在各目标版本 1.21.5~26.2 已逐一验证签名一致，
 * 且无重载）；@Inject 捕获目标参数时必须完整捕获全部参数（含返回值的
 * 目标需用 CallbackInfoReturnable）。异常路径 RETURN 不触发会残留引用，
 * 但仅限主线程单槽位、下次使用时被覆盖，无实际泄漏。
 *
 * 仅在 Minecraft 1.21.5+ 注册（mixins.json 门控）。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Inject(method = "useItem", at = @At("HEAD"))
    private void playerScaleLinkedEntities$beginUseItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        SpawnContext.ACTING_PLAYER.set(player);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void playerScaleLinkedEntities$endUseItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        SpawnContext.ACTING_PLAYER.remove();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void playerScaleLinkedEntities$beginUseItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        SpawnContext.ACTING_PLAYER.set(player);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void playerScaleLinkedEntities$endUseItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        SpawnContext.ACTING_PLAYER.remove();
    }
}
