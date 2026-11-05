package me.primaryuan.carpet.clones;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同 UUID 分身的注入点白名单。
 *
 * <p>三个实体层 UUID 拦截点（PESM.addEntityUuid / EntityLookup.add /
 * ServerLevel.addPlayer）在放行重复 UUID 前必须先查这里：
 * 只有显式登记为分身允许的 UUID 才走多实例路径，其余一切实体
 * （普通假人、真人、生物）零行为差异。</p>
 *
 * <p>由 CloneGroupRegistry 随分身组的创建和销毁登记与注销。</p>
 */
public final class CloneAllowlist {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 允许多实例共存的 UUID（分身组身份） */
    private static final Set<UUID> ALLOWED = ConcurrentHashMap.newKeySet();

    private CloneAllowlist() {}

    /** 登记：允许该 UUID 创建第二个及以后的实例 */
    public static void allow(UUID uuid, String debugName) {
        ALLOWED.add(uuid);
        LOGGER.info("[pry:clones] allowlist + {} ({})", uuid, debugName);
    }

    /** 注销（全组退出时调用） */
    public static void revoke(UUID uuid, String debugName) {
        if (ALLOWED.remove(uuid)) {
            LOGGER.info("[pry:clones] allowlist - {} ({})", uuid, debugName);
        }
    }

    /** 注入点查询：该 UUID 是否放行多实例 */
    public static boolean isAllowed(UUID uuid) {
        return uuid != null && ALLOWED.contains(uuid);
    }

    public static int size() {
        return ALLOWED.size();
    }

    /** 停服/换世界清理 */
    public static void clear() {
        ALLOWED.clear();
    }
}
