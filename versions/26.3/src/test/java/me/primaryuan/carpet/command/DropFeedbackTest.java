package me.primaryuan.carpet.command;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropFeedbackTest {
    @Test
    void allLanguagesAcceptActualSchedulerArguments() throws Exception {
        for (String language : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            try (var stream = getClass().getResourceAsStream("/assets/carpet-pry-addition/lang/" + language + ".json")) {
                var translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String mode : new String[]{"interval", "after", "perTick", "randomly"}) {
                    Object[] values = FrequencyCommandTree.concat("Steve",
                            mode.equals("randomly") ? new Object[]{10, 20} : new Object[]{10}, "dropall");
                    String template = translations.get("carpetprimaryuan.command.dropall.started_" + mode).getAsString();
                    String result = assertDoesNotThrow(() -> String.format(template, values), language + ": " + mode);
                    assertTrue(result.contains("Steve") && result.contains("dropall") && result.contains("10"));
                }
            }
        }
    }
}
