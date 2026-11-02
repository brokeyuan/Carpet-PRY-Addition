package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneAllowlist;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 同 UUID 分身注入点三（玩家加入路径）：ServerLevel.addPlayer 发现同 UUID
 * 已有实体时会 DISCARD 旧玩家（"Force-added player with duplicate UUID"）。
 *
 * <p>@Redirect getEntity(UUID)：分身允许名单内的 UUID 查询返回 null，
 * 跳过 DISCARD 分支，新分身与旧分身共存。普通玩家/假人路径不满足守卫，
 * Redirect 直通原调用，行为零差异。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelAddPlayerMixin {

    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;",
                    remap = true
            ),
            require = 0
    )
    private net.minecraft.world.entity.Entity pry$noDiscardExistingClone(
            ServerLevel instance, UUID uuid
    ) {
        if (CloneAllowlist.isAllowed(uuid)) {
            return null;
        }
        return instance.getEntity(uuid);
    }

    /** 1.21~1.21.4：addPlayer 通过 getEntities().get(UUID) 查重 */
    @Redirect(
            method = "addPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;",
                    remap = true
            ),
            require = 0
    )
    private net.minecraft.world.level.entity.EntityAccess pry$noDiscardExistingCloneOld(
            net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.level.entity.EntityAccess> getter,
            UUID uuid
    ) {
        if (CloneAllowlist.isAllowed(uuid)) {
            return null;
        }
        return getter.get(uuid);
    }
}
