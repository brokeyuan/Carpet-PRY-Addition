package me.primaryuan.carpet.util;

import com.mojang.authlib.properties.Property;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假人皮肤管理器：fakePlayerSkinMode 的出生前注入状态与 SkinRestorer 会话压制。
 *
 * <p>语义（所有假人出生即穿配置皮肤）：真人名与合成名假人一视同仁；真人玩家的
 * 保护是双保险——皮肤从不写入 SkinRestorer 持久存储（save=false），且 SkinRestorer
 * join 压制仅对在线的 EntityPlayerMPFake 实例生效（真人 join 不是假人实例）。</p>
 *
 * <p>注入链路（无闪皮）：
 * <ol>
 *   <li>PlayerCommandSkinMixin 在 /player spawn HEAD 按假人名入队皮肤来源
 *       （TIS Addition 的 rejoin 复用 Carpet 的 spawn 方法，同样覆盖）；</li>
 *   <li>EntityPlayerMPFakeSkinMixin 在假人构造器 RETURN 消费——Carpet createFake
 *       为两段式（异步拉取 profile 后回调构造实体），构造器收到的 profile 就是
 *       placeNewPlayer 出生包所带 profile；新 authlib 属性表不可变，故复制重组后
 *       经 accessor 整体替换实体 gameProfile 字段，观战者首帧即目标皮肤；</li>
 *   <li>SkinRestorerGuardMixin 压制 SkinRestorer join 钩子对已注入假人的存储皮
 *       覆盖（防历史 save=true 时代落库残留），真人玩家不受影响。</li>
 * </ol></p>
 *
 * <p>summon 模式的皮肤优先取召唤者在线 profile 的纹理快照（内存直拷，零网络），
 * 快照缺失（离线服真人 profile 无纹理）时由兜底按召唤者名走 provider 解析；
 * same_skin 经 SkinRestorer 的 mojang provider 后台预热到内存缓存（不落库）。
 * 任何来源未就绪的生成都降级为出生后换肤兜底（观感为闪一次），全部路径有日志。</p>
 */
public final class FakePlayerSkinManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 一次假人生成的皮肤来源与注入结果（构造器与 spawn TAIL 之间共享） */
    public static final class PendingSkin {
        final Property summonerSkin;
        final String sameSkinName;
        /** 兜底后置换肤用的目标玩家名（summon=召唤者名；same_skin=规则值） */
        public final String fallbackName;
        public volatile boolean injected;

        PendingSkin(Property summonerSkin, String sameSkinName, String fallbackName) {
            this.summonerSkin = summonerSkin;
            this.sameSkinName = sameSkinName;
            this.fallbackName = fallbackName;
        }

        public void markInjected() {
            this.injected = true;
        }
    }

    /** 假人名(小写) → 进行中的皮肤来源（spawn HEAD 入队，构造器打标，兜底循环终态时移除） */
    private static final Map<String, PendingSkin> PENDING = new ConcurrentHashMap<>();
    /** 已成功注入皮肤的假人 UUID：SkinRestorer join 钩子对这些假人跳过存储皮应用 */
    private static final Set<UUID> PROTECTED = ConcurrentHashMap.newKeySet();
    /** same_skin 预热缓存：皮肤名(小写) → 纹理属性 */
    private static final Map<String, Property> SKIN_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean lifecycleRegistered = false;
    private static volatile MinecraftServer currentServer;

    private FakePlayerSkinManager() {}

    private static void ensureLifecycle() {
        if (lifecycleRegistered) return;
        lifecycleRegistered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            if (isSameSkinMode()) {
                warmup(CarpetPrimaryuanSettings.fakePlayerSkinSet);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PENDING.clear();
            PROTECTED.clear();
            SKIN_CACHE.clear();
            currentServer = null;
        });
    }

    /** spawn HEAD：入队本次生成的皮肤来源（summon 与 same_skin 二选一） */
    public static void enqueue(String fakeName, Property summonerSkin, String sameSkinName, String fallbackName) {
        ensureLifecycle();
        PENDING.put(key(fakeName), new PendingSkin(summonerSkin, sameSkinName, fallbackName));
    }

    /**
     * 假人构造器 RETURN / spawn TAIL：按假人名读取皮肤来源。
     * 构造器（异步拉取 profile 后、晚于 spawn TAIL）只打标记不移除；
     * 移除统一由 TAIL 兜底循环在终态时完成（{@link #remove}）。
     */
    public static PendingSkin peek(String fakeName) {
        return PENDING.get(key(fakeName));
    }

    /** 兜底循环终态（已注入 / 已兜底后置换肤 / 超时）时移除条目 */
    public static void remove(String fakeName) {
        PENDING.remove(key(fakeName));
    }

    /**
     * 解析条目对应的目标纹理：召唤者快照直取；same_skin 只读预热缓存，绝不主线程网络。
     * summon 空快照（sameSkinName 为 null，离线服场景）返回 null，由兜底按名走 provider。
     */
    public static Property pickProperty(PendingSkin entry) {
        if (entry.summonerSkin != null) {
            return entry.summonerSkin;
        }
        if (entry.sameSkinName == null) {
            return null;
        }
        String cacheKey = key(entry.sameSkinName);
        Property cached = SKIN_CACHE.get(cacheKey);
        if (cached == null) {
            warmup(entry.sameSkinName);
        }
        return cached;
    }

    public static void markProtected(UUID uuid) {
        PROTECTED.add(uuid);
    }

    public static boolean isProtected(UUID uuid) {
        return PROTECTED.contains(uuid);
    }

    /** 规则变更：仅统一皮肤名变更时清缓存（模式切换保留缓存，控制台回退可立即命中）；
     *  运行中且 same_skin 时按新值预热 */
    public static void onRuleChanged(String ruleName) {
        if ("fakePlayerSkinSet".equals(ruleName)) {
            SKIN_CACHE.clear();
        }
        if (currentServer != null && isSameSkinMode()) {
            warmup(CarpetPrimaryuanSettings.fakePlayerSkinSet);
        }
    }

    private static boolean isSameSkinMode() {
        return "same_skin".equals(CarpetPrimaryuanSettings.fakePlayerSkinMode);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** 后台经 SkinRestorer 的 mojang provider 解析皮肤纹理（一次性网络调用，只入缓存不落库） */
    private static void warmup(String skinName) {
        if (skinName == null || skinName.isEmpty()) return;
        String cacheKey = key(skinName);
        if (SKIN_CACHE.containsKey(cacheKey)) return;
        CompletableFuture.runAsync(() -> {
            Property property = resolveViaSkinRestorer(skinName);
            if (property != null) {
                SKIN_CACHE.put(cacheKey, property);
                LOGGER.info("[pry] same_skin 皮肤预解析完成: {}", skinName);
            } else {
                LOGGER.warn("[pry] same_skin 皮肤预解析失败（skinrestorer 缺失或查无此玩家）: {}", skinName);
            }
        });
    }

    /**
     * 反射调用 SkinRestorer：getProvider("mojang").fetchSkin(name, SLIM)
     * → Result&lt;Optional&lt;Property&gt;, Exception&gt;。fetchSkin 为同步网络请求，仅限后台线程。
     */
    static Property resolveViaSkinRestorer(String skinName) {
        try {
            Class<?> skinRestorer = Class.forName("net.lionarius.skinrestorer.SkinRestorer");
            Object providerOpt = skinRestorer.getMethod("getProvider", String.class).invoke(null, "mojang");
            if (!(providerOpt instanceof Optional<?> opt) || opt.isEmpty()) {
                return null;
            }
            Object provider = opt.get();
            Class<?> variantClass = Class.forName("net.lionarius.skinrestorer.skin.SkinVariant");
            Object slim = variantClass.getField("SLIM").get(null);
            Object result = provider.getClass()
                    .getMethod("fetchSkin", String.class, variantClass)
                    .invoke(provider, skinName, slim);
            if (!(Boolean) result.getClass().getMethod("isSuccess").invoke(result)) {
                return null;
            }
            Object successValue = result.getClass().getMethod("getSuccessValue").invoke(result);
            if (successValue instanceof Optional<?> value && value.isPresent()
                    && value.get() instanceof Property property) {
                return property;
            }
            return null;
        } catch (ClassNotFoundException e) {
            // skinrestorer 未安装（可选依赖）
            return null;
        } catch (Exception e) {
            LOGGER.warn("[pry] same_skin 皮肤解析异常: {}", e.toString());
            return null;
        }
    }

    /**
     * 兜底：出生后换肤（save=false，不写入 SkinRestorer 持久存储——假人 UUID 与同名真人
     * 相同时落库会导致真人上线被换肤）。目标集合元素按 SkinRestorer 时代自适应：
     * SkinTarget 期用 SkinTarget.of(ServerPlayer)，旧版直接传 GameProfile。
     * 新版 SkinService.applySkin 内部含 refreshPlayer 重发，无需额外刷新。
     */
    public static void applyPostSpawn(MinecraftServer server, ServerPlayer fakePlayer, String skinName) {
        try {
            LOGGER.info("[pry] fakePlayerSkin: 后置换肤 {} -> {} (save=false，仅本次会话)",
                    fakePlayer.getUUID(), skinName);
            Class<?> contextClass = Class.forName("net.lionarius.skinrestorer.skin.provider.SkinProviderContext");
            Class<?> variantClass = Class.forName("net.lionarius.skinrestorer.skin.SkinVariant");
            Class<?> serviceClass = Class.forName("net.lionarius.skinrestorer.skin.SkinService");
            Object slim = variantClass.getField("SLIM").get(null);
            Object context = contextClass.getConstructor(String.class, String.class, variantClass)
                    .newInstance("mojang", skinName, slim);
            Object target = toSkinRestorerTarget(fakePlayer);
            serviceClass.getMethod("setSkinAsync", MinecraftServer.class, java.util.Collection.class,
                    contextClass, boolean.class).invoke(null,
                    server, Collections.singletonList(target), context, false);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[pry] fakePlayerSkin: skinrestorer 未安装，无法换肤（可安装 skinrestorer 或设 fakePlayerSkinMode=default）");
        } catch (Exception e) {
            LOGGER.error("[pry] fakePlayerSkin: 后置换肤失败", e);
        }
    }

    private static Object toSkinRestorerTarget(ServerPlayer player) {
        try {
            Class<?> skinTargetClass = Class.forName("net.lionarius.skinrestorer.skin.SkinTarget");
            return skinTargetClass.getMethod("of", ServerPlayer.class).invoke(null, player);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return player.getGameProfile();
        } catch (Exception e) {
            LOGGER.warn("[pry] fakePlayerSkin: SkinTarget.of 包装失败，回退直接传实体: {}", e.toString());
            return player.getGameProfile();
        }
    }
}
