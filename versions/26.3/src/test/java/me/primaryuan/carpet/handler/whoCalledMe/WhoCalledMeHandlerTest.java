package me.primaryuan.carpet.handler.whoCalledMe;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WhoCalledMeHandler#mentionsName} 的子串匹配契约：大小写不敏感、紧邻字母数字同样命中 */
class WhoCalledMeHandlerTest {

    private static boolean match(String content, String name) {
        return WhoCalledMeHandler.mentionsName(content.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
    }

    @Test
    void exactAndEmbeddedMention() {
        assertTrue(match("Alex 来一下", "Alex"));
        assertTrue(match("来一下alex！", "Alex"));
        assertTrue(match("alex哥来帮忙", "Alex"));
        assertTrue(match("Alex.", "Alex"));
        assertTrue(match("ALEX!!", "alex"));
    }

    @Test
    void substringInsideOtherNameAlsoMentions() {
        assertTrue(match("timy 来一下", "Tim"));
        assertTrue(match("steve_alice", "steve"));
        assertTrue(match("steve_alice", "alice"));
    }

    @Test
    void adjacentLettersAndDigitsMention() {
        assertTrue(match("Brokeyuan1", "Brokeyuan"));
        assertTrue(match("abcBrokeyuan", "Brokeyuan"));
        assertTrue(match("steve_ 来一下", "steve"));
    }

    @Test
    void chineseNameGlueCountsAsMention() {
        assertTrue(match("请 小明 过来一下", "小明"));
        assertTrue(match("这是别的小明哥", "小明"));
    }

    @Test
    void emptyNameNeverMatches() {
        assertFalse(match("anything", ""));
    }
}
