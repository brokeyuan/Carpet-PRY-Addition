package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同 UUID 分身注入点四：PlayerList.save(ServerPlayer) 每个实例退出/自动保存
 * 都写同一 playerdata/<uuid>.dat，后写覆盖。
 *
 * <p>M1 原型策略：分身组的非主实例（playersByUUID 里登记的不是当前玩家）
 * cancel 保存，只有主实例写盘。这样组内背包数据以主实例为准落到原版
 * playerdata，M4 再换成组级统一保存（独立文件 + 快照）。真人无分身时
 * 主实例就是自己，路径零改动。</p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListSaveMixin {

    @Inject(
            method = "save(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void pry$skipNonPrimaryCloneSave(ServerPlayer player, CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        if (CloneIdentity.isNonPrimaryClone(player)) {
            ci.cancel();
        }
    }
}
