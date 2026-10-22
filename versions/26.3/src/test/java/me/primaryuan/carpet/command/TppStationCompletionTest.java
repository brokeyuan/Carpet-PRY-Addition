package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.TppConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TppStationCompletionTest {
    private Map<String, String> stations;
    private Map<String, String> savedStations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpStations() throws Exception {
        // 仅替换内存站点，避免测试写入服务器配置。
        var field = TppConfigManager.class.getDeclaredField("stationMap");
        field.setAccessible(true);
        stations = (Map<String, String>) field.get(null);
        savedStations = new LinkedHashMap<>(stations);
        stations.clear();
        stations.put("qwp", "全物品");
        stations.put("farm", "农场");
        stations.put("spawn", null);
        stations.put("end", "");
    }

    @AfterEach
    void restoreStations() {
        stations.clear();
        stations.putAll(savedStations);
    }

    private static Suggestions complete(String input, int start) throws Exception {
        var method = TppCommand.class.getDeclaredMethod("suggestStations",
                CommandContext.class, SuggestionsBuilder.class);
        method.setAccessible(true);
        return (Suggestions) ((CompletableFuture<?>) method.invoke(null, null,
                new SuggestionsBuilder(input, start))).get();
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "qw", "qwp", "QwP", "全"})
    void internalOrDisplayPrefixSuggestsChineseName(String prefix) throws Exception {
        String input = "/tpp " + prefix;
        Suggestions suggestions = complete(input, 5);
        assertEquals(List.of("全物品"), suggestions.getList().stream().map(s -> s.getText()).toList());
        assertEquals("/tpp 全物品", suggestions.getList().getFirst().apply(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tppset spawn ", "/tppset remove ", "/tppset rule use 3 "})
    void stationCompletionPreservesManagementCommand(String command) throws Exception {
        String input = command + "qwp";
        Suggestions suggestions = complete(input, command.length());
        assertEquals(List.of(command + "全物品"),
                suggestions.getList().stream().map(s -> s.apply(input)).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sp", "SP", "en"})
    void missingOrEmptyDisplayNameFallsBackToInternalName(String prefix) throws Exception {
        String input = "/tpp " + prefix;
        String expected = prefix.equals("en") ? "end" : "spawn";
        assertEquals(List.of(expected), complete(input, 5).getList().stream().map(s -> s.getText()).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "wp", "物品"})
    void unrelatedOrNonPrefixInputHasNoSuggestions(String prefix) throws Exception {
        assertTrue(complete("/tpp " + prefix, 5).isEmpty());
    }

    @Test
    void emptyInputListsEachDisplayNameOnce() throws Exception {
        assertEquals(List.of("end", "spawn", "全物品", "农场"),
                complete("/tpp ", 5).getList().stream().map(s -> s.getText()).toList());
    }
}
