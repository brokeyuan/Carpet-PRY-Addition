package me.primaryuan.carpet.replay.phantom;

import com.mojang.authlib.properties.Property;
import me.primaryuan.carpet.mixins.rule.playerReplay.PlayerInfoUpdateAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PlayerInfo 包构造桥（全版本同构，Entry 公开构造 + accessor 填充）。
 * 26.3 Entry 9 参（showHat/listOrder），1.21~1.21.10 Entry 7 参 —— 预处理分支。
 */
final class PhantomPacketBridge
{
    private PhantomPacketBridge() {}

    static Packet<?> playerInfoAdd(String uuid, String name, String textures, String signature)
    {
        com.mojang.authlib.properties.PropertyMap props = pry$newProps(textures, signature);
        com.mojang.authlib.GameProfile profile = pry$profile(uuid, name, props);
        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                java.util.EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of());
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(1);
        entries.add(buildEntry(UUID.fromString(uuid), profile));
        PlayerInfoUpdateAccessor acc = (PlayerInfoUpdateAccessor) (Object) packet;
        acc.pry$setEntries(entries);
        acc.pry$setActions(java.util.EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER));
        return packet;
    }

    private static ClientboundPlayerInfoUpdatePacket.Entry buildEntry(
            UUID uuid, com.mojang.authlib.GameProfile profile)
    {
        //#if MC >= 12104
        return new ClientboundPlayerInfoUpdatePacket.Entry(
                uuid, profile, false, 0, GameType.SURVIVAL, (Component) null, true, 0, null);
        //#elseif MC >= 12103
        //$$ return new ClientboundPlayerInfoUpdatePacket.Entry(
        //$$         uuid, profile, false, 0, GameType.SURVIVAL, (Component) null, 0, null);
        //#else
        //$$ return new ClientboundPlayerInfoUpdatePacket.Entry(
        //$$         uuid, profile, false, 0, GameType.SURVIVAL, (Component) null, null);
        //#endif
    }

    static Packet<?> playerInfoRemove(String uuid)
    {
        return new ClientboundPlayerInfoRemovePacket(List.of(UUID.fromString(uuid)));
    }

    /**
     * 构造皮肤属性表。authlib 10（1.21.11+）的 PropertyMap(Multimap) 内部 ImmutableMultimap.copyOf
     * ——拿到即不可变，必须先在 HashMultimap 放好内容再包装；authlib 6（1.21~1.21.1）无参构造可变。
     */
    private static com.mojang.authlib.properties.PropertyMap pry$newProps(String textures, String signature)
    {
        com.google.common.collect.HashMultimap<String, com.mojang.authlib.properties.Property> raw =
                com.google.common.collect.HashMultimap.create();
        if (textures != null && !textures.isEmpty())
        {
            // 签名非空时三参 Property（客户端 26.x 按 secure 皮肤接受带 Mojang 签名的属性）
            if (signature != null && !signature.isEmpty())
            {
                raw.put("textures", new Property("textures", textures, signature));
            }
            else
            {
                raw.put("textures", new Property("textures", textures));
            }
        }
        //#if MC >= 12110
        return new com.mojang.authlib.properties.PropertyMap(raw);
        //#else
        //$$ com.mojang.authlib.properties.PropertyMap props = new com.mojang.authlib.properties.PropertyMap();
        //$$ props.putAll(raw);
        //$$ return props;
        //#endif
    }

    private static com.mojang.authlib.GameProfile pry$profile(
            String uuid, String name, com.mojang.authlib.properties.PropertyMap props)
    {
        //#if MC >= 12110
        return new com.mojang.authlib.GameProfile(UUID.fromString(uuid), name, props);
        //#else
        //$$ com.mojang.authlib.GameProfile p = new com.mojang.authlib.GameProfile(UUID.fromString(uuid), name);
        //$$ p.getProperties().putAll(props);
        //$$ return p;
        //#endif
    }
}
