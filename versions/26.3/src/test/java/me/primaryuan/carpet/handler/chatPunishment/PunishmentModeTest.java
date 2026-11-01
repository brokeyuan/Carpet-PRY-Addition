package me.primaryuan.carpet.handler.chatPunishment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 聊天惩罚的纯文本转换：固定替换、猫娘追加、结巴与反转（含代理对/emoji 组合）。 */
class PunishmentModeTest {

    @Test
    void fixedReplacementsUseExactTexts() {
        assertEquals(PunishmentMode.TAPE_TEXT, PunishmentMode.TAPE.transform("今晚去挖矿，谁一起？"));
        assertEquals("阿巴巴阿巴……阿巴！", PunishmentMode.IDIOT.transform("anything"));
        assertEquals("#$%%%！##！！", PunishmentMode.CENSOR.transform("anything"));
        assertEquals("嗯哼～哼嗯～", PunishmentMode.VILLAGER.transform("anything"));
        assertEquals(PunishmentMode.TAPE_TEXT, "呜呜...呜呜......呜呜........");
    }

    @Test
    void catAppendsSuffixAndKeepsOriginal() {
        assertEquals("今晚去挖矿，谁一起？喵~", PunishmentMode.CAT.transform("今晚去挖矿，谁一起？"));
        assertEquals("喵~", PunishmentMode.CAT.transform(""));
    }

    @Test
    void muteHasNoTextTransform() {
        assertNull(PunishmentMode.MUTE.transform("anything"));
    }

    @Test
    void stutterMatchesSpecExample() {
        assertEquals("今、今晚去挖、挖矿，谁、谁一起？",
                PunishmentMode.STUTTER.transform("今晚去挖矿，谁一起？"));
    }

    @Test
    void stutterIsDeterministicAndSkipsPunctuationAndSymbols() {
        // 标点、空白与 emoji 不复读但仍占步长；同输入恒同输出
        assertEquals("你、你好😀", PunishmentMode.STUTTER.transform("你好😀"));
        assertEquals("😀你好", PunishmentMode.STUTTER.transform("😀你好"));
        assertEquals("h、hell、lo w、worl、ld", PunishmentMode.STUTTER.transform("hello world"));
        assertEquals(PunishmentMode.STUTTER.transform("hello world"),
                PunishmentMode.STUTTER.transform("hello world"));
        assertEquals("", PunishmentMode.STUTTER.transform(""));
    }

    @Test
    void reverseMatchesSpecExample() {
        assertEquals("？起一谁，矿挖去晚今", PunishmentMode.REVERSE.transform("今晚去挖矿，谁一起？"));
    }

    @Test
    void reverseKeepsGraphemeClustersIntact() {
        // emoji 组合序列（ZWJ 家庭、旗帜、修饰符）按字素簇整体反转，不被拆散
        assertEquals("b😀a", PunishmentMode.REVERSE.transform("a😀b"));
        assertEquals("x👨‍👩‍👧", PunishmentMode.REVERSE.transform("👨‍👩‍👧x"));
        assertEquals("e👔🇨🇳", PunishmentMode.REVERSE.transform("🇨🇳👔e"));
        assertEquals("", PunishmentMode.REVERSE.transform(""));
    }

    @Test
    void byIdRoundTrip() {
        for (PunishmentMode mode : PunishmentMode.values()) {
            assertEquals(mode, PunishmentMode.byId(mode.id()));
        }
        assertNull(PunishmentMode.byId("banana"));
        assertNull(PunishmentMode.byId(null));
    }
}
