package me.primaryuan.carpet.clones;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关键注入命中探针：require=0 的生命周期/保存入口在运行时首次命中时写一条
 * INFO，证明注入确实执行，而不是仅仅存在同参数形状的方法。只记录首次，
 * 不在热路径持续输出。
 */
public final class CloneInjectProbe {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Map<String, Integer> HITS = new ConcurrentHashMap<>();

    private CloneInjectProbe() {}

    public static void hit(String site) {
        int n = HITS.merge(site, 1, Integer::sum);
        if (n == 1) {
            LOGGER.info("[pry:clones] inject hit {}", site);
        }
    }

    public static Map<String, Integer> snapshot() {
        return Map.copyOf(HITS);
    }

    /** 停服清理，避免同一 JVM 下一会话沿用旧命中记录 */
    public static void clear() {
        HITS.clear();
    }
}
