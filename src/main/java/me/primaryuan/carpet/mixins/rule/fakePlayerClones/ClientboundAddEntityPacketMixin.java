package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.CloneDisplayIds;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientboundAddEntityPacket：分身实例的 uuid 字段换显示 UUID（客户端实体
 * 索引 key）；entityId 保持真实值——交互/攻击走 entityId 定位，与 UUID 无关。
 *
 * <p>构造点改写：this 即新造包，无共享复用问题。非分身实体零改动。</p>
 */
@Mixin(ClientboundAddEntityPacket.class)
public abstract class ClientboundAddEntityPacketMixin {

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerEntity;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$rewriteUuidEntity(net.minecraft.world.entity.Entity entity,
                                       net.minecraft.server.level.ServerEntity serverEntity, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            pry$rewriteUuid(player, (ClientboundAddEntityPacket) (Object) this);
        }
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerEntity;I)V",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$rewriteUuidEntityWithBuf(net.minecraft.world.entity.Entity entity,
                                              net.minecraft.server.level.ServerEntity serverEntity,
                                              int ignored, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            pry$rewriteUuid(player, (ClientboundAddEntityPacket) (Object) this);
        }
    }

    private static void pry$rewriteUuid(ServerPlayer player, ClientboundAddEntityPacket self) {
        java.util.UUID display = CloneDisplayIds.displayOf(player);
        if (display != null && !display.equals(player.getUUID())) {
            ((me.primaryuan.carpet.mixins.rule.fakePlayerClones.ClientboundAddEntityPacketAccessor) (Object) self)
                    .pry$setUuid(display);
        }
    }
}
