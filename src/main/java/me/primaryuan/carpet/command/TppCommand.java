package me.primaryuan.carpet.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.TppConfigManager;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.FakePlayerSessionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TppCommand {

    private static final String STATION_ARG = "station";
    private static final String PLAYER_ARG = "player";
    private static final String ALIAS_ARG = "alias";
    private static final String DISPLAY_NAME_ARG = "displayName";
    private static final String RULE_STATION_ARG = "stationName";
    /** 别名最大长度（别名本应短小；构建假人名时还会按站点长度动态截断） */
    private static final int MAX_BASE_NAME_LENGTH = 10;
    /** Minecraft 玩家名/GameProfile 名上限（也是站点内部名上限：站点必须完整保留） */
    private static final int MAX_FAKE_NAME_LENGTH = 16;

    /** resolveStation 的解析结果：站点内部名 / 构建好的假人名 / 显示名 */
    private record StationRequest(String station, String fakePlayerName, String displayName) {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // === /tpp <station> — 玩家传送命令（规则关闭时整棵命令不可见）===
            LiteralArgumentBuilder<CommandSourceStack> tppRoot = Commands.literal("tpp")
                    .requires(source -> CarpetPrimaryuanSettings.TppFakePlayer);
            // /tpp <station> — 传送（支持中文显示名或英文内部名）
            tppRoot.then(Commands.argument(STATION_ARG, StringArgumentType.greedyString())
                    .suggests(TppCommand::suggestStations)
                    .executes(TppCommand::teleportToStation));
            dispatcher.register(tppRoot);

            // === /tppset — 站点管理与规则配置命令（规则关闭时整棵命令不可见）===
            LiteralArgumentBuilder<CommandSourceStack> setRoot = Commands.literal("tppset")
                    .requires(source -> CarpetPrimaryuanSettings.TppFakePlayer);

            // /tppset spawn <station> — 设置假人生成点（继承父级 TppFakePlayer 可见性）
            setRoot.then(Commands.literal("spawn")
                    .then(Commands.argument(STATION_ARG, StringArgumentType.greedyString())
                            .suggests(TppCommand::suggestStations)
                            .executes(TppCommand::setSpawnFakePlayer)));

            // /tppset set <name> [displayName] — 添加站点（管理员专属）
            setRoot.then(Commands.literal("set")
                    .requires(CommandSupport::isAdmin)
                    .then(Commands.argument(STATION_ARG, StringArgumentType.word())
                            .then(Commands.argument(DISPLAY_NAME_ARG, StringArgumentType.greedyString())
                                    .executes(TppCommand::addStationWithDisplay))
                            .executes(TppCommand::addStation)));

            // /tppset rename <playerName> [set <alias>|remove]（管理员专属）
            setRoot.then(Commands.literal("rename")
                    .requires(CommandSupport::isAdmin)
                    .then(Commands.argument(PLAYER_ARG, StringArgumentType.word())
                            .suggests(CommandSupport::suggestOnlinePlayers)
                            .then(Commands.literal("remove")
                                    .executes(TppCommand::removePlayerAlias))
                            .then(Commands.literal("set")
                                    .then(Commands.argument(ALIAS_ARG, StringArgumentType.word())
                                            .executes(TppCommand::renamePlayer)))));

            // /tppset remove <station>（管理员专属）
            setRoot.then(Commands.literal("remove")
                    .requires(CommandSupport::isAdmin)
                    .then(Commands.argument(STATION_ARG, StringArgumentType.greedyString())
                            .suggests(TppCommand::suggestStations)
                            .executes(TppCommand::removeStation)));

            // /tppset rule [use <count> [station]] — 查看/设置右键次数（管理员专属，无站点=全局，有站点=站点级）
            setRoot.then(Commands.literal("rule")
                    .requires(CommandSupport::isAdmin)
                    .then(Commands.literal("use")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                    .executes(TppCommand::setUseCount)
                                    .then(Commands.argument(RULE_STATION_ARG, StringArgumentType.greedyString())
                                            .suggests(TppCommand::suggestStations)
                                            .executes(TppCommand::setUseCount))))
                    .executes(TppCommand::showRules));

            dispatcher.register(setRoot);
        });
    }

    /**
     * 构建假人名（总长 ≤ {@link #MAX_FAKE_NAME_LENGTH}），空间不足时按优先级取舍：
     * 站点完整保留（必须）→ "_" 分隔符（无玩家名时不保留）→ 玩家名尽可能多（按剩余空间截断）。
     * 站点过长时玩家名部分为 0 字符，该站点所有玩家共用同一个假人名（即站点名本身）。
     *
     * @param playerName 玩家真实名
     * @param station    地区名
     * @return 假人名，格式: {基础名}_{地区}；站点占满时为 {地区}
     */
    static String buildFakePlayerName(String playerName, String station) {
        // 步骤1: 别名优先
        String baseName = TppConfigManager.getAlias(playerName);
        if (baseName == null) {
            baseName = playerName;
        }

        // 步骤2: 按站点长度动态截断玩家名（预留 1 位给 "_"；站点占满时玩家名 0 字符）
        int baseBudget = MAX_FAKE_NAME_LENGTH - station.length() - 1;
        if (baseBudget <= 0) {
            return station;
        }
        if (baseName.length() > baseBudget) {
            baseName = baseName.substring(0, baseBudget);
        }

        // 步骤3: 拼接
        return baseName + "_" + station;
    }

    /**
     * 站点内部名长度守卫：超长时向来源发送提示并返回 true。
     * 站点必须完整保留在假人名内，因此站点名本身最长即假人名上限。
     */
    private static boolean stationNameTooLong(CommandSourceStack source, String name) {
        if (name.length() > MAX_FAKE_NAME_LENGTH) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.tpp.station_name_too_long", name, MAX_FAKE_NAME_LENGTH));
            return true;
        }
        return false;
    }

    /**
     * /tpp 与 /tppset spawn 的公共前导：解析站点 → 会话冲突检查 → 构建假人名（含长度兜底守卫）。
     *
     * @return 解析结果；任一步失败时已向来源发送提示并返回 null
     */
    private static StationRequest resolveStation(CommandSourceStack source, ServerPlayer player, String input) {
        // 解析输入（可能是内部名或显示名）为内部名
        String station = TppConfigManager.getInternalName(input);
        if (station == null) {
            player.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.command.tpp.station_not_found", input,
                    String.join(", ", TppConfigManager.getDisplayNames())));
            return null;
        }

        // 同一玩家同时只允许一个进行中的假人操作
        if (FakePlayerSessionManager.hasActiveSession(player)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.operation_in_progress"));
            return null;
        }

        String fakePlayerName = buildFakePlayerName(CommandSupport.profileName(player), station);
        // 兜底守卫：旧配置中的长站点名/别名可能拼出超过上限的假人名
        if (fakePlayerName.length() > MAX_FAKE_NAME_LENGTH) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.tpp.fake_player_name_too_long", fakePlayerName, MAX_FAKE_NAME_LENGTH));
            return null;
        }
        return new StationRequest(station, fakePlayerName, TppConfigManager.getDisplayName(station));
    }

    /**
     * /tpp <station> - 玩家传送到指定站点（requires 已保证 TppFakePlayer=true）
     * 流程（tick 状态机，见 {@link FakePlayerSessionManager}）: rejoin → 等待上线 → use×N → 3秒 → kill
     */
    private static int teleportToStation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        StationRequest request = resolveStation(source, player, context.getArgument(STATION_ARG, String.class));
        if (request == null) return 0;

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.teleporting", request.displayName(), request.fakePlayerName()), false);

        // 使用站点级右键次数配置，未设置则使用全局默认值
        int totalUses = TppConfigManager.getUseCount(request.station());
        FakePlayerSessionManager.startTeleport(source.getServer(), player,
                request.fakePlayerName(), request.displayName(), totalUses);

        return 1;
    }

    /**
     * /tppset spawn <station> - 立即以玩家身份生成假人，3 秒后自动下线（tick 状态机）
     * requires 已保证 TppFakePlayer=true
     */
    private static int setSpawnFakePlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();

        StationRequest request = resolveStation(source, player, context.getArgument(STATION_ARG, String.class));
        if (request == null) return 0;

        FakePlayerSessionManager.startSpawn(source.getServer(), player, request.fakePlayerName());
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.fake_player_spawned", request.fakePlayerName()), false);

        return 1;
    }

    /**
     * /tppset set <name> - 添加传送站点（无备注）
     */
    private static int addStation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        String name = context.getArgument(STATION_ARG, String.class);
        if (stationNameTooLong(source, name)) return 0;

        if (!TppConfigManager.addStation(name, null)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.station_exists", name, TppConfigManager.getDisplayName(name)));
            return 0;
        }

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.station_added", name), false);
        return 1;
    }

    /**
     * /tppset set <name> <displayName> - 添加传送站点（带显示名称/备注）
     */
    private static int addStationWithDisplay(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        String name = context.getArgument(STATION_ARG, String.class);
        if (stationNameTooLong(source, name)) return 0;
        String displayName = context.getArgument(DISPLAY_NAME_ARG, String.class);

        if (!TppConfigManager.addStation(name, displayName)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.station_exists", name, TppConfigManager.getDisplayName(name)));
            return 0;
        }

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.station_added_with_display", name, displayName), false);
        return 1;
    }

    /**
     * /tppset remove <name> - 删除传送站点（支持内部名或显示名）
     */
    private static int removeStation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        String input = context.getArgument(STATION_ARG, String.class);
        String internalName = TppConfigManager.getInternalName(input);

        if (internalName == null) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.station_not_exists", input));
            return 0;
        }

        TppConfigManager.removeStation(internalName);

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.station_removed", internalName), false);
        return 1;
    }

    /**
     * /tppset rename <playerName> set <alias> - 为玩家设置假人传送别名
     */
    private static int renamePlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        String playerName = context.getArgument(PLAYER_ARG, String.class);
        String alias = context.getArgument(ALIAS_ARG, String.class);

        if (playerName.isEmpty()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.player_name_empty"));
            return 0;
        }
        if (alias.isEmpty()) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.alias_empty"));
            return 0;
        }

        // 别名取保守固定上限（别名本应短小）；总长 ≤ 假人名上限由 buildFakePlayerName 按站点长度动态保证
        if (alias.length() > MAX_BASE_NAME_LENGTH) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.alias_too_long", MAX_BASE_NAME_LENGTH));
            return 0;
        }

        TppConfigManager.setAlias(playerName, alias);

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.alias_set", playerName, alias, alias), false);
        return 1;
    }

    /**
     * /tppset rename <playerName> remove - 移除玩家别名
     */
    private static int removePlayerAlias(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        String playerName = context.getArgument(PLAYER_ARG, String.class);

        if (!TppConfigManager.removeAlias(playerName)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.no_alias", playerName));
            return 0;
        }

        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.alias_removed", playerName), false);
        return 1;
    }

    /**
     * /tppset rule use <count> [station] - 设置假人右键次数
     * 无 station 参数：设置全局次数
     * 有 station 参数：设置站点级次数
     */
    private static int setUseCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.admin_only"));
            return 0;
        }

        int count = context.getArgument("count", Integer.class);

        // 可选站点参数：按命令树是否实际匹配到该节点判断（替代 try-catch 探测）
        boolean hasStation = context.getNodes().stream()
                .anyMatch(node -> RULE_STATION_ARG.equals(node.getNode().getName()));
        String stationInput = hasStation ? context.getArgument(RULE_STATION_ARG, String.class) : null;

        if (stationInput != null && !stationInput.isEmpty()) {
            // 站点级设置
            String station = TppConfigManager.getInternalName(stationInput);
            if (station == null) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.tpp.station_not_exists", stationInput));
                return 0;
            }
            TppConfigManager.setStationUseCount(station, count);
            String displayName = TppConfigManager.getDisplayName(station);
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.use_count_set_station", displayName, count), false);
        } else {
            // 全局设置
            TppConfigManager.setGlobalUseCount(count);
            source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.tpp.use_count_set_global", count), false);
        }
        return 1;
    }

    /**
     * /tppset rule - 查看当前 TPP 规则配置
     */
    private static int showRules(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MutableComponent message = Component.empty()
                .append(ServerI18n.tr("carpetprimaryuan.command.tpp.rules_header"))
                .append(Component.literal("\n"))
                .append(ServerI18n.tr("carpetprimaryuan.command.tpp.rules_global_count", TppConfigManager.getGlobalUseCount()))
                .append(Component.literal("\n"));
        Map<String, Integer> stationCounts = TppConfigManager.getStationUseCountSnapshot();
        if (!stationCounts.isEmpty()) {
            message = message.append(ServerI18n.tr("carpetprimaryuan.command.tpp.rules_station_counts")).append(Component.literal("\n"));
            for (Map.Entry<String, Integer> entry : stationCounts.entrySet()) {
                String displayName = TppConfigManager.getDisplayName(entry.getKey());
                message = message.append(ServerI18n.tr("carpetprimaryuan.command.tpp.rules_station_entry", displayName, entry.getValue())).append(Component.literal("\n"));
            }
        } else {
            message = message.append(ServerI18n.tr("carpetprimaryuan.command.tpp.rules_no_station_counts")).append(Component.literal("\n"));
        }
        final MutableComponent finalMessage = message;
        source.sendSuccess(() -> finalMessage, false);
        return 1;
    }

    /**
     * 自动补全：从 TppConfigManager 获取所有站点显示名称
     */
    private static CompletableFuture<Suggestions> suggestStations(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSupport.suggestMatching(builder, TppConfigManager.getDisplayNames());
        return builder.buildFuture();
    }
}
