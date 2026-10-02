package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mutable;

/**
 * Player.gameProfile 字段访问器：出生前注入皮肤时整体替换 profile 用。
 *
 * <p>1.21.11+ 的 authlib GameProfile 属性表不可变（removeAll/put 抛
 * UnsupportedOperationException，本地 E2E 实证），无法原地改纹理——与 SkinRestorer
 * 自己的 PlayerAccessor 同款方案：构建带目标皮肤的新 profile 后经 @Mutable
 * accessor 整体替换 final 字段。字段名跨版本一致（SkinRestorer 的同名 accessor
 * 在 1.21~26.3 全分支共用同一份源码），class 字面量 target 由 loom 自动重映射。</p>
 */
@Mixin(Player.class)
public interface PlayerGameProfileAccessor {

    @Accessor("gameProfile")
    @Mutable
    void pry$setGameProfile(GameProfile profile);
}
