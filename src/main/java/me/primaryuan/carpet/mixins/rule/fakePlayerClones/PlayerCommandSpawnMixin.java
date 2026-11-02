package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import carpet.CarpetSettings;
import carpet.commands.PlayerCommand;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneAllowlist;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.CloneSpawnManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Carpet /player 的增量 spawn：分身组身份改走 {@link CloneSpawnManager#spawnClone}
 * （同序列但异常透明、组登记统一）。
 *
 * <p>首次 spawn 先按 Carpet 相同的身份解析预建组与 allowlist，再放行 Carpet
 * createFake——这样 placeNewPlayer 内的显示 UUID 登记早于任何包，且首个
 * 假人仍由 Carpet 异步解析完整 profile（皮肤）。</p>
 */
@Mixin(PlayerCommand.class)
public abstract class PlayerCommandSpawnMixin {

    @Inject(
            method = "spawn",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void pry$cloneGroupSpawn(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        String name;
        try {
            name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
        } catch (IllegalArgumentException e) {
            return;
        }
        CommandSourceStack source = context.getSource();
        CloneGroup group = CloneGroupRegistry.byName(name);
        if (group == null) {
            UUID uuid = resolveIdentity(source, name);
            if (uuid == null) {
                return; // 交给 Carpet 报错
            }
            CloneGroupRegistry.getOrCreate(uuid, name);
            CloneAllowlist.allow(uuid, name);
            return; // Carpet createFake 负责解析 profile 并生成首个成员
        }
        if (CloneGroupRegistry.isOnlineRealPlayer(source.getServer(), group.uuid())) {
            cir.setReturnValue(0);
            return;
        }
        ServerLevel level = source.getLevel();
        var pos = source.getPosition();
        var rot = source.getRotation();
        ServerPlayer created = CloneSpawnManager.spawnClone(source.getServer(), name, level,
                pos, rot.y, rot.x);
        if (created == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[clones] spawn " + name + " failed (real player online, exiting, or internal error)"));
            cir.setReturnValue(0);
            return;
        }
        int cid = me.primaryuan.carpet.clones.CloneInstanceRegistry.cloneIdOf(created);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "[clones] spawned " + name + "#" + cid + " entityId=" + created.getId()), true);
        cir.setReturnValue(1);
    }

    /** 与 Carpet createFake 一致的 UUID 解析（旧存档映射 + 离线策略） */
    private static UUID resolveIdentity(CommandSourceStack source, String name) {
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(source.getServer(), name);
        if (uuid == null && CarpetSettings.allowSpawningOfflinePlayers) {
            uuid = UUIDUtil.createOfflinePlayerUUID(name);
        }
        return uuid;
    }
}
