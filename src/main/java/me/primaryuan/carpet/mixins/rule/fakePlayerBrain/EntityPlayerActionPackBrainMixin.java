package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import carpet.helpers.EntityPlayerActionPack;
import me.primaryuan.carpet.brain.BrainManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AI 接管期间屏蔽 Carpet 的假人手动指令（"换脑"冲突协调的关键一环）。
 *
 * 为什么必须取消 onUpdate 而不是靠注入顺序：Carpet 的 onUpdate 对假人
 * <b>每 tick 无条件</b>把 actionPack 的 forward/strafing 写入
 * {@code LivingEntity.zza/xxa}（哪怕值全是 0 也写 0）——若不取消，任何
 * 排在脑子的 tick 注入之后的 onUpdate 都会把脑子刚写的移动输入覆盖掉，
 * 假人将永远原地不动。取消后，移动输入在接管期间的唯一写入者是 Brain；
 * 同时 onUpdate 里的 USE/ATTACK/JUMP 等手动计划任务也一并停摆
 * （挂载时已 stopAll 清空存量，接管期间新下发的指令静默失效）。
 *
 * 卸载脑子（/player <name> brain off 或关闭规则）后本取消不再命中，
 * Carpet 手动指令立即恢复原状。
 */
@Mixin(EntityPlayerActionPack.class)
public abstract class EntityPlayerActionPackBrainMixin {

    @Shadow
    @Final
    private ServerPlayer player;

    @Inject(
            method = "onUpdate",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pry$brainTakeOver(CallbackInfo ci) {
        if (BrainManager.hasBrain(player)) {
            ci.cancel();
        }
    }
}
