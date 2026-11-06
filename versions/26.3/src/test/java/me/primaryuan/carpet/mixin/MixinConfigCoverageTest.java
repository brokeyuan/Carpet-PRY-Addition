package me.primaryuan.carpet.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版本 Mixin 配置覆盖检查：26.1.2/26.2 不应再用不完整独立配置遮蔽根模板，
 * 否则新主线的聊天惩罚与假人分身注册不会进入最终 JAR。检查在源码资源层执行，
 * 最终发布 JAR 的类存在性由构建后的 JAR 检查脚本复核。
 */
class MixinConfigCoverageTest {

    private static final Path ROOT_CONFIG =
            Path.of("src/main/resources/carpet-primaryuan.mixins.json");
    private static final String CHAT_MIXIN =
            "rule.chatPunishment.ServerGamePacketListenerMixin";
    private static final String BRAIN_PREFIX = "rule.fakePlayerBrain.";
    private static final String CLONE_PREFIX = "rule.fakePlayerClones.";
    private static final String STANDING_ON_AIR_MIXIN =
            "rule.fixStandingOnAir.ServerGamePacketListenerMixin";

    @Test
    void affectedVersionsInheritTheMainlineMixinSet() throws Exception {
        Set<String> root = readConfig(repoRoot().resolve(ROOT_CONFIG));
        assertEquals(1, countPrefix(root, CHAT_PREFIX));
        assertEquals(21, countPrefix(root, CLONE_PREFIX));
        assertEquals(4, countPrefix(root, BRAIN_PREFIX));

        for (String version : List.of("26.1.2", "26.2")) {
            Set<String> effective = effectiveConfig(repoRoot(), version);
            assertEquals(root, effective,
                    version + " 必须继承根模板，不得被不完整版本配置遮蔽");
            assertTrue(effective.contains(CHAT_MIXIN), version + " 缺聊天惩罚入口");
            assertEquals(4, countPrefix(effective, BRAIN_PREFIX),
                    version + " 必须保留四个换脑 Mixin");
        }
    }

    @Test
    void currentVersionKeepsMainlineRegistrations() throws Exception {
        Path root = repoRoot();
        Set<String> mainline = readConfig(root.resolve(ROOT_CONFIG));
        Set<String> current = effectiveConfig(root, "26.3");

        assertTrue(current.containsAll(mainline), "26.3 不得丢失主线注册");
        assertTrue(current.contains(STANDING_ON_AIR_MIXIN),
                "26.3 应保留版本专用 fixStandingOnAir 注册");
    }

    @Test
    void allEffectiveConfigsHaveNoDuplicateEntries() throws Exception {
        Path root = repoRoot();
        for (String version : List.of("1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5",
                "1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")) {
            Path config = configFor(root, version);
            if (Files.exists(config)) {
                assertNoDuplicates(config);
            }
        }
        assertNoDuplicates(root.resolve(ROOT_CONFIG));
    }

    private static final String CHAT_PREFIX = "rule.chatPunishment.";

    private static Set<String> effectiveConfig(Path root, String version) throws Exception {
        return readConfig(configFor(root, version));
    }

    private static Path configFor(Path root, String version) {
        Path override = root.resolve("versions").resolve(version)
                .resolve("src/main/resources/carpet-primaryuan.mixins.json");
        return Files.exists(override) ? override : root.resolve(ROOT_CONFIG);
    }

    private static Set<String> readConfig(Path path) throws Exception {
        JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        Set<String> entries = new LinkedHashSet<>();
        for (String group : List.of("mixins", "client", "server")) {
            JsonElement element = object.get(group);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                entries.add(item.getAsString());
            }
        }
        return entries;
    }

    private static void assertNoDuplicates(Path path) throws Exception {
        JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (String group : List.of("mixins", "client", "server")) {
            JsonElement element = object.get(group);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            long count = element.getAsJsonArray().size();
            long distinct = element.getAsJsonArray().asList().stream()
                    .map(JsonElement::getAsString).distinct().count();
            assertEquals(distinct, count, path + " " + group + " 存在重复条目");
        }
    }

    private static long countPrefix(Set<String> entries, String prefix) {
        return entries.stream().filter(entry -> entry.startsWith(prefix)).count();
    }

    private static Path repoRoot() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.json"))
                    && Files.exists(current.resolve("build.gradle"))
                    && Files.isDirectory(current.resolve("versions"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repo root not found from "
                + Paths.get("").toAbsolutePath());
    }
}
