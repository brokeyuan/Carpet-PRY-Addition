package me.primaryuan.carpet.mixins.rule.playerHeadSkin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity pry$headSkinServerEntity();

    @Accessor("seenBy")
    Set<ServerPlayerConnection> pry$headSkinSeenBy();
}
