package me.primaryuan.carpet.handler.playerHead;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

/**
 * 一次查询得到的皮肤纹理快照。
 *
 * <p>只保存 name/id 与 textures 属性的 value/signature：纪念头颅据此离线重建
 * 原版 PROFILE，后续显示与放置都不再查询账号最新皮肤。</p>
 */
public record HeadSkin(UUID id, String name, String value, String signature) {

    public boolean hasTexture() {
        return id != null && value != null && !value.isEmpty();
    }

    /** 从已解析档案提取纹理；不含 textures 时返回 null（调用方按无皮肤处理）。 */
    public static HeadSkin fromProfile(GameProfile profile) {
        if (profile == null) return null;
        //#if MC >= 12110
        UUID id = profile.id();
        String name = profile.name();
        var textures = profile.properties().get("textures");
        //#else
        //$$ UUID id = profile.getId();
        //$$ String name = profile.getName();
        //$$ var textures = profile.getProperties().get("textures");
        //#endif
        if (id == null || textures.isEmpty()) return null;
        Property property = textures.iterator().next();
        if (property.value() == null || property.value().isEmpty()) return null;
        return new HeadSkin(id, name == null ? "" : name, property.value(), property.signature());
    }

    /** 构造带该纹理的已解析原版档案。 */
    public GameProfile gameProfile() {
        //#if MC >= 12110
        // authlib 7+ 的 PropertyMap 构造时复制为不可变视图，必须先填好多重映射。
        com.google.common.collect.LinkedHashMultimap<String, Property> map =
                com.google.common.collect.LinkedHashMultimap.create();
        map.put("textures", textureProperty());
        return new GameProfile(id, name, new PropertyMap(map));
        //#else
        //$$ GameProfile profile = new GameProfile(id, name);
        //$$ profile.getProperties().put("textures", textureProperty());
        //$$ return profile;
        //#endif
    }

    /** 该版本可直接写入 PROFILE 组件的已解析档案。 */
    public ResolvableProfile resolvable() {
        //#if MC >= 12110
        return ResolvableProfile.createResolved(gameProfile());
        //#else
        //$$ return new ResolvableProfile(gameProfile());
        //#endif
    }

    private Property textureProperty() {
        return signature == null || signature.isEmpty()
                ? new Property("textures", value)
                : new Property("textures", value, signature);
    }
}
