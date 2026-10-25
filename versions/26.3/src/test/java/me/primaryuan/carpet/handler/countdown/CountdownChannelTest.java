package me.primaryuan.carpet.handler.countdown;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountdownChannelTest {

    @Test
    void parsesSingleAndCombos() {
        assertEquals(EnumSet.of(CountdownChannel.BOSSBAR), CountdownChannel.parseCombo("bossbar"));
        assertEquals(EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE),
                CountdownChannel.parseCombo("bossbar+title"));
        assertEquals(EnumSet.allOf(CountdownChannel.class),
                CountdownChannel.parseCombo("bossbar+title+subtitle+actionbar"));
    }

    @Test
    void caseInsensitiveAndDeduplicates() {
        assertEquals(EnumSet.of(CountdownChannel.TITLE), CountdownChannel.parseCombo("TITLE"));
        assertEquals(EnumSet.of(CountdownChannel.BOSSBAR, CountdownChannel.TITLE),
                CountdownChannel.parseCombo("BossBar+title+TITLE"));
    }

    @Test
    void rejectsUnknownChannel() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CountdownChannel.parseCombo("bossbar+chunk"));
        assertEquals("chunk", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> CountdownChannel.parseCombo(""));
        assertThrows(IllegalArgumentException.class, () -> CountdownChannel.parseCombo("  "));
    }

    @Test
    void formatsCanonicalCombo() {
        EnumSet<CountdownChannel> set = CountdownChannel.parseCombo("actionbar+subtitle+title+bossbar");
        assertEquals("bossbar+title+subtitle+actionbar", CountdownChannel.formatCombo(set));
    }

    @Test
    void suggestNextSkipsUsedAndMatchesPrefix() {
        EnumSet<CountdownChannel> used = EnumSet.of(CountdownChannel.BOSSBAR);
        EnumSet<CountdownChannel> out = EnumSet.noneOf(CountdownChannel.class);
        CountdownChannel.suggestNext("s", used, out);
        assertEquals(EnumSet.of(CountdownChannel.SUBTITLE), out, "bossbar 已用不应再建议");
        out.clear();
        CountdownChannel.suggestNext("", used, out);
        assertEquals(EnumSet.of(CountdownChannel.TITLE, CountdownChannel.SUBTITLE, CountdownChannel.ACTIONBAR), out);
        out.clear();
        CountdownChannel.suggestNext("x", used, out);
        assertTrue(out.isEmpty());
    }
}
