package me.primaryuan.carpet.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.TptoConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TptoCommand 纯解析：目标解码、addserver 参数拆分、双名称前缀补全与命令前缀保留 */
class TptoCommandTest {

    @TempDir
    Path temp;

    // ===== 目标解析 =====

    @Test
    void targetParsing() {
        assertEquals("sc", TptoCommand.parseTarget("sc"));
        assertEquals("sc", TptoCommand.parseTarget("  sc  "));
        assertEquals("生存服", TptoCommand.parseTarget("生存服"));
        assertEquals("生存 服", TptoCommand.parseTarget("生存 服"));
        // 合法整体双引号解码（含转义）
        assertEquals("生存 服", TptoCommand.parseTarget("\"生存 服\""));
        assertEquals("sc", TptoCommand.parseTarget("\"sc\""));
        assertEquals("a\"b", TptoCommand.parseTarget("\"a\\\"b\""));
        // 未闭合 / 引号后多余内容
        assertNull(TptoCommand.parseTarget("\"生存 服"));
        assertNull(TptoCommand.parseTarget("\"a\" b"));
        // 空白输入按列表处理，由命令层决定
        assertEquals("", TptoCommand.parseTarget("   "));
    }

    // ===== addserver 参数拆分 =====

    @Test
    void addArgsParsing() {
        TptoCommand.AddArgs full = TptoCommand.parseAddArgs("survival.example.com:25565 sc 生存服");
        assertEquals("survival.example.com:25565", full.address());
        assertEquals("sc", full.id());
        assertEquals("生存服", full.displayName());

        TptoCommand.AddArgs noDisplay = TptoCommand.parseAddArgs("localhost:25565 sc");
        assertEquals("localhost:25565", noDisplay.address());
        assertEquals("sc", noDisplay.id());
        assertNull(noDisplay.displayName());

        // 展示名可含空格与中文，多余空白收敛为词间
        TptoCommand.AddArgs spaces = TptoCommand.parseAddArgs("  [::1]:25565   sc   生 存 服  ");
        assertEquals("[::1]:25565", spaces.address());
        assertEquals("sc", spaces.id());
        assertEquals("生 存 服", spaces.displayName());

        // 缺标识 / 空输入
        assertNull(TptoCommand.parseAddArgs("survival.example.com:25565"));
        assertNull(TptoCommand.parseAddArgs(""));
        assertNull(TptoCommand.parseAddArgs("   "));
        assertNull(TptoCommand.parseAddArgs(null));
    }

    // ===== 目标前缀匹配（标识或展示名，Locale.ROOT 忽略大小写） =====

    @Test
    void targetPrefixMatching() {
        List<TptoConfigManager.ServerView> views = List.of(
                new TptoConfigManager.ServerView("sc", "生存服", "a.example.com", 25565),
                new TptoConfigManager.ServerView("survival", "Survival", "b.example.com", 25566));

        assertEquals(List.of("sc", "survival"), ids(TptoCommand.matchTargets("s", views)));
        assertEquals(List.of("survival"), ids(TptoCommand.matchTargets("su", views)));
        assertEquals(List.of("sc"), ids(TptoCommand.matchTargets("SC", views)));
        assertEquals(List.of("sc"), ids(TptoCommand.matchTargets("生", views)));
        assertEquals(List.of("sc"), ids(TptoCommand.matchTargets("生存服", views)));
        assertEquals(List.of("survival"), ids(TptoCommand.matchTargets("SURVIVAL", views)));
        assertTrue(TptoCommand.matchTargets("x", views).isEmpty());
        assertTrue(TptoCommand.matchTargets("", views).size() == 2);
    }

    private static List<String> ids(List<TptoConfigManager.ServerView> views) {
        return views.stream().map(TptoConfigManager.ServerView::name).toList();
    }

    // ===== 补全：插入英文标识，apply 保留完整命令前缀，tooltip 为展示名 =====

    @Test
    void suggestionsInsertIdAndKeepCommandPrefix() throws Exception {
        Path file = temp.resolve("tpto.json");
        seedStore(file);
        try {
            Method suggest = TptoCommand.class.getDeclaredMethod("suggestTargets",
                    com.mojang.brigadier.context.CommandContext.class, SuggestionsBuilder.class);
            suggest.setAccessible(true);

            String input = "/tpto s";
            Object future = suggest.invoke(null, null, new SuggestionsBuilder(input, 6));
            Suggestions suggestions = (Suggestions) ((java.util.concurrent.CompletableFuture<?>) future).get();
            assertTrue(suggestions.getList().stream().anyMatch(s ->
                            s.apply(input).equals("/tpto sc")),
                    () -> "补全应保留命令前缀并插入英文标识: " + suggestions.getList());

            String chinese = "/tpto 生";
            Object chineseFuture = suggest.invoke(null, null, new SuggestionsBuilder(chinese, 6));
            Suggestions byDisplay = (Suggestions) ((java.util.concurrent.CompletableFuture<?>) chineseFuture).get();
            assertTrue(byDisplay.getList().stream().anyMatch(s ->
                    s.apply(chinese).equals("/tpto sc")));
        } finally {
            cleanupStore();
        }
    }

    /** 借 STORE 单例注入临时路径并写入一条记录，供补全快照使用 */
    private static void seedStore(Path file) throws Exception {
        Field storeField = TptoConfigManager.class.getDeclaredField("STORE");
        storeField.setAccessible(true);
        Object store = storeField.get(null);
        Field pathField = storeField.getType().getDeclaredField("testPath");
        pathField.setAccessible(true);
        pathField.set(store, file);
        assertEquals(TptoConfigManager.MutationStatus.OK,
                TptoConfigManager.addServer("sc", "a.example.com", 25565, "生存服"));
    }

    private static void cleanupStore() throws Exception {
        Field storeField = TptoConfigManager.class.getDeclaredField("STORE");
        storeField.setAccessible(true);
        Object store = storeField.get(null);
        Field pathField = storeField.getType().getDeclaredField("testPath");
        pathField.setAccessible(true);
        pathField.set(store, null);
        TptoConfigManager.onServerClosed();
    }
}
