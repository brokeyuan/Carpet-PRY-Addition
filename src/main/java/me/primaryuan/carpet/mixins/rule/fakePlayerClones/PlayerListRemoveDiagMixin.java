package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.clones.CloneAllowlist;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M1 临时诊断：PlayerList.remove 移除分身时打印调用栈（定位静默移除者）。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListRemoveDiagMixin {

    private static final Logger DIAG = LogManager.getLogger("CarpetPrimaryuan");

    @Inject(
            method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void pry$diagRemove(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        if (CloneAllowlist.isAllowed(player.getUUID())) {
            StringBuilder sb = new StringBuilder("[pry:clones][DIAG] PlayerList.remove -> id=")
                    .append(player.getId()).append(" removed=").append(player.getRemovalReason());
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(st.length, 14); i++) {
                sb.append("\n    at ").append(st[i]);
            }
            DIAG.warn(sb.toString());
        }
    }
}
