package me.primaryuan.carpet.handler.playerHeadSkin;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HeadSkinSessionTest {
    private final GameProfile original = profile("wearer", "original", "marker");

    @Test
    void resolvedHeadReplacesOnlyTexturesOnMainThread() {
        var session = new HeadSkinSession(original);
        var queue = new ArrayDeque<Runnable>();
        var visible = new AtomicReference<>(original);
        var future = new CompletableFuture<GameProfile>();
        session.resolve(future, queue::add, () -> true, visible::set, failure -> fail(failure));
        future.complete(profile("head-owner", "head-skin", "donor-marker"));
        assertSame(original, visible.get(), "后台完成不能直接修改玩家");
        queue.remove().run();
        assertEquals(original.id(), visible.get().id());
        assertEquals("wearer", visible.get().name());
        assertEquals("head-skin", visible.get().properties().get("textures").iterator().next().value());
        assertEquals("marker", visible.get().properties().get("marker").iterator().next().value());
        assertEquals("original", original.properties().get("textures").iterator().next().value());
    }

    @Test
    void removalBeforeCompletionRejectsOldSkin() {
        var session = new HeadSkinSession(original);
        var future = new CompletableFuture<GameProfile>();
        var visible = new AtomicReference<>(original);
        session.resolve(future, Runnable::run, () -> true, visible::set, failure -> fail(failure));
        session.invalidate();
        future.complete(profile("old-head", "old-skin", "donor"));
        assertSame(original, visible.get());
    }

    @Test
    void invalidationAfterCompletionBeforeMainThreadStillRejectsOldSkin() {
        var session = new HeadSkinSession(original);
        var queue = new ArrayDeque<Runnable>();
        var visible = new AtomicReference<>(original);
        session.resolve(CompletableFuture.completedFuture(profile("head", "skin", "donor")),
                queue::add, () -> true, visible::set, failure -> fail(failure));
        session.invalidate();
        queue.remove().run();
        assertSame(original, visible.get());
    }

    @Test
    void headChangeOrEntityReplacementRejectsCompletion() {
        var session = new HeadSkinSession(original);
        var future = new CompletableFuture<GameProfile>();
        var current = new AtomicBoolean(true);
        var visible = new AtomicReference<>(original);
        session.resolve(future, Runnable::run, current::get, visible::set, failure -> fail(failure));
        current.set(false);
        future.complete(profile("old-head", "old-skin", "donor"));
        assertSame(original, visible.get());
    }

    @Test
    void switchingHeadsKeepsFirstOriginalAndRejectsSlowerOldRequest() {
        var old = new HeadSkinSession(original);
        var slow = new CompletableFuture<GameProfile>();
        var visible = new AtomicReference<>(original);
        old.resolve(slow, Runnable::run, () -> true, visible::set, failure -> fail(failure));
        old.invalidate();
        var next = new HeadSkinSession(old.original());
        next.resolve(CompletableFuture.completedFuture(profile("new-head", "new-skin", "donor")),
                Runnable::run, () -> true, visible::set, failure -> fail(failure));
        slow.complete(profile("old-head", "old-skin", "donor"));
        assertEquals("new-skin", visible.get().properties().get("textures").iterator().next().value());
        assertSame(original, next.original());
    }

    @Test
    void emptyResolvedProfileLeavesOriginalSkinAndReportsMissingTexture() {
        var visible = new AtomicReference<>(original);
        var reported = new AtomicReference<Throwable>();
        new HeadSkinSession(original).resolve(CompletableFuture.completedFuture(
                        new GameProfile(UUID.randomUUID(), "empty", new PropertyMap(LinkedHashMultimap.create()))),
                Runnable::run, () -> true, visible::set, reported::set);
        assertSame(original, visible.get());
        assertNotNull(reported.get(), "原版可能以无纹理正常返回，仍需记录无法换肤的原因");
    }

    @Test
    void failureIsReportedWithoutApplyingSkin() {
        var visible = new AtomicReference<>(original);
        var reported = new AtomicReference<Throwable>();
        var failure = new IllegalStateException("unavailable");
        new HeadSkinSession(original).resolve(CompletableFuture.failedFuture(failure),
                Runnable::run, () -> true, visible::set, reported::set);
        assertSame(original, visible.get());
        assertSame(failure, reported.get());
    }

    private static GameProfile profile(String name, String skin, String marker) {
        var properties = LinkedHashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", skin));
        properties.put("marker", new Property("marker", marker));
        return new GameProfile(UUID.randomUUID(), name, new PropertyMap(properties));
    }
}
