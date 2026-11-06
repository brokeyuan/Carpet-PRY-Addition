package me.primaryuan.carpet.handler.fakegames.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 你演我猜词匹配：归一化与中英文双答案 */
class CharadesMatchTest {

    @Test
    void matchesZhExactly() {
        assertTrue(CharadesGame.wordMatches("猫", "猫", "cat"));
        assertTrue(CharadesGame.wordMatches(" 苦力怕 ", "苦力怕", "creeper"));
    }

    @Test
    void matchesEnCaseInsensitive() {
        assertTrue(CharadesGame.wordMatches("CAT", "猫", "cat"));
        assertTrue(CharadesGame.wordMatches("Iron Golem", "铁傀儡", "iron golem"));
    }

    @Test
    void rejectsWrongAndPartial() {
        assertFalse(CharadesGame.wordMatches("狗", "猫", "cat"));
        assertFalse(CharadesGame.wordMatches("猫叫", "猫", "cat"));
        assertFalse(CharadesGame.wordMatches("ca", "猫", "cat"));
        assertFalse(CharadesGame.wordMatches("", "猫", "cat"));
    }
}
