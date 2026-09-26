package me.primaryuan.carpet.mixins.rule.entitiesRidingPlayers;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    // 注入点已在全部 10 个构建版本上用字节码核实（setGameMode(GameType) 唯一重载，
    // 体内恰一处 ServerGamePacketListenerImpl.send(Packet)，紧邻 ClientboundGameEventPacket）。
    // require=0 仍保留：产物声明的版本范围比构建版本宽（1.21.8 jar 允许 1.21.6/1.21.7、
    // 26.x jar 开放上界），未构建版本无法核实，静默跳过比启动崩溃安全。
    // 该钩子是旁观者禁令"骑乘中切旁观立即下车"的关键路径，见
    // EntitiesRidingPlayersHandler.onGameModeChange。
    @Inject(method = "setGameMode", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER), require = 0)
    private void ridingPlayers$onGameModeChange(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
            EntitiesRidingPlayersHandler.onGameModeChange((Player) (Object) this, gameType);
        }
    }
}
