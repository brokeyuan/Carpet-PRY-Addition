package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneAllowlist;
import me.primaryuan.carpet.clones.CloneDisplayIds;
import me.primaryuan.carpet.clones.CloneDistanceManagerHelper;
import me.primaryuan.carpet.clones.CloneVersionAdapter;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.CloneSpawnManager;
import me.primaryuan.carpet.clones.CloneInstanceRegistry;
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
 * placeNewPlayer 三段处理（均仅作用于 allowlist 内的分身实例）：
 * <ol>
 *   <li>HEAD：DistanceManager 预注册——placeNewPlayer 内 connection.teleport →
 *       ChunkMap.move 会对"旧 section"（构造器初始化的 (0,0,0)）做
 *       removePlayer，该 chunk 无玩家集合时 NPE（M1 实证 DistanceManager:125
 *       "chunkPlayers is null"）。预注册出生 section 使 remove/add 成对净空。</li>
 *   <li>@WrapMethod：异常可见化——Carpet createFake 的 whenCompleteAsync 吞掉
 *       placeNewPlayer 抛出的任何异常（M1/M2 两次实机受阻均因此无法定位）。
 *       分身路径捕获后打完整栈并重抛（不改变正常路径语义）。</li>
 *   <li>RETURN：分身实例统一入组（覆盖 Carpet createFake、CloneSpawnManager、
 *       shadow 等一切加入路径）。CloneSpawnManager 已登记的（identity 幂等）
 *       不重复分配 cloneId。</li>
 * </ol>
 * 普通玩家/普通假人不满足守卫，三条注入全走原路径。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListPreRegisterMixin {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    @WrapMethod(method = "placeNewPlayer")
    private void pry$wrapPlaceNewPlayer(Connection connection, ServerPlayer player,
                                        CommonListenerCookie cookie, Operation<Void> original) {
        boolean clonePath = CarpetPrimaryuanSettings.fakePlayerClones
                && CloneAllowlist.isAllowed(player.getUUID());
        if (!clonePath) {
            original.call(connection, player, cookie);
            return;
        }
        try {
            original.call(connection, player, cookie);
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] placeNewPlayer 异常（原被 createFake 吞掉） id={}", player.getId(), t);
            throw t;
        }
    }

    /**
     * PlayerList.remove HEAD：组内成员退出统一注销（覆盖 kill/断连/重复登录
     * 踢出等一切路径——修复 Review R1 F1：此前只有 killGroup 一个清理出口，
     * 真人登录踢出整组后组映射残留，primary() 指向死实例导致背包写丢失）。
     * 组空时撤销 allowlist 并清组注册。
     */
    @Inject(
            method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void pry$unregisterCloneOnRemove(ServerPlayer player, CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        if (group == null || !group.members().containsValue(player)) {
            return;
        }
        int cloneId = CloneInstanceRegistry.cloneIdOf(player);
        // 先缓存显示 UUID（本实例退出产生的 PlayerInfoRemove 包改写用），再注销
        CloneDisplayIds.markExiting(player);
        group.remove(player);
        CloneInstanceRegistry.remove(player);
        // 组记录保留（供 /fakeclone rejoin），只撤 allowlist——
        // rejoin 的 spawnClone 会重新登记。组内还有存活成员（如重复登录
        // 只踢出部分实例）时 allowlist 必须保留。
        if (group.allOffline()) {
            CloneAllowlist.revoke(player.getUUID(), group.name());
        }
        LOGGER.info("[pry:clones] unregistered {}#{} on remove", group.name(), cloneId);
    }

    @Inject(
            method = "placeNewPlayer",
            at = @At("HEAD"),
            require = 0
    )
    private void pry$preRegisterCloneDistanceManager(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        if (!CloneAllowlist.isAllowed(player.getUUID())) {
            return;
        }
        // 显示 UUID 必须在 placeNewPlayer 内部任何 PlayerInfo/AddEntity 包构造
        // 之前登记——RETURN 阶段才注册就晚了（包已按真实 UUID 发出）
        if (!CloneDisplayIds.isClone(player)) {
            CloneDisplayIds.register(player, player.getUUID(),
                    CloneSpawnManager.nextCloneId(player.getUUID()));
        }
        try {
            CloneDistanceManagerHelper.preRegister(player);
        } catch (Throwable t) {
            LOGGER.error("[pry:clones] DistanceManager 预注册失败 id={}", player.getId(), t);
        }
    }

    @Inject(
            method = "placeNewPlayer",
            at = @At("RETURN"),
            require = 0
    )
    private void pry$registerCloneInstance(
            Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        if (!(player instanceof carpet.patches.EntityPlayerMPFake)) {
            return;
        }
        if (CloneInstanceRegistry.cloneIdOf(player) >= 0) {
            return;
        }
        CloneGroup group = CloneGroupRegistry.getOrCreate(player.getUUID(),
                CloneVersionAdapter.profileName(player.getGameProfile()));
        int cloneId = group.add(player);
        CloneInstanceRegistry.put(player, cloneId);
        // 新成员初始对齐：把权威背包/装备快照到新成员（否则 give 只在主实例）
        me.primaryuan.carpet.clones.SharedInventoryManager.syncGroup(player);
        me.primaryuan.carpet.clones.SharedInventoryManager.syncEquipment(player);
        LOGGER.info("[pry:clones] registered {}#{} entityId={}", CloneVersionAdapter.profileName(player.getGameProfile()),
                cloneId, player.getId());
    }
}
