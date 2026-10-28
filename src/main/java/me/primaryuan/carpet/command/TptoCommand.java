package me.primaryuan.carpet.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.TptoAddress;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 跨服传送命令：/tpto（全员，点击列表即转移）与 /tptoset（管理员，服务器列表管理）。
 *
 * <p>传送走原版 transfer 协议：向执行玩家本人下发
 * {@link ClientboundTransferPacket}，客户端断开并连入目标服；无需客户端模组，
 * 不经过代理端。目标服必须在 server.properties 开启 accepts-transfers=true，
 * 否则客户端连接会被目标服以 transfers_disabled 踢出。</p>
 *
 * <p>一条服务器记录对应一个地址和端口（v3）。目标按英文标识或展示名定位，
 * 补全插入英文标识；地址:端口为单个参数，由 greedy 参数 + 纯解析拆分，
 * 不引入需要客户端注册的自定义参数类型。</p>
 */
public class TptoCommand {

    private static final String SERVER_ARG = "server";
    private static final String ARGS_ARG = "args";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // === /tpto — 玩家传送命令（规则关闭时整棵命令不可见）===
            LiteralArgumentBuilder<CommandSourceStack> tptoRoot = Commands.literal("tpto")
                    .requires(source -> CarpetPrimaryuanSettings.serverTransfer);

            // /tpto <server> — 传送（标识或展示名；greedy 使裸中文/含空格展示名可直接输入）
            tptoRoot.then(Commands.argument(SERVER_ARG, StringArgumentType.greedyString())
                    .suggests(TptoCommand::suggestTargets)
                    .executes(TptoCommand::transfer));

            // /tpto（无参数）— 列出可转移服务器
            tptoRoot.executes(TptoCommand::listServers);
            dispatcher.register(tptoRoot);

            // === /tptoset — 服务器管理命令（规则关闭或非管理员时整棵不可见）===
            LiteralArgumentBuilder<CommandSourceStack> setRoot = Commands.literal("tptoset")
                    .requires(source -> CarpetPrimaryuanSettings.serverTransfer && CommandSupport.isAdmin(source));

            // /tptoset — 展示已配置服务器（每行可点击删除）
            setRoot.executes(TptoCommand::manageList);

            // /tptoset addserver <地址:端口> <标识> [展示名] — 添加服务器（一服一地址）
            setRoot.then(Commands.literal("addserver")
                    .then(Commands.argument(ARGS_ARG, StringArgumentType.greedyString())
                            .executes(TptoCommand::addServer)));

            // /tptoset removeserver <服务器> — 删除整条记录（标识或展示名）
            setRoot.then(Commands.literal("removeserver")
                    .then(Commands.argument(SERVER_ARG, StringArgumentType.greedyString())
                            .suggests(TptoCommand::suggestTargets)
                            .executes(TptoCommand::removeServer)));

            // /tptoset reload — 重新加载配置文件（手改 tpto.json 后生效）
            setRoot.then(Commands.literal("reload")
                    .executes(TptoCommand::reload));

            dispatcher.register(setRoot);
        });
    }

    // ===== 玩家：列表与传送 =====

    /**
     * /tpto（无参数）- 列出全部可转移服务器；名称可点击传送，悬停显示地址与端口。
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
            message = message.append(Component.literal("\n")).append(serverRow(view));
        }
        final MutableComponent finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
        return 1;
    }

    /** 单个服务器行：名称点击即传送，悬停显示目标地址:端口（IPv6 带方括号） */
    private static MutableComponent serverRow(TptoConfigManager.ServerView view) {
        String label = ServerI18n.tr("carpetprimaryuan.command.tpto.list_entry", view.displayName()).getString();
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(runCommand("/tpto " + view.name()))
                .withHoverEvent(showText(ServerI18n.tr(
                        "carpetprimaryuan.command.tpto.list_entry_hover",
                        TptoAddress.format(view.host(), view.port()))))
                .withUnderlined(true));
    }

    /**
     * /tpto &lt;server&gt; - 以执行玩家本人的身份转移（requires 已保证 serverTransfer=true）。
     * 永远只转移命令来源玩家，不提供指定他人的形式；控制台不可执行。
     */
    private static int transfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;

        String raw = context.getArgument(SERVER_ARG, String.class);
        String target = parseTarget(raw);
        if (target == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.invalid_target", raw));
            return 0;
        }
        if (target.isEmpty()) {
            return listServers(context);
        }
        String internalName = TptoConfigManager.resolveInternalName(target);
        TptoConfigManager.ServerEntry server = internalName == null
                ? null : TptoConfigManager.getServer(internalName);
        if (server == null) {
            sendServerNotFound(source, target);
            return 0;
        }

        // 原版 transfer 协议：服务端下发目标地址，客户端断开并连入目标服
        player.connection.send(new ClientboundTransferPacket(server.host(), server.port()));
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpto.transferring",
                TptoConfigManager.getDisplayName(internalName),
                TptoAddress.format(server.host(), server.port())), false);
        return 1;
    }

    // ===== 管理：服务器列表 =====

    /**
     * /tptoset（无参数）- 展示已配置服务器；每行尾部 [删除] 可点击。
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
                            view.displayName(), view.name(),
                            TptoAddress.format(view.host(), view.port())))
                    .append(Component.literal(" "))
                    .append(deleteButton(view));
        }
        message = message.append(Component.literal("\n"))
                .append(ServerI18n.tr("carpetprimaryuan.command.tptoset.list_usage"));
        final MutableComponent finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
        return 1;
    }

    /** 管理列表中服务器行的 [删除] 按钮：点击执行 /tptoset removeserver &lt;标识&gt; */
    private static MutableComponent deleteButton(TptoConfigManager.ServerView view) {
        String label = ServerI18n.tr("carpetprimaryuan.command.tptoset.entry_delete").getString();
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(runCommand("/tptoset removeserver " + view.name()))
                .withHoverEvent(showText(ServerI18n.tr(
                        "carpetprimaryuan.command.tptoset.entry_delete_hover", view.displayName()))));
    }

    /**
     * /tptoset addserver &lt;地址:端口&gt; &lt;标识&gt; [展示名] - 添加服务器（一服一地址）。
     * 标识或展示名与现有记录忽略大小写重名时拒绝（修改须先 removeserver 再 addserver）。
     */
    private static int addServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        AddArgs args = parseAddArgs(context.getArgument(ARGS_ARG, String.class));
        if (args == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.add_usage"));
            return 0;
        }
        TptoAddress address;
        try {
            address = TptoAddress.parse(args.address());
        } catch (IllegalArgumentException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.invalid_address",
                    args.address(), e.getMessage()));
            return 0;
        }
        if (!TptoConfigManager.isValidId(args.id())) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.invalid_id", args.id()));
            return 0;
        }

        TptoConfigManager.MutationStatus status = TptoConfigManager.addServer(
                args.id(), address.host(), address.port(), args.displayName());
        switch (status) {
            case OK -> {
                String display = TptoConfigManager.getDisplayName(args.id());
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.added",
                        display, address.format()), false);
                return 1;
            }
            case DUPLICATE_ID -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.exists",
                    args.id(), args.id()));
            case NAME_CONFLICT -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.name_conflict",
                    args.displayName() == null ? args.id() : args.displayName()));
            case WRITE_PROTECTED -> source.sendFailure(
                    ServerI18n.tr("carpetprimaryuan.command.tptoset.write_protected"));
            default -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.save_failed"));
        }
        return 0;
    }

    /**
     * /tptoset removeserver &lt;服务器&gt; - 按标识或展示名删除整条记录。
     */
    private static int removeServer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        String raw = context.getArgument(SERVER_ARG, String.class);
        String target = parseTarget(raw);
        if (target == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.invalid_target", raw));
            return 0;
        }
        String internalName = TptoConfigManager.resolveInternalName(target);
        if (internalName == null) {
            sendServerNotFound(source, target);
            return 0;
        }

        String displayName = TptoConfigManager.getDisplayName(internalName);
        TptoConfigManager.MutationStatus status = TptoConfigManager.removeServer(internalName);
        switch (status) {
            case OK -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.removed",
                        displayName), false);
                return 1;
            }
            case WRITE_PROTECTED -> source.sendFailure(
                    ServerI18n.tr("carpetprimaryuan.command.tptoset.write_protected"));
            default -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.save_failed"));
        }
        return 0;
    }

    /**
     * /tptoset reload - 从盘重读 tpto.json（手改文件后无需重启）。
     * 任何失败保留现有列表并暂停写入，修复文件并成功 reload 后恢复。
     */
    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TptoConfigManager.ReloadResult result = TptoConfigManager.reload();
        switch (result.status()) {
            case OK -> {
                source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_success",
                        TptoConfigManager.count()), false);
                return 1;
            }
            case MISSING -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_missing"));
            case READ_FAILED -> source.sendFailure(
                    ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_read_failed"));
            case FUTURE_VERSION -> source.sendFailure(
                    ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_future"));
            default -> source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tptoset.reload_invalid",
                    result.detail() == null ? "" : result.detail()));
        }
        return 0;
    }

    // ===== 共用 =====

    /** 服务器未找到的统一失败提示（列表为空时给空态引导） */
    private static void sendServerNotFound(CommandSourceStack source, String input) {
        List<String> names = TptoConfigManager.getDisplayNames();
        if (names.isEmpty()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.list_empty"));
        } else {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpto.not_found", input,
                    String.join(", ", names)));
        }
    }

    /** /tpto 与 removeserver 的目标补全：标识或展示名前缀匹配（忽略大小写），插入英文标识 */
    private static CompletableFuture<Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (TptoConfigManager.ServerView view : matchTargets(builder.getRemaining(),
                TptoConfigManager.getServerViews())) {
            builder.suggest(view.name(), Component.literal(view.displayName()));
        }
        return builder.buildFuture();
    }

    /** 目标前缀匹配（纯函数）：标识或有效展示名按 Locale.ROOT 忽略大小写，返回匹配记录 */
    static List<TptoConfigManager.ServerView> matchTargets(String prefix,
                                                           List<TptoConfigManager.ServerView> views) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<TptoConfigManager.ServerView> matched = new ArrayList<>();
        for (TptoConfigManager.ServerView view : views) {
            if (view.name().toLowerCase(Locale.ROOT).startsWith(needle)
                    || view.displayName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                matched.add(view);
            }
        }
        return matched;
    }

    /**
     * 解析 /tpto 与 removeserver 的尾部目标（纯函数）：裸中文、含空格展示名直接可用；
     * 接受包围整个目标的合法双引号并解码（含转义），未闭合或引号后有多余内容返回 null。
     */
    static String parseTarget(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"")) {
            StringReader reader = new StringReader(trimmed);
            try {
                String decoded = reader.readString();
                if (reader.canRead()) {
                    return null;
                }
                return decoded;
            } catch (CommandSyntaxException e) {
                return null;
            }
        }
        return trimmed;
    }

    /**
     * 拆分 addserver 的 greedy 参数（纯函数）：地址词、英文标识词、剩余展示文本。
     * 展示名缺省为 null；缺地址或缺标识返回 null。
     */
    static AddArgs parseAddArgs(String raw) {
        if (raw == null) {
            return null;
        }
        int i = 0;
        int n = raw.length();
        while (i < n && isWhitespace(raw.charAt(i))) i++;
        int addressStart = i;
        while (i < n && !isWhitespace(raw.charAt(i))) i++;
        if (i == addressStart) {
            return null;
        }
        String address = raw.substring(addressStart, i);
        while (i < n && isWhitespace(raw.charAt(i))) i++;
        int idStart = i;
        while (i < n && !isWhitespace(raw.charAt(i))) i++;
        if (i == idStart) {
            return null;
        }
        String id = raw.substring(idStart, i);
        String display = raw.substring(i).trim();
        return new AddArgs(address, id, display.isEmpty() ? null : display);
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    /** addserver 参数拆分结果；displayName 为 null 表示省略（展示回退标识） */
    record AddArgs(String address, String id, String displayName) {}

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
