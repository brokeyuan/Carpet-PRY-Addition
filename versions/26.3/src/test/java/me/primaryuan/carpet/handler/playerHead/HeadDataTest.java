package me.primaryuan.carpet.handler.playerHead;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** PRY 头颅档案的保存、纹理快照与外部名称分离。 */
class HeadDataTest {
    @BeforeAll static void bootstrap() { TestRegistries.bootstrap(); }

    @Test void universalItemCarriesMarkerAndLore() {
        ItemStack stack = HeadItems.universal(1);
        assertEquals(Items.PLAYER_HEAD, stack.getItem());
        assertTrue(HeadItems.isUniversal(stack));
        assertNotNull(stack.get(DataComponents.LORE), "发放时必须有未选择皮肤的说明");
        assertFalse(HeadData.read(stack).hasTexture());
    }

    @Test void textureSnapshotRoundTrips() {
        ItemStack stack = HeadItems.universal(1);
        HeadData data = HeadData.read(stack);
        UUID id = UUID.randomUUID();
        data.applySkin(new HeadSkin(id, "Notch", "texture-value", "signature-value"));
        data.write(stack);

        HeadData reread = HeadData.read(stack);
        assertNotNull(reread);
        assertEquals(HeadData.Kind.UNIVERSAL, reread.kind());
        assertEquals("Notch", reread.sourceName());
        assertEquals(id, reread.sourceId());
        assertTrue(reread.hasTexture());
        assertEquals("texture-value", reread.skin().value());
        assertEquals("signature-value", reread.skin().signature());
    }

    @Test void externalNameDoesNotTouchArchive() {
        ItemStack stack = HeadItems.universal(1);
        HeadData data = HeadData.read(stack);
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Notch", "value", "sig"));
        data.write(stack);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("我的宝贝"));

        HeadData reread = HeadData.read(stack);
        assertEquals("Notch", reread.sourceName());
        assertEquals("我的宝贝", stack.get(DataComponents.CUSTOM_NAME).getString());
        reread.write(stack);
        assertEquals("我的宝贝", stack.get(DataComponents.CUSTOM_NAME).getString());
    }

    @Test void writingKeepsOtherCustomData() {
        ItemStack stack = HeadItems.universal(1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("other_mod", "keep-me"));
        HeadData data = HeadData.read(stack);
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Notch", "value", null));
        data.write(stack);

        CompoundTag root = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        assertEquals("keep-me", root.getStringOr("other_mod", ""));
        assertTrue(root.contains("pry_head"));
    }

    @Test void unknownFormatIsIgnored() {
        ItemStack stack = HeadItems.universal(1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag root = tag.getCompoundOrEmpty("pry_head");
            root.putInt("v", 999);
            tag.put("pry_head", root);
        });
        assertNull(HeadData.read(stack));
        assertFalse(HeadItems.isUniversal(stack));
    }

    @Test void loreIsRebuiltFromArchive() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        HeadData data = HeadData.universal();
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Notch", "value", null));
        data.write(stack);
        HeadItems.applyLore(stack, data);
        var lore = stack.get(DataComponents.LORE);
        assertNotNull(lore);
        assertFalse(lore.lines().isEmpty());
    }
}
