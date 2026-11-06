package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 临时缩放使用独立 transient 修饰符：不覆盖基础值，外部修改保留，可重复清理。 */
class FakeGameTempEffectsTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    private static AttributeInstance scale() {
        return new AttributeInstance(Attributes.SCALE, ignored -> { });
    }

    @Test
    void shrinkMultipliesCurrentBaseAndRestoresExactly() {
        AttributeInstance attribute = scale();
        attribute.setBaseValue(2.0);

        assertTrue(FakeGameTempEffects.applyShrink(attribute));
        assertTrue(FakeGameTempEffects.hasShrink(attribute));
        assertEquals(1.2, attribute.getValue(), 1.0E-6);
        assertTrue(attribute.getPermanentModifiers().isEmpty(),
                "缩小修饰符必须是 transient，崩溃/重启不能写入玩家存档");

        FakeGameTempEffects.removeShrink(attribute);
        assertFalse(FakeGameTempEffects.hasShrink(attribute));
        assertEquals(2.0, attribute.getValue(), 1.0E-6);
    }

    @Test
    void adminBaseChangeDuringGameIsNotOverwritten() {
        AttributeInstance attribute = scale();
        attribute.setBaseValue(0.5);
        FakeGameTempEffects.applyShrink(attribute);
        assertEquals(0.3, attribute.getValue(), 1.0E-6);

        attribute.setBaseValue(4.0);
        assertEquals(2.4, attribute.getValue(), 1.0E-6);

        FakeGameTempEffects.removeShrink(attribute);
        assertEquals(4.0, attribute.getValue(), 1.0E-6);
    }

    @Test
    void repeatedApplyAndRemoveIsIdempotent() {
        AttributeInstance attribute = scale();
        attribute.setBaseValue(1.0);
        FakeGameTempEffects.applyShrink(attribute);
        FakeGameTempEffects.applyShrink(attribute);
        assertEquals(1, attribute.getModifiers().size());
        FakeGameTempEffects.removeShrink(attribute);
        FakeGameTempEffects.removeShrink(attribute);
        assertFalse(FakeGameTempEffects.hasShrink(attribute));
        assertEquals(1.0, attribute.getValue(), 1.0E-6);
    }

    @Test
    void shrinkValueFollowsCurrentBase() {
        assertEquals(0.6, FakeGameTempEffects.shrinkValue(1.0), 1.0E-9);
        assertEquals(1.2, FakeGameTempEffects.shrinkValue(2.0), 1.0E-9);
    }
}
