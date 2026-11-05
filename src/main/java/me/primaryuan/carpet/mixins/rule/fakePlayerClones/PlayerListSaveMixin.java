package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.CloneIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同 UUID 分身组只由主实例写 playerdata。
 *
 * <p>判定不依赖 fakePlayerClones 当前值：组仍在时规则被外部配置关闭，
 * 也不能让多实例同时写同一 UUID 存档。整组退出前已显式保存一次，
 * 退出过程中的逐实例 save 全部跳过。</p>
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
        if (CloneIdentity.shouldSkipSave(player)) {
            ci.cancel();
        }
    }
}
