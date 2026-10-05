package me.primaryuan.carpet.handler.textAnimation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitleStateTest {
    @Test
    void opaqueGlyphUsesUnsignedAlphaRange() {
        Glyph glyph = new Glyph(null, 0, 0, 0, 0, null);
        assertEquals(255, glyph.opacity);
    }

    @Test
    void actionbarHonorsRequestedHold() throws Exception {
        var timer = ActionbarSession.class.getDeclaredField("holdLeft");
        timer.setAccessible(true);
        for (int hold : new int[]{20, 40, 600}) {
            var session = new ActionbarSession(List.of(), List.of(), null, hold);
            assertEquals(hold, timer.getInt(session));
        }
    }
}
