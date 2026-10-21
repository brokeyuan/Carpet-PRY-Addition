package me.primaryuan.carpet.handler.playerHeadSkin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 保存一次佩戴的原皮肤，并作废迟到的解析结果。 */
final class HeadSkinSession {
    private final GameProfile original;
    private boolean active = true;

    HeadSkinSession(GameProfile original) {
        this.original = original;
    }

    GameProfile original() {
        return original;
    }

    void invalidate() {
        active = false;
    }

    void resolve(CompletableFuture<GameProfile> future, Executor mainThread, BooleanSupplier current,
                 Consumer<GameProfile> apply, Consumer<Throwable> failure) {
        future.whenComplete((resolved, problem) -> mainThread.execute(() -> {
            if (!active || !current.getAsBoolean()) return;
            if (problem != null || resolved == null) {
                failure.accept(problem != null ? problem : new IllegalStateException("头颅档案解析结果为空"));
                return;
            }
            //#if MC >= 12110
            var textures = resolved.properties().get("textures");
            //#else
            //$$ var textures = resolved.getProperties().get("textures");
            //#endif
            if (textures.isEmpty()) {
                failure.accept(new IllegalStateException("头颅档案不含皮肤纹理"));
                return;
            }
            GameProfile withSkin;
            //#if MC >= 12110
            var mutable = com.google.common.collect.LinkedHashMultimap.<String, Property>create(original.properties());
            mutable.removeAll("textures");
            mutable.putAll("textures", textures);
            withSkin = new GameProfile(original.id(), original.name(), new PropertyMap(mutable));
            //#else
            //$$ withSkin = new GameProfile(original.getId(), original.getName());
            //$$ withSkin.getProperties().putAll(original.getProperties());
            //$$ withSkin.getProperties().removeAll("textures");
            //$$ withSkin.getProperties().putAll("textures", textures);
            //#endif
            apply.accept(withSkin);
        }));
    }
}
