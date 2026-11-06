package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 草地是岩浆：危险方块白名单校验（Blocks 常量需注册表引导） */
class GrassIsLavaOptionTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void whitelistAccepted() {
        assertTrue(GrassIsLavaGame.isValidOption("grass_block"));
        assertTrue(GrassIsLavaGame.isValidOption("sand"));
        assertTrue(GrassIsLavaGame.isValidOption("red_sand"));
        assertTrue(GrassIsLavaGame.isValidOption("stone"));
        assertTrue(GrassIsLavaGame.isValidOption("oak_planks"));
        assertTrue(GrassIsLavaGame.isValidOption("snow"));
    }

    @Test
    void arbitraryIdsRejected() {
        // 不做注册表解析，任意 id 一律拒绝并提示白名单
        assertFalse(GrassIsLavaGame.isValidOption("minecraft:stone"));
        assertFalse(GrassIsLavaGame.isValidOption("tnt"));
        assertFalse(GrassIsLavaGame.isValidOption(""));
        assertFalse(GrassIsLavaGame.isValidOption(null));
    }
}
