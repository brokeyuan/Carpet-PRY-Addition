package me.primaryuan.carpet.handler.whoCalledMe;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static List<int[]> uncovered(String content, String name, String... allNames) {
        return WhoCalledMeHandler.uncoveredHits(content, name, List.of(allNames));
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

    @Test
    void uncoveredHitsKeepAllOwnMentions() {
        // title 多命中全高亮的根因场景：两次点名都要在列
        List<int[]> hits = uncovered("Brokeyuan 123 Brokeyuan", "Brokeyuan", "Brokeyuan");
        assertEquals(2, hits.size());
        assertEquals(0, hits.get(0)[0]);
        assertEquals(9, hits.get(0)[1]);
        assertEquals(14, hits.get(1)[0]);
        assertEquals(23, hits.get(1)[1]);
    }

    @Test
    void uncoveredHitsDropOnlyCoveredRanges() {
        // "timy tim" 中 Tim 的 [0,3) 属于 Timy 的 [0,4)，只保留独立第二命中
        List<int[]> hits = uncovered("timy tim", "Tim", "tim", "timy");
        assertEquals(1, hits.size());
        assertEquals(5, hits.get(0)[0]);
        assertEquals(8, hits.get(0)[1]);
    }

    @Test
    void uncoveredHitsIncludeAtSignInMentionMode() {
        // mention 模式由调用方以 @+名字 作 needle，区间为 @+名字 整体
        List<int[]> hits = uncovered("@Brokeyuan 来", "@Brokeyuan", "@Brokeyuan", "@Alice");
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0)[0]);
        assertEquals(10, hits.get(0)[1]);
    }

    @Test
    void rainbowPaletteCycles() {
        assertEquals(ChatFormatting.RED, WhoCalledMeHandler.rainbowColor(0));
        assertEquals(ChatFormatting.AQUA, WhoCalledMeHandler.rainbowColor(4));
        assertEquals(ChatFormatting.LIGHT_PURPLE, WhoCalledMeHandler.rainbowColor(6));
        assertEquals(ChatFormatting.RED, WhoCalledMeHandler.rainbowColor(7), "循环回到色板头");
    }

    @Test
    void legacyNoneNoLongerAliasesOff() {
        String original = CarpetPrimaryuanSettings.whoCalledMeHighlight;
        try {
            CarpetPrimaryuanSettings.whoCalledMeHighlight = "none";
            assertEquals(ChatFormatting.AQUA, WhoCalledMeHandler.highlightColor(), "旧值 none 按未知值回落默认");
            CarpetPrimaryuanSettings.whoCalledMeHighlight = "false";
            assertNull(WhoCalledMeHandler.highlightColor(), "false 仍关闭高亮");
        } finally {
            CarpetPrimaryuanSettings.whoCalledMeHighlight = original;
        }
    }

    @Test
    void soundOptionFallsBackToDing() {
        assertEquals(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                WhoCalledMeHandler.soundOf("what").value(), "未知值回落 ding");
        assertEquals(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                WhoCalledMeHandler.soundOf("true").value(), "旧开关值等同 ding");
    }

    @Test
    void displayWidthCountsWideCharsDouble() {
        assertEquals(9, WhoCalledMeHandler.displayWidth("Brokeyuan"));
        assertEquals(4, WhoCalledMeHandler.displayWidth("小明"));
        assertEquals(7, WhoCalledMeHandler.displayWidth("ab小明c"));
    }

    @Test
    void titleWindowKeepsFullTextWithinLimit() {
        String content = "Brokeyuan 123 Brokeyuan";
        assertArrayEquals(new int[]{0, content.length()},
                WhoCalledMeHandler.titleWindow(content, List.of(new int[]{0, 9}), 40));
    }

    @Test
    void titleWindowPrefixCutKeepsLeadingName() {
        // 名字在前的超长消息：按预算取前缀（名字 9 + 空格 1 + 14 个宽字符 = 38 ≤ 39）
        String content = "Brokeyuan 一二三四五六七八九十一二三四五六七八九十";
        int[] win = WhoCalledMeHandler.titleWindow(content, List.of(new int[]{0, 9}), 40);
        assertEquals(0, win[0]);
        assertEquals(24, win[1]);
    }

    @Test
    void titleWindowCentersOnLateHit() {
        // 20 个汉字（40 单位）后才有名字：前缀截不到，保名窗口向左回退 6 单位（3 字）
        String content = "一二三四五六七八九十一二三四五六七八九十Brokeyuan";
        int[] win = WhoCalledMeHandler.titleWindow(content, List.of(new int[]{20, 29}), 40);
        assertEquals(17, win[0]);
        assertEquals(content.length(), win[1]);
    }

    @Test
    void titleWindowBothSidesCutStaysWithinLimit() {
        // 名字两侧各有 20 个汉字：左 … + 窗口 + 右 … 总显示宽不得超上限
        String content = "一二三四五六七八九十一二三四五六七八九十Bob一二三四五六七八九十一二三四五六七八九十";
        int[] win = WhoCalledMeHandler.titleWindow(content, List.of(new int[]{20, 23}), 40);
        assertEquals(17, win[0]);
        assertEquals(37, win[1]);
        int total = WhoCalledMeHandler.displayWidth(
                "…" + content.substring(win[0], win[1]) + "…");
        assertTrue(total <= 40, "含两侧 … 总宽 " + total + " 超限");
    }

    @Test
    void titleTruncatesEvenWhenHighlightOff() {
        // 截断与配色无关：highlight=false 时超长 title 同样截断（Review R1 F1）
        String original = CarpetPrimaryuanSettings.whoCalledMeHighlight;
        try {
            CarpetPrimaryuanSettings.whoCalledMeHighlight = "false";
            String content = "一二三四五六七八九十一二三四五六七八九十Brokeyuan";
            Component title = WhoCalledMeHandler.buildTitle(content, List.of(new int[]{20, 29}));
            assertEquals("…" + content.substring(17, 29), title.getString());
        } finally {
            CarpetPrimaryuanSettings.whoCalledMeHighlight = original;
        }
    }
}
