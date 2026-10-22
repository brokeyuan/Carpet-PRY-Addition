package me.primaryuan.carpet;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.config.PryJsonStore;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 假人珍珠站传送（/tpp /tppset）的站点配置。
 *
 * <p>数据跟随存档：&lt;存档&gt;/carpet-pry-addition/tpp.json（站点本质是"本存档假人
 * 下线点"的名字表，坐标存于该存档假人 playerdata 的 rejoin 位置，脱离原存档无意义）。
 * 旧版平铺文件 config/carpet-pry-tpp.json 首次启动自动收编。所有状态仅在服务器
 * 主线程访问（Carpet 命令与 tick 事件均在主线程），普通集合即可。</p>
 */
public final class TppConfigManager {

    private static final TppStore STORE = new TppStore();

    private TppConfigManager() {}

    private static final class TppStore extends PryJsonStore {
        /**
         * 站点映射：key=内部名（用于假人命名），value=显示名称/备注
         * （null 表示无备注，显示时等同内部名）。LinkedHashMap 保持添加顺序
         */
        private final LinkedHashMap<String, String> stationMap = new LinkedHashMap<>();

        /** 玩家名 → 别名（用于假人命名） */
        private final Map<String, String> aliases = new HashMap<>();

        /** 全局默认传送时假人右键使用珍珠的次数（默认 1），站点未单独设置时使用此值 */
        private int useCount = 1;

        /** 站点级右键次数配置：key=站点内部名，value=右键次数 */
        private final Map<String, Integer> stationUseCount = new HashMap<>();

        private TppStore() {
            super("TPP", "tpp.json", Scope.WORLD, 1, "carpet-pry-tpp.json");
        }

        @Override
        protected void applyData(JsonObject data) {
            // 先解析到临时结构、全部成功后再原子提交内存态——
            // 中途抛异常（如手改成错误类型）不破坏现有内存态，之后任意一次 save()
            // 不会把残缺态全量写盘
            LinkedHashMap<String, String> newStations = new LinkedHashMap<>();
            JsonElement stationsElem = data.get("stations");
            if (stationsElem != null && stationsElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : stationsElem.getAsJsonObject().entrySet()) {
                    String value = entry.getValue().isJsonNull() ? null : entry.getValue().getAsString();
                    newStations.put(entry.getKey(), value);
                }
            }

            Map<String, String> newAliases = new LinkedHashMap<>();
            JsonElement aliasesElem = data.get("aliases");
            if (aliasesElem != null && aliasesElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : aliasesElem.getAsJsonObject().entrySet()) {
                    newAliases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            int newUseCount = data.has("useCount") ? data.get("useCount").getAsInt() : 1;

            Map<String, Integer> newStationUseCount = new HashMap<>();
            JsonElement countsElem = data.get("stationUseCount");
            if (countsElem != null && countsElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : countsElem.getAsJsonObject().entrySet()) {
                    newStationUseCount.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }

            stationMap.clear();
            stationMap.putAll(newStations);
            aliases.clear();
            aliases.putAll(newAliases);
            useCount = newUseCount;
            stationUseCount.clear();
            stationUseCount.putAll(newStationUseCount);
        }

        @Override
        protected JsonObject collectData() {
            JsonObject data = new JsonObject();

            JsonObject stationsObj = new JsonObject();
            for (Map.Entry<String, String> entry : stationMap.entrySet()) {
                if (entry.getValue() == null) {
                    stationsObj.add(entry.getKey(), JsonNull.INSTANCE);
                } else {
                    stationsObj.addProperty(entry.getKey(), entry.getValue());
                }
            }
            data.add("stations", stationsObj);

            JsonObject aliasesObj = new JsonObject();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                aliasesObj.addProperty(entry.getKey(), entry.getValue());
            }
            data.add("aliases", aliasesObj);
            data.addProperty("useCount", useCount);

            JsonObject countsObj = new JsonObject();
            for (Map.Entry<String, Integer> entry : stationUseCount.entrySet()) {
                countsObj.addProperty(entry.getKey(), entry.getValue());
            }
            data.add("stationUseCount", countsObj);
            return data;
        }

        @Override
        protected JsonObject fromLegacy(JsonObject legacyRoot) {
            // 旧平铺文件（v0）：更早期格式的 stations 为 JsonArray，统一收编为对象格式
            JsonObject data = legacyRoot.deepCopy();
            JsonElement stationsElem = data.get("stations");
            if (stationsElem != null && stationsElem.isJsonArray()) {
                JsonObject obj = new JsonObject();
                for (JsonElement element : stationsElem.getAsJsonArray()) {
                    obj.add(element.getAsString(), JsonNull.INSTANCE);
                }
                data.add("stations", obj);
            }
            return data;
        }

        @Override
        protected void clearData() {
            stationMap.clear();
            aliases.clear();
            useCount = 1;
            stationUseCount.clear();
        }
    }

    // ===== 服务器生命周期（CarpetPrimaryuanServer 调用） =====

    public static void onServerLoaded(MinecraftServer server) {
        STORE.attach(server);
    }

    public static void onServerClosed() {
        STORE.detach();
    }

    // ===== 领域方法：所有状态变更经由此处，并自动持久化 =====

    /**
     * 添加站点。
     *
     * @param name        站点内部名
     * @param displayName 显示名称/备注（null 表示无备注）
     * @return false 表示站点已存在（不覆盖）
     */
    public static boolean addStation(String name, String displayName) {
        if (STORE.stationMap.containsKey(name)) {
            return false;
        }
        STORE.stationMap.put(name, displayName);
        STORE.save();
        return true;
    }

    /**
     * 删除站点，同时清理其站点级右键次数配置（避免残留孤儿配置）。
     */
    public static void removeStation(String internalName) {
        STORE.stationMap.remove(internalName);
        STORE.stationUseCount.remove(internalName);
        STORE.save();
    }

    /**
     * 获取玩家别名；未设置返回 null。
     */
    public static String getAlias(String playerName) {
        return STORE.aliases.get(playerName);
    }

    /**
     * 设置玩家别名。
     */
    public static void setAlias(String playerName, String alias) {
        STORE.aliases.put(playerName, alias);
        STORE.save();
    }

    /**
     * 移除玩家别名。
     *
     * @return false 表示该玩家未设置别名
     */
    public static boolean removeAlias(String playerName) {
        if (STORE.aliases.remove(playerName) == null) {
            return false;
        }
        STORE.save();
        return true;
    }

    /**
     * 设置全局默认右键次数。
     */
    public static void setGlobalUseCount(int count) {
        STORE.useCount = count;
        STORE.save();
    }

    /**
     * 设置站点级右键次数。
     */
    public static void setStationUseCount(String station, int count) {
        STORE.stationUseCount.put(station, count);
        STORE.save();
    }

    /**
     * 获取全局默认右键次数。
     */
    public static int getGlobalUseCount() {
        return STORE.useCount;
    }

    /**
     * 获取站点级右键次数配置快照（用于 /tppset rule 展示）。
     */
    public static Map<String, Integer> getStationUseCountSnapshot() {
        return Map.copyOf(STORE.stationUseCount);
    }

    /**
     * 获取站点的显示名称。无备注时返回内部名本身。
     */
    public static String getDisplayName(String internalName) {
        String display = STORE.stationMap.get(internalName);
        return (display == null || display.isEmpty()) ? internalName : display;
    }

    /**
     * 根据玩家输入（可能是内部名或显示名）反查内部名。
     *
     * @param input 玩家输入的字符串
     * @return 内部名，未找到返回 null
     */
    public static String getInternalName(String input) {
        if (input == null || input.isEmpty()) return null;
        // 先精确匹配内部名
        if (STORE.stationMap.containsKey(input)) return input;
        // 再匹配显示名（忽略大小写）
        for (Map.Entry<String, String> entry : STORE.stationMap.entrySet()) {
            String display = entry.getValue();
            if (display != null && !display.isEmpty() && display.equalsIgnoreCase(input)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 获取所有站点的显示名称列表。
     */
    public static List<String> getDisplayNames() {
        return getMatchingDisplayNames("");
    }

    /** 按内部名或显示名的前缀匹配站点，忽略大小写，返回显示名供补全。 */
    public static List<String> getMatchingDisplayNames(String prefix) {
        List<String> result = new ArrayList<>();
        for (String key : STORE.stationMap.keySet()) {
            String displayName = getDisplayName(key);
            if (key.regionMatches(true, 0, prefix, 0, prefix.length())
                    || displayName.regionMatches(true, 0, prefix, 0, prefix.length())) {
                result.add(displayName);
            }
        }
        return result;
    }

    /**
     * 获取所有站点内部名的逗号分隔字符串
     */
    public static String getStationsString() {
        return String.join(",", STORE.stationMap.keySet());
    }

    /**
     * 获取指定站点的右键次数
     * 如果站点未单独设置，则返回全局默认值
     *
     * @param station 站点内部名
     * @return 该站点的右键次数
     */
    public static int getUseCount(String station) {
        return STORE.stationUseCount.getOrDefault(station, STORE.useCount);
    }
}
