package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 调用 protected PlayerList.save(ServerPlayer)：整组退出前只写一次权威存档。
 */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {

    @Invoker("save")
    void pry$save(ServerPlayer player);
}
