package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.primaryuan.carpet.clones.CloneAllowlist;
import me.primaryuan.carpet.clones.CloneDisplayIds;
import me.primaryuan.carpet.clones.CloneDistanceManagerHelper;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.CloneInstanceRegistry;
import me.primaryuan.carpet.clones.CloneSpawnManager;
import me.primaryuan.carpet.clones.CloneVersionAdapter;
import me.primaryuan.carpet.clones.SharedInventoryManager;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 分身入组与退组的唯一登记点。
 *
 * <ul>
 *   <li>placeNewPlayer HEAD：为分身预留 cloneId 并登记显示 UUID（必须早于
 *       任何 PlayerInfo/AddEntity 包构造）；DistanceManager 预注册。</li>
 *   <li>placeNewPlayer RETURN：预留转正、登记实例、接管共享库存。失败时
 *       立即撤销本实例，不留下未接管的身体。</li>
 *   <li>placeNewPlayer Wrapper：异常时释放预留/显示映射。</li>
 *   <li>remove Wrapper：save 期间保留成员身份（非主实例存档过滤仍能识别），
 *       Remove 包构造期间提供精确实例上下文，最后 finally 才注销。</li>
 * </ul>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListPreRegisterMixin {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    private static boolean isClonePath(ServerPlayer player) {
        return player instanceof carpet.patches.EntityPlayerMPFake
                && CloneAllowlist.isAllowed(player.getUUID());
    }

    @WrapMethod(method = "placeNewPlayer")
    private void pry$wrapPlaceNewPlayer(Connection connection, ServerPlayer player,
                                        CommonListenerCookie cookie, Operation<Void> original) {
        boolean clonePath = isClonePath(player);
        try {
            original.call(connection, player, cookie);
        } catch (Throwable t) {
            if (clonePath) {
                CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
                if (group != null) {
                    group.release(player);
                }
                CloneDisplayIds.unregister(player);
            }
            LOGGER.error("[pry:clones] placeNewPlayer 异常 id={}", player.getId(), t);
            throw t;
        }
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"), require = 0)
    private void pry$preRegisterClone(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci
    ) {
        if (!isClonePath(player)) {
            return;
        }
        me.primaryuan.carpet.clones.CloneInjectProbe.hit("placeNewPlayer.head");
        CloneGroup group = CloneGroupRegistry.getOrCreate(player.getUUID(),
                CloneVersionAdapter.profileName(player.getGameProfile()));
        int cloneId = group.reserve(player);
        if (!CloneDisplayIds.isClone(player)) {
            CloneDisplayIds.register(player, player.getUUID(), cloneId);
        }
        try {
            CloneDistanceManagerHelper.preRegister(player);
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] DistanceManager 预注册失败 id={}", player.getId(), t);
        }
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"), require = 0)
    private void pry$registerCloneInstance(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci
    ) {
        if (!isClonePath(player)) {
            return;
        }
        me.primaryuan.carpet.clones.CloneInjectProbe.hit("placeNewPlayer.return");
        if (CloneInstanceRegistry.cloneIdOf(player) >= 0) {
            return;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(player.getUUID(),
                CloneVersionAdapter.profileName(player.getGameProfile()));
        int cloneId = group.commit(player);
        CloneInstanceRegistry.put(player, cloneId);
        group.captureProfile(player.getGameProfile());
        if (!SharedInventoryManager.attach(group, player)) {
            LOGGER.error("[pry:clones] 共享库存接管失败，撤销实例 {}#{}", group.name(), cloneId);
            CloneSpawnManager.abortSpawn(player.level().getServer(), group, player);
            return;
        }
        LOGGER.info("[pry:clones] registered {}#{} entityId={}",
                CloneVersionAdapter.profileName(player.getGameProfile()), cloneId, player.getId());
    }

    @WrapMethod(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V")
    private void pry$wrapRemove(ServerPlayer player, Operation<Void> original) {
        me.primaryuan.carpet.clones.CloneInjectProbe.hit("playerList.remove");
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        boolean member = group != null && group.members().containsValue(player);
        boolean registeredDisplay = CloneDisplayIds.isClone(player);
        if (member || registeredDisplay) {
            // 显示映射必须保留到 remove 内部构造 Remove 包之后
            CloneDisplayIds.beginRemoval(player);
        }
        try {
            original.call(player);
        } finally {
            if (member || registeredDisplay) {
                CloneDisplayIds.endRemoval();
            }
            pry$cleanupAfterRemove(player, group);
        }
    }

    private static void pry$cleanupAfterRemove(ServerPlayer player, CloneGroup group) {
        if (group == null) {
            CloneDisplayIds.unregister(player);
            return;
        }
        if (!group.members().containsValue(player)) {
            group.release(player);
            CloneDisplayIds.unregister(player);
            return;
        }
        int cloneId = CloneInstanceRegistry.cloneIdOf(player);
        group.remove(player);
        CloneInstanceRegistry.remove(player);
        CloneDisplayIds.unregister(player);
        if (group.allOffline()) {
            group.finishExitIfEmpty();
            CloneAllowlist.revoke(group.uuid(), group.name());
        }
        LOGGER.info("[pry:clones] unregistered {}#{} on remove", group.name(), cloneId);
    }
}
