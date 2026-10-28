package me.primaryuan.carpet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TptoConfigManager} 的 v3 单地址契约：严格 JSON 校验、v1/v2 迁移与备份、
 * 候选写入、失败保留与写保护。磁盘流使用临时目录，不触碰 fabric loader。
 */
class TptoConfigManagerTest {

    @TempDir
    Path temp;

    private static final String HOST = "survival.example.com";

    // ===== 构造辅助 =====

    private static TptoConfigManager.TptoStore newStore(Path file) {
        TptoConfigManager.TptoStore store = new TptoConfigManager.TptoStore();
        store.testPath = file;
        return store;
    }

    private static TptoConfigManager.TptoStore newStoreWriteFails(Path file) {
        TptoConfigManager.TptoStore store = new TptoConfigManager.TptoStore() {
            @Override
            protected boolean writeFileAtomic(Path file, JsonObject root) {
                return false;
            }
        };
        store.testPath = file;
        return store;
    }

    private static TptoConfigManager.TptoStore newStoreBackupFails(Path file) {
        TptoConfigManager.TptoStore store = new TptoConfigManager.TptoStore() {
            @Override
            protected boolean backupOriginalFile(String label) {
                return false;
            }
        };
        store.testPath = file;
        return store;
    }

    private static JsonObject serverJson(String host, int port, String displayName) {
        JsonObject obj = new JsonObject();
        obj.addProperty("host", host);
        obj.addProperty("port", port);
        if (displayName != null) {
            obj.addProperty("displayName", displayName);
        }
        return obj;
    }

    private static JsonObject v3Data(JsonObject servers) {
        JsonObject data = new JsonObject();
        data.add("servers", servers);
        return data;
    }

    private static JsonObject envelope(int version, JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        root.add("data", data);
        return root;
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content);
    }

    private static JsonObject readRoot(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static List<Path> backups(Path dir) throws IOException {
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".bak")).toList();
        }
    }

    // ===== v3 round-trip =====

    @Test
    void v3RoundTripKeepsOrderAndOmitsNullDisplayName() throws IOException {
        Path file = temp.resolve("tpto.json");
        JsonObject servers = new JsonObject();
        servers.add("main", serverJson("primaryuan.top", 25565, "主服"));
        servers.add("survival", serverJson(HOST, 25566, null));
        JsonObject explicitNull = serverJson("c.example.com", 25567, null);
        explicitNull.addProperty("displayName", (String) null);
        servers.add("third", explicitNull);
        write(file, envelope(3, v3Data(servers)).toString());

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals(3, store.size());
        List<String> order = store.views().stream().map(TptoConfigManager.ServerView::name).toList();
        assertEquals(List.of("main", "survival", "third"), order);
        // collectData 即落盘形态：无/空 displayName 省略字段
        JsonObject out = store.collectData().getAsJsonObject("servers");
        assertTrue(out.getAsJsonObject("survival").has("host"));
        assertFalse(out.getAsJsonObject("survival").has("displayName"));
        assertFalse(out.getAsJsonObject("third").has("displayName"));
        assertEquals("主服", out.getAsJsonObject("main").get("displayName").getAsString());
    }

    @Test
    void emptyServerObjectIsValidAndWritable() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, envelope(3, v3Data(new JsonObject())).toString());

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();
        assertEquals(0, count(store));

        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("sc", HOST, 25565, "生存服"));
        assertEquals(1, count(store));
        assertEquals(3, readRoot(file).get("version").getAsInt());
    }

    // ===== v1/v2 → v3 迁移 =====

    @Test
    void v1MigratesToFlatEntryWithoutRewritingFile() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{\"version\":1,\"data\":{\"servers\":{\"main\":{"
                + "\"host\":\"primaryuan.top\",\"port\":25565,\"displayName\":\"主服\"}}}}");
        byte[] before = Files.readAllBytes(file);

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();
        assertEquals(1, count(store));
        TptoConfigManager.ServerView view = store.views().get(0);
        assertEquals("primaryuan.top", view.host());
        assertEquals(25565, view.port());
        assertEquals("主服", view.displayName());
        // 加载不回写：磁盘仍是 v1
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(0, backups(temp).size());
        // 再次加载仍可读取 v1
        assertEquals(TptoConfigManager.ReloadResult.Status.OK, store.reloadFromDisk().status());

        // 首次写入前备份原文件，主文件变 v3
        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("extra", HOST, 25566, null));
        assertEquals(3, readRoot(file).get("version").getAsInt());
        List<Path> bak = backups(temp);
        assertEquals(1, bak.size());
        assertArrayEquals(before, Files.readAllBytes(bak.get(0)));
        // 第二次写入不再追加备份
        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("third", "c.example.com", 25567, null));
        assertEquals(1, backups(temp).size());
    }

    @Test
    void v2MigratesDefaultLineAndDropsPlayerLines() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{\"version\":2,\"data\":{\"servers\":{\"main\":{"
                + "\"port\":25565,\"displayName\":\"主服\",\"defaultLine\":\"线路1\","
                + "\"lines\":{\"线路1\":{\"host\":\"primaryuan.top\"},\"BGP\":{\"host\":\"bgp.primaryuan.top\"}}},"
                + "\"survival\":{\"port\":25566,\"lines\":{\"默认\":{\"host\":\"" + HOST + "\"}}}},"
                + "\"playerLines\":{\"Steve\":\"BGP\"}}}");
        byte[] before = Files.readAllBytes(file);

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals(2, count(store));
        TptoConfigManager.ServerView main = store.views().get(0);
        assertEquals("main", main.name());
        assertEquals("primaryuan.top", main.host());
        assertEquals(25565, main.port());
        TptoConfigManager.ServerView survival = store.views().get(1);
        assertEquals(HOST, survival.host());
        assertEquals(25566, survival.port());
        // 磁盘原样保留（含全部线路与 playerLines），reload 仍可读
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(TptoConfigManager.ReloadResult.Status.OK, store.reloadFromDisk().status());
        assertEquals(0, backups(temp).size());

        // 首次写入：备份含全部旧数据（字节一致），主文件变 v3 单地址
        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("lobby", "lobby.example.com", 25560, null));
        List<Path> bak = backups(temp);
        assertEquals(1, bak.size());
        assertArrayEquals(before, Files.readAllBytes(bak.get(0)));
        JsonObject out = readRoot(file);
        assertEquals(3, out.get("version").getAsInt());
        assertEquals(3, out.getAsJsonObject("data").getAsJsonObject("servers").size());
        assertFalse(out.getAsJsonObject("data").has("playerLines"));
        assertEquals("primaryuan.top",
                out.getAsJsonObject("data").getAsJsonObject("servers").getAsJsonObject("main").get("host").getAsString());
    }

    @Test
    void v2InvalidDefaultLineFallsBackToFirstEntry() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{\"version\":2,\"data\":{\"servers\":{\"main\":{"
                + "\"port\":25565,\"defaultLine\":\"不存在\","
                + "\"lines\":{\"A\":{\"host\":\"a.example.com\"},\"B\":{\"host\":\"b.example.com\"}}}}}}");

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals("a.example.com", store.views().get(0).host());
    }

    @Test
    void v2WithoutValidLinesFailsWholeLoad() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{\"version\":2,\"data\":{\"servers\":{\"main\":{"
                + "\"port\":25565,\"lines\":{\"A\":{\"host\":\"\"}}},"
                + "\"good\":{\"port\":25566,\"lines\":{\"默认\":{\"host\":\"a.example.com\"}}}}}}");

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals(0, count(store));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, store.insert("x", HOST, 1, null));
    }

    // ===== v3 严格校验 =====

    private static TptoConfigManager.ReloadResult reloadOf(Path file, String content) throws IOException {
        write(file, content);
        TptoConfigManager.TptoStore store = newStore(file);
        return store.reloadFromDisk();
    }

    @Test
    void strictRejections() throws IOException {
        Path file = temp.resolve("tpto.json");
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, envelope(3, new JsonObject()).toString()).status());

        JsonObject noServers = new JsonObject();
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, envelope(3, noServers).toString()).status());

        JsonObject badPortString = new JsonObject();
        badPortString.addProperty("host", HOST);
        badPortString.addProperty("port", "25565");
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":\"25565\"}}}}").status());

        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":25565.5}}}}").status());

        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":true}}}}").status());

        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\" \",\"port\":25565}}}}")
                        .status());

        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":5,\"port\":25565}}}}")
                        .status());

        // displayName 必须是字符串或 null
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":25565,\"displayName\":5}}}}").status());

        // 标识字符集
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"中文\":{"
                        + "\"host\":\"" + HOST + "\",\"port\":25565}}}}").status());

        // v3 混入旧结构
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":25565,\"lines\":{\"x\":{\"host\":\"y\"}}}}}}").status());
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{},\"playerLines\":{}}}").status());
    }

    @Test
    void nameConflictsRejectWholeLoad() throws IOException {
        Path file = temp.resolve("tpto.json");
        // 标识忽略大小写重复
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"sc\":{\"host\":\"" + HOST
                        + "\",\"port\":25565},\"SC\":{\"host\":\"b.example.com\",\"port\":25566}}}}").status());
        // 展示名重复
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"a\":{\"host\":\"" + HOST
                        + "\",\"port\":25565,\"displayName\":\"生存\"},\"b\":{\"host\":\"b.example.com\","
                        + "\"port\":25566,\"displayName\":\"生存\"}}}}").status());
        // 展示名撞其他标识
        assertEquals(TptoConfigManager.ReloadResult.Status.INVALID,
                reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"sc\":{\"host\":\"" + HOST
                        + "\",\"port\":25565},\"b\":{\"host\":\"b.example.com\",\"port\":25566,"
                        + "\"displayName\":\"SC\"}}}}").status());

        // 本条自身标识=展示名允许；line 可作为普通标识
        TptoConfigManager.ReloadResult ok = reloadOf(file, "{\"version\":3,\"data\":{\"servers\":{\"line\":{"
                + "\"host\":\"" + HOST + "\",\"port\":25565,\"displayName\":\"line\"}}}}");
        assertEquals(TptoConfigManager.ReloadResult.Status.OK, ok.status());
    }

    @Test
    void futureVersionIsReadOnly() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, envelope(3, v3Data(single("sc", HOST, 25565, null))).toString());
        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();
        assertEquals(1, count(store));

        write(file, envelope(4, v3Data(single("sc", HOST, 25565, null))).toString());
        assertEquals(TptoConfigManager.ReloadResult.Status.FUTURE_VERSION, store.reloadFromDisk().status());
        assertEquals(1, count(store));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, store.insert("x", HOST, 1, null));
        byte[] before = Files.readAllBytes(file);
        assertArrayEquals(before, Files.readAllBytes(file));

        // 首次启动即遇超前格式：空列表且写保护，不覆盖文件
        TptoConfigManager.TptoStore fresh = newStore(file);
        fresh.loadInitial();
        assertEquals(0, count(fresh));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, fresh.insert("x", HOST, 1, null));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    // ===== 坏文件、reload 保留与写保护恢复 =====

    private static JsonObject single(String id, String host, int port, String displayName) {
        JsonObject servers = new JsonObject();
        servers.add(id, serverJson(host, port, displayName));
        return servers;
    }

    private static int count(TptoConfigManager.TptoStore store) {
        return store.size();
    }

    @Test
    void badJsonAtStartupKeepsEmptyListAndBlocksWrites() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{oops");
        byte[] before = Files.readAllBytes(file);

        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals(0, count(store));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, store.insert("x", HOST, 1, null));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void failedReloadKeepsListAndBlocksWritesUntilFixed() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, envelope(3, v3Data(single("sc", HOST, 25565, "生存服"))).toString());
        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();
        assertEquals(1, count(store));

        write(file, "{oops");
        assertEquals(TptoConfigManager.ReloadResult.Status.READ_FAILED, store.reloadFromDisk().status());
        assertEquals(1, count(store));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, store.insert("x", HOST, 1, null));

        // 修复后 reload 恢复写入
        write(file, "{\"version\":3,\"data\":{\"servers\":{\"sc\":{\"host\":\"" + HOST
                + "\",\"port\":25565,\"displayName\":\"生存服\"},\"x\":{\"host\":\"x.example.com\",\"port\":1}}}}");
        assertEquals(TptoConfigManager.ReloadResult.Status.OK, store.reloadFromDisk().status());
        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("y", "y.example.com", 2, null));
        assertEquals(3, count(store));
    }

    @Test
    void reloadWithMissingFileKeepsListAndBlocksWrites() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, envelope(3, v3Data(single("sc", HOST, 25565, null))).toString());
        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();
        assertEquals(1, count(store));

        Files.delete(file);
        assertEquals(TptoConfigManager.ReloadResult.Status.MISSING, store.reloadFromDisk().status());
        assertEquals(1, count(store));
        assertEquals(TptoConfigManager.MutationStatus.WRITE_PROTECTED, store.insert("x", HOST, 1, null));
    }

    @Test
    void startupWithoutFileStartsEmptyAndWritable() {
        TptoConfigManager.TptoStore store = newStore(temp.resolve("tpto.json"));
        store.loadInitial();
        assertEquals(0, count(store));
        assertFalse(Files.exists(temp.resolve("tpto.json")));
        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("sc", HOST, 25565, "生存服"));
        assertEquals(1, count(store));
    }

    // ===== 保存失败 =====

    @Test
    void writeFailureKeepsMemoryAndFile() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, envelope(3, v3Data(single("sc", HOST, 25565, null))).toString());
        byte[] before = Files.readAllBytes(file);

        TptoConfigManager.TptoStore store = newStoreWriteFails(file);
        store.loadInitial();
        assertEquals(1, count(store));

        assertEquals(TptoConfigManager.MutationStatus.SAVE_FAILED, store.insert("x", HOST, 1, null));
        assertEquals(1, count(store));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(TptoConfigManager.MutationStatus.SAVE_FAILED,
                store.delete("sc"));
        assertEquals(1, count(store));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void backupFailureBlocksFirstWriteOfLegacyFile() throws IOException {
        Path file = temp.resolve("tpto.json");
        write(file, "{\"version\":2,\"data\":{\"servers\":{\"main\":{"
                + "\"port\":25565,\"lines\":{\"默认\":{\"host\":\"primaryuan.top\"}}}}}}");
        byte[] before = Files.readAllBytes(file);

        TptoConfigManager.TptoStore store = newStoreBackupFails(file);
        store.loadInitial();
        assertEquals(1, count(store));

        assertEquals(TptoConfigManager.MutationStatus.SAVE_FAILED, store.insert("x", HOST, 1, null));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(0, backups(temp).size());
    }

    // ===== 新增/删除与唯一性（纯函数） =====

    @Test
    void addRejectsDuplicateNamesIgnoringCase() {
        Map<String, TptoConfigManager.ServerEntry> servers = new java.util.LinkedHashMap<>();
        assertEquals(TptoConfigManager.MutationStatus.OK,
                TptoConfigManager.addServerTo(servers, "sc", HOST, 25565, "Arena"));

        assertEquals(TptoConfigManager.MutationStatus.DUPLICATE_ID,
                TptoConfigManager.addServerTo(servers, "sc", "b.example.com", 25566, null));
        assertEquals(TptoConfigManager.MutationStatus.DUPLICATE_ID,
                TptoConfigManager.addServerTo(servers, "SC", "b.example.com", 25566, null));
        // 标识撞其他记录的展示名
        assertEquals(TptoConfigManager.MutationStatus.DUPLICATE_ID,
                TptoConfigManager.addServerTo(servers, "Arena", "b.example.com", 25566, null));
        // 展示名撞其他记录的标识
        assertEquals(TptoConfigManager.MutationStatus.NAME_CONFLICT,
                TptoConfigManager.addServerTo(servers, "b", "b.example.com", 25566, "SC"));
        // 展示名撞其他记录的展示名
        assertEquals(TptoConfigManager.MutationStatus.NAME_CONFLICT,
                TptoConfigManager.addServerTo(servers, "b", "b.example.com", 25566, "arena"));
        // 本条自身标识=展示名允许
        assertEquals(TptoConfigManager.MutationStatus.OK,
                TptoConfigManager.addServerTo(servers, "b", "b.example.com", 25566, "b"));
        assertEquals(2, servers.size());
        // 未通过校验不写入
        assertEquals(TptoConfigManager.MutationStatus.INVALID_ID,
                TptoConfigManager.addServerTo(servers, "生存", "b.example.com", 25566, null));
        assertEquals(2, servers.size());
    }

    @Test
    void insertAndDeleteRoundTripThroughStore() throws IOException {
        Path file = temp.resolve("tpto.json");
        TptoConfigManager.TptoStore store = newStore(file);
        store.loadInitial();

        assertEquals(TptoConfigManager.MutationStatus.OK, store.insert("sc", HOST, 25565, "生存服"));
        assertEquals(TptoConfigManager.MutationStatus.DUPLICATE_ID, store.insert("SC", "b", 1, null));
        assertEquals("生存服", store.views().get(0).displayName());
        assertEquals(TptoConfigManager.MutationStatus.OK, store.delete("sc"));
        assertEquals(TptoConfigManager.MutationStatus.NOT_FOUND, store.delete("sc"));
        assertEquals(0, count(store));
        JsonObject root = readRoot(file);
        assertEquals(0, root.getAsJsonObject("data").getAsJsonObject("servers").size());
    }

    // ===== 名称解析 =====

    @Test
    void displayNameAndInternalNameResolution() {
        Map<String, TptoConfigManager.ServerEntry> servers = new java.util.LinkedHashMap<>();
        servers.put("lobby", new TptoConfigManager.ServerEntry("a.example.com", 25565, "大厅"));
        servers.put("pvp", new TptoConfigManager.ServerEntry("b.example.com", 25566, null));

        assertEquals("大厅", TptoConfigManager.displayNameOf(servers, "lobby"));
        assertEquals("pvp", TptoConfigManager.displayNameOf(servers, "pvp"));
        assertEquals("unknown", TptoConfigManager.displayNameOf(servers, "unknown"));
        assertEquals("lobby", TptoConfigManager.resolveInternalName(servers, "lobby"));
        assertEquals("lobby", TptoConfigManager.resolveInternalName(servers, "LOBBY"));
        assertEquals("lobby", TptoConfigManager.resolveInternalName(servers, "大厅"));
        assertEquals("pvp", TptoConfigManager.resolveInternalName(servers, "PVP"));
        assertNull(TptoConfigManager.resolveInternalName(servers, "nope"));
        assertNull(TptoConfigManager.resolveInternalName(servers, ""));
        assertNull(TptoConfigManager.resolveInternalName(servers, null));
    }

    @Test
    void idCharset() {
        assertTrue(TptoConfigManager.isValidId("sc"));
        assertTrue(TptoConfigManager.isValidId("Sc_1.x+y"));
        assertTrue(TptoConfigManager.isValidId("line"));
        assertFalse(TptoConfigManager.isValidId("生存"));
        assertFalse(TptoConfigManager.isValidId("a b"));
        assertFalse(TptoConfigManager.isValidId("a:b"));
        assertFalse(TptoConfigManager.isValidId(""));
        assertFalse(TptoConfigManager.isValidId(null));
    }
}
