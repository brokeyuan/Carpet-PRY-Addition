package me.primaryuan.carpet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TppConfigManager {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    // 以下状态仅在服务器主线程访问（Carpet 命令与 tick 事件均在主线程），普通集合即可

    /**
     * 站点映射：key=内部名（用于假人命名），value=显示名称/备注（null 表示无备注，显示时等同内部名）
     * 使用 LinkedHashMap 保持添加顺序
     */
    private static final LinkedHashMap<String, String> stationMap = new LinkedHashMap<>();

    private static final Map<String, String> aliases = new HashMap<>();

    /** 全局默认传送时假人右键使用珍珠的次数（默认 1），站点未单独设置时使用此值 */
    private static int useCount = 1;

    /** 站点级右键次数配置：key=站点内部名，value=右键次数 */
    private static final Map<String, Integer> stationUseCount = new HashMap<>();

    /** 配置文件路径（统一以 UTF-8 读写，避免 Windows 默认 GBK 编码导致中文站点名乱码） */
    private static final Path CONFIG_FILE = Path.of("config/carpet-pry-tpp.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            // 创建默认空配置
            JsonObject defaultConfig = new JsonObject();
            defaultConfig.add("stations", new JsonObject());
            defaultConfig.add("aliases", new JsonObject());

            try {
                Files.createDirectories(CONFIG_FILE.getParent());
                try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaultConfig, writer);
                }
            } catch (IOException e) {
                System.err.println("[TPP] Failed to create default config file: " + e.getMessage());
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                LOGGER.warn("[TPP] Config file is empty, using defaults");
                return;
            }

            stationMap.clear();
            JsonElement stationsElem = json.get("stations");
            if (stationsElem != null) {
                if (stationsElem.isJsonArray()) {
                    // 旧格式兼容：JsonArray → 转为 stationMap（value=null）
                    JsonArray arr = stationsElem.getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        stationMap.put(arr.get(i).getAsString(), null);
                    }
                } else if (stationsElem.isJsonObject()) {
                    // 新格式：JsonObject
                    JsonObject obj = stationsElem.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        String value = entry.getValue().isJsonNull() ? null : entry.getValue().getAsString();
                        stationMap.put(entry.getKey(), value);
                    }
                }
            }

            aliases.clear();
            JsonObject aliasesObj = json.getAsJsonObject("aliases");
            if (aliasesObj != null) {
                for (Map.Entry<String, JsonElement> entry : aliasesObj.entrySet()) {
                    aliases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            // 加载 useCount
            if (json.has("useCount")) {
                useCount = json.get("useCount").getAsInt();
            }

            // 加载站点级右键次数配置
            stationUseCount.clear();
            JsonObject stationUseCountObj = json.getAsJsonObject("stationUseCount");
            if (stationUseCountObj != null) {
                for (Map.Entry<String, JsonElement> entry : stationUseCountObj.entrySet()) {
                    stationUseCount.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[TPP] Failed to load config", e);
        }
    }

    /** 持久化到配置文件（UTF-8）；由各领域变更方法在变更后自动调用 */
    private static void save() {
        JsonObject config = new JsonObject();

        JsonObject stationsObj = new JsonObject();
        for (Map.Entry<String, String> entry : stationMap.entrySet()) {
            if (entry.getValue() == null) {
                stationsObj.add(entry.getKey(), null);  // JSON null
            } else {
                stationsObj.addProperty(entry.getKey(), entry.getValue());
            }
        }
        config.add("stations", stationsObj);

        JsonObject aliasesObj = new JsonObject();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            aliasesObj.addProperty(entry.getKey(), entry.getValue());
        }
        config.add("aliases", aliasesObj);
        config.addProperty("useCount", useCount);

        // 保存站点级右键次数配置
        JsonObject stationUseCountObj = new JsonObject();
        for (Map.Entry<String, Integer> entry : stationUseCount.entrySet()) {
            stationUseCountObj.addProperty(entry.getKey(), entry.getValue());
        }
        config.add("stationUseCount", stationUseCountObj);

        try {
            // 原子写：先写临时文件再原子替换，避免写一半崩溃/断电留下损坏的 JSON
            Path tmp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("[TPP] Failed to save config", e);
        }
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
        if (stationMap.containsKey(name)) {
            return false;
        }
        stationMap.put(name, displayName);
        save();
        return true;
    }

    /**
     * 删除站点，同时清理其站点级右键次数配置（避免残留孤儿配置）。
     */
    public static void removeStation(String internalName) {
        stationMap.remove(internalName);
        stationUseCount.remove(internalName);
        save();
    }

    /**
     * 获取玩家别名；未设置返回 null。
     */
    public static String getAlias(String playerName) {
        return aliases.get(playerName);
    }

    /**
     * 设置玩家别名。
     */
    public static void setAlias(String playerName, String alias) {
        aliases.put(playerName, alias);
        save();
    }

    /**
     * 移除玩家别名。
     *
     * @return false 表示该玩家未设置别名
     */
    public static boolean removeAlias(String playerName) {
        if (aliases.remove(playerName) == null) {
            return false;
        }
        save();
        return true;
    }

    /**
     * 设置全局默认右键次数。
     */
    public static void setGlobalUseCount(int count) {
        useCount = count;
        save();
    }

    /**
     * 设置站点级右键次数。
     */
    public static void setStationUseCount(String station, int count) {
        stationUseCount.put(station, count);
        save();
    }

    /**
     * 获取全局默认右键次数。
     */
    public static int getGlobalUseCount() {
        return useCount;
    }

    /**
     * 获取站点级右键次数配置快照（用于 /tppset rule 展示）。
     */
    public static Map<String, Integer> getStationUseCountSnapshot() {
        return Map.copyOf(stationUseCount);
    }

    /**
     * 获取站点的显示名称。无备注时返回内部名本身。
     */
    public static String getDisplayName(String internalName) {
        String display = stationMap.get(internalName);
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
        if (stationMap.containsKey(input)) return input;
        // 再匹配显示名（忽略大小写）
        for (Map.Entry<String, String> entry : stationMap.entrySet()) {
            String display = entry.getValue();
            if (display != null && !display.isEmpty() && display.equalsIgnoreCase(input)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 获取所有站点的显示名称列表（用于 Tab 补全）
     */
    public static List<String> getDisplayNames() {
        List<String> result = new ArrayList<>();
        for (String key : stationMap.keySet()) {
            result.add(getDisplayName(key));
        }
        return result;
    }

    /**
     * 获取所有站点内部名的逗号分隔字符串
     */
    public static String getStationsString() {
        return String.join(",", stationMap.keySet());
    }

    /**
     * 获取指定站点的右键次数
     * 如果站点未单独设置，则返回全局默认值
     *
     * @param station 站点内部名
     * @return 该站点的右键次数
     */
    public static int getUseCount(String station) {
        return stationUseCount.getOrDefault(station, useCount);
    }
}
