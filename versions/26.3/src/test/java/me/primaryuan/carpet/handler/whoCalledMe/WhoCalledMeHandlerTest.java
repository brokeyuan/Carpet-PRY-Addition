package me.primaryuan.carpet.handler.whoCalledMe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WhoCalledMeHandler#mentionIndex} 的匹配契约：子串命中、紧邻字母数字也算、最长名优先；
 *  入参与返回下标均为原文坐标（匹配内部大小写不敏感，不经 toLowerCase） */
class WhoCalledMeHandlerTest {

    private static int index(String content, String name, String... allNames) {
        return WhoCalledMeHandler.mentionIndex(content, name, List.of(allNames));
    }

    private static boolean match(String content, String name) {
        return WhoCalledMeHandler.mentionsName(content, name);
    }

    @Test
    void exactAndEmbeddedMention() {
        assertEquals(0, index("Alex 来一下", "Alex", "Alex"));
        assertEquals(3, index("来一下alex！", "Alex", "Alex"));
        assertEquals(0, index("alex哥来帮忙", "Alex", "Alex"));
        assertEquals(0, index("Alex.", "Alex", "Alex"));
        assertEquals(0, index("ALEX!!", "alex", "alex"));
    }

    @Test
    void adjacentLettersAndDigitsMention() {
        assertEquals(0, index("Brokeyuan1", "Brokeyuan", "Brokeyuan"));
        assertEquals(3, index("abcBrokeyuan", "Brokeyuan", "Brokeyuan"));
        assertEquals(0, index("steve_ 来一下", "steve", "steve"));
    }

    @Test
    void longestNameWins() {
        assertEquals(-1, index("timy 来一下", "Tim", "tim", "timy"), "短名命中被长名覆盖则不提醒");
        assertEquals(0, index("timy 来一下", "Timy", "tim", "timy"), "完整名命中提醒");
        assertEquals(5, index("timy tim", "Tim", "tim", "timy"), "独立第二命中仍提醒");
        assertEquals(0, index("timothy", "Tim", "tim", "timy"), "无更长玩家名时子串照常命中");
    }

    @Test
    void longestNameCascade() {
        assertEquals(-1, index("timothy 来", "Tim", "tim", "timy", "timothy"));
        assertEquals(-1, index("timothy 来", "Timy", "tim", "timy", "timothy"));
        assertEquals(0, index("timothy 来", "Timothy", "tim", "timy", "timothy"));
    }

    @Test
    void chineseNameGlueCountsAsMention() {
        assertEquals(2, index("请 小明 过来一下", "小明", "小明"));
        assertEquals(4, index("这是别的小明哥", "小明", "小明"));
    }

    @Test
    void decorationRangesKeepLongestNonOverlapping() {
        // tim(0,3)/timy(0,4) 重叠取最长 timy；tim(5,8) 独立保留
        List<int[]> ranges = WhoCalledMeHandler.nonOverlappingLongestFirst(List.of(
                new int[]{0, 3}, new int[]{0, 4}, new int[]{5, 8}));
        assertEquals(2, ranges.size());
        assertEquals(0, ranges.get(0)[0]);
        assertEquals(4, ranges.get(0)[1]);
        assertEquals(5, ranges.get(1)[0]);
        assertEquals(8, ranges.get(1)[1]);
        // 相邻不重叠都保留
        assertEquals(2, WhoCalledMeHandler.nonOverlappingLongestFirst(List.of(
                new int[]{0, 3}, new int[]{3, 6})).size());
    }

    @Test
    void emptyNameNeverMatches() {
        assertEquals(-1, index("anything", "", "anything"));
        assertFalse(match("anything", ""));
        assertTrue(match("timy 来一下", "timy"));
    }

    @Test
    void caseFoldingExpansionDoesNotDriftIndices() {
        // 土耳其 İ（U+0130）小写化为两字符（i + 组合附点）：若在小写串上算下标
        // 再回原文切分，alex 的下标会从 2 漂移到 3（高亮错位/越界的根因）
        String content = "İ alex 来一下";
        assertEquals(2, index(content, "Alex", "Alex"), "命中下标必须落在原文的 alex 上");
    }
}
