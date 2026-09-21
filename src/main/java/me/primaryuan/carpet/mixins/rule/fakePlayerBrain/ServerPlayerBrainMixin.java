package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import me.primaryuan.carpet.brain.BrainManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 假人脑子驱动入口：拦截 {@code ServerPlayer.tick()} 头部。
 *
 * Carpet 自身的 {@code ServerPlayer_actionPackMixin} 同样注入 tick 头部调用
 * {@code actionPack.onUpdate()}——两个 HEAD 注入器的相对顺序不保证，
 * 因此本模组不依赖顺序取胜，而是由 {@code EntityPlayerActionPackBrainMixin}
 * 直接取消 onUpdate 本身；本注入器只负责每 tick 推进脑子的决策。
 *
 * 头部时序的意义：此处写入的移动输入（zza/xxa）会在<b>本 tick</b> 稍后
 * {@code super.tick() → LivingEntity.aiStep → travel()} 中被消费并转换为
 * 位移与速度，与原版生物在 tickMovement 里写移动输入的时序完全一致；
 * 位移随后由 ServerEntity 追踪器照常同步（Carpet /player move 同路径），
 * 客户端自动播放行走动画，无需任何客户端模组。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBrainMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void pry$brainTick(CallbackInfo ci) {
        BrainManager.onPlayerTick((ServerPlayer) (Object) this);
    }
}
