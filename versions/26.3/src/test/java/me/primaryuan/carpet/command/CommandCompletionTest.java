package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandCompletionTest {
    private static Suggestions suggest(Class<?> owner, String methodName, String input, int start) throws Exception {
        var method = owner.getDeclaredMethod(methodName, CommandContext.class, SuggestionsBuilder.class);
        method.setAccessible(true);
        return (Suggestions) ((CompletableFuture<?>) method.invoke(null, null,
                new SuggestionsBuilder(input, start))).get();
    }

    private static void contains(Suggestions suggestions, String input, String expected) {
        assertTrue(suggestions.getList().stream().anyMatch(s -> s.apply(input).equals(expected)),
                () -> "补全应保留已有输入: " + expected + ", 实际 " + suggestions.getList());
    }

    @Test
    void textOptionPreservesTargetAndMessage() throws Exception {
        String input = "/text @a hello|h";
        contains(suggest(TextCommand.class, "suggestInput", input, 6), input, "/text @a hello|hold=");
    }

    @Test
    void completeOptionKeyCanStillAddEquals() throws Exception {
        String input = "/text @a hello|hold";
        contains(suggest(TextCommand.class, "suggestInput", input, 6), input, "/text @a hello|hold=");
    }

    @Test
    void nextOptionPreservesEarlierOptions() throws Exception {
        String input = "/text @a hello|hold=20;d";
        contains(suggest(TextCommand.class, "suggestInput", input, 6), input,
                "/text @a hello|hold=20;drop=");
    }

    @Test
    void escapedMessagePipeDoesNotStartOptions() throws Exception {
        String input = "/text @a hello||h";
        assertTrue(suggest(TextCommand.class, "suggestInput", input, 6).isEmpty());
    }

    @Test
    void brainKeepPreservesMode() throws Exception {
        String input = "/player Steve brain zombie k";
        contains(suggest(PlayerBrainCommand.class, "suggestModes", input, 20), input,
                "/player Steve brain zombie keep");
    }
}
