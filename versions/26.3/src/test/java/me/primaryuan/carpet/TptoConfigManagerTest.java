package me.primaryuan.carpet;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TptoConfigManager} 的 JSON 契约：applyData/collectData round-trip、
 * 坏条目跳过、名称解析与重名拒绝。不触碰 fabric loader 与磁盘。
 */
class TptoConfigManagerTest {

    private static TptoConfigManager.TptoStore newStore() {
        return new TptoConfigManager.TptoStore();
    }

    private static JsonObject server(String host, int port, String displayName) {
        JsonObject obj = new JsonObject();
        obj.addProperty("host", host);
        obj.addProperty("port", port);
        if (displayName != null) {
            obj.addProperty("displayName", displayName);
        }
        return obj;
    }

    private static JsonObject wrap(JsonObject servers) {
        JsonObject data = new JsonObject();
        data.add("servers", servers);
        return data;
    }

    @Test
    void roundTripKeepsOrderAndFields() {
        JsonObject servers = new JsonObject();
        servers.add("lobby", server("mc.example.com", 25565, "大厅"));
        servers.add("survival", server("10.0.0.2", 25566, null));

        var store = newStore();
        store.applyData(wrap(servers));
        JsonObject out = store.collectData();

        assertEquals(wrap(servers), out);
        List<String> order = new ArrayList<>(out.getAsJsonObject("servers").keySet());
        assertEquals(List.of("lobby", "survival"), order);
    }

    @Test
    void malformedEntriesAreSkippedWithoutLosingValidOnes() {
        JsonObject servers = new JsonObject();
        servers.add("good", server("a.example.com", 25565, null));
        servers.add("badPort", server("b.example.com", 70000, null));
        JsonObject missingHost = new JsonObject();
        missingHost.addProperty("port", 25565);
        servers.add("missingHost", missingHost);
        servers.addProperty("notAnObject", "oops");

        var store = newStore();
        store.applyData(wrap(servers));
        JsonObject out = store.collectData().getAsJsonObject("servers");

        assertEquals(1, out.size());
        assertTrue(out.has("good"));
    }

    @Test
    void emptyDisplayNameFallsBackToInternalName() {
        Map<String, TptoConfigManager.ServerEntry> servers = new LinkedHashMap<>();
        servers.put("lobby", new TptoConfigManager.ServerEntry("a", 25565, null));
        servers.put("pvp", new TptoConfigManager.ServerEntry("b", 25566, ""));

        assertEquals("lobby", TptoConfigManager.displayNameOf(servers, "lobby"));
        assertEquals("pvp", TptoConfigManager.displayNameOf(servers, "pvp"));
        assertEquals("unknown", TptoConfigManager.displayNameOf(servers, "unknown"));
    }

    @Test
    void resolveAcceptsInternalNameThenDisplayNameIgnoringCase() {
        Map<String, TptoConfigManager.ServerEntry> servers = new LinkedHashMap<>();
        servers.put("lobby", new TptoConfigManager.ServerEntry("a", 25565, "大厅"));
        servers.put("pvp", new TptoConfigManager.ServerEntry("b", 25566, "Arena"));

        assertEquals("lobby", TptoConfigManager.resolveInternalName(servers, "lobby"));
        assertEquals("lobby", TptoConfigManager.resolveInternalName(servers, "大厅"));
        assertEquals("pvp", TptoConfigManager.resolveInternalName(servers, "arena"));
        assertNull(TptoConfigManager.resolveInternalName(servers, "nope"));
        assertNull(TptoConfigManager.resolveInternalName(servers, ""));
        assertNull(TptoConfigManager.resolveInternalName(servers, null));
    }

    @Test
    void duplicateAddIsRejectedWithoutOverwriting() {
        Map<String, TptoConfigManager.ServerEntry> servers = new LinkedHashMap<>();
        assertTrue(TptoConfigManager.addServerTo(servers, "lobby", "a", 25565, null));
        assertFalse(TptoConfigManager.addServerTo(servers, "lobby", "b", 25566, "换掉"));
        assertEquals(new TptoConfigManager.ServerEntry("a", 25565, null), servers.get("lobby"));
    }

    @Test
    void missingDisplayNameOmitsJsonField() {
        JsonObject servers = new JsonObject();
        servers.add("lobby", server("a.example.com", 25565, null));

        var store = newStore();
        store.applyData(wrap(servers));
        JsonObject out = store.collectData().getAsJsonObject("servers").getAsJsonObject("lobby");

        assertFalse(out.has("displayName"));
    }

    @Test
    void explicitJsonNullDisplayNameOmitsJsonField() {
        JsonObject raw = new JsonObject();
        raw.addProperty("host", "a.example.com");
        raw.addProperty("port", 25565);
        raw.add("displayName", JsonNull.INSTANCE);
        JsonObject servers = new JsonObject();
        servers.add("lobby", raw);

        var store = newStore();
        store.applyData(wrap(servers));
        JsonObject out = store.collectData().getAsJsonObject("servers").getAsJsonObject("lobby");

        assertFalse(out.has("displayName"));
    }

    @Test
    void nonObjectServersIsTreatedAsEmpty() {
        JsonObject data = new JsonObject();
        data.addProperty("servers", "oops");

        var store = newStore();
        store.applyData(data);

        assertEquals(0, store.collectData().getAsJsonObject("servers").size());
    }
}
