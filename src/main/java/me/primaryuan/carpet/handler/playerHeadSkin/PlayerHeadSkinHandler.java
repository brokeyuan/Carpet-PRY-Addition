package me.primaryuan.carpet.handler.playerHeadSkin;

import com.mojang.authlib.GameProfile;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.mixins.rule.fakePlayerSkin.PlayerGameProfileAccessor;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 玩家头颅只覆盖本次佩戴的纹理，不改变玩家身份或换肤模组存储。 */
public final class PlayerHeadSkinHandler {
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Map<ServerPlayer, WornHead> SESSIONS = new IdentityHashMap<>();
    private static boolean initialized;

    private static final class WornHead {
        final ResolvableProfile source;
        final HeadSkinSession skin;
        GameProfile expected;

        WornHead(ResolvableProfile source, GameProfile original) {
            this.source = source;
            this.skin = new HeadSkinSession(original);
            this.expected = original;
        }
    }

    private PlayerHeadSkinHandler() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ServerTickScheduler.register(server -> {
            tick(server);
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SESSIONS.forEach((player, worn) -> {
                worn.skin.invalidate();
                // 停服只还原实体字段，避免为即将断开的连接重建客户端。
                setProfile(player, originalProfile(player, worn));
            });
            SESSIONS.clear();
            HeadSkinRefresh.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        var iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!isCurrent(server, entry.getKey())) {
                entry.getValue().skin.invalidate();
                setProfile(entry.getKey(), originalProfile(entry.getKey(), entry.getValue()));
                iterator.remove();
            }
        }
        if (CarpetPrimaryuanSettings.playerHeadSkin || !SESSIONS.isEmpty()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    observe(server, player);
                } catch (RuntimeException problem) {
                    LOGGER.error("[pry] playerHeadSkin: 玩家 {} 头颅皮肤更新失败", player.getUUID(), problem);
                }
            }
        }
        HeadSkinRefresh.flushSelf(server);
    }

    private static ResolvableProfile headProfile(ServerPlayer player) {
        var stack = player.getItemBySlot(EquipmentSlot.HEAD);
        return stack.is(Items.PLAYER_HEAD) ? stack.get(DataComponents.PROFILE) : null;
    }

    private static void observe(MinecraftServer server, ServerPlayer player) {
        var source = CarpetPrimaryuanSettings.playerHeadSkin ? headProfile(player) : null;
        var previous = SESSIONS.get(player);
        if (previous == null && source == null) return;
        if (previous != null && previous.source.equals(source) && previous.expected == player.getGameProfile()) return;

        // 换肤模组或假人出生兜底在佩戴期间更新 profile 时，以其新皮肤作为还原基准。
        GameProfile original = originalProfile(player, previous);
        if (previous != null) {
            previous.skin.invalidate();
            SESSIONS.remove(player);
        }
        replaceProfile(player, original);
        if (source == null) return;
        var worn = new WornHead(source, original);
        SESSIONS.put(player, worn);
        try {
            CompletableFuture<GameProfile> future;
            //#if MC >= 12110
            GameProfile partial = source.partialProfile();
            if (!partial.properties().get("textures").isEmpty()) {
                future = CompletableFuture.completedFuture(partial);
            } else {
                future = source.resolveProfile(server.services().profileResolver());
            }
            //#else
            //$$ GameProfile partial = source.gameProfile();
            //$$ boolean hasTextures = !partial.getProperties().get("textures").isEmpty();
            //$$ future = hasTextures ? CompletableFuture.completedFuture(partial)
            //$$         : source.resolve().thenApply(ResolvableProfile::gameProfile);
            //#endif
            worn.skin.resolve(future, task -> {
                if (!server.isStopped()) {
                    if (server.isSameThread()) task.run();
                    else server.execute(task);
                }
            }, () -> CarpetPrimaryuanSettings.playerHeadSkin && isCurrent(server, player)
                    && SESSIONS.get(player) == worn && source.equals(headProfile(player))
                    && player.getGameProfile() == worn.expected,
                    profile -> {
                        worn.expected = profile;
                        replaceProfile(player, profile);
                    }, problem -> LOGGER.warn("[pry] playerHeadSkin: 玩家 {} 头颅皮肤解析失败，保留原皮肤",
                            player.getUUID(), problem));
        } catch (RuntimeException problem) {
            LOGGER.warn("[pry] playerHeadSkin: 玩家 {} 头颅皮肤解析失败，保留原皮肤", player.getUUID(), problem);
        }
    }

    private static boolean isCurrent(MinecraftServer server, ServerPlayer player) {
        return !player.isRemoved() && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static GameProfile originalProfile(ServerPlayer player, WornHead worn) {
        return worn == null || worn.expected != player.getGameProfile()
                ? player.getGameProfile() : worn.skin.original();
    }

    private static void setProfile(ServerPlayer player, GameProfile profile) {
        ((PlayerGameProfileAccessor) player).pry$setGameProfile(profile);
    }

    private static void replaceProfile(ServerPlayer player, GameProfile profile) {
        // 旧 authlib 的 equals 只比较身份；皮肤变化必须按实例判断。
        if (player.getGameProfile() == profile) return;
        setProfile(player, profile);
        HeadSkinRefresh.refresh(player);
    }

    /** 原版及 Carpet 重生都经过 restoreFrom，新实体不得把临时头颅皮肤当成原皮肤。 */
    public static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        var worn = SESSIONS.remove(oldPlayer);
        if (worn == null) return;
        worn.skin.invalidate();
        setProfile(newPlayer, originalProfile(oldPlayer, worn));
        HeadSkinRefresh.forget(oldPlayer);
        // 即使重生后已无头颅，也须纠正客户端 PlayerInfo 中的临时皮肤。
        HeadSkinRefresh.refresh(newPlayer);
    }

    /** shadow 不经过 restoreFrom，须在 Carpet 读取旧实体 profile 前还原。 */
    public static void beforeShadow(ServerPlayer player) {
        var worn = SESSIONS.remove(player);
        if (worn == null) return;
        worn.skin.invalidate();
        setProfile(player, originalProfile(player, worn));
        HeadSkinRefresh.forget(player);
    }
}
