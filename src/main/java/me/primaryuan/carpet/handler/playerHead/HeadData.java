package me.primaryuan.carpet.handler.playerHead;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * PRY 头颅档案：保存在原版 {@code minecraft:custom_data} 的 {@code pry_head}
 * 子标签中，不依赖 Lore。写入时只替换本模组子标签，保留其它自定义数据。
 */
public final class HeadData {

    public enum Kind {
        UNIVERSAL("universal"),
        MEMORIAL("memorial");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) return kind;
            }
            return null;
        }
    }

    private static final String ROOT = "pry_head";
    private static final int FORMAT = 1;
    private static final String KEY_FORMAT = "v";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_SOURCE_ID = "source_id";
    private static final String KEY_TEXTURE = "texture";
    private static final String KEY_SIGNATURE = "signature";

    private final Kind kind;
    private String sourceName;
    private UUID sourceId;
    private String textureValue;
    private String textureSignature;

    public HeadData(Kind kind) {
        this.kind = kind;
    }

    public static HeadData universal() {
        return new HeadData(Kind.UNIVERSAL);
    }

    public Kind kind() {
        return kind;
    }

    public String sourceName() {
        return sourceName == null ? "" : sourceName;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public boolean hasTexture() {
        return sourceId != null && textureValue != null && !textureValue.isEmpty();
    }

    public HeadSkin skin() {
        return new HeadSkin(sourceId, sourceName(), textureValue, textureSignature);
    }

    /** 记录查询成功后的皮肤快照；同名换肤直接覆盖旧快照。 */
    public void applySkin(HeadSkin skin) {
        if (skin == null) return;
        this.sourceName = skin.name();
        this.sourceId = skin.id();
        this.textureValue = skin.value();
        this.textureSignature = skin.signature();
    }

    public static HeadData read(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : read(stack.get(DataComponents.CUSTOM_DATA));
    }

    /** 方块实体同样通过组件表保存 custom_data，掉落扩展直接读组件表。 */
    public static HeadData read(CustomData customData) {
        if (customData == null || customData.isEmpty()) return null;
        CompoundTag root = customData.copyTag();
        //#if MC >= 12105
        CompoundTag tag = root.getCompoundOrEmpty(ROOT);
        //#else
        //$$ CompoundTag tag = root.getCompound(ROOT);
        //#endif
        if (tag.isEmpty()) return null;
        if (intValue(tag, KEY_FORMAT, 0) != FORMAT) return null;
        Kind kind = Kind.byId(stringValue(tag, KEY_TYPE, ""));
        if (kind == null) return null;
        HeadData data = new HeadData(kind);
        data.sourceName = stringValue(tag, KEY_SOURCE, "");
        data.sourceId = parseUuid(stringValue(tag, KEY_SOURCE_ID, ""));
        data.textureValue = stringValue(tag, KEY_TEXTURE, "");
        data.textureSignature = stringValue(tag, KEY_SIGNATURE, "");
        if (data.textureValue.isEmpty()) data.textureValue = null;
        if (data.textureSignature.isEmpty()) data.textureSignature = null;
        return data;
    }

    public void write(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(ROOT, toTag()));
    }

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_FORMAT, FORMAT);
        tag.putString(KEY_TYPE, kind.id());
        tag.putString(KEY_SOURCE, sourceName());
        if (sourceId != null) tag.putString(KEY_SOURCE_ID, sourceId.toString());
        if (textureValue != null) tag.putString(KEY_TEXTURE, textureValue);
        if (textureSignature != null) tag.putString(KEY_SIGNATURE, textureSignature);
        return tag;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stringValue(CompoundTag tag, String key, String fallback) {
        //#if MC >= 12105
        return tag.getStringOr(key, fallback);
        //#else
        //$$ String value = tag.getString(key);
        //$$ return value.isEmpty() ? fallback : value;
        //#endif
    }

    private static int intValue(CompoundTag tag, String key, int fallback) {
        //#if MC >= 12105
        return tag.getIntOr(key, fallback);
        //#else
        //$$ return tag.contains(key) ? tag.getInt(key) : fallback;
        //#endif
    }
}
