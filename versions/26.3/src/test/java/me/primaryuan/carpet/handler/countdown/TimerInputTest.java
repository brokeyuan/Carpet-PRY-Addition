package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerInputTest {

    @Test
    void emptyTailParsesToDefaults() {
        TimerInput.Parsed p = TimerInput.parse(null);
        assertNull(p.style());
        assertNull(p.layout());
        assertFalse(p.silent());
        assertNull(p.selectorRaw());
        assertNull(p.label());
        assertTrue(TimerInput.parse("").label() == null);
    }

    @Test
    void singleWordsResolve() {
        assertEquals(TimerStyle.FANCY, TimerInput.parse("FANCY").style(), "大小写不敏感");
        assertNull(TimerInput.parse("fancy").layout());
        assertNull(TimerInput.parse("fancy").selectorRaw());
    }

    @Test
    void layoutAndSilentCaseInsensitive() {
        TimerInput.Parsed p = TimerInput.parse("BOTH silent");
        assertEquals(TimerLayout.BOTH, p.layout());
        assertTrue(p.silent());
        assertNull(p.style());
        assertNull(p.label());
    }

    @Test
    void fullOptionOrderFree() {
        TimerInput.Parsed p = TimerInput.parse("@a[team=red] fancy -- 活动开始");
        assertEquals(TimerStyle.FANCY, p.style());
        assertEquals("@a[team=red]", p.selectorRaw());
        assertEquals("活动开始", p.label());
        assertNull(p.layout());
    }

    @Test
    void labelAfterDoubleDashWinsEverything() {
        TimerInput.Parsed p = TimerInput.parse("fancy -- simple bar silent @a");
        assertEquals(TimerStyle.FANCY, p.style());
        assertEquals("simple bar silent @a", p.label());
        assertNull(p.layout());
        assertFalse(p.silent());
    }

    @Test
    void selectorWithQuotedSpaceKeptWhole() {
        TimerInput.Parsed p = TimerInput.parse("@a[name=\"a b\"] normal");
        assertEquals("@a[name=\"a b\"]", p.selectorRaw());
        assertEquals(TimerStyle.NORMAL, p.style());
    }

    @Test
    void labelMayContainSpaces() {
        assertEquals("红队 决赛 第三轮", TimerInput.parse("-- 红队 决赛 第三轮").label());
    }

    @Test
    void rejectsUnknownDuplicateEmpty() {
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("quick"),
                "未识别词");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("fancy normal"),
                "同类重复");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("bar both"),
                "布局重复");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("silent silent"),
                "silent 重复");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("@a @s"),
                "选择器重复");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("@a[team=red"),
                "选择器未闭合");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("--   "),
                "空名称");
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("-- " + "字".repeat(129)),
                "名称超长");
    }

    @Test
    void barePlayerNameRejected() {
        assertThrows(TimerInput.TimerSyntaxException.class, () -> TimerInput.parse("Steve"),
                "裸玩家名不作为目标词");
    }

    @Test
    void suggestionsExcludeChosenCategories() {
        assertTrue(TimerInput.suggestNext("").contains("fancy"));
        assertTrue(TimerInput.suggestNext("").contains("both"));
        assertTrue(TimerInput.suggestNext("").contains("silent"));
        assertTrue(TimerInput.suggestNext("").contains("--"));

        assertTrue(TimerInput.suggestNext("fancy").contains("bar"), "选了效果仍建议布局");
        assertFalse(TimerInput.suggestNext("fancy").contains("simple"), "已选类别不再候补");
        assertFalse(TimerInput.suggestNext("fancy bar").contains("center"), "布局已选不再候补");
        assertFalse(TimerInput.suggestNext("fancy bar silent").contains("silent"));

        assertTrue(TimerInput.suggestNext("fancy bar").contains("@a"));
        assertFalse(TimerInput.suggestNext("fancy bar @a").contains("@s"), "选择器已选");
        // 选择器输入中不给出词候选（由原版选择器补全接管）
        assertTrue(TimerInput.suggestNext("@a[te").isEmpty());
        // -- 之后不再建议任何词
        assertTrue(TimerInput.suggestNext("-- 标题").isEmpty());
    }
}
