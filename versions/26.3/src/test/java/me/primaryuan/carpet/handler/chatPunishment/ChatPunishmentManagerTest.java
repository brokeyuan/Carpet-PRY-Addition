package me.primaryuan.carpet.handler.chatPunishment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 聊天惩罚的记录模型：限时解析、剩余显示、到期判定与 JSON 往返（不触碰 fabric loader）。 */
class ChatPunishmentManagerTest {

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

    // ===== 剩余时间显示 =====

    @Test
    void formatRemainingSkipsZeroSegments() {
        assertEquals("0s", ChatPunishmentManager.formatRemaining(0));
        assertEquals("1s", ChatPunishmentManager.formatRemaining(1_000));
        assertEquals("10m", ChatPunishmentManager.formatRemaining(600_000));
        assertEquals("1m1s", ChatPunishmentManager.formatRemaining(61_000));
        assertEquals("1d", ChatPunishmentManager.formatRemaining(86_400_000));
        assertEquals("1d1h1m1s", ChatPunishmentManager.formatRemaining(90_061_000));
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
        String aliveId = UUID.randomUUID().toString();
        store.punishments.put(aliveId,
                new ChatPunishmentManager.Entry("Alive", PunishmentMode.CAT, Long.MAX_VALUE));
        store.punishments.put(UUID.randomUUID().toString(),
                new ChatPunishmentManager.Entry("Expired", PunishmentMode.TAPE, 1));

        JsonObject data = store.collectData();
        JsonObject players = data.getAsJsonObject("players");
        assertEquals(1, players.size());
        JsonObject alive = players.getAsJsonObject(aliveId);
        assertEquals("Alive", alive.get("name").getAsString());
        assertEquals("cat", alive.get("mode").getAsString());
        assertEquals(Long.MAX_VALUE, alive.get("expireAt").getAsLong());
    }

    @Test
    void applyDataSkipsUnknownModesAndBadShapes() {
        ChatPunishmentManager.ChatPunishStore store = newStore();
        JsonObject data = JsonParser.parseString("""
                {"players": {
                  "id-a": {"name": "Tape", "mode": "tape", "expireAt": 0},
                  "id-b": {"name": "Ghost", "mode": "banana", "expireAt": 0},
                  "id-c": {"name": "Cat", "mode": "cat"},
                  "id-d": "not-an-object"
                }}
                """).getAsJsonObject();

        store.applyData(data);
        assertEquals(2, store.punishments.size());
        assertEquals(PunishmentMode.TAPE, store.punishments.get("id-a").mode());
        assertEquals(0, store.punishments.get("id-a").expireAt());
        assertEquals(PunishmentMode.CAT, store.punishments.get("id-c").mode());
        assertEquals("Cat", store.punishments.get("id-c").name());
        assertEquals(0, store.punishments.get("id-c").expireAt());
    }

    @Test
    void applyCollectRoundTripKeepsEntries() {
        ChatPunishmentManager.ChatPunishStore store = newStore();
        store.punishments.put("id-a", new ChatPunishmentManager.Entry("Steve", PunishmentMode.STUTTER, Long.MAX_VALUE));

        JsonObject data = store.collectData();
        ChatPunishmentManager.ChatPunishStore reloaded = newStore();
        reloaded.applyData(data);
        ChatPunishmentManager.Entry entry = reloaded.punishments.get("id-a");
        assertEquals("Steve", entry.name());
        assertEquals(PunishmentMode.STUTTER, entry.mode());
        assertEquals(Long.MAX_VALUE, entry.expireAt());
    }
}
