package me.primaryuan.carpet.mixins.rule.moreEndCrystalTypes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.handler.moreEndCrystalTypes.MoreEndCrystalTypesHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * moreEndCrystalTypes 规则 —— 更多种类的末地水晶。
 *
 * 原版 EndCrystalItem.useOn 开头取点击位置的 BlockState 并校验
 * is(OBSIDIAN) || is(BEDROCK)，哭泣的黑曜石被拒。三个注入点均按方法名
 * 命中（方法体自 1.21 起结构稳定，已在 1.21 / 1.21.11 / 26.2 的映射
 * 字节码逐一核实：useOn 内 Level.getBlockState 仅一次调用，位于基座
 * 校验前；setShowBottom(Z)V 与 Level.addFreshEntity 各一次）：
 *
 *  - Redirect 唯一一处 Level.getBlockState：点击哭泣的黑曜石且规则开启时
 *    替换为黑曜石状态，让原版基座校验通过——后续放置逻辑（上方空间检查、
 *    实体碰撞检查、生成、扣物品、gameEvent、末地龙复活判定）全部复用
 *    原版，不触碰 InteractionResult（规避 1.21.2 的返回值重构差异）。
 *    useOn 在客户端与服务端都会执行，本地预测与服务端判定天然一致；
 *    纯原版客户端同样可用（1.19+ 客户端先发送 UseItemOn 包再做本地
 *    预测，本地 FAIL 不影响发包）。
 *  - ModifyArg 原版 setShowBottom(false)：无敌模式下改为 true，复刻
 *    原版复活龙产出的无敌水晶外观（投影实体 ShowBottom=1）。
 *  - WrapOperation Level.addFreshEntity：规则开启时给生成的水晶设置
 *    beamTarget=(0,128,0)（与原版复活龙产出的水晶相同的固定指向光束，
 *    放置位置无关，光束指向世界原点上空 y=128）；无敌模式再把水晶设为
 *    Invulnerable（setInvulnerable 位于 Entity 基类）。用 instanceof
 *    EndCrystal 判型，类名 net.minecraft.world.entity.boss.enderdragon.EndCrystal
 *    与 setBeamTarget(BlockPos) 签名已在 1.21 / 1.21.11 / 26.2 核实一致
 *    （注意 26.2 中 EntityType 常量类改名为 EntityTypes，故不引用它）。
 */
@Mixin(EndCrystalItem.class)
public abstract class EndCrystalItemMixin {

    /** 无敌水晶光束的固定指向点（与解析出的投影实体 beam_target=(0,128,0) 一致） */
    @Unique
    private static final BlockPos pry$BEAM_TARGET = new BlockPos(0, 128, 0);

    @Redirect(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState moreEndCrystalTypes$swapCryingObsidian(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (MoreEndCrystalTypesHelper.mode() != MoreEndCrystalTypesHelper.Mode.OFF && state.is(Blocks.CRYING_OBSIDIAN)) {
            return Blocks.OBSIDIAN.defaultBlockState();
        }
        return state;
    }

    @ModifyArg(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;setShowBottom(Z)V"
            )
    )
    private boolean moreEndCrystalTypes$showBottom(boolean showBottom) {
        return MoreEndCrystalTypesHelper.mode() == MoreEndCrystalTypesHelper.Mode.INVULNERABLE;
    }

    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean moreEndCrystalTypes$invulnerableCrystal(Level level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof EndCrystal crystal) {
            MoreEndCrystalTypesHelper.Mode mode = MoreEndCrystalTypesHelper.mode();
            if (mode != MoreEndCrystalTypesHelper.Mode.OFF) {
                crystal.setBeamTarget(pry$BEAM_TARGET);
            }
            if (mode == MoreEndCrystalTypesHelper.Mode.INVULNERABLE) {
                //#if MC >= 260300
                //$$ // 26.3: setInvulnerable 拆分为 setPermanentlyInvulnerable/setTemporarilyInvulnerable
                //$$ crystal.setPermanentlyInvulnerable(true);
                //#else
                crystal.setInvulnerable(true);
                //#endif
            }
        }
        return original.call(level, entity);
    }
}
