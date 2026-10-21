package me.primaryuan.carpet.handler.playerHeadSkin;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.mixins.rule.playerHeadSkin.ChunkMapAccessor;
import me.primaryuan.carpet.mixins.rule.playerHeadSkin.CooldownInstanceAccessor;
import me.primaryuan.carpet.mixins.rule.playerHeadSkin.ItemCooldownsAccessor;
import me.primaryuan.carpet.mixins.rule.playerHeadSkin.TrackedEntityAccessor;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//#if MC >= 12103
import net.minecraft.world.entity.PositionMoveRotation;
//#endif
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** 刷新客户端缓存，不移动真实实体或重建服务端玩家。 */
final class HeadSkinRefresh {
    private static final Set<ServerPlayer> CHANGED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ServerPlayer> SELF = Collections.newSetFromMap(new IdentityHashMap<>());

    private HeadSkinRefresh() {}

    static void clear() {
        CHANGED.clear();
        SELF.clear();
    }

    static void forget(ServerPlayer player) {
        CHANGED.remove(player);
        SELF.remove(player);
    }

    static void refresh(ServerPlayer player) {
        // 一 tick 内换头或重生只广播最终皮肤；重生钩子触发时新实体尚未注册。
        CHANGED.add(player);
    }

    private static void refreshObservers(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        var server = level.getServer();
        var remove = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
        var add = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));
        for (var observer : server.getPlayerList().getPlayers()) {
            if (observer instanceof EntityPlayerMPFake) continue;
            observer.connection.send(remove);
            observer.connection.send(add);
        }
        var chunkMap = level.getChunkSource().chunkMap;
        Object tracked = ((ChunkMapAccessor) chunkMap).pry$headSkinEntityMap().get(player.getId());
        if (tracked instanceof TrackedEntityAccessor access) {
            for (var connection : List.copyOf(access.pry$headSkinSeenBy())) {
                var observer = connection.getPlayer();
                if (observer instanceof EntityPlayerMPFake) continue;
                observer.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));
                // 原版配对数据包含属性、元数据、装备、速度和乘客，保留追踪集合。
                access.pry$headSkinServerEntity().sendPairingData(observer, observer.connection::send);
            }
        }
        if (!(player instanceof EntityPlayerMPFake)) SELF.add(player);
    }

    static void flushSelf(MinecraftServer server) {
        var changed = List.copyOf(CHANGED);
        CHANGED.clear();
        for (var player : changed) {
            if (player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player) continue;
            try {
                refreshObservers(player);
            } catch (RuntimeException problem) {
                LogManager.getLogger("CarpetPrimaryuan").error(
                        "[pry] playerHeadSkin: 玩家 {} 皮肤同步失败", player.getUUID(), problem);
            }
        }
        var iterator = SELF.iterator();
        while (iterator.hasNext()) {
            var player = iterator.next();
            if (player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player) {
                iterator.remove();
                continue;
            }
            // Respawn 会让客户端关闭容器；等箱子关闭、鼠标物品放回后再刷新本人。
            if (!player.isAlive() || player.isChangingDimension()
                    || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) continue;
            iterator.remove();
            try {
                refreshSelf(player);
            } catch (RuntimeException problem) {
                LogManager.getLogger("CarpetPrimaryuan").error(
                        "[pry] playerHeadSkin: 玩家 {} 自身皮肤刷新失败", player.getUUID(), problem);
            }
        }
    }

    private static void refreshSelf(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        var players = level.getServer().getPlayerList();
        var connection = player.connection;
        connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), ClientboundRespawnPacket.KEEP_ALL_DATA));
        float headYaw = player.getYHeadRot();
        //#if MC >= 12103
        connection.teleport(new PositionMoveRotation(player.position(), player.getDeltaMovement(),
                player.getYRot(), player.getXRot()), Set.of());
        //#else
        //$$ connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        //#endif
        player.setYHeadRot(headYaw);
        connection.send(new ClientboundSetEntityMotionPacket(player));
        player.onUpdateAbilities();
        players.sendAllPlayerInfo(player);
        connection.send(new ClientboundSetHealthPacket(player.getHealth(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel()));
        connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        players.sendActivePlayerEffects(player);
        players.sendPlayerPermissionLevel(player);
        players.sendLevelInfo(player, level);
        // KEEP_ALL_DATA 保留实体属性和元数据，但不会保留药效、经验、物品冷却与骑乘。
        var cooldowns = (ItemCooldownsAccessor) player.getCooldowns();
        cooldowns.pry$headSkinCooldowns().forEach((key, value) -> {
            int remaining = ((CooldownInstanceAccessor) value).pry$headSkinEndTime() - cooldowns.pry$headSkinTickCount();
            if (remaining <= 0) return;
            //#if MC >= 12111
            connection.send(new ClientboundCooldownPacket((net.minecraft.resources.Identifier) key, remaining));
            //#elseif MC >= 12103
            //$$ connection.send(new ClientboundCooldownPacket((net.minecraft.resources.ResourceLocation) key, remaining));
            //#else
            //$$ connection.send(new ClientboundCooldownPacket((net.minecraft.world.item.Item) key, remaining));
            //#endif
        });
        if (player.getVehicle() != null) connection.send(new ClientboundSetPassengersPacket(player.getVehicle()));
        if (!player.getPassengers().isEmpty()) connection.send(new ClientboundSetPassengersPacket(player));
        connection.send(new ClientboundSetCameraPacket(player.getCamera()));
    }
}
