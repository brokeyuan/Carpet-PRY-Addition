package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.TptoConfigManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 跨服传送命令：/tpto（全员，点击列表即转移）与 /tptoset（管理员，服务器列表管理）。
 *
 * <p>传送走原版 transfer 协议：向执行玩家本人下发
 * {@link ClientboundTransferPacket}，客户端断开并连入目标服；无需客户端模组，
 * 不经过代理端。目标服必须在 server.properties 开启 accepts-transfers=true，
 * 否则客户端连接会被目标服以 transfers_disabled 踢出。</p>
 */
public class TptoCommand {

    private static final String SERVER_ARG = "server";
    private static final String ADDRESS_ARG = "address";
    private static final String PORT_ARG = "port";
    private static final String NAME_ARG = "name";
    private static final String DISPLAY_NAME_ARG = "displayName";
    private static final int MAX_PORT = 65535;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // === /tpto <server> — 玩家传送命令（规则关闭时整棵命令不可见）===
            LiteralArgumentBuilder<CommandSourceStack> tptoRoot = Commands.literal("tpto")
                    .requires(source -> CarpetPrimaryuanSettings.serverTransfer);
            // /tpto <server> — 传送（支持内部名或显示名；列表点击即执行此命令）
            tptoRoot.then(Commands.argument(SERVER_ARG, StringArgumentType.greedyString())
                    .suggests(TptoCommand::suggestServers)
                    .executes(TptoCommand::transfer));
            // /tpto（无参数）— 列出可转移服务器（点击传送），替代 Brigadier 原生 usage 报错
            tptoRoot.executes(TptoCommand::listServers);
            dispatcher.register(tptoRoot);

            // === /tptoset — 服务器列表管理命令（规则关闭或非管理员时整棵不可见）===
            LiteralArgumentBuilder<CommandSourceStack> setRoot = Commands.literal("tptoset")
                    .requires(source -> CarpetPrimaryuanSettings.serverTransfer && CommandSupport.isAdmin(source));

            // /tptoset — 展示已配置服务器（每行可点击删除）与用法提示
            setRoot.executes(TptoCommand::manageList);

            // /tptoset add <address> <port> <name> [displayName] — 添加服务器
            // address 用可引号字符串参数：裸写主机名，含冒号的 IPv6 字面地址加引号
            setRoot.then(Commands.literal("add")
                    .then(Commands.argument(ADDRESS_ARG, StringArgumentType.string())
                            .then(Commands.argument(PORT_ARG, IntegerArgumentType.integer(1, MAX_PORT))
                                    .then(Commands.argument(NAME_ARG, StringArgumentType.word())
                                            .then(Commands.argument(DISPLAY_NAME_ARG, StringArgumentType.greedyString())
                                                    .executes(TptoCommand::addServer))
                                            .executes(TptoCommand::addServer)))));

            // /tptoset remove <name> — 删除服务器（内部名或显示名）
            setRoot.then(Commands.literal("remove")
                    .then(Commands.argument(SERVER_ARG, StringArgumentType.greedyString())
                            .suggests(TptoCommand::suggestServers)
                            .executes(TptoCommand::removeServer)));

            // /tptoset reload — 重新加载配置文件（手改 tpto.json 后生效）
            setRoot.then(Commands.literal("reload")
                    .executes(TptoCommand::reload));

            dispatcher.register(setRoot);
        });
    }

    /**
     * /tpto（无参数）- 列出全部可转移服务器，每项是一个可点击组件，
     * 点击执行 /tpto &lt;内部名&gt;（RUN_COMMAND），hover 显示 host:port。
     */
    private static int listServers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<TptoConfigManager.ServerView> views = TptoConfigManager.getServerViews();
        if (views.isEmpty()) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpto.list_empty"), false);
            return 1;
        }

        MutableComponent message = Component.empty()
                .append(ServerI18n.tr("carpetprimaryuan.command.tpto.list_header"));
        for (TptoConfigManager.ServerView view : views) {
            message = message.append(Component.literal("\n")).append(clickableServer(view));
        }
        final MutableComponent finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
        return 1;
    }

    /**
     * 单个可点击服务器条目：显示名下划线，点击执行 /tpto &lt;内部名&gt;，
     * hover 提示目标地址（host:port），普通玩家可据此确认指向。
     */
    private static MutableComponent clickableServer(TptoConfigManager.ServerView view) {
        String label = ServerI18n.tr("carpetprimaryuan.command.tpto.list_entry", view.displayName()).getString();
        String command = "/tpto " + view.name();
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(runCommand(command))
                .withHoverEvent(showText(ServerI18n.tr(
                        "carpetprimaryuan.command.tpto.list_entry_hover", view.host(), view.port())))
                .withUnderlined(true));
    }

    /**
     * /tpto &lt;server&gt; - 以执行玩家本人的身份转移（requires 已保证 serverTransfer=true）。
     * 永远只转移命令来源玩家，不提供指定他人的形式。
     */
    private static int transfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;

        String input = context.getArgument(SERVER_ARG, String.class);
        String internalName = TptoConfigManager.getInternalName(input);
        TptoConfigManager.ServerEntry server = internalName == null ? null : TptoConfigManager.getServer(internalName);
        if (server == null) {
            List<String> names = TptoConfigManager.getDisplayNames();
            if (names.isEmpty()) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.list_empty"));
            } else {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.not_found", input,
                        String.join(", ", names)));
            }
            return 0;
        }

        // 原版 transfer 协议：服务端下发目标地址，客户端断开并连入目标服
        player.connection.send(new ClientboundTransferPacket(server.host(), server.port()));
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpto.transferring",
                TptoConfigManager.getDisplayName(internalName)), false);
        return 1;
    }

    /**
     * /tptoset（无参数）- 展示已配置服务器（显示名、内部名、地址）与用法提示；
     * 每行尾部 [删除] 可点击，点击执行 /tptoset remove &lt;内部名&gt;。
     */
    private static int manageList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<TptoConfigManager.ServerView> views = TptoConfigManager.getServerViews();
        if (views.isEmpty()) {
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.list_empty"), false);
            return 1;
        }

        MutableComponent message = Component.empty()
                .append(ServerI18n.tr("carpetprimaryuan.command.tptoset.list_header"));
        for (TptoConfigManager.ServerView view : views) {
            message = message.append(Component.literal("\n"))
                    .append(ServerI18n.tr("carpetprimaryuan.command.tptoset.entry",
                            view.displayName(), view.name(), view.host(), view.port()))
                    .append(deleteButton(view));
        }
        message = message.append(Component.literal("\n"))
                .append(ServerI18n.tr("carpetprimaryuan.command.tptoset.list_usage"));
        final MutableComponent finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
        return 1;
    }

    /** 管理列表中每行的 [删除] 按钮：点击执行 /tptoset remove &lt;内部名&gt; */
    private static MutableComponent deleteButton(TptoConfigManager.ServerView view) {
        String label = ServerI18n.tr("carpetprimaryuan.command.tptoset.entry_delete").getString();
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(runCommand("/tptoset remove " + view.name()))
                .withHoverEvent(showText(ServerI18n.tr(
                        "carpetprimaryuan.command.tptoset.entry_delete_hover", view.displayName()))));
    }

    /**
     * /tptoset add &lt;address&gt; &lt;port&gt; &lt;name&gt; [displayName] - 添加服务器。
     * 内部名重名拒绝（修改须先 remove 再 add）；displayName 缺省回退内部名。
     */
    private static int addServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        String address = context.getArgument(ADDRESS_ARG, String.class);
        int port = context.getArgument(PORT_ARG, Integer.class);
        String name = context.getArgument(NAME_ARG, String.class);

        // 可选 displayName：按命令树是否实际匹配到该节点判断（替代 try-catch 探测）
        boolean hasDisplayName = context.getNodes().stream()
                .anyMatch(node -> DISPLAY_NAME_ARG.equals(node.getNode().getName()));
        String displayName = hasDisplayName
                ? context.getArgument(DISPLAY_NAME_ARG, String.class).trim() : null;
        if (displayName != null && displayName.isEmpty()) {
            displayName = null;
        }

        if (!TptoConfigManager.addServer(name, address, port, displayName)) {
            String existingDisplay = TptoConfigManager.getDisplayName(name);
            final String existing = existingDisplay;
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.exists",
                    name, existing, name));
            return 0;
        }

        final String finalDisplay = displayName;
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.added",
                finalDisplay == null ? name : finalDisplay, address, port), false);
        return 1;
    }

    /**
     * /tptoset remove &lt;name&gt; - 删除服务器（内部名或显示名均可）。
     */
    private static int removeServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        String input = context.getArgument(SERVER_ARG, String.class);
        String internalName = TptoConfigManager.getInternalName(input);
        if (internalName == null) {
            List<String> names = TptoConfigManager.getDisplayNames();
            if (names.isEmpty()) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.list_empty"));
            } else {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.not_found", input,
                        String.join(", ", names)));
            }
            return 0;
        }

        String displayName = TptoConfigManager.getDisplayName(internalName);
        TptoConfigManager.removeServer(internalName);
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.removed", displayName), false);
        return 1;
    }

    /**
     * /tptoset reload - 从盘重读 tpto.json（手改文件后无需重启）。
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TptoConfigManager.reload(source.getServer());
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_success",
                TptoConfigManager.count()), false);
        return 1;
    }

    /** 自动补全：全部服务器显示名 */
    private static CompletableFuture<Suggestions> suggestServers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSupport.suggestMatching(builder, TptoConfigManager.getDisplayNames());
        return builder.buildFuture();
    }

    // ===== 聊天点击组件（跨版本分界：1.21.5 起 ClickEvent/HoverEvent 改为 record 式） =====

    private static ClickEvent runCommand(String command) {
        //#if MC < 12105
        //$$ return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#else
        return new ClickEvent.RunCommand(command);
        //#endif
    }

    private static HoverEvent showText(Component text) {
        //#if MC < 12105
        //$$ return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //#else
        return new HoverEvent.ShowText(text);
        //#endif
    }
}
