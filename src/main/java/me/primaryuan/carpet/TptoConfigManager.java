package me.primaryuan.carpet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.config.PryJsonStore;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 跨服传送（/tpto /tptoset）的服务器配置：一条记录对应一个地址和端口。
 *
 * <p>数据跟随服务器：config/carpet-pry-addition/tpto.json（GLOBAL 作用域）。
 * 数据版本 3；v1（单 host）/v2（多线路）加载时在内存扁平化为单地址（v2 取
 * defaultLine 指向的入口，不可用取首条有效入口），首次回写前先备份原文件。
 * 所有状态仅在服务器主线程访问，普通集合即可。</p>
 */
public final class TptoConfigManager {

    /** 单台目标服配置；displayName 为 null 表示无显示名（展示时回退标识） */
    public record ServerEntry(String host, int port, String displayName) {}

    /** 列表/管理界面渲染用的只读视图；displayName 为有效展示名（无显示名时为标识） */
    public record ServerView(String name, String displayName, String host, int port) {}

    /** 增删结果：能区分成功、未找到、重名、标识非法、写保护与落盘失败 */
    public enum MutationStatus { OK, NOT_FOUND, DUPLICATE_ID, NAME_CONFLICT, INVALID_ID, WRITE_PROTECTED, SAVE_FAILED }

    /** reload 结果；detail 为技术性错误说明（非法结构/冲突名称），仅日志与提示用 */
    public record ReloadResult(Status status, String detail) {
        public enum Status { OK, MISSING, READ_FAILED, FUTURE_VERSION, INVALID }
    }

    private static final TptoStore STORE = new TptoStore();

    private TptoConfigManager() {}

    /**
     * 包内可见以便单测直接驱动纯函数与 JSON 层（不触碰 fabric loader）。
     *
     * <p>不走基类 attach/save（那套对失败只清空内存且照常回写），改用候选快照流：
     * 读盘先解析校验、成功才提交内存；任何加载失败保留现有列表并写保护，
     * 首次对旧格式文件回写前先备份原文件。</p>
     */
    static class TptoStore extends PryJsonStore {

        private static final int VERSION = 3;

        /** 内部名 → 服务器配置；LinkedHashMap 保持添加顺序（列表展示顺序） */
        private final LinkedHashMap<String, ServerEntry> servers = new LinkedHashMap<>();

        /** 磁盘状态未确认（坏文件/超前格式/读失败）时阻止写入，直到成功 reload */
        private boolean writeProtect;
        /** 盘上仍是 v1/v2 原始格式：首次回写前须备份（含未选线路与 playerLines） */
        private boolean needsBackup;

        /** 单测注入：无 fabric loader 环境下固定数据文件路径 */
        Path testPath;

        TptoStore() {
            super("TPTO", "tpto.json", Scope.GLOBAL, VERSION, null);
        }

        @Override
        protected Path filePath() {
            return testPath != null ? testPath : super.filePath();
        }

        /** 服务器启动读盘：无文件 → 空列表可写；坏文件 → 空列表并写保护 */
        synchronized void loadInitial() {
            writeProtect = false;
            needsBackup = false;
            Path file = filePath();
            if (file == null || Files.notExists(file)) {
                servers.clear();
                return;
            }
            loadCandidate(file, true);
        }

        /** /tptoset reload：候选成功才替换内存；任何失败保留现有列表并写保护 */
        synchronized ReloadResult reloadFromDisk() {
            Path file = filePath();
            if (file == null || Files.notExists(file)) {
                writeProtect = true;
                return new ReloadResult(ReloadResult.Status.MISSING, null);
            }
            return loadCandidate(file, false);
        }

        private ReloadResult loadCandidate(Path file, boolean initial) {
            try {
                JsonObject root = readFileOrNull(file);
                if (root == null) {
                    return fail(initial, ReloadResult.Status.READ_FAILED, null);
                }
                JsonElement versionElem = root.get("version");
                if (versionElem == null || !versionElem.isJsonPrimitive()
                        || !versionElem.getAsJsonPrimitive().isNumber()
                        || !versionElem.getAsString().matches("\\d+")) {
                    return fail(initial, ReloadResult.Status.INVALID, "missing or invalid 'version' envelope");
                }
                int fileVersion = versionElem.getAsInt();
                if (fileVersion > VERSION) {
                    LOGGER.error("[TPTO] Config version {} is newer than supported {} (downgraded mod?); "
                            + "current list kept, writes paused until a successful /tptoset reload",
                            fileVersion, VERSION);
                    if (initial) {
                        servers.clear();
                    }
                    writeProtect = true;
                    return new ReloadResult(ReloadResult.Status.FUTURE_VERSION, null);
                }
                JsonElement dataElem = root.get("data");
                if (dataElem == null || !dataElem.isJsonObject()) {
                    return fail(initial, ReloadResult.Status.INVALID, "missing or invalid 'data' object");
                }
                JsonObject data = dataElem.getAsJsonObject();
                if (fileVersion < VERSION) {
                    flattenLegacy(data, fileVersion);
                }
                LinkedHashMap<String, ServerEntry> candidate = parseServers(data);
                servers.clear();
                servers.putAll(candidate);
                needsBackup = fileVersion < VERSION;
                writeProtect = false;
                return new ReloadResult(ReloadResult.Status.OK, null);
            } catch (Exception e) {
                return fail(initial, ReloadResult.Status.INVALID, String.valueOf(e.getMessage()));
            }
        }

        /** 加载失败统一出口：首次启动给空列表，运行中保留现有列表；一律写保护 */
        private ReloadResult fail(boolean initial, ReloadResult.Status status, String detail) {
            if (initial) {
                servers.clear();
            }
            writeProtect = true;
            if (detail != null) {
                LOGGER.error("[TPTO] Config rejected ({}): {}", status, detail);
            }
            return new ReloadResult(status, detail);
        }

        /** v1/v2 → v3 内存扁平化（不回写盘）：v2 取默认入口或首条有效入口；playerLines 不导入 */
        private void flattenLegacy(JsonObject data, int fileVersion) {
            JsonElement playerElem = data.remove("playerLines");
            if (playerElem != null && playerElem.isJsonObject()) {
                LOGGER.warn("[TPTO] v2 playerLines ({} entries) not imported; "
                        + "original file will be kept in backup on first write",
                        playerElem.getAsJsonObject().size());
            }
            JsonElement serversElem = data.get("servers");
            if (serversElem == null || !serversElem.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : serversElem.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonObject()) {
                    continue;
                }
                JsonObject server = value.getAsJsonObject();
                String defaultLine = server.has("defaultLine") && server.get("defaultLine").isJsonPrimitive()
                        && server.get("defaultLine").getAsJsonPrimitive().isString()
                        ? server.get("defaultLine").getAsString() : null;
                server.remove("defaultLine");
                JsonElement linesElem = server.remove("lines");
                if (linesElem == null) {
                    continue;
                }
                flattenLines(entry.getKey(), server, defaultLine, linesElem);
            }
        }

        /** 多线路服务器收敛为单地址：选默认入口（不可用选首条有效入口），其余入口留在原文件备份 */
        private void flattenLines(String name, JsonObject server, String defaultLine, JsonElement linesElem) {
            if (!linesElem.isJsonObject()) {
                throw new IllegalArgumentException("server '" + name + "': 'lines' is not an object");
            }
            LinkedHashMap<String, String> entries = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> lineEntry : linesElem.getAsJsonObject().entrySet()) {
                JsonElement lineValue = lineEntry.getValue();
                if (!lineValue.isJsonObject()) {
                    LOGGER.warn("[TPTO] Server '{}': skipping line '{}': not an object", name, lineEntry.getKey());
                    continue;
                }
                JsonElement hostElem = lineValue.getAsJsonObject().get("host");
                if (hostElem == null || !hostElem.isJsonPrimitive() || !hostElem.getAsJsonPrimitive().isString()) {
                    LOGGER.warn("[TPTO] Server '{}': skipping line '{}': host is missing or not a string",
                            name, lineEntry.getKey());
                    continue;
                }
                String host = hostElem.getAsString();
                try {
                    TptoAddress.validateHost(host);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[TPTO] Server '{}': skipping line '{}': {}", name, lineEntry.getKey(), e.getMessage());
                    continue;
                }
                entries.put(lineEntry.getKey(), host);
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("server '" + name + "': no valid line entries to migrate");
            }
            String chosenLabel = defaultLine != null && entries.containsKey(defaultLine)
                    ? defaultLine : null;
            if (chosenLabel == null) {
                if (defaultLine != null) {
                    LOGGER.warn("[TPTO] Server '{}': default line '{}' is invalid, using first entry",
                            name, defaultLine);
                }
                chosenLabel = entries.keySet().iterator().next();
            }
            server.addProperty("host", entries.get(chosenLabel));
            LOGGER.info("[TPTO] Server '{}': kept line '{}' ({}), {} other line entries remain in the original file",
                    name, chosenLabel, entries.get(chosenLabel), entries.size() - 1);
        }

        /** v3 严格解析：servers 必须为对象；单条无效或名称冲突整体失败，不逐条吞错 */
        private LinkedHashMap<String, ServerEntry> parseServers(JsonObject data) {
            if (data.has("playerLines")) {
                throw new IllegalArgumentException("legacy 'playerLines' present in v3 data; "
                        + "fix the file manually, it will not be imported");
            }
            JsonElement serversElem = data.get("servers");
            if (serversElem == null || !serversElem.isJsonObject()) {
                throw new IllegalArgumentException("'servers' is missing or not an object");
            }
            LinkedHashMap<String, ServerEntry> parsed = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : serversElem.getAsJsonObject().entrySet()) {
                parsed.put(entry.getKey(), parseServerStrict(entry.getKey(), entry.getValue()));
            }
            checkNameConflicts(parsed);
            return parsed;
        }

        private ServerEntry parseServerStrict(String name, JsonElement value) {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("server '" + name + "': not an object");
            }
            JsonObject obj = value.getAsJsonObject();
            if (obj.has("lines") || obj.has("defaultLine")) {
                throw new IllegalArgumentException("server '" + name + "': legacy 'lines'/'defaultLine' present "
                        + "in v3 data; fix the file manually");
            }
            if (!isValidId(name)) {
                throw new IllegalArgumentException("server '" + name + "': id must be non-empty ASCII "
                        + "[0-9A-Za-z_.+-]");
            }
            JsonElement portElem = obj.get("port");
            if (portElem == null || !portElem.isJsonPrimitive() || !portElem.getAsJsonPrimitive().isNumber()
                    || !portElem.getAsString().matches("\\d+")) {
                throw new IllegalArgumentException("server '" + name + "': 'port' must be an integer number");
            }
            int port = portElem.getAsInt();
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("server '" + name + "': port " + port + " out of range 1-65535");
            }
            JsonElement hostElem = obj.get("host");
            if (hostElem == null || !hostElem.isJsonPrimitive() || !hostElem.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("server '" + name + "': 'host' must be a string");
            }
            try {
                TptoAddress.validateHost(hostElem.getAsString());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("server '" + name + "': " + e.getMessage());
            }
            String displayName = null;
            JsonElement displayElem = obj.get("displayName");
            if (displayElem != null && !displayElem.isJsonNull()) {
                if (!displayElem.isJsonPrimitive() || !displayElem.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("server '" + name + "': 'displayName' must be a string or null");
                }
                String display = displayElem.getAsString();
                displayName = display.isBlank() ? null : display;
            }
            return new ServerEntry(hostElem.getAsString(), port, displayName);
        }

        /** 唯一性：标识之间、展示名之间、标识与展示名交叉均忽略大小写；本条自身标识=展示名允许 */
        private static void checkNameConflicts(Map<String, ServerEntry> servers) {
            Map<String, String> lowerIds = new HashMap<>();
            for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
                String prev = lowerIds.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getKey());
                if (prev != null) {
                    throw new IllegalArgumentException("duplicate server id '" + entry.getKey()
                            + "' (matches '" + prev + "' ignoring case)");
                }
            }
            Map<String, String> lowerDisplays = new HashMap<>();
            for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
                String display = displayNameOf(servers, entry.getKey());
                String lower = display.toLowerCase(Locale.ROOT);
                String idOwner = lowerIds.get(lower);
                if (idOwner != null && !idOwner.equals(entry.getKey())) {
                    throw new IllegalArgumentException("display name '" + display + "' of server '"
                            + entry.getKey() + "' conflicts with id of server '" + idOwner + "'");
                }
                String prev = lowerDisplays.putIfAbsent(lower, entry.getKey());
                if (prev != null) {
                    throw new IllegalArgumentException("duplicate display name '" + display
                            + "' (servers '" + prev + "' and '" + entry.getKey() + "')");
                }
            }
        }

        @Override
        protected JsonObject collectData() {
            return collectServers(servers);
        }

        /** 基类钩子：本类加载走 loadCandidate（候选解析+提交），常规 attach 路径不再调用 */
        @Override
        protected void applyData(JsonObject data) {
            LinkedHashMap<String, ServerEntry> candidate = parseServers(data);
            servers.clear();
            servers.putAll(candidate);
        }

        private static JsonObject collectServers(Map<String, ServerEntry> servers) {
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

        /** 新增/删除的候选快照落盘：必要时先备份旧格式，写成功才算成功 */
        private boolean persist(Map<String, ServerEntry> candidate) {
            Path file = filePath();
            if (file == null) {
                LOGGER.error("[TPTO] Save skipped: not attached to a server");
                return false;
            }
            if (Files.exists(file) && needsBackup && !backupOriginalFile("pre-v3")) {
                return false;
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.add("data", collectServers(candidate));
            if (!writeFileAtomic(file, root)) {
                return false;
            }
            needsBackup = false;
            return true;
        }

        synchronized MutationStatus insert(String name, String host, int port, String displayName) {
            if (writeProtect) {
                return MutationStatus.WRITE_PROTECTED;
            }
            LinkedHashMap<String, ServerEntry> candidate = new LinkedHashMap<>(servers);
            MutationStatus validation = addServerTo(candidate, name, host, port, displayName);
            if (validation != MutationStatus.OK) {
                return validation;
            }
            if (!persist(candidate)) {
                return MutationStatus.SAVE_FAILED;
            }
            servers.clear();
            servers.putAll(candidate);
            return MutationStatus.OK;
        }

        synchronized MutationStatus delete(String internalName) {
            if (writeProtect) {
                return MutationStatus.WRITE_PROTECTED;
            }
            if (!servers.containsKey(internalName)) {
                return MutationStatus.NOT_FOUND;
            }
            LinkedHashMap<String, ServerEntry> candidate = new LinkedHashMap<>(servers);
            candidate.remove(internalName);
            if (!persist(candidate)) {
                return MutationStatus.SAVE_FAILED;
            }
            servers.remove(internalName);
            return MutationStatus.OK;
        }

        synchronized void onServerClosed() {
            super.detach();
            writeProtect = false;
            needsBackup = false;
        }

        /** 本实例的渲染快照（添加顺序）；包内可见以便单测驱动非 STORE 实例 */
        synchronized List<ServerView> views() {
            List<ServerView> list = new ArrayList<>(servers.size());
            for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
                list.add(new ServerView(entry.getKey(), displayNameOf(servers, entry.getKey()),
                        entry.getValue().host(), entry.getValue().port()));
            }
            return list;
        }

        /** 本实例的记录数 */
        synchronized int size() {
            return servers.size();
        }
    }

    // ===== 服务器生命周期（CarpetPrimaryuanServer 调用） =====

    public static void onServerLoaded(MinecraftServer server) {
        STORE.loadInitial();
    }

    public static void onServerClosed() {
        STORE.onServerClosed();
    }

    /** 从盘重读（/tptoset reload）；失败保留现有列表并写保护 */
    public static ReloadResult reload() {
        return STORE.reloadFromDisk();
    }

    // ===== 领域方法：所有状态变更经由此处，写成功才提交内存 =====

    /**
     * 添加服务器。
     *
     * @param displayName 显示名（null 或空白表示无，展示时回退标识）
     * @return DUPLICATE_ID/NAME_CONFLICT 表示与现有记录忽略大小写重名（不覆盖）
     */
    public static MutationStatus addServer(String name, String host, int port, String displayName) {
        return STORE.insert(name, host, port, displayName);
    }

    /** 按内部名删除服务器 */
    public static MutationStatus removeServer(String internalName) {
        return STORE.delete(internalName);
    }

    /** 按内部名取配置；不存在返回 null */
    public static ServerEntry getServer(String internalName) {
        return STORE.servers.get(internalName);
    }

    /** 服务器数量（reload 反馈用） */
    public static int count() {
        return STORE.size();
    }

    /** 有效展示名：无显示名回退内部名 */
    public static String getDisplayName(String internalName) {
        return displayNameOf(STORE.servers, internalName);
    }

    /** 按玩家输入（标识或展示名，忽略大小写）反查内部名；未找到返回 null */
    public static String resolveInternalName(String input) {
        return resolveInternalName(STORE.servers, input);
    }

    /** 列表/管理界面渲染快照（添加顺序） */
    public static List<ServerView> getServerViews() {
        return STORE.views();
    }

    /** 全部有效展示名（未找到提示用，添加顺序） */
    public static List<String> getDisplayNames() {
        List<String> names = new ArrayList<>(STORE.servers.size());
        for (String key : STORE.servers.keySet()) {
            names.add(displayNameOf(STORE.servers, key));
        }
        return names;
    }

    // ===== 纯函数（单测可独立驱动） =====

    /** 英文标识字符集：原版 word 参数允许的非空 ASCII（数字/字母/_-.+） */
    public static boolean isValidId(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || c == '_' || c == '-' || c == '.' || c == '+')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验并添加到候选快照；全部校验通过才写入。
     * host/port 非法直接抛 IllegalArgumentException（命令层已先用 TptoAddress 校验）。
     */
    static MutationStatus addServerTo(Map<String, ServerEntry> servers, String name,
                                      String host, int port, String displayName) {
        if (!isValidId(name)) {
            return MutationStatus.INVALID_ID;
        }
        TptoAddress.validateHost(host);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port out of range 1-65535: " + port);
        }
        String lowerId = name.toLowerCase(Locale.ROOT);
        String effectiveDisplay = displayName == null || displayName.isBlank() ? name : displayName;
        String lowerDisplay = effectiveDisplay.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ServerEntry> existing : servers.entrySet()) {
            String existingId = existing.getKey().toLowerCase(Locale.ROOT);
            String existingDisplay = displayNameOf(servers, existing.getKey()).toLowerCase(Locale.ROOT);
            if (lowerId.equals(existingId) || lowerId.equals(existingDisplay)) {
                return MutationStatus.DUPLICATE_ID;
            }
            if (lowerDisplay.equals(existingId) || lowerDisplay.equals(existingDisplay)) {
                return MutationStatus.NAME_CONFLICT;
            }
        }
        servers.put(name, new ServerEntry(host, port,
                displayName == null || displayName.isBlank() ? null : displayName));
        return MutationStatus.OK;
    }

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
        // 先精确匹配标识，再忽略大小写匹配标识，最后忽略大小写匹配展示名
        if (servers.containsKey(input)) {
            return input;
        }
        String lower = input.toLowerCase(Locale.ROOT);
        for (String key : servers.keySet()) {
            if (key.toLowerCase(Locale.ROOT).equals(lower)) {
                return key;
            }
        }
        for (Map.Entry<String, ServerEntry> entry : servers.entrySet()) {
            String display = entry.getValue().displayName();
            if (display != null && !display.isEmpty() && display.toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
