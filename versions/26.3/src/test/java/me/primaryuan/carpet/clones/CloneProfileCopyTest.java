package me.primaryuan.carpet.clones;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F08 回归：显示 profile 必须保留完整签名属性。authlib 7+ 的 PropertyMap
 * 构造器会快照为不可变集合，必须先填好再构造 GameProfile。
 */
class CloneProfileCopyTest {

    @Test
    void copyProfileKeepsTexturesAndName() {
        UUID real = UUID.nameUUIDFromBytes("clone-profile-real".getBytes());
        UUID display = UUID.nameUUIDFromBytes("clone-profile-display".getBytes());
        Multimap<String, Property> properties = HashMultimap.create();
        properties.put("textures", new Property("textures", "skin-value", "skin-signature"));
        GameProfile source = new GameProfile(real, "Source", new PropertyMap(properties));

        GameProfile copy = CloneVersionAdapter.copyProfile(source, display, "Copy");

        assertEquals(display, CloneVersionAdapter.profileId(copy));
        assertEquals("Copy", CloneVersionAdapter.profileName(copy));
        assertEquals("skin-value",
                copy.properties().get("textures").iterator().next().value());
        assertEquals("skin-signature",
                copy.properties().get("textures").iterator().next().signature());
    }
}
