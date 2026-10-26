package me.primaryuan.carpet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.config.PryJsonStore;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨服传送（/tpto /tptoset）的服务器列表配置。
 *
 * <p>数据跟随服务器：config/carpet-pry-addition/tpto.json（目标服地址与存档无关，
 * 换存档不漂移）。新增功能无旧格式可收编。所有状态仅在服务器主线程访问
 * （Carpet 命令与服务器生命周期回调均在主线程），普通集合即可。</p>
 */
public final class TptoConfigManager {

    /** 单台目标服配置；displayName 为 null 表示无显示名（展示时回退内部名） */
    public record ServerEntry(String host, int port, String displayName) {}

    /** 列表/管理界面渲染用的只读视图 */
    public record ServerView(String name, String displayName, String host, int port) {}

    private static final TptoStore STORE = new TptoStore();

    private TptoConfigManager() {}

    /** 包内可见以便单测直接驱动 applyData/collectData（不触碰 fabric loader） */
    static final class TptoStore extends PryJsonStore {
        /** 内部名 → 服务器配置；LinkedHashMap 保持添加顺序（列表展示顺序） */
        private final LinkedHashMap<String, ServerEntry> servers = new LinkedHashMap<>();

        TptoStore() {
            super("TPTO", "tpto.json", Scope.GLOBAL, 1, null);
        }

        @Override
        protected void applyData(JsonObject data) {
            // 先解析到临时结构、全部成功后再原子提交内存态。
            // 单条坏数据（手改成错误类型/端口越界）只跳过该条并告警，不拖垮整个列表
            LinkedHashMap<String, ServerEntry> newServers = new LinkedHashMap<>();
            JsonElement serversElem = data.get("servers");
            if (serversElem != null && !serversElem.isJsonObject()) {
                // 顶层结构损坏时无法逐条跳过，按空表处理但必须留痕，
                // 否则下一次 save 会把空表静默写回
                LOGGER.warn("[TPTO] 'servers' is not a JSON object, treating the list as empty");
            }
            if (serversElem != null && serversElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : serversElem.getAsJsonObject().entrySet()) {
                    JsonElement value = entry.getValue();
                    if (!value.isJsonObject()) {
                        LOGGER.warn("[TPTO] Skipping server '{}': not an object", entry.getKey());
                        continue;
                    }
                    try {
                        JsonObject obj = value.getAsJsonObject();
                        String host = obj.get("host").getAsString();
                        int port = obj.get("port").getAsInt();
                        if (port < 1 || port > 65535) {
                            LOGGER.warn("[TPTO] Skipping server '{}': port {} out of range",
                                    entry.getKey(), port);
                            continue;
                        }
                        String displayName = obj.has("displayName") && !obj.get("displayName").isJsonNull()
                                ? obj.get("displayName").getAsString() : null;
                        newServers.put(entry.getKey(), new ServerEntry(host, port, displayName));
                    } catch (RuntimeException e) {
                        LOGGER.warn("[TPTO] Skipping malformed server '{}'", entry.getKey(), e);
                    }
                }
            }
            servers.clear();
            servers.putAll(newServers);
        }

        @Override
        protected JsonObject collectData() {
            JsonObject data = new JsonObject();
            JsonObject serversObj = new JsonObject();
            for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
                ServerEntry server = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("host", server.host());
                obj.addProperty("port", server.port());
                if (server.displayName() != null) {
                    obj.addProperty("displayName", server.displayName());
                }
                serversObj.add(entry.getKey(), obj);
            }
            data.add("servers", serversObj);
            return data;
        }

        @Override
        protected void clearData() {
            servers.clear();
        }
    }

    // ===== 服务器生命周期（CarpetPrimaryuanServer 调用） =====

    public static void onServerLoaded(MinecraftServer server) {
        STORE.attach(server);
    }

    public static void onServerClosed() {
        STORE.detach();
    }

    /** 从盘重读（/tptoset reload，供手改文件后生效） */
    public static void reload(MinecraftServer server) {
        STORE.attach(server);
    }

    // ===== 领域方法：所有状态变更经由此处，并自动持久化 =====

    /**
     * 添加服务器。
     *
     * @param name        内部名（唯一键，同时用于点击事件命令）
     * @param displayName 显示名（null 表示无，展示时回退内部名）
     * @return false 表示内部名已存在（不覆盖）
     */
    public static boolean addServer(String name, String host, int port, String displayName) {
        if (!addServerTo(STORE.servers, name, host, port, displayName)) {
            return false;
        }
        STORE.save();
        return true;
    }

    /** 添加逻辑的纯函数形态（重名不覆盖），单测不触碰 STORE 与磁盘 */
    static boolean addServerTo(Map<String, ServerEntry> servers, String name,
                               String host, int port, String displayName) {
        if (servers.containsKey(name)) {
            return false;
        }
        servers.put(name, new ServerEntry(host, port, displayName));
        return true;
    }

    /**
     * 删除服务器。
     *
     * @return false 表示内部名不存在
     */
    public static boolean removeServer(String name) {
        if (STORE.servers.remove(name) == null) {
            return false;
        }
        STORE.save();
        return true;
    }

    /** 按内部名取配置；不存在返回 null */
    public static ServerEntry getServer(String internalName) {
        return STORE.servers.get(internalName);
    }

    /** 服务器数量（reload 反馈用） */
    public static int count() {
        return STORE.servers.size();
    }

    /** 展示用名称：无显示名回退内部名 */
    public static String getDisplayName(String internalName) {
        return displayNameOf(STORE.servers, internalName);
    }

    /** 按玩家输入（内部名或显示名）反查内部名；未找到返回 null */
    public static String getInternalName(String input) {
        return resolveInternalName(STORE.servers, input);
    }

    /** 列表/管理界面渲染快照（添加顺序） */
    public static List<ServerView> getServerViews() {
        List<ServerView> views = new ArrayList<>(STORE.servers.size());
        for (Map.Entry<String, ServerEntry> entry : STORE.servers.entrySet()) {
            ServerEntry server = entry.getValue();
            views.add(new ServerView(entry.getKey(), getDisplayName(entry.getKey()),
                    server.host(), server.port()));
        }
        return views;
    }

    /** 全部显示名（Tab 补全用，添加顺序） */
    public static List<String> getDisplayNames() {
        List<String> names = new ArrayList<>(STORE.servers.size());
        for (String key : STORE.servers.keySet()) {
            names.add(getDisplayName(key));
        }
        return names;
    }

    // ===== 纯函数（单测可独立驱动） =====

    static String displayNameOf(Map<String, ServerEntry> servers, String internalName) {
        ServerEntry server = servers.get(internalName);
        if (server == null) {
            return internalName;
        }
        String display = server.displayName();
        return (display == null || display.isEmpty()) ? internalName : display;
    }

    static String resolveInternalName(Map<String, ServerEntry> servers, String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        // 先精确匹配内部名
        if (servers.containsKey(input)) {
            return input;
        }
        // 再匹配显示名（忽略大小写）
        for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
            String display = entry.getValue().displayName();
            if (display != null && !display.isEmpty() && display.equalsIgnoreCase(input)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
