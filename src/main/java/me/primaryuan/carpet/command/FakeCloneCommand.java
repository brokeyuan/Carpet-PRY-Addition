package me.primaryuan.carpet.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import me.primaryuan.carpet.clones.CloneInstanceRegistry;
import me.primaryuan.carpet.clones.CloneSnapshotManager;
import me.primaryuan.carpet.clones.CloneSpawnManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /fakeclone：分身组管理命令（fakePlayerClones 规则开启才可见）。
 *
 * <p>list：列出全部组与在线成员。
 * status &lt;name&gt;：单组详情（含 cloneId/EntityId/位置）。
 * spawn &lt;name&gt;：增量生成一个分身。
 * kill &lt;name&gt;：整组退出。
 * select &lt;name&gt; &lt;cloneId&gt;：把执行者视角锁定到指定分身（输出其位置），
 * 供假人脚本将来按实例定位。</p>
 */
public final class FakeCloneCommand {

    private FakeCloneCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(build());
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fakeclone")
                .requires(s -> me.primaryuan.carpet.clones.CloneVersionAdapter.isGameMaster(s)
                        && CarpetPrimaryuanSettings.fakePlayerClones)
                .then(Commands.literal("list").executes(FakeCloneCommand::list))
                .then(Commands.literal("status")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FakeCloneCommand::status)))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FakeCloneCommand::spawn)))
                .then(Commands.literal("kill")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FakeCloneCommand::kill)))
                .then(Commands.literal("rejoin")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FakeCloneCommand::rejoin)))
                .then(Commands.literal("select")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("cloneId", IntegerArgumentType.integer(1))
                                        .executes(FakeCloneCommand::select))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<CloneGroup> groups = CloneGroupRegistry.all();
        if (groups.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[clones] no groups"), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[clones] groups:\n");
        for (CloneGroup g : groups) {
            sb.append("  ").append(g.name()).append(" uuid=").append(g.uuid())
                    .append(" online=").append(g.onlineCount()).append('\n');
        }
        String out = sb.toString();
        source.sendSuccess(() -> Component.literal(out), false);
        return groups.size();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g == null) {
            source.sendFailure(Component.literal("[clones] unknown group " + name));
            return 0;
        }
        StringBuilder sb = new StringBuilder("[clones] ").append(g.name())
                .append(" uuid=").append(g.uuid())
                .append(" online=").append(g.onlineCount()).append('\n');
        for (ServerPlayer p : g.onlineMembers()) {
            int cid = CloneInstanceRegistry.cloneIdOf(p);
            sb.append("  #").append(cid)
                    .append(" entityId=").append(p.getId())
                    .append(" pos=(").append(String.format("%.1f, %.1f, %.1f", p.getX(), p.getY(), p.getZ())).append(')')
                    .append(" dim=").append(p.level().dimension().identifier()).append('\n');
        }
        String out = sb.toString();
        source.sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        ServerLevel level = source.getLevel();
        var pos = source.getPosition();
        ServerPlayer created = CloneSpawnManager.spawnClone(source.getServer(), name, level, pos, 0f, 0f);
        if (created == null) {
            source.sendFailure(Component.literal("[clones] spawn " + name + " failed (real player online or internal error)"));
            return 0;
        }
        int cid = CloneInstanceRegistry.cloneIdOf(created);
        source.sendSuccess(() -> Component.literal("[clones] spawned " + name + "#" + cid
                + " entityId=" + created.getId()), false);
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g == null || g.onlineCount() == 0) {
            source.sendFailure(Component.literal("[clones] no online members in " + name));
            return 0;
        }
        int n = CloneSpawnManager.killGroup(source.getServer(), g);
        source.sendSuccess(() -> Component.literal("[clones] killed " + n + " member(s) of " + name), false);
        return 1;
    }

    private static int rejoin(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        CloneSnapshotManager.GroupSnapshot snap = CloneSnapshotManager.load(source.getServer(), name);
        if (snap == null) {
            source.sendFailure(Component.literal("[clones] no snapshot for " + name));
            return 0;
        }
        int n = CloneSnapshotManager.rejoin(source.getServer(), snap);
        if (n < 0) {
            source.sendFailure(Component.literal("[clones] rejoin " + name + " blocked: real player online"));
            return 0;
        }
        final int restored = n;
        final int total = snap.members.size();
        source.sendSuccess(() -> Component.literal("[clones] rejoined " + name
                + " (" + restored + "/" + total + ")"), false);
        return restored > 0 ? 1 : 0;
    }

    private static int select(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");
        int cloneId = IntegerArgumentType.getInteger(ctx, "cloneId");
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g == null) {
            source.sendFailure(Component.literal("[clones] unknown group " + name));
            return 0;
        }
        ServerPlayer target = g.instance(cloneId);
        if (target == null) {
            source.sendFailure(Component.literal("[clones] " + name + "#" + cloneId + " not online"));
            return 0;
        }
        final int eid = target.getId();
        final String dim = target.level().dimension().identifier().toString();
        final String pos = String.format("%.1f, %.1f, %.1f", target.getX(), target.getY(), target.getZ());
        source.sendSuccess(() -> Component.literal("[clones] selected " + name + "#" + cloneId
                + " entityId=" + eid + " dim=" + dim + " pos=(" + pos + ")"), false);
        return 1;
    }
}
