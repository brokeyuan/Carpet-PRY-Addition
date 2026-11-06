package me.primaryuan.carpet.handler.playerHead;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//#if MC < 12110
//$$ import net.minecraft.world.level.block.entity.SkullBlockEntity;
//#endif

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 皮肤来源查询：按玩家名异步解析皮肤纹理，带容量、过期与并发合并。
 *
 * <p>结果回调统一投递到发起查询的服务器线程；服务器实例变化、停服或规则关闭后
 * 迟到结果不会交付。查询本身不做任何阻塞等待。</p>
 */
public final class HeadSkinResolver {
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final int CACHE_LIMIT = 128;
    private static final long POSITIVE_TTL_MS = 10 * 60 * 1000L;
    private static final long NEGATIVE_TTL_MS = 60 * 1000L;
    private static final long TIMEOUT_MS = 10_000L;

    private static final Object LOCK = new Object();
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static final Map<String, CompletableFuture<HeadSkin>> IN_FLIGHT = new HashMap<>();
    private static MinecraftServer boundServer;

    private HeadSkinResolver() {}

    /** 查询指定玩家名的皮肤；成功/失败回调都在服务器线程执行。 */
    public static void resolve(MinecraftServer server, String name,
                               Consumer<HeadSkin> success, Consumer<Throwable> failure) {
        if (server == null || name == null) return;
        String trimmed = name.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);
        CompletableFuture<HeadSkin> future;
        Entry cached;
        synchronized (LOCK) {
            if (boundServer != server) {
                CACHE.clear();
                IN_FLIGHT.clear();
                boundServer = server;
            }
            long now = System.currentTimeMillis();
            cached = CACHE.get(key);
            if (cached != null && cached.expiresAt <= now) {
                CACHE.remove(key);
                cached = null;
            }
            if (cached == null) {
                future = IN_FLIGHT.get(key);
                if (future == null) {
                    future = start(server, trimmed);
                    IN_FLIGHT.put(key, future);
                    registerCleanup(server, key, trimmed, future);
                }
            } else {
                future = null;
            }
        }

        if (cached != null) {
            if (cached.skin != null) success.accept(cached.skin);
            else failure.accept(new IllegalStateException("皮肤来源不存在或没有纹理: " + trimmed));
            return;
        }
        if (future == null) return;
        future.whenComplete((skin, problem) -> runOnServer(server, () -> {
            if (skin != null) success.accept(skin);
            else failure.accept(problem != null ? problem : new IllegalStateException("皮肤查询结果为空"));
        }));
    }

    /** 服务器切换、停服或规则关闭时清空，旧世界的查询结果不再复用。 */
    public static void clear() {
        synchronized (LOCK) {
            CACHE.clear();
            IN_FLIGHT.clear();
            boundServer = null;
        }
    }

    private static CompletableFuture<HeadSkin> start(MinecraftServer server, String name) {
        try {
            return raw(server, name)
                    .thenApply(skin -> {
                        if (skin == null || !skin.hasTexture()) {
                            throw new IllegalStateException("皮肤来源不存在或没有纹理: " + name);
                        }
                        return skin;
                    })
                    .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (RuntimeException problem) {
            CompletableFuture<HeadSkin> failed = new CompletableFuture<>();
            failed.completeExceptionally(problem);
            return failed;
        }
    }

    /** 完成后释放并发表并写入短时缓存（失败也缓存，避免无效名称被连续改名反复查询）。 */
    private static void registerCleanup(MinecraftServer server, String key, String name,
                                        CompletableFuture<HeadSkin> future) {
        future.whenComplete((skin, problem) -> {
            synchronized (LOCK) {
                // 只清理本次请求：服务器切换后同名的下一次请求不能被旧结果误删。
                if (!IN_FLIGHT.remove(key, future)) return;
                if (boundServer == server) remember(key, skin);
            }
            if (problem != null) {
                LOGGER.debug("[pry] 头颅皮肤解析失败: {}", name, problem);
            }
        });
    }

    private static CompletableFuture<HeadSkin> raw(MinecraftServer server, String name) {
        //#if MC >= 12110
        return ResolvableProfile.createUnresolved(name)
                .resolveProfile(server.services().profileResolver())
                .thenApply(HeadSkin::fromProfile);
        //#else
        //$$ return SkullBlockEntity.fetchGameProfile(name)
        //$$         .thenApply(profile -> profile.map(HeadSkin::fromProfile).orElse(null));
        //#endif
    }

    private static void remember(String key, HeadSkin skin) {
        long now = System.currentTimeMillis();
        long ttl = skin != null && skin.hasTexture() ? POSITIVE_TTL_MS : NEGATIVE_TTL_MS;
        CACHE.put(key, new Entry(skin, now + ttl));
        Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > CACHE_LIMIT && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void runOnServer(MinecraftServer server, Runnable task) {
        synchronized (LOCK) {
            if (server.isStopped() || server != boundServer) return;
        }
        if (server.isSameThread()) task.run();
        else server.execute(task);
    }

    private record Entry(HeadSkin skin, long expiresAt) {}
}
