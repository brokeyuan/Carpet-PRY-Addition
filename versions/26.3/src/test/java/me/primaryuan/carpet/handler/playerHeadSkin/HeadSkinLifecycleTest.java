package me.primaryuan.carpet.handler.playerHeadSkin;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.primaryuan.carpet.mixins.rule.fakePlayerSkin.PlayerGameProfileAccessor;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;

/** 覆盖同 UUID 换实体时原皮肤被临时头颅皮肤污染的回归路径。 */
class HeadSkinLifecycleTest {
    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void respawnRestoresFirstOriginalAndInvalidatesOldRequest() throws Exception {
        var original = profile("original");
        var masked = profile("head");
        var oldPlayer = player(masked);
        var replacement = player(masked);
        var session = install(oldPlayer, original);
        var late = new CompletableFuture<GameProfile>();
        session.resolve(late, Runnable::run, () -> true, replacement::pry$setGameProfile, failure -> {});

        PlayerHeadSkinHandler.onRespawn(oldPlayer, replacement);
        late.complete(profile("late"));
        assertSame(original, replacement.getGameProfile());
    }

    @Test
    void shadowReadsOriginalProfileAndRejectsLateRequest() throws Exception {
        var original = profile("configured-fake-skin");
        var oldPlayer = player(profile("head"));
        var session = install(oldPlayer, original);
        var late = new CompletableFuture<GameProfile>();
        session.resolve(late, Runnable::run, () -> true, oldPlayer::pry$setGameProfile, failure -> {});

        PlayerHeadSkinHandler.beforeShadow(oldPlayer);
        late.complete(profile("late"));
        assertSame(original, oldPlayer.getGameProfile());
    }

    @Test
    void respawnWithoutHeadSessionLeavesOtherSkinUntouched() throws Exception {
        var original = profile("own-skin");
        var next = profile("other-mod-skin");
        var oldPlayer = player(original);
        var replacement = player(next);
        PlayerHeadSkinHandler.onRespawn(oldPlayer, replacement);
        assertSame(next, replacement.getGameProfile());
    }

    @Test
    void externalSkinAppliedBeforeRespawnBecomesNewOriginal() throws Exception {
        var oldPlayer = player(profile("head"));
        install(oldPlayer, profile("original"));
        var configured = profile("late-fake-skin-configuration");
        oldPlayer.pry$setGameProfile(configured);
        var replacement = player(configured);
        PlayerHeadSkinHandler.onRespawn(oldPlayer, replacement);
        assertSame(configured, replacement.getGameProfile());
    }

    @Test
    void externalSkinAppliedBeforeShadowIsRetained() throws Exception {
        var oldPlayer = player(profile("head"));
        install(oldPlayer, profile("original"));
        var configured = profile("new-external-skin");
        oldPlayer.pry$setGameProfile(configured);
        PlayerHeadSkinHandler.beforeShadow(oldPlayer);
        assertSame(configured, oldPlayer.getGameProfile());
    }

    @SuppressWarnings("unchecked")
    private static HeadSkinSession install(StubPlayer player, GameProfile original) throws Exception {
        var type = Class.forName(PlayerHeadSkinHandler.class.getName() + "$WornHead");
        var constructor = type.getDeclaredConstructor(ResolvableProfile.class, GameProfile.class);
        constructor.setAccessible(true);
        var worn = constructor.newInstance(null, original);
        var expected = type.getDeclaredField("expected");
        expected.setAccessible(true);
        expected.set(worn, player.getGameProfile());
        var sessions = PlayerHeadSkinHandler.class.getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        ((Map<ServerPlayer, Object>) sessions.get(null)).put(player, worn);
        var skin = type.getDeclaredField("skin");
        skin.setAccessible(true);
        return (HeadSkinSession) skin.get(worn);
    }

    private static GameProfile profile(String texture) {
        var properties = LinkedHashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", texture));
        return new GameProfile(new UUID(12, 34), "wearer", new PropertyMap(properties));
    }

    private static StubPlayer player(GameProfile profile) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var player = (StubPlayer) ((Unsafe) field.get(null)).allocateInstance(StubPlayer.class);
        player.profile = profile;
        return player;
    }

    private static final class StubPlayer extends ServerPlayer implements PlayerGameProfileAccessor {
        private GameProfile profile;
        private StubPlayer() { super(null, null, null, null); }
        @Override public GameProfile getGameProfile() { return profile; }
        @Override public void pry$setGameProfile(GameProfile value) { profile = value; }
    }
}
