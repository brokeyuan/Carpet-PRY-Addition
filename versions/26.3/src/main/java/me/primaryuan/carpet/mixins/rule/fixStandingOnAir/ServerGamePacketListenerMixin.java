package me.primaryuan.carpet.mixins.rule.fixStandingOnAir;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回移植 26.4 Snapshot 1 的方块重发时序修复，并额外取消站空 INFO 日志。
 *
 * <p>javap 实证：26.3 在 handlePlayerPositionChange 偏移 518 预读
 * verticalCollisionBelow，534 调用恢复方法，557 执行 move；26.4 s1
 * 仍在 518 预读，改为 543 move、557 恢复。守卫与 200 tick 冷却不变。
 * 恢复方法偏移 37 打印 INFO，46 才读碰撞箱；日志不代表实际支撑检查结果。</p>
 *
 * <p>保留原版守卫，将恢复调用延迟到唯一的 move 后，读取新碰撞箱。
 * 恢复调用至 move 间无返回路径；传送确认恒传 isOnGround=false。
 * HEAD 重置待执行状态。日志单独包装，关闭规则时全部透传。
 * 注入失配须启动报错；仅登记 26.3，未来 26.4+ 节点须另行处理。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    /** 守卫已按旧位置命中，恢复调用待 move 应用后补执行 */
    @Unique
    private boolean fixStandingOnAir$pendingResend;

    @Invoker("forceSendPlayerSupportBlocks")
    public abstract void fixStandingOnAir$invokeForceSend();

    @Inject(method = "handlePlayerPositionChange", at = @At("HEAD"))
    private void fixStandingOnAir$resetPending(CallbackInfo ci) {
        this.fixStandingOnAir$pendingResend = false;
    }

    @WrapOperation(
            method = "handlePlayerPositionChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;forceSendPlayerSupportBlocks()V"
            )
    )
    private void fixStandingOnAir$deferSend(ServerGamePacketListenerImpl instance, Operation<Void> original) {
        if (!CarpetPrimaryuanSettings.fixStandingOnAir) {
            original.call(instance);
            return;
        }
        this.fixStandingOnAir$pendingResend = true;
    }

    @Inject(
            method = "handlePlayerPositionChange",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fixStandingOnAir$sendAfterMove(CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fixStandingOnAir || !this.fixStandingOnAir$pendingResend) {
            return;
        }
        this.fixStandingOnAir$pendingResend = false;
        this.fixStandingOnAir$invokeForceSend();
    }

    // 26.3 字节码：恢复方法偏移 37 唯一的 info(String, Object)，在读碰撞箱之前打印。
    // 只取消提示，恢复方法和 200 tick 冷却继续执行；关闭规则时原样透传。
    @WrapOperation(
            method = "forceSendPlayerSupportBlocks",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"),
            require = 1
    )
    private void fixStandingOnAir$suppressLog(Logger logger, String message, Object playerName,
                                              Operation<Void> original) {
        if (!CarpetPrimaryuanSettings.fixStandingOnAir) {
            original.call(logger, message, playerName);
        }
    }
}
