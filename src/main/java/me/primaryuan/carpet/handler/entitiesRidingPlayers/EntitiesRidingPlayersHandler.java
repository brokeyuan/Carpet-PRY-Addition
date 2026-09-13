package me.primaryuan.carpet.handler.entitiesRidingPlayers;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class EntitiesRidingPlayersHandler {

    /** 骑乘 / 捡起两类许可 */
    public enum Permission { RIDE, PICKUP }

    /** key=玩家名，value=是否允许（缺省允许）；仅服务器主线程访问，下线即清理 */
    private static final Map<Permission, Map<String, Boolean>> permissions =
            new EnumMap<>(Map.of(Permission.RIDE, new HashMap<>(), Permission.PICKUP, new HashMap<>()));

    public static InteractionResult rideEntity(Player player, Entity targetEntity, Level level, InteractionHand hand) {
        if (preconditionsUnmet(player, targetEntity, level, hand)) {
            return InteractionResult.PASS;
        }
        // 副手金胡萝卜 → 交给 pickup 路径处理
        if (player.getItemInHand(InteractionHand.OFF_HAND).is(Items.GOLDEN_CARROT)) {
            return InteractionResult.PASS;
        }

        Player targetPlayer = (Player) targetEntity;
        if (denied(Permission.RIDE, player, targetPlayer, "carpetprimaryuan.command.ride.disallow_ride_subtitle")) {
            return InteractionResult.PASS;
        }

        Entity vehicle = getHighestOrSelf(targetEntity, player, CarpetPrimaryuanSettings.ridingPlayersStackLimit);

        if (vehicle == null) return InteractionResult.FAIL;
        return player.startRiding(vehicle) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static InteractionResult pickUpEntity(Player player, Entity targetEntity, Level level, InteractionHand hand) {
        if (preconditionsUnmet(player, targetEntity, level, hand)) {
            return InteractionResult.PASS;
        }
        // 副手必须持金胡萝卜
        if (!player.getItemInHand(InteractionHand.OFF_HAND).is(Items.GOLDEN_CARROT)) {
            return InteractionResult.PASS;
        }

        Player targetPlayer = (Player) targetEntity;
        if (denied(Permission.PICKUP, player, targetPlayer, "carpetprimaryuan.command.ride.disallow_pickup_subtitle")) {
            return InteractionResult.PASS;
        }

        Entity vehicle = getHighestOrSelf(player, targetEntity, CarpetPrimaryuanSettings.ridingPlayersStackLimit);

        if (vehicle == null) return InteractionResult.FAIL;
        return targetEntity.startRiding(vehicle) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** 公共前置校验：服务端 + 主手 + 目标为玩家 + 主手持不死图腾 */
    private static boolean preconditionsUnmet(Player player, Entity targetEntity, Level level, InteractionHand hand) {
        return level.isClientSide()
                || hand != InteractionHand.MAIN_HAND
                || !(targetEntity instanceof Player)
                || !player.getItemInHand(hand).is(Items.TOTEM_OF_UNDYING);
    }

    /** 权限被目标玩家拒绝时向发起者发送字幕提示；返回是否被拒 */
    private static boolean denied(Permission type, Player actor, Player target, String subtitleKey) {
        if (isAllowed(type, target.getName().getString())) {
            return false;
        }
        if (actor instanceof ServerPlayer serverPlayer) {
            Component subtitle = ServerI18n.tr(subtitleKey, target.getName().getString());
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        }
        return true;
    }

    /**
     * 取得骑乘塔的顶端（新乘客的落点）。
     * 语义与规则描述一致：塔内玩家总数（含基座与新乘客）不得超过 limit；
     * 检测到 newPassenger 已在塔内（防自环/重复骑乘）或加入后超限时返回 null。
     */
    public static Entity getHighestOrSelf(Entity vehicle, Entity newPassenger, int limit) {
        int towerCount = 1; // 基座
        while (vehicle.isVehicle()) {
            vehicle = vehicle.getFirstPassenger();
            if (vehicle == newPassenger) return null;
            towerCount++;
        }
        return towerCount + 1 > limit ? null : vehicle;
    }

    public static void setPermission(Permission type, String playerName, boolean allow) {
        permissions.get(type).put(playerName, allow);
    }

    public static boolean isAllowed(Permission type, String playerName) {
        return permissions.get(type).getOrDefault(playerName, true);
    }

    /** 玩家下线时清理其全部许可（骑乘 + 捡起） */
    public static void clearPermissions(String playerName) {
        permissions.values().forEach(m -> m.remove(playerName));
    }

    /** 乘客变动后向被骑乘的玩家客户端同步乘客列表（原 onMount/onDismount 合并，两者逻辑完全相同） */
    public static void syncPassengers(Entity vehicle) {
        if (!vehicle.level().isClientSide() && vehicle instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetPassengersPacket(vehicle));
        }
    }

    public static void onPlayerTick(Player player) {
        if (!player.level().isClientSide() && player.onGround() && player.isVehicle() && player.isCrouching()) {
            player.getFirstPassenger().stopRiding();
        }
    }

    public static void onLogOut(Player player) {
        if (player.isPassenger() && player.getVehicle() instanceof Player)
            player.stopRiding();
        clearPermissions(player.getName().getString());
    }

    public static void onGameModeChange(Player player, GameType gameMode) {
        if (player.isVehicle() && (CarpetPrimaryuanSettings.ridingPlayersAutoDismount || gameMode == GameType.SPECTATOR))
            player.getFirstPassenger().stopRiding();
    }
}
