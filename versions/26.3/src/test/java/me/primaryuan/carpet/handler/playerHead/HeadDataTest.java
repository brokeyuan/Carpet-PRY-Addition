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

    @Test void memorialArchiveSurvivesExternalRename() {
        HeadData data = HeadData.memorial("纪念名称", "周年活动", "2026-11-07", "张三", "说明文字");
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Notch", "texture-value", "sig"));
        ItemStack stack = HeadItems.memorial(data);

        assertTrue(HeadItems.isMemorial(stack));
        assertEquals("纪念名称", stack.get(DataComponents.CUSTOM_NAME).getString());
        assertNotNull(stack.get(DataComponents.PROFILE), "创建时即写入纹理快照");

        // 玩家把外部名称改成别的或清空，正式档案与纹理快照不受影响。
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("我的宝贝"));
        HeadData renamed = HeadData.read(stack);
        assertEquals("纪念名称", renamed.formalName());
        stack.remove(DataComponents.CUSTOM_NAME);
        HeadData cleared = HeadData.read(stack);
        assertEquals("纪念名称", cleared.formalName());
        assertEquals("周年活动", cleared.eventName());
        assertEquals("2026-11-07", cleared.eventDate());
        assertEquals("张三", cleared.recipient());
        assertEquals("说明文字", cleared.note());
        assertEquals("Notch", cleared.sourceName());
        assertEquals("texture-value", cleared.skin().value());
        assertEquals("sig", cleared.skin().signature());

        HeadItems.applyLore(stack, cleared);
        // 单测环境没有 Carpet 翻译表，Lore 行显示翻译键；这里核对档案派生的行数完整。
        assertEquals(6, stack.get(DataComponents.LORE).lines().size(),
                "正式名称/活动/日期/颁发对象/说明/皮肤来源各一行");
        assertTrue(HeadItems.isMemorial(stack));
    }

    @Test void skinChangeDoesNotRewriteMemorialFields() {
        HeadData data = HeadData.memorial("正式", "活动", "2026-01-01", "对象", "备注");
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Notch", "v1", null));
        data.applySkin(new HeadSkin(UUID.randomUUID(), "Herobrine", "v2", null));
        assertEquals("正式", data.formalName());
        assertEquals("活动", data.eventName());
        assertEquals("2026-01-01", data.eventDate());
        assertEquals("对象", data.recipient());
        assertEquals("备注", data.note());
        assertEquals("Herobrine", data.sourceName());
        assertEquals("v2", data.skin().value());
    }
}
