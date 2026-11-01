package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import com.mojang.authlib.GameProfile;
import me.primaryuan.carpet.clones.CloneDisplayIds;
import me.primaryuan.carpet.clones.CloneVersionAdapter;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.Optionull;

/**
 * 显示 UUID 包改写（PlayerInfo）：分身实例的 Entry.profile.id 换显示 UUID，
 * name 与 textures 属性原样保留（客户端皮肤/名字来源，不回查 sessionserver）。
 *
 * <p>两条构造路径都在构造点改写（Entry 字段 final 不可回写，redirect 构造入参）：</p>
 * <ul>
 *   <li>(Action, ServerPlayer)：redirect 单个 Entry(ServerPlayer) 构造；</li>
 *   <li>(EnumSet, Collection)：redirect Collection.stream()——返回以
 *       pry$entryOf 映射后的元素构造的新流（等价 map(Entry::new) 的改写版）。</li>
 * </ul>
 * <p>非分身实例返回原构造结果，零改动。构造后包即含显示 UUID，广播复用一致。</p>
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public abstract class ClientboundPlayerInfoUpdatePacketMixin {

    /** 分身实例 → 显示 UUID 的 Entry（字段集按版本：1.21 无 showHat/listOrder） */
    private static Entry pry$entryOf(ServerPlayer player) {
        UUID display = CloneDisplayIds.displayOf(player);
        GameProfile old = player.getGameProfile();
        GameProfile profile;
        if (display == null || display.equals(player.getUUID())) {
            profile = old;
        } else {
            profile = new GameProfile(display, CloneVersionAdapter.profileName(old));
            CloneVersionAdapter.copyProperties(old, profile);
        }
        var chatSession = Optionull.map(player.getChatSession(),
                net.minecraft.network.chat.RemoteChatSession::asData);
        //#if MC >= 12104
        return new Entry(CloneVersionAdapter.profileId(profile), profile,
                true,
                player.connection.latency(),
                player.gameMode.getGameModeForPlayer(),
                player.getTabListDisplayName(),
                player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT),
                CloneVersionAdapter.tabListOrder(player),
                chatSession);
        //#elseif MC >= 12103
        //$$ return new Entry(CloneVersionAdapter.profileId(profile), profile,
        //$$         true,
        //$$         player.connection.latency(),
        //$$         player.gameMode.getGameModeForPlayer(),
        //$$         player.getTabListDisplayName(),
        //$$         CloneVersionAdapter.tabListOrder(player),
        //$$         chatSession);
        //#else
        //$$ return new Entry(profile.getId(), profile,
        //$$         true,
        //$$         player.connection.latency(),
        //$$         player.gameMode.getGameModeForPlayer(),
        //$$         player.getTabListDisplayName(),
        //$$         chatSession);
        //#endif
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Action;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Entry;",
                    remap = true
            ),
            require = 0
    )
    private Entry pry$redirectSingleEntry(ServerPlayer player) {
        return pry$entryOf(player);
    }

    @Redirect(
            method = "<init>(Ljava/util/EnumSet;Ljava/util/Collection;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;",
                    remap = true
            ),
            require = 0
    )
    private <R> java.util.stream.Stream<R> pry$redirectMap(java.util.stream.Stream<ServerPlayer> stream,
                                                           java.util.function.Function<? super ServerPlayer, ? extends R> mapper) {
        java.util.function.Function<ServerPlayer, R> wrapped = p -> {
            if (CloneDisplayIds.isClone(p)) {
                R entry = pry$entryOfTyped(p);
                if (entry != null) {
                    return entry;
                }
            }
            return mapper.apply(p);
        };
        return stream.map(wrapped);
    }

    @SuppressWarnings("unchecked")
    private static <R> R pry$entryOfTyped(ServerPlayer player) {
        // pry$entryOf 与 map 目标类型同为 Entry；这里以类型擦除桥接
        return (R) pry$entryOf(player);
    }
}
