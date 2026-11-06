package me.primaryuan.carpet.handler.fakegames;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.config.PryJsonStore;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 假游戏战绩与排行榜（存档级，&lt;存档&gt;/carpet-pry-addition/fake-game-results.json）。
 *
 * <p>按游戏与关键模式分榜；使用 UUID 作为身份，显示名只作快照。每局只入账一次；
 * 取消局不入正常胜负。文件读取失败保留原文件并用空表运行，写入走基类原子写。</p>
 */
public final class FakeGameResults extends PryJsonStore {

    private static final FakeGameResults INSTANCE = new FakeGameResults();
    private static final int MAX_RECORDED_SESSIONS = 512;

    private final Map<String, Map<UUID, Entry>> games = new LinkedHashMap<>();
    private final Set<String> recordedSessions = new LinkedHashSet<>();
    private boolean active;

    private FakeGameResults() {
        super("FakeGameResults", "fake-game-results.json", Scope.WORLD, 1, null);
    }

    public static void onServerLoaded(MinecraftServer server) {
        INSTANCE.attach(server);
        INSTANCE.active = true;
    }

    public static void onServerClosed() {
        INSTANCE.active = false;
        INSTANCE.detach();
    }

    public record TopEntry(UUID id, String name, int plays, int wins, int best, int total) {}

    /** 结算入账；中途取消不记正常胜负，也不生成“取消”排行榜记录。 */
    public static void record(GameSession session, boolean cancelled, List<UUID> winners) {
        if (!INSTANCE.active || cancelled || session == null) {
            return;
        }
        if (!INSTANCE.recordedSessions.add(session.id)) {
            return;
        }
        while (INSTANCE.recordedSessions.size() > MAX_RECORDED_SESSIONS) {
            String oldest = INSTANCE.recordedSessions.iterator().next();
            INSTANCE.recordedSessions.remove(oldest);
        }
        String key = session.resultKey();
        Map<UUID, Entry> board = INSTANCE.games.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        Set<UUID> winSet = new LinkedHashSet<>(winners);
        for (UUID id : session.participants()) {
            Entry entry = board.computeIfAbsent(id, ignored -> new Entry());
            entry.name = session.nameOf(id);
            entry.plays++;
            if (winSet.contains(id)) {
                entry.wins++;
            }
            GameMember member = session.member(id);
            int score = member == null ? 0 : member.score;
            entry.best = Math.max(entry.best, score);
            entry.total = safeAdd(entry.total, score);
        }
        INSTANCE.save();
    }

    private static int safeAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public static List<TopEntry> top(String key, int limit) {
        Map<UUID, Entry> board = INSTANCE.games.get(key);
        if (board == null || board.isEmpty()) {
            return List.of();
        }
        List<TopEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : board.entrySet()) {
            Entry entry = e.getValue();
            entries.add(new TopEntry(e.getKey(), entry.name, entry.plays, entry.wins,
                    entry.best, entry.total));
        }
        entries.sort(Comparator.comparingInt(TopEntry::wins).reversed()
                .thenComparing(Comparator.comparingInt(TopEntry::best).reversed())
                .thenComparing(TopEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    public static TopEntry personal(UUID id, String key) {
        Map<UUID, Entry> board = INSTANCE.games.get(key);
        if (board == null) {
            return null;
        }
        Entry entry = board.get(id);
        return entry == null ? null : new TopEntry(id, entry.name, entry.plays, entry.wins,
                entry.best, entry.total);
    }

    /** 有无该榜数据（面板据此显示“暂无战绩”）。 */
    public static boolean hasBoard(String key) {
        Map<UUID, Entry> board = INSTANCE.games.get(key);
        return board != null && !board.isEmpty();
    }

    /** 全部已产生的榜单键（游戏|模式|难度），供排行面板按前缀筛选。 */
    public static List<String> keys() {
        return List.copyOf(INSTANCE.games.keySet());
    }

    @Override
    protected void applyData(JsonObject data) {
        Map<String, Map<UUID, Entry>> parsed = new LinkedHashMap<>();
        JsonElement gamesElement = data.get("games");
        if (gamesElement != null && gamesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> gameEntry : gamesElement.getAsJsonObject().entrySet()) {
                if (!gameEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject boardObject = gameEntry.getValue().getAsJsonObject();
                Map<UUID, Entry> board = new LinkedHashMap<>();
                JsonElement entriesElement = boardObject.get("entries");
                if (entriesElement != null && entriesElement.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : entriesElement.getAsJsonObject().entrySet()) {
                        try {
                            UUID id = UUID.fromString(entry.getKey());
                            JsonObject object = entry.getValue().getAsJsonObject();
                            Entry record = new Entry();
                            record.name = object.has("name") ? object.get("name").getAsString() : "";
                            record.plays = object.has("plays") ? object.get("plays").getAsInt() : 0;
                            record.wins = object.has("wins") ? object.get("wins").getAsInt() : 0;
                            record.best = object.has("best") ? object.get("best").getAsInt() : 0;
                            record.total = object.has("total") ? object.get("total").getAsInt() : 0;
                            board.put(id, record);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
                parsed.put(gameEntry.getKey(), board);
            }
        }
        games.clear();
        games.putAll(parsed);
    }

    @Override
    protected JsonObject collectData() {
        JsonObject data = new JsonObject();
        JsonObject gamesObject = new JsonObject();
        for (Map.Entry<String, Map<UUID, Entry>> gameEntry : games.entrySet()) {
            JsonObject boardObject = new JsonObject();
            JsonObject entriesObject = new JsonObject();
            for (Map.Entry<UUID, Entry> entry : gameEntry.getValue().entrySet()) {
                Entry record = entry.getValue();
                JsonObject object = new JsonObject();
                object.addProperty("name", record.name);
                object.addProperty("plays", record.plays);
                object.addProperty("wins", record.wins);
                object.addProperty("best", record.best);
                object.addProperty("total", record.total);
                entriesObject.add(entry.getKey().toString(), object);
            }
            boardObject.add("entries", entriesObject);
            gamesObject.add(gameEntry.getKey(), boardObject);
        }
        data.add("games", gamesObject);
        return data;
    }

    @Override
    protected void clearData() {
        games.clear();
        recordedSessions.clear();
    }

    private static final class Entry {
        String name = "";
        int plays;
        int wins;
        int best;
        int total;
    }
}
