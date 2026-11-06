package me.primaryuan.carpet.handler.playerHead;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 皮肤纹理快照的提取与重建。 */
class HeadSkinTest {

    @Test void profileWithoutTexturesIsRejected() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Notch", new PropertyMap(LinkedHashMultimap.create()));
        assertNull(HeadSkin.fromProfile(profile));
    }

    @Test void textureAndSignatureSurviveRoundTrip() {
        UUID id = UUID.randomUUID();
        // authlib 10 的 PropertyMap 在构造时复制为不可变视图，先填好多重映射。
        LinkedHashMultimap<String, Property> map = LinkedHashMultimap.create();
        map.put("textures", new Property("textures", "value-1", "sig-1"));
        GameProfile profile = new GameProfile(id, "Notch", new PropertyMap(map));

        HeadSkin skin = HeadSkin.fromProfile(profile);
        assertNotNull(skin);
        assertTrue(skin.hasTexture());
        assertEquals(id, skin.id());
        assertEquals("Notch", skin.name());
        assertEquals("value-1", skin.value());
        assertEquals("sig-1", skin.signature());

        GameProfile rebuilt = skin.gameProfile();
        Property texture = rebuilt.properties().get("textures").iterator().next();
        assertEquals("value-1", texture.value());
        assertEquals("sig-1", texture.signature());
        assertEquals("Notch", rebuilt.name());
        assertEquals(id, rebuilt.id());
    }

    @Test void resolvableProfileKeepsSnapshot() {
        HeadSkin skin = new HeadSkin(UUID.randomUUID(), "Notch", "value-2", null);
        var profile = skin.resolvable();
        Property texture = profile.partialProfile().properties().get("textures").iterator().next();
        assertEquals("value-2", texture.value());
        assertFalse(texture.hasSignature());
    }
}
