package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import carpet.commands.PlayerCommand;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.CloneSpawnManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carpet /player 的增量 spawn：分身组身份的重复 spawn 不放行到
 * Carpet createFake（其 whenCompleteAsync 吞掉 placeNewPlayer 的任何异常，
 * 失败无日志、实体半死，M1/M2 两次实机受阻），改走
 * {@link CloneSpawnManager#spawnClone}——与 createFake 同序列但异常透明、
 * 组登记统一（M1 已实机验证该路径）。
 *
 * <p>注入点：PlayerCommand.spawn HEAD。守卫：规则开启 + 名字已是分身组。
 * 未建组身份（首次 spawn、普通假人、真人）不命中守卫，走原路径。</p>
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
        if (CloneGroupRegistry.byName(name) == null) {
            return;
        }
        CommandSourceStack source = context.getSource();
        // 已在线真人占用该身份时拒绝（CloneSpawnManager 内还有第二层保护）
        if (CloneGroupRegistry.isOnlineRealPlayer(source.getServer(),
                CloneGroupRegistry.byName(name).uuid())) {
            cir.setReturnValue(0);
            return;
        }
        ServerLevel level = source.getLevel();
        var pos = source.getPosition();
        var rot = source.getRotation();
        ServerPlayer created = CloneSpawnManager.spawnClone(source.getServer(), name, level, pos, rot.y, rot.x);
        if (created == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(
                    "[clones] spawn " + name + " failed (real player online or internal error)"));
            cir.setReturnValue(0);
            return;
        }
        int cid = me.primaryuan.carpet.clones.CloneInstanceRegistry.cloneIdOf(created);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "[clones] spawned " + name + "#" + cid + " entityId=" + created.getId()), true);
        cir.setReturnValue(1);
    }
}
