package me.primaryuan.carpet.command;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneAllowlist;
import me.primaryuan.carpet.clones.CloneIdentity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M1 原型调试命令 /cloneprobe（fakePlayerClones 规则开启才可见）。
 *
 * <p>clone &lt;name&gt;：对指定假人身份再创建一个同 UUID 分身（放行三处
 * UUID 拦截点），并把两实例的 UUID/EntityId/位置 dump 进日志。
 * dump：列出 playersByUUID/实体查找/allowlist 三层证据。
 * killone：移除"最后一个"分身，验证另一实例不受影响。</p>
 */
public final class CloneProbeCommand {

    private CloneProbeCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(build());
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cloneprobe")
                .requires(s -> Commands.LEVEL_GAMEMASTERS.check(s.permissions()) && CarpetPrimaryuanSettings.fakePlayerClones)
                .then(Commands.literal("clone")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CloneProbeCommand::spawnClone)))
                .then(Commands.literal("dump").executes(CloneProbeCommand::dump))
                .then(Commands.literal("killone")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CloneProbeCommand::killOne)));
    }

    /** 对已有假人名再创建一个同 UUID 分身 */
    private static int spawnClone(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        MinecraftServer server = source.getServer();

        // 找该名字当前的任一在线实例（首个即主实例）作为身份来源
        ServerPlayer existing = server.getPlayerList().getPlayerByName(name);
        if (existing == null) {
            // 尚无实例：先创建第一分身（走 Carpet createFake 原路径）
            CloneAllowlist.allow(resolveUuid(server, name), name);
            Vec3 pos = source.getPosition();
            boolean ok = EntityPlayerMPFake.createFake(name, server, pos, 0f, 0f,
                    source.getLevel().dimension(), GameType.SURVIVAL, false);
            source.sendSuccess(() -> Component.literal("[clones] first instance of " + name + ": " + ok), false);
            return ok ? 1 : 0;
        }

        UUID uuid = existing.getUUID();
        CloneAllowlist.allow(uuid, name);
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition().add(2, 0, 0); // 错开两格便于客户端分辨
        // 复刻 EntityPlayerMPFake.createFake（异常不吞，直接打栈定位 placeNewPlayer 中断点）
        try {
            GameProfile profile = new GameProfile(uuid, name);
            EntityPlayerMPFake instance = EntityPlayerMPFake.respawnFake(server, level, profile,
                    net.minecraft.server.level.ClientInformation.createDefault());
            instance.fixStartingPosition = () -> instance.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
            // placeNewPlayer 内部 connection.teleport -> ChunkMap.move 会对"旧 section"
            // 做 DistanceManager.removePlayer；新实例 lastSectionPos 恒为 (0,0,0)，
            // 该 chunk 无玩家集合 -> NPE。先在出生点注册 DistanceManager 并把
            // lastSectionPos 预设为出生 section，使 remove/add 成对净空。
            net.minecraft.core.SectionPos spawnSection = net.minecraft.core.SectionPos.of(instance);
            var chunkMap = (net.minecraft.server.level.ChunkMap) (Object) level.getChunkSource().chunkMap;
            java.lang.reflect.Field dmF = net.minecraft.server.level.ChunkMap.class.getDeclaredField("distanceManager");
            dmF.setAccessible(true);
            Object dm = dmF.get(chunkMap);
            var addP = dm.getClass().getMethod("addPlayer", net.minecraft.core.SectionPos.class, net.minecraft.server.level.ServerPlayer.class);
            addP.invoke(dm, spawnSection, instance);
            var setLsp = net.minecraft.server.level.ServerPlayer.class.getDeclaredMethod("setLastSectionPos", net.minecraft.core.SectionPos.class);
            setLsp.setAccessible(true);
            setLsp.invoke(instance, spawnSection);
            server.getPlayerList().placeNewPlayer(
                    new carpet.patches.FakeClientConnection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                    instance,
                    new net.minecraft.server.network.CommonListenerCookie(profile, 0, instance.clientInformation(), false));
            instance.stopRiding();
            instance.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), 0f, 0f, true);
            instance.setHealth(20.0F);
            var m = net.minecraft.world.entity.Entity.class.getDeclaredMethod("unsetRemoved");
            m.setAccessible(true);
            m.invoke(instance);
            instance.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(0.6F);
            instance.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(instance, (byte) 0), level.dimension());
            server.getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket.of(instance), level.dimension());
            instance.getAbilities().flying = false;
            org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
                    .info("[pry:clones][DIAG] direct clone done id={}", instance.getId());
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
                    .error("[pry:clones][DIAG] direct clone FAILED", t);
        }
        dumpInstances(source, server, name, "after clone spawn");
        return 1;
    }

    /** 三层证据 dump：PlayerList 注册 / 实体查找 / allowlist */
    private static int dump(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        dumpInstances(source, source.getServer(), "*", "manual dump");
        return 1;
    }

    /** 移除最后一个分身实例，验证其余实例不受影响 */
    private static int killOne(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        MinecraftServer server = source.getServer();
        List<ServerPlayer> same = instancesOf(server, name);
        if (same.size() < 2) {
            source.sendFailure(Component.literal("[clones] need >=2 instances, have " + same.size()));
            return 0;
        }
        ServerPlayer victim = same.get(same.size() - 1);
        int victimId = victim.getId();
        boolean nonPrimary = CloneIdentity.isNonPrimaryClone(victim);
        if (victim instanceof EntityPlayerMPFake fake) {
            fake.kill(Component.literal("cloneprobe killone"));
        }
        final int remain = same.size() - 1;
        source.sendSuccess(() -> Component.literal("[clones] killed id=" + victimId
                + " nonPrimary=" + nonPrimary + " remain=" + remain), false);
        return 1;
    }

    private static void dumpInstances(CommandSourceStack source, MinecraftServer server, String name, String tag) {
        PlayerList pl = server.getPlayerList();
        List<String> lines = new ArrayList<>();
        lines.add("=== cloneprobe dump (" + tag + ") ===");
        for (ServerPlayer p : pl.getPlayers()) {
            if (!"*".equals(name) && !p.getGameProfile().name().equalsIgnoreCase(name)) {
                continue;
            }
            boolean isFake = p instanceof EntityPlayerMPFake;
            boolean nonPrimary = isFake && CloneIdentity.isNonPrimaryClone(p);
            lines.add(String.format(
                    "player name=%s uuid=%s entityId=%d fake=%s nonPrimary=%s dim=%s pos=(%.1f, %.1f, %.1f) removed=%s conn=%s level=%s inLevel=%s",
                    p.getGameProfile().name(), p.getUUID(), p.getId(), isFake, nonPrimary,
                    p.level().dimension().identifier(),
                    p.getX(), p.getY(), p.getZ(),
                    p.getRemovalReason(), p.connection.getClass().getSimpleName(),
                    p.level().dimension().identifier(),
                    p.level().getEntity(p.getUUID()) != null));
        }
        ServerPlayer byUuidLookup = "*".equals(name) ? null
                : pl.getPlayerByName(name);
        if (byUuidLookup != null) {
            lines.add("playersByUUID-get(" + byUuidLookup.getUUID() + ") -> entityId="
                    + pl.getPlayer(byUuidLookup.getUUID()).getId());
        }
        lines.add("allowlist.size=" + CloneAllowlist.size());
        String joined = String.join("\n", lines);
        source.sendSuccess(() -> Component.literal(joined), false);
        org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan").info("[pry:clones]\n{}", joined);
    }

    private static List<ServerPlayer> instancesOf(MinecraftServer server, String name) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().name().equalsIgnoreCase(name)) {
                out.add(p);
            }
        }
        return out;
    }

    private static UUID resolveUuid(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null) {
            return p.getUUID();
        }
        return net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(name);
    }
}
