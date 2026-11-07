package me.primaryuan.carpet.mixins.rule.fakeGames;

import me.primaryuan.carpet.handler.fakegames.FakeGameManager;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 射击玩法接管原版换手输入：在服务端主线程检查之后消费动作，不执行原版交换。
 * 只在射击局运行且玩家为存活成员时消费；其余状态返回 false 走原版。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwapItemMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;"
                            + "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
                            + "Lnet/minecraft/network/PacketListener;"
                            + "Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void pry$handleFakeGameSwap(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            return;
        }
        if (FakeGameManager.get().handleSwap(player)) {
            ci.cancel();
        }
    }
}
