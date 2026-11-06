package me.primaryuan.carpet.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.brain.BrainTeams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** brain 命令 team 子句：解析分流、off 退出关键字、队名补全 */
class PlayerBrainTeamCommandTest {

    @BeforeAll
    static void setUp() throws Exception {
        me.primaryuan.carpet.util.TestRegistries.bootstrap();
    }

    @AfterEach
    void tearDown() throws Exception {
        var field = BrainTeams.class.getDeclaredField("TEAMS");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }

    // ===== team 子句解析 =====

    @Test
    void bareTeamMeansQuery() {
        PlayerBrainCommand.TeamClause clause = PlayerBrainCommand.parseTeamClause("team");
        assertNull(clause.teamName(), "brain team → 查询");
    }

    @Test
    void teamWithSpacesStillQueries() {
        // setMode 先 trim，"team " 等价 "team" → 查询而非空队名
        assertNull(PlayerBrainCommand.parseTeamClause("team").teamName());
    }

    @Test
    void teamWithTeamNameJoins() {
        PlayerBrainCommand.TeamClause clause = PlayerBrainCommand.parseTeamClause("team red");
        assertEquals("red", clause.teamName());
        clause = PlayerBrainCommand.parseTeamClause("team  红军  ");
        assertEquals("红军", clause.teamName(), "队名首尾空白收敛");
    }

    @Test
    void teamKeywordCaseInsensitive() {
        assertEquals("red", PlayerBrainCommand.parseTeamClause("TEAM red").teamName());
        assertEquals("red", PlayerBrainCommand.parseTeamClause("Team red").teamName());
    }

    @Test
    void teamOffMeansLeave() {
        PlayerBrainCommand.TeamClause clause = PlayerBrainCommand.parseTeamClause("team off");
        assertEquals("off", clause.teamName(), "off 传给 setTeam 判为退出");
    }

    @Test
    void nonTeamInputFallsThrough() {
        assertNull(PlayerBrainCommand.parseTeamClause("zombie"));
        assertNull(PlayerBrainCommand.parseTeamClause("zombie keep"));
        assertNull(PlayerBrainCommand.parseTeamClause("teams"));
        assertNull(PlayerBrainCommand.parseTeamClause("teamx red"));
        assertNull(PlayerBrainCommand.parseTeamClause("off"));
    }

    // ===== 队名补全 =====

    private static Suggestions suggest(String input) throws Exception {
        var method = PlayerBrainCommand.class.getDeclaredMethod("suggestModes",
                com.mojang.brigadier.context.CommandContext.class, SuggestionsBuilder.class);
        method.setAccessible(true);
        return (Suggestions) ((CompletableFuture<?>) method.invoke(null, null,
                new SuggestionsBuilder(input, 0))).get();
    }

    @Test
    void suggestTeamNamesAfterTeamClause() throws Exception {
        BrainTeams.join(UUID.randomUUID(), "red");
        BrainTeams.join(UUID.randomUUID(), "blue");
        BrainTeams.join(UUID.randomUUID(), "red");
        Suggestions suggestions = suggest("team ");
        assertTrue(hasSuggestion(suggestions, "red"), "已有队名参与补全（去重）");
        assertTrue(hasSuggestion(suggestions, "blue"));
        assertTrue(hasSuggestion(suggestions, "off"), "off 退出关键字参与补全");
        assertFalse(hasSuggestion(suggestions, "zombie"), "team 子句内不再建议模式名");
    }

    @Test
    void suggestTeamNamesFilterByPrefix() throws Exception {
        BrainTeams.join(UUID.randomUUID(), "red");
        BrainTeams.join(UUID.randomUUID(), "blue");
        Suggestions suggestions = suggest("team r");
        assertTrue(hasSuggestion(suggestions, "red"));
        assertFalse(hasSuggestion(suggestions, "blue"));
    }

    @Test
    void suggestTeamKeywordAtHead() throws Exception {
        Suggestions suggestions = suggest("t");
        assertTrue(hasSuggestion(suggestions, "team"), "根位置建议 team 子句");
        Suggestions zombie = suggest("z");
        assertFalse(hasSuggestion(zombie, "team"));
        assertTrue(hasSuggestion(zombie, "zombie"), "模式补全不受影响");
    }

    private static boolean hasSuggestion(Suggestions suggestions, String expected) {
        return suggestions.getList().stream().anyMatch(s -> s.getText().equals(expected));
    }
}
