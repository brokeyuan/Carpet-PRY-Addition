package me.primaryuan.carpet.handler.entitiesRidingPlayers;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
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
import net.minecraft.world.phys.EntityHitResult;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntitiesRidingPlayersHandler {

    /** 骑乘 / 捡起两类许可 */
    public enum Permission { RIDE, PICKUP }

    /** key=玩家名，value=是否允许（缺省允许）；仅服务器主线程访问，下线即清理 */
    private static final Map<Permission, Map<String, Boolean>> permissions =
            new EnumMap<>(Map.of(Permission.RIDE, new HashMap<>(), Permission.PICKUP, new HashMap<>()));

    /** 交互冷却 tick 数：一次骑乘/捡起交互被处理后，同一玩家需等待 N tick 才能再次交互 */
    private static final int INTERACTION_COOLDOWN_TICKS = 10;

    /** 头部区下缘：命中点距目标脚底 ≥ 碰撞箱高度 × 0.65 视为头部（与摸摸头 HEAD_ZONE_MIN_FRACTION 同口径） */
    private static final double HEAD_ZONE_MIN_FRACTION = 0.65;

    /** 脚部区上缘：命中点距目标脚底 < 碰撞箱高度 × 0.375 视为腿脚（原版玩家模型腿部占比），躯干居中不交互 */
    private static final double FEET_ZONE_MAX_FRACTION = 0.375;

    /** key=玩家名，value=可再次交互的 game time；仅服务器主线程访问，下线即清理 */
    private static final Map<String, Long> interactionCooldowns = new HashMap<>();

    /** 周期清理的注册标志（惰性注册一次） */
    private static boolean sweepRegistered = false;

    /**
     * 注册下线清理的兜底扫描（CarpetPrimaryuanServer.onGameStarted 调用）。
     *
     * <p>DISCONNECT 快路径只覆盖真人 + fixBlueMap=true 的假人；默认配置下假人
     * 被 kill 不会触发该事件，按名记录的许可/冷却条目会残留并被同名重召的
     * 假人继承（违背"下线即清理"契约）。只要存在条目就每 tick 扫一次在线
     * 名单，把不在名单里的条目清掉——真人下线本就清过，此扫描对真人无感。</p>
     */
    public static void init() {
        if (sweepRegistered) return;
        sweepRegistered = true;
        ServerTickScheduler.register(server -> {
            if (permissions.get(Permission.RIDE).isEmpty()
                    && permissions.get(Permission.PICKUP).isEmpty()
                    && interactionCooldowns.isEmpty()) {
                return true;
            }
            Set<String> onlineNames = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                onlineNames.add(p.getName().getString());
            }
            for (Map<String, Boolean> m : permissions.values()) {
                m.keySet().removeIf(name -> !onlineNames.contains(name));
            }
            interactionCooldowns.keySet().removeIf(name -> !onlineNames.contains(name));
            return true;
        });
    }

    /**
     * 主手不死图腾右键玩家的统一入口：点头部骑上对方（ridingPlayers，RIDE 许可），
     * 点腿脚捡起对方到自己头上（pickupPlayers，PICKUP 许可）；
     * 点躯干或对应侧规则未开启时不交互，整体放行不消耗冷却。
     * 仅带坐标的 INTERACT_AT 包触发（裸 INTERACT 包 hitResult 为 null 直接放行），
     * 天然单次触发；部位以命中点距目标脚底的高度占碰撞箱比例计算，缩放体型自动适配。
     */
    public static InteractionResult rideOrPickUp(Player player, Entity targetEntity, Level level,
                                                 InteractionHand hand, EntityHitResult hitResult) {
        if (preconditionsUnmet(player, targetEntity, level, hand) || hitResult == null) {
            return InteractionResult.PASS;
        }
        Player targetPlayer = (Player) targetEntity;
        double hitY = hitResult.getLocation().y - targetPlayer.getY();
        if (hitY >= targetPlayer.getBbHeight() * HEAD_ZONE_MIN_FRACTION) {
            // 骑乘侧规则未开启：整体放行，不消耗冷却
            return CarpetPrimaryuanSettings.ridingPlayers ? ride(player, targetPlayer) : InteractionResult.PASS;
        }
        if (hitY < targetPlayer.getBbHeight() * FEET_ZONE_MAX_FRACTION) {
            // 捡起侧规则未开启：整体放行，不消耗冷却
            return CarpetPrimaryuanSettings.pickupPlayers ? pickUp(player, targetPlayer) : InteractionResult.PASS;
        }
        // 躯干：无交互死区，不消耗冷却，放行后续监听（摸摸头等）
        return InteractionResult.PASS;
    }

    /** 头部区动作：发起者骑上目标塔顶 */
    private static InteractionResult ride(Player player, Player targetPlayer) {
        boolean deniedRide = denied(Permission.RIDE, player, targetPlayer, "carpetprimaryuan.command.ride.disallow_ride_subtitle");
        // 点击已被处理：无论放行、被拒还是失败都进入交互冷却（防连点刷字幕/高频重复交互）
        markInteraction(player);
        if (deniedRide) {
            return InteractionResult.PASS;
        }
        Entity vehicle = getHighestOrSelf(targetPlayer, player, CarpetPrimaryuanSettings.ridingPlayersStackLimit);
        if (vehicle == null) return InteractionResult.FAIL;
        return player.startRiding(vehicle) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** 脚部区动作：目标（含其子塔）骑上发起者塔顶 */
    private static InteractionResult pickUp(Player player, Player targetPlayer) {
        boolean deniedPickup = denied(Permission.PICKUP, player, targetPlayer, "carpetprimaryuan.command.ride.disallow_pickup_subtitle");
        // 点击已被处理：无论放行、被拒还是失败都进入交互冷却
        markInteraction(player);
        if (deniedPickup) {
            return InteractionResult.PASS;
        }
        Entity vehicle = getHighestOrSelf(player, targetPlayer, CarpetPrimaryuanSettings.ridingPlayersStackLimit);
        if (vehicle == null) return InteractionResult.FAIL;
        return targetPlayer.startRiding(vehicle) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * 公共前置校验：服务端 + 主手 + 目标为玩家 + 双方非旁观者 + 双方均为真人 + 不在交互冷却中 + 主手持不死图腾。
     * 原版 startRiding 整条门禁链（couldAcceptPassenger/canSerialize/canRide/canAddPassenger）
     * 都不含游戏模式检查，服务端 handleInteract 对到达的交互包也无游戏模式门禁，
     * 旁观者只能骑乘与被骑乘的过滤必须在此显式完成；
     * 假人（EntityPlayerMPFake）不参与，与摸摸头"仅真人"同口径，发起者与目标双向排除
     */
    private static boolean preconditionsUnmet(Player player, Entity targetEntity, Level level, InteractionHand hand) {
        return level.isClientSide()
                || hand != InteractionHand.MAIN_HAND
                || !(targetEntity instanceof Player)
                || player.isSpectator()
                || targetEntity.isSpectator()
                || player instanceof EntityPlayerMPFake
                || targetEntity instanceof EntityPlayerMPFake
                || isOnInteractionCooldown(player)
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
     * 新乘客自己头上已有的乘客塔会随原版 startRiding 整体移植，一并计入
     * （只统计攀爬侧会让 pickUp 场景以 limit=2 造出 3 人塔）；
     * 检测到 newPassenger 已在塔内（防自环/重复骑乘）或加入后超限时返回 null。
     */
    public static Entity getHighestOrSelf(Entity vehicle, Entity newPassenger, int limit) {
        int towerCount = 1; // 基座
        while (vehicle.isVehicle()) {
            vehicle = vehicle.getFirstPassenger();
            if (vehicle == newPassenger) return null;
            towerCount++;
        }
        // 新乘客自身的子塔随 startRiding 一同迁入目标塔，必须计入总人数
        Entity sub = newPassenger;
        while (sub.isVehicle()) {
            sub = sub.getFirstPassenger();
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

    private static boolean isOnInteractionCooldown(Player player) {
        Long until = interactionCooldowns.get(player.getName().getString());
        return until != null && player.level().getGameTime() < until;
    }

    private static void markInteraction(Player player) {
        interactionCooldowns.put(player.getName().getString(),
                player.level().getGameTime() + INTERACTION_COOLDOWN_TICKS);
    }

    /** 玩家下线时清理其全部许可（骑乘 + 捡起）与交互冷却 */
    public static void clearPermissions(String playerName) {
        permissions.values().forEach(m -> m.remove(playerName));
        interactionCooldowns.remove(playerName);
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
        // 切入旁观：自己身上的乘客立即脱离（塔中段切换时上方玩家随之脱离）
        if (player.isVehicle() && (CarpetPrimaryuanSettings.ridingPlayersAutoDismount || gameMode == GameType.SPECTATOR))
            player.getFirstPassenger().stopRiding();
        // 切入旁观：自己若正骑着玩家也立即下车——旁观者禁令在状态产生瞬间生效，
        // 不再依赖每 tick 兜底；新骑乘关系则由 EntityMixin 在 startRiding 入口统一拒绝
        if (gameMode == GameType.SPECTATOR && player.isPassenger() && player.getVehicle() instanceof Player)
            player.stopRiding();
    }
}
