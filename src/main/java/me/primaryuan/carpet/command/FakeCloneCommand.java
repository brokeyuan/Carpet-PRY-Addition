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
import me.primaryuan.carpet.clones.CloneVersionAdapter;
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
        var root = Commands.literal("fakeclone")
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
                                .executes(FakeCloneCommand::kill)
                                .then(Commands.argument("cloneId", IntegerArgumentType.integer(1))
                                        .executes(FakeCloneCommand::killOne))))
                .then(Commands.literal("rejoin")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(FakeCloneCommand::rejoin)))
                .then(Commands.literal("select")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("cloneId", IntegerArgumentType.integer(1))
                                        .executes(FakeCloneCommand::select))))
                .then(Commands.literal("action")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        ACTIONS.keySet(), b))
                                .executes(ctx -> actionGroup(ctx, StringArgumentType.getString(ctx, "action")))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> actionOne(ctx,
                                                StringArgumentType.getString(ctx, "action"),
                                                StringArgumentType.getString(ctx, "name"),
                                                null))
                                        .then(Commands.argument("cloneId", IntegerArgumentType.integer(1))
                                                .executes(ctx -> actionOne(ctx,
                                                        StringArgumentType.getString(ctx, "action"),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "cloneId")))))));
        return root;
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
        if (CloneGroupRegistry.byName(name) == null) {
            java.util.UUID uuid = CloneSpawnManager.resolveUuid(source.getServer(), name);
            if (CloneGroupRegistry.isOnlineRealPlayer(source.getServer(), uuid)) {
                source.sendFailure(Component.literal("[clones] spawn " + name + " failed: real player online"));
                return 0;
            }
            CloneGroupRegistry.getOrCreate(uuid, name);
            me.primaryuan.carpet.clones.CloneAllowlist.allow(uuid, name);
            // 首建组交给 Carpet：异步解析完整 profile（含皮肤属性）
            boolean queued = carpet.patches.EntityPlayerMPFake.createFake(name, source.getServer(),
                    pos, 0.0, 0.0, level.dimension(), net.minecraft.world.level.GameType.SURVIVAL, false);
            if (queued) {
                source.sendSuccess(() -> Component.literal("[clones] spawning first clone of " + name
                        + " (profile resolving)"), false);
                return 1;
            }
            source.sendFailure(Component.literal("[clones] spawn " + name + " failed"));
            return 0;
        }
        ServerPlayer created = CloneSpawnManager.spawnClone(source.getServer(), name, level, pos, 0f, 0f);
        if (created == null) {
            source.sendFailure(Component.literal("[clones] spawn " + name
                    + " failed (real player online, exiting, or internal error)"));
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
        if (n < 0) {
            source.sendFailure(Component.literal("[clones] kill " + name
                    + " failed: state=" + g.state() + " (snapshot/playerdata save failed or already exiting)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[clones] killed " + n + " member(s) of " + name), false);
        return 1;
    }

    /** 指定单个分身正常退出（组与共享库存继续存在） */
    private static int killOne(CommandContext<CommandSourceStack> ctx) {
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
        if (CloneSpawnManager.exitMember(source.getServer(), g, target) < 0) {
            source.sendFailure(Component.literal("[clones] cannot exit " + name + "#" + cloneId
                    + " (group exiting or member gone)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[clones] requested exit for " + name + "#" + cloneId), false);
        return 1;
    }

    // ===== 动作（复用 Carpet EntityPlayerActionPack；单体与整组共用一套实现） =====

    /** 动作名 -> 执行器 */
    private static final java.util.Map<String, java.util.function.UnaryOperator<ServerPlayer>> ACTIONS = buildActions();

    private static java.util.Map<String, java.util.function.UnaryOperator<ServerPlayer>> buildActions() {
        java.util.Map<String, java.util.function.UnaryOperator<ServerPlayer>> m = new java.util.LinkedHashMap<>();
        var api = new Object() {
            carpet.helpers.EntityPlayerActionPack pack(ServerPlayer p) {
                return ((carpet.fakes.ServerPlayerInterface) p).getActionPack();
            }
        };
        m.put("stop", p -> { api.pack(p).stopAll(); return p; });
        m.put("drop", p -> { api.pack(p).drop(CloneVersionAdapter.selectedSlot(p.getInventory()), false); return p; });
        m.put("dropstack", p -> { api.pack(p).drop(CloneVersionAdapter.selectedSlot(p.getInventory()), true); return p; });
        m.put("swaphands", p -> { api.pack(p).start(carpet.helpers.EntityPlayerActionPack.ActionType.SWAP_HANDS, carpet.helpers.EntityPlayerActionPack.Action.once()); return p; });
        m.put("jump", p -> { api.pack(p).start(carpet.helpers.EntityPlayerActionPack.ActionType.JUMP, carpet.helpers.EntityPlayerActionPack.Action.once()); return p; });
        m.put("use", p -> { api.pack(p).start(carpet.helpers.EntityPlayerActionPack.ActionType.USE, carpet.helpers.EntityPlayerActionPack.Action.once()); return p; });
        m.put("attack", p -> { api.pack(p).start(carpet.helpers.EntityPlayerActionPack.ActionType.ATTACK, carpet.helpers.EntityPlayerActionPack.Action.once()); return p; });
        m.put("sneak", p -> { api.pack(p).setSneaking(true); return p; });
        m.put("unsneak", p -> { api.pack(p).setSneaking(false); return p; });
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            m.put("slot" + (i + 1), p -> { api.pack(p).setSlot(idx + 1); return p; });
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    private static void op(ServerPlayer p, String action) {
        var op = ACTIONS.get(action);
        if (op != null) {
            op.apply(p);
        }
    }

    private static int applyAction(CommandSourceStack source, String action, ServerPlayer p, int cloneId) {
        op(p, action);
        final int eid = p.getId();
        source.sendSuccess(() -> Component.literal("[clones] action " + action + " -> "
                + CloneVersionAdapter.profileName(p.getGameProfile()) + "#" + cloneId
                + " (entityId=" + eid + ")"), true);
        return 1;
    }

    /** 整组（全部组的全部在线成员）按 cloneId 顺序执行同一动作（同 Tick 顺序结算，共享库存随之顺序更新） */
    private static int actionGroup(CommandContext<CommandSourceStack> ctx, String action) {
        CommandSourceStack source = ctx.getSource();
        if (!ACTIONS.containsKey(action)) {
            source.sendFailure(Component.literal("[clones] unknown action: " + action
                    + " (available: " + String.join(", ", ACTIONS.keySet()) + ")"));
            return 0;
        }
        int n = 0;
        StringBuilder ids = new StringBuilder();
        for (CloneGroup g : CloneGroupRegistry.all()) {
            for (ServerPlayer p : g.onlineMembers()) {
                op(p, action);
                ids.append('#').append(CloneInstanceRegistry.cloneIdOf(p)).append(' ');
                n++;
            }
        }
        if (n == 0) {
            source.sendFailure(Component.literal("[clones] no online clones"));
            return 0;
        }
        final String done = ids.toString();
        source.sendSuccess(() -> Component.literal("[clones] action " + action
                + " -> group members: " + done), true);
        return 1;
    }

    /** name（可选 cloneId）执行动作：单体精确操作入口 */
    private static int actionOne(CommandContext<CommandSourceStack> ctx, String action, String name, Integer cloneId) {
        CommandSourceStack source = ctx.getSource();
        CloneGroup g = CloneGroupRegistry.byName(name);
        if (g == null) {
            source.sendFailure(Component.literal("[clones] unknown group " + name));
            return 0;
        }
        if (!ACTIONS.containsKey(action)) {
            source.sendFailure(Component.literal("[clones] unknown action: " + action
                    + " (available: " + String.join(", ", ACTIONS.keySet()) + ")"));
            return 0;
        }
        if (cloneId == null) {
            int n = 0;
            for (ServerPlayer p : g.onlineMembers()) {
                op(p, action);
                n++;
            }
            final int done = n;
            source.sendSuccess(() -> Component.literal("[clones] action " + action + " -> "
                    + name + " (" + done + " member(s))"), true);
            return done > 0 ? 1 : 0;
        }
        ServerPlayer target = g.instance(cloneId);
        if (target == null) {
            source.sendFailure(Component.literal("[clones] " + name + "#" + cloneId + " not online"));
            return 0;
        }
        return applyAction(source, action, target, cloneId);
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
            source.sendFailure(Component.literal("[clones] rejoin " + name + " blocked: " + reason(n)));
            return 0;
        }
        final int restored = n;
        final int total = snap.members.size();
        source.sendSuccess(() -> Component.literal("[clones] rejoined " + name
                + " (" + restored + "/" + total + ")"), false);
        return restored > 0 ? 1 : 0;
    }

    private static String reason(int code) {
        return switch (code) {
            case -1 -> "real player online";
            case -2 -> "group online or exiting";
            case -3 -> "member creation failed (this attempt rolled back)";
            case -4 -> "invalid snapshot data or missing dimension";
            default -> "error " + code;
        };
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
