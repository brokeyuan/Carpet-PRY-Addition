package me.primaryuan.carpet.handler.chatPunishment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 聊天惩罚的记录、持久化与边界：限时解析/溢出、严格加载、候选落盘与故障反馈。 */
class ChatPunishmentManagerTest {

    private static final UUID ALIVE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID EXPIRED = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID THIRD = UUID.fromString("99999999-8888-7777-6666-555555555555");

    // ===== 限时解析 =====

    @Test
    void parseDurationAcceptsSingleUnits() {
        assertEquals(45_000L, ChatPunishmentManager.parseDuration("45s"));
        assertEquals(600_000L, ChatPunishmentManager.parseDuration("10m"));
        assertEquals(3_600_000L, ChatPunishmentManager.parseDuration("1h"));
        assertEquals(86_400_000L, ChatPunishmentManager.parseDuration("1d"));
    }

    @Test
    void parseDurationRejectsInvalid() {
        assertNull(ChatPunishmentManager.parseDuration("10x"));
        assertNull(ChatPunishmentManager.parseDuration("m"));
        assertNull(ChatPunishmentManager.parseDuration("10"));
        assertNull(ChatPunishmentManager.parseDuration("-5m"));
        assertNull(ChatPunishmentManager.parseDuration("0m"));
        assertNull(ChatPunishmentManager.parseDuration(""));
        assertNull(ChatPunishmentManager.parseDuration("1h30m"));
        assertNull(ChatPunishmentManager.parseDuration("99999999999999999999m"));
    }

    @Test
    void parseDurationRejectsMultiplicationOverflow() {
        // 负值输入必须拒绝，不能回绕成负数或短时长。
        assertNull(ChatPunishmentManager.parseDuration("213503982334d"));
        assertNull(ChatPunishmentManager.parseDuration("999999999999d"));
        // 12 位上限内各单位的精确边界仍可解析
        assertEquals(999_999_999_999_000L, ChatPunishmentManager.parseDuration("999999999999s"));
        assertEquals(59_999_999_999_940_000L, ChatPunishmentManager.parseDuration("999999999999m"));
        assertEquals(3_599_999_999_996_400_000L, ChatPunishmentManager.parseDuration("999999999999h"));
        assertNull(ChatPunishmentManager.parseDuration("1000000000000d")); // 13 位，格式拒绝
    }

    @Test
    void expiryAdditionRejectsOverflow() {
        assertEquals(1_500L, ChatPunishmentManager.expiryFrom(1_000L, 500L));
        assertThrows(ArithmeticException.class,
                () -> ChatPunishmentManager.expiryFrom(Long.MAX_VALUE - 5, 10));
    }

    // ===== 剩余时间显示 =====

    @Test
    void formatRemainingSkipsZeroSegments() {
        assertEquals("0s", ChatPunishmentManager.formatRemaining(0));
        assertEquals("0s", ChatPunishmentManager.formatRemaining(-1));
        assertEquals("1s", ChatPunishmentManager.formatRemaining(1_000));
        assertEquals("1s", ChatPunishmentManager.formatRemaining(1));
        assertEquals("10m", ChatPunishmentManager.formatRemaining(600_000));
        assertEquals("1m1s", ChatPunishmentManager.formatRemaining(61_000));
        assertEquals("1d", ChatPunishmentManager.formatRemaining(86_400_000));
        assertEquals("1d1h1m1s", ChatPunishmentManager.formatRemaining(90_061_000));
        assertDoesNotThrow(() -> ChatPunishmentManager.formatRemaining(Long.MAX_VALUE));
    }

    // ===== 到期判定 =====

    @Test
    void permanentEntryNeverExpires() {
        ChatPunishmentManager.Entry entry = new ChatPunishmentManager.Entry("Steve", PunishmentMode.TAPE, 0);
        assertFalse(entry.expired(Long.MAX_VALUE));
    }

    @Test
    void timedEntryExpiresAtTimestamp() {
        ChatPunishmentManager.Entry entry = new ChatPunishmentManager.Entry("Steve", PunishmentMode.TAPE, 1_000);
        assertFalse(entry.expired(999));
        assertTrue(entry.expired(1_000));
        assertTrue(entry.expired(1_001));
    }

    // ===== JSON 往返 =====

    private static ChatPunishmentManager.ChatPunishStore newStore() {
        return new ChatPunishmentManager.ChatPunishStore();
    }

    @Test
    void collectDataDropsExpiredEntries() {
        ChatPunishmentManager.ChatPunishStore store = newStore();
        long future = System.currentTimeMillis() + 60_000;
        store.punishments.put(ALIVE.toString(),
                new ChatPunishmentManager.Entry("Alive", PunishmentMode.CAT, future));
        store.punishments.put(EXPIRED.toString(),
                new ChatPunishmentManager.Entry("Expired", PunishmentMode.TAPE, 1));

        JsonObject root = store.collectData();
        assertEquals(1, root.get("version").getAsInt());
        JsonObject players = root.getAsJsonObject("data").getAsJsonObject("players");
        assertEquals(1, players.size());
        JsonObject alive = players.getAsJsonObject(ALIVE.toString());
        assertEquals("Alive", alive.get("name").getAsString());
        assertEquals("cat", alive.get("mode").getAsString());
        assertEquals(future, alive.get("expireAt").getAsLong());
    }

    @Test
    void applyCollectRoundTripKeepsEntries() {
        ChatPunishmentManager.ChatPunishStore store = newStore();
        store.punishments.put(ALIVE.toString(),
                new ChatPunishmentManager.Entry("Steve", PunishmentMode.STUTTER, Long.MAX_VALUE));

        JsonObject root = store.collectData();
        ChatPunishmentManager.ChatPunishStore reloaded = newStore();
        reloaded.applyData(root.getAsJsonObject("data"));
        ChatPunishmentManager.Entry entry = reloaded.punishments.get(ALIVE.toString());
        assertEquals("Steve", entry.name());
        assertEquals(PunishmentMode.STUTTER, entry.mode());
        assertEquals(Long.MAX_VALUE, entry.expireAt());
    }

    // ===== 真实加载入口与只读故障态 =====

    private static void write(Path file, String text) throws Exception {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String envelope(String playersJson) {
        return "{\"version\":1,\"data\":{\"players\":{" + playersJson + "}}}";
    }

    private static ChatPunishmentManager.ChatPunishStore load(Path file) {
        ChatPunishmentManager.ChatPunishStore store = newStore();
        store.loadFrom(file);
        return store;
    }

    @Test
    void missingFileStartsWritableAndCreatesOnSet(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chatpunish.json");
        ChatPunishmentManager.ChatPunishStore store = load(file);
        assertTrue(store.writable());
        assertEquals(ChatPunishmentManager.Result.APPLIED,
                store.set(ALIVE, "Steve", PunishmentMode.CAT, 0));
        assertTrue(Files.exists(file));

        ChatPunishmentManager.ChatPunishStore reloaded = load(file);
        assertEquals(PunishmentMode.CAT, reloaded.punishments.get(ALIVE.toString()).mode());
    }

    @Test
    void listGateDistinguishesEmptyFromFault(@TempDir Path dir) throws Exception {
        Path missing = dir.resolve("missing.json");
        assertTrue(load(missing).writable(), "missing file is a writable empty table");

        Path empty = dir.resolve("empty.json");
        write(empty, envelope(""));
        assertTrue(load(empty).writable(), "valid empty table stays listable");

        Path bad = dir.resolve("bad.json");
        write(bad, "{not json");
        assertFalse(load(bad).writable(), "corrupt file must not look like an empty table");

        Path newer = dir.resolve("newer.json");
        write(newer, "{\"version\":2,\"data\":{\"players\":{}}}");
        assertFalse(load(newer).writable(), "newer schema must not look like an empty table");
    }

    @Test
    void badFilesStayReadOnlyAndByteIdentical(@TempDir Path dir) throws Exception {
        String validEntry = "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"tape\",\"expireAt\":0}";
        String badNameEntry = "\"" + ALIVE + "\":{\"name\":1,\"mode\":\"tape\",\"expireAt\":0}";
        String badExpireEntry = "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"tape\",\"expireAt\":\"bad\"}";
        String negativeEntry = "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"tape\",\"expireAt\":-1}";
        String unknownModeEntry = "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"banana\",\"expireAt\":0}";
        String extraFieldEntry = "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"tape\",\"expireAt\":0,\"x\":1}";

        String[] badFiles = {
                "{not json",
                envelope(badNameEntry),
                envelope(badExpireEntry),
                envelope(negativeEntry),
                envelope(unknownModeEntry),
                envelope(extraFieldEntry),
                envelope("\"id-a\":{\"name\":\"Steve\",\"mode\":\"tape\",\"expireAt\":0}"),
                "{\"version\":1,\"data\":{\"players\":[]}}",
                "{\"version\":2,\"data\":{\"players\":{" + validEntry + "}}}",
                "{\"version\":1.5,\"data\":{\"players\":{" + validEntry + "}}}",
                "{\"version\":1,\"data\":{\"players\":{},\"extra\":1}}",
        };

        for (int i = 0; i < badFiles.length; i++) {
            Path file = dir.resolve("bad-" + i + ".json");
            write(file, badFiles[i]);
            byte[] before = Files.readAllBytes(file);
            ChatPunishmentManager.ChatPunishStore store = load(file);
            assertFalse(store.writable(), "writable " + i);
            assertEquals(ChatPunishmentManager.Result.READ_ONLY,
                    store.set(ALIVE, "Steve", PunishmentMode.CAT, 0), "set " + i);
            assertEquals(ChatPunishmentManager.Result.READ_ONLY,
                    store.clear(ALIVE), "clear " + i);
            assertArrayEquals(before, Files.readAllBytes(file), "bytes " + i);
            assertTrue(store.punishments.isEmpty(), "memory " + i);
        }
    }

    @Test
    void setFailureDoesNotPublishOrWrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chatpunish.json");
        write(file, envelope("\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"cat\",\"expireAt\":0}"));
        ChatPunishmentManager.ChatPunishStore store = load(file);
        byte[] before = Files.readAllBytes(file);
        store.writer = (path, root) -> false;

        assertEquals(ChatPunishmentManager.Result.IO_ERROR,
                store.set(THIRD, "Alex", PunishmentMode.TAPE, 0));
        assertEquals(PunishmentMode.CAT, store.punishments.get(ALIVE.toString()).mode());
        assertFalse(store.punishments.containsKey(THIRD.toString()));
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void clearFailureDoesNotUnpublish(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chatpunish.json");
        write(file, envelope("\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"cat\",\"expireAt\":0}"));
        ChatPunishmentManager.ChatPunishStore store = load(file);
        byte[] before = Files.readAllBytes(file);
        store.writer = (path, root) -> false;

        assertEquals(ChatPunishmentManager.Result.IO_ERROR, store.clear(ALIVE));
        assertEquals(PunishmentMode.CAT, store.punishments.get(ALIVE.toString()).mode());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void clearExpiredCleansUpButReportsNotFound(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chatpunish.json");
        write(file, envelope("\"" + EXPIRED + "\":{\"name\":\"Old\",\"mode\":\"tape\",\"expireAt\":1}"));
        ChatPunishmentManager.ChatPunishStore store = load(file);

        assertEquals(ChatPunishmentManager.Result.NOT_FOUND, store.clear(EXPIRED));
        assertTrue(store.punishments.isEmpty());
        JsonObject players = JsonParser.parseString(Files.readString(file))
                .getAsJsonObject().getAsJsonObject("data").getAsJsonObject("players");
        assertEquals(0, players.size());
    }

    @Test
    void setPrunesExpiredEntries(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("chatpunish.json");
        write(file, envelope("\"" + EXPIRED + "\":{\"name\":\"Old\",\"mode\":\"tape\",\"expireAt\":1},"
                + "\"" + ALIVE + "\":{\"name\":\"Steve\",\"mode\":\"cat\",\"expireAt\":0}"));
        ChatPunishmentManager.ChatPunishStore store = load(file);

        assertEquals(ChatPunishmentManager.Result.APPLIED,
                store.set(THIRD, "Alex", PunishmentMode.REVERSE, 0));
        ChatPunishmentManager.ChatPunishStore reloaded = load(file);
        assertFalse(reloaded.punishments.containsKey(EXPIRED.toString()));
        assertEquals(PunishmentMode.CAT, reloaded.punishments.get(ALIVE.toString()).mode());
        assertEquals(PunishmentMode.REVERSE, reloaded.punishments.get(THIRD.toString()).mode());
    }

    @Test
    void worldSwitchDoesNotLeak(@TempDir Path dir) throws Exception {
        Path worldA = dir.resolve("a.json");
        Path worldB = dir.resolve("b.json");
        write(worldA, envelope("\"" + ALIVE + "\":{\"name\":\"A\",\"mode\":\"cat\",\"expireAt\":0}"));
        write(worldB, envelope("\"" + EXPIRED + "\":{\"name\":\"B\",\"mode\":\"tape\",\"expireAt\":0}"));

        ChatPunishmentManager.ChatPunishStore store = load(worldA);
        assertTrue(store.punishments.containsKey(ALIVE.toString()));
        store.unload();
        store.loadFrom(worldB);
        assertFalse(store.punishments.containsKey(ALIVE.toString()));
        assertTrue(store.punishments.containsKey(EXPIRED.toString()));
    }

    @Test
    void transformPublicChatPassesNullThrough() {
        assertNull(ChatPunishmentManager.transformPublicChat(null, null));
    }
}
