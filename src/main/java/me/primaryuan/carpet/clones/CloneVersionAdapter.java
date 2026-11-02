package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerLevel;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 版本敏感 API 适配（//#if 分支集中在此，主逻辑零分支）。
 * 阈值矩阵全部 javap 字节码核实（★★★）：
 * <ul>
 *   <li>teleportTo：1.21 = 7 参 (Set&lt;RelativeMovement&gt;)；1.21.3+ = 8 参
 *       (Set&lt;Relative&gt;, boolean)；26.x 同 1.21.3（teleportTo 未再变）</li>
 *   <li>位置广播：1.21 = new ClientboundTeleportEntityPacket(Entity)；
 *       1.21.3+ = record，静态工厂 teleport(int, PositionMoveRotation, Set, boolean)；
 *       26.1.2+ = ClientboundEntityPositionSyncPacket.of(Entity)</li>
 *   <li>Inventory items：1.21~1.21.8 = public 字段；1.21.10+ = private +
 *       getNonEquipmentItems()</li>
 *   <li>GameProfile：authlib 6（1.21~1.21.10）= getName()；authlib 9+
 *       （1.21.11+）= name()</li>
 *   <li>loadPlayerData：26.x = PlayerList.loadPlayerData(NameAndId)+ValueInput；
 *       1.21.x = PlayerList.load(ServerPlayer)+Entity.load(CompoundTag)</li>
 * </ul>
 */
public final class CloneVersionAdapter {

    private CloneVersionAdapter() {}

    /** spawn 序列里的落位 */
    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        //#if MC >= 12103
        player.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, pitch, true);
        //#else
        //$$ player.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), yaw, pitch);
        //#endif
    }

    /** 位置/朝向广播 */
    public static void broadcastPosition(net.minecraft.server.MinecraftServer server, ServerPlayer player, ServerLevel level) {
        //#if MC >= 260102
        server.getPlayerList().broadcastAll(
                net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.of(player),
                level.dimension());
        //#elseif MC >= 12103
        //$$ server.getPlayerList().broadcastAll(
        //$$         net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket.teleport(
        //$$                 player.getId(),
        //$$                 net.minecraft.world.entity.PositionMoveRotation.of(player),
        //$$                 java.util.Set.of(),
        //$$                 true),
        //$$         level.dimension());
        //#else
        //$$ server.getPlayerList().broadcastAll(
        //$$         new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(player),
        //$$         level.dimension());
        //#endif
    }

    /** Inventory 非装备槽 items 列表（1.21.5 起 private + getter） */
    public static net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> itemsOf(net.minecraft.world.entity.player.Inventory inv) {
        //#if MC >= 12105
        return inv.getNonEquipmentItems();
        //#else
        //$$ return inv.items;
        //#endif
    }

    /** GameProfile 名字（authlib 9+（1.21.10+）record name()，6.x getName()） */
    public static String profileName(com.mojang.authlib.GameProfile profile) {
        //#if MC >= 12110
        return profile.name();
        //#else
        //$$ return profile.getName();
        //#endif
    }

    /** placeNewPlayer 后读回 playerdata；失败返回 false，调用方撤销本次创建 */
    public static boolean loadPlayerData(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        //#if MC >= 12110
        net.minecraft.util.ProblemReporter.ScopedCollector collector =
                new net.minecraft.util.ProblemReporter.ScopedCollector(
                        player.problemPath(), carpet.CarpetSettings.LOG);
        try {
            java.util.Optional<net.minecraft.nbt.CompoundTag> tag =
                    server.getPlayerList().loadPlayerData(player.nameAndId());
            if (tag.isPresent()) {
                net.minecraft.world.level.storage.ValueInput input =
                        net.minecraft.world.level.storage.TagValueInput.create(
                                collector, player.registryAccess(), tag.get());
                player.load(input);
                player.loadAndSpawnEnderPearls(input);
                player.loadAndSpawnParentVehicle(input);
            }
            return true;
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
                    .warn("[pry:clones] loadPlayerData failed for {}", profileName(player.getGameProfile()), t);
            return false;
        } finally {
            collector.close();
        }
        //#elseif MC >= 12108
        //$$ try {
        //$$     server.getPlayerList().load(player, net.minecraft.util.ProblemReporter.DISCARDING)
        //$$             .ifPresent(player::load);
        //$$     return true;
        //$$ } catch (Throwable t) {
        //$$     org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
        //$$             .warn("[pry:clones] loadPlayerData failed for {}", profileName(player.getGameProfile()), t);
        //$$     return false;
        //$$ }
        //#else
        //$$ try {
        //$$     server.getPlayerList().load(player).ifPresent(player::load);
        //$$     return true;
        //$$ } catch (Throwable t) {
        //$$     org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
        //$$             .warn("[pry:clones] loadPlayerData failed for {}", profileName(player.getGameProfile()), t);
        //$$     return false;
        //$$ }
        //#endif
    }

    /** Inventory selected 槽位（26.x/1.21.5+ getSelectedSlot；1.21~1.21.4 selected 字段） */
    public static int selectedSlot(net.minecraft.world.entity.player.Inventory inv) {
        //#if MC >= 12105
        return inv.getSelectedSlot();
        //#else
        //$$ return inv.selected;
        //#endif
    }

    /** selected 槽换物（1.21.5+ setSelectedItem；1.21~1.21.4 无此 API，返回 null 由调用方跳过） */
    public static net.minecraft.world.item.ItemStack setSelectedItem(net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.item.ItemStack stack) {
        //#if MC >= 12105
        return inv.setSelectedItem(stack);
        //#else
        //$$ return null;
        //#endif
    }

    /** Tab 排序值（1.21.3+ getTabListOrder；1.21 恒 0） */
    public static int tabListOrder(net.minecraft.server.level.ServerPlayer player) {
        //#if MC >= 12103
        return player.getTabListOrder();
        //#else
        //$$ return 0;
        //#endif
    }

    /** 空属性 profile */
    public static GameProfile newProfile(java.util.UUID id, String name) {
        return new GameProfile(id, name);
    }

    /**
     * 复制为带完整属性的新 profile。authlib 7+ 的 PropertyMap 构造器会把内容
     * 快照为 ImmutableMultimap（创建后不可修改），必须先填好再构造 GameProfile。
     */
    public static GameProfile copyProfile(GameProfile source, java.util.UUID id, String name) {
        //#if MC >= 12110
        com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> map =
                com.google.common.collect.HashMultimap.create();
        map.putAll(source.properties());
        return new GameProfile(id, name,
                new com.mojang.authlib.properties.PropertyMap(map));
        //#else
        //$$ GameProfile profile = new GameProfile(id, name);
        //$$ profile.getProperties().putAll(source.getProperties());
        //$$ return profile;
        //#endif
    }

    /** textures 属性值/签名；无则 null（快照持久化皮肤用） */
    public static String[] profileTexture(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        //#if MC >= 12110
        var props = profile.properties().get("textures");
        //#else
        //$$ var props = profile.getProperties().get("textures");
        //#endif
        if (props == null || props.isEmpty()) {
            return null;
        }
        var prop = props.iterator().next();
        return new String[]{prop.value(), prop.signature()};
    }

    /** 用快照中的 textures 重建 profile（重启后首个成员皮肤） */
    public static GameProfile profileWithTexture(java.util.UUID id, String name,
                                                 String value, String signature) {
        if (value == null) {
            return newProfile(id, name);
        }
        //#if MC >= 12110
        com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> map =
                com.google.common.collect.HashMultimap.create();
        map.put("textures", new com.mojang.authlib.properties.Property("textures", value, signature));
        return new GameProfile(id, name,
                new com.mojang.authlib.properties.PropertyMap(map));
        //#else
        //$$ GameProfile profile = new GameProfile(id, name);
        //$$ profile.getProperties().put("textures",
        //$$         new com.mojang.authlib.properties.Property("textures", value, signature));
        //$$ return profile;
        //#endif
    }

    /** GameProfile id（26.x id()，1.21.x getId()） */
    public static java.util.UUID profileId(GameProfile profile) {
        //#if MC >= 12110
        return profile.id();
        //#else
        //$$ return profile.getId();
        //#endif
    }

    /** kill 实体（1.21.3+ 带 level 参，1.21~1.21.2 无参） */
    public static void killEntity(net.minecraft.world.entity.LivingEntity entity) {
        //#if MC >= 12103
        entity.kill((net.minecraft.server.level.ServerLevel) entity.level());
        //#else
        //$$ entity.kill();
        //#endif
    }

    /** source 权限 >= OP（26.x PermissionSet，1.21.x hasPermission(2)） */
    public static boolean isGameMaster(net.minecraft.commands.CommandSourceStack source) {
        //#if MC >= 260102
        return net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        //#else
        //$$ return source.hasPermission(2);
        //#endif
    }
}
