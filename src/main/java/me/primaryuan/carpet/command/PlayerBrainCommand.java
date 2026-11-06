package me.primaryuan.carpet.command;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.Translations;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.CarpetPrimaryuanServer;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.brain.BrainTeams;
import me.primaryuan.carpet.brain.PlayerBrainController;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /player &lt;name&gt; brain [mode] —— 假人脑子（AI 接管）命令。
 *
 * <p>用法：</p>
 * <ul>
 *   <li>{@code /player <name> brain}：查询当前 AI 模式与诊断四态</li>
 *   <li>{@code /player <name> brain <mode> [keep]}：挂载/切换对应脑子
 *       （19 种模式；carpet 语言为中文时可用中文模式名如 僵尸/殭屍，英文键恒可用；
 *       {@code keep} 保持脑子——假人下线重上后自动恢复；wolf 模式以命令执行者
 *       为主人做仇恨同步；allay 模式以命令执行者为默认交付对象）</li>
 *   <li>{@code /player <name> brain off}：卸载脑子，恢复 Carpet 手动控制
 *       （战斗队伍设置保留）</li>
 *   <li>{@code /player <name> brain team}：查询战斗队伍（与脑子独立，
 *       未挂脑也可设置）</li>
 *   <li>{@code /player <name> brain team <队名>}：加入或更换战斗队伍
 *       （同队假人互为友军，跨模式生效；队名支持中英文，off 为退出关键字）</li>
 *   <li>{@code /player <name> brain team off}：退出战斗队伍</li>
 * </ul>
 *
 * <p>mode/team 参数用 greedy 字符串（Brigadier 未引号参数只认 ASCII，中文模式名
 * 与中文队名必须 greedy 才能不带引号输入），解析时剥离尾部 {@code keep}、
 * 头部 {@code team} 子句；状态展示靠头顶名牌后缀（BrainManager 计分板队伍），
 * 命令回执不再复述模式名。</p>
 *
 * <p>整棵子树通过 requires 谓词受 {@link CarpetPrimaryuanSettings#fakePlayerBrain}
 * 控制可见性；规则关闭时不可见不可执行，已挂载的脑子也会在下一 tick
 * 由 BrainManager 自动卸载（战斗队伍保留）。接入方式与 dropall/sendto 一致：
 * 由 PlayerCommandExtensionsMixin 注入 Carpet 的 /player 命令树。</p>
 */
public final class PlayerBrainCommand {

    /** 可用的 AI 模式（不含 off，off 单独作为卸载语义处理） */
    private static final List<String> MODES = List.of(
            "zombie", "babyzombie", "skeleton", "witherskeleton", "drowned", "zombiepiglin",
            "pillager", "vindicator", "irongolem", "spider", "piglin", "piglinbrute",
            "slime", "magmacube", "fish", "enderman", "wolf", "villager", "pig",
            "allay", "fox", "witch",
            //#if MC >= 12110
            "coppergolem",
            //#endif
            "off");

    /** 三语模式名的 lang 键前缀 */
    private static final String MODE_KEY_PREFIX = "carpetprimaryuan.command.brain.mode_";

    /** team 子句关键字（/player <name> brain team ...，与模式名共用 greedy 参数） */
    private static final String TEAM_KEYWORD = "team";

    /**
     * 目标假人身份门（package-private 测试缝）：生产恒为 Carpet 原生假人。
     * 纯 JVM 测试无法构造可用的 EntityPlayerMPFake 子类，测试临时放行桩。
     */
    static java.util.function.Predicate<ServerPlayer> fakePlayerGate =
            p -> p instanceof EntityPlayerMPFake;

    private PlayerBrainCommand() {}

    /** 构建 brain 命令节点（挂到 /player <name> 下） */
    public static LiteralArgumentBuilder<CommandSourceStack> buildBrainNode() {
        return Commands.literal("brain")
                .requires(source -> CarpetPrimaryuanSettings.fakePlayerBrain)
                .executes(PlayerBrainCommand::showStatus)
                .then(Commands.argument("mode", StringArgumentType.greedyString())
                        .suggests(PlayerBrainCommand::suggestModes)
                        .executes(PlayerBrainCommand::setMode));
    }

    /** /player <name> brain：查询当前模式 + 诊断四态 */
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = CommandSupport.resolvePlayer(ctx);
        if (player == null) return 0;

        String name = CommandSupport.profileName(player);
        String mode = BrainManager.getModeKey(player);
        if (mode == null) {
            // 诊断态一：脑子被卸载（会话表无有效记录）
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.status_off", name), false);
            return 1;
        }
        PlayerBrainController.Diagnostics diag = BrainManager.diagnosticsOf(player);
        String modeName = ServerI18n.tr("carpetprimaryuan.command.brain.mode_" + mode).getString();
        source.sendSuccess(() -> ServerI18n.tr(
                "carpetprimaryuan.command.brain.status_on", name, modeName), false);
        if (diag != null) {
            // 诊断态二~四：无目标 / 有目标导航受阻 / 有路径但移动驻立（Goal 停滞特征）
            String targetPart = diag.targetName() == null
                    ? ServerI18n.tr("carpetprimaryuan.command.brain.diag_no_target").getString()
                    : ServerI18n.tr("carpetprimaryuan.command.brain.diag_target",
                            diag.targetName(), diag.targetDistance().intValue()).getString();
            String navPart = diag.navStuck()
                    ? ServerI18n.tr("carpetprimaryuan.command.brain.diag_nav_stuck").getString()
                    : diag.navInProgress()
                            ? ServerI18n.tr("carpetprimaryuan.command.brain.diag_nav_progress").getString()
                            : ServerI18n.tr("carpetprimaryuan.command.brain.diag_nav_idle").getString();
            String movePart = ServerI18n.tr(
                    "carpetprimaryuan.command.brain.diag_move_" + diag.moveOp().toLowerCase()).getString();
            String crashPart = diag.consecutiveCrashes() > 0
                    ? ServerI18n.tr("carpetprimaryuan.command.brain.diag_crash",
                            diag.consecutiveCrashes()).getString()
                    : "";
            String finalModeName = modeName;
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.diag_line",
                    finalModeName, targetPart, navPart, movePart, crashPart), false);
        }
        return 1;
    }

    /** team 子句解析结果：teamName == null 表示查询（{@code brain team}） */
    record TeamClause(boolean leave, String teamName) {
    }

    /**
     * team 子句解析（可测缝）：{@code team} → 查询；{@code team <队名>} →
     * 加入或更换（off 由 {@link #setTeam} 判为退出）；其余输入返回 null 走模式解析。
     * 关键字大小写不敏感；队名首尾空白收敛。
     */
    static TeamClause parseTeamClause(String raw) {
        if (raw.equalsIgnoreCase(TEAM_KEYWORD)) {
            return new TeamClause(false, null);
        }
        String prefix = TEAM_KEYWORD + " ";
        if (raw.length() > prefix.length()
                && raw.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return new TeamClause(false, raw.substring(prefix.length()).trim());
        }
        return null;
    }

    /**
     * 命令层默认主人：wolf 用执行者做仇恨同步主人；allay 用执行者做默认
     * 交付对象（keep 补挂复用同一 UUID）。其他模式无主人；控制台执行时
     * owner 为 null——狼在调用前已拒绝，悦灵则等待有效音符盒。
     */
    static UUID defaultOwnerUuid(String mode, ServerPlayer executor) {
        if (!"wolf".equals(mode) && !"allay".equals(mode)) {
            return null;
        }
        return executor != null ? executor.getUUID() : null;
    }

    /** /player <name> brain <mode> [keep]：挂载/切换/卸载；team 子句在此分流 */
    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = CommandSupport.resolvePlayer(ctx);
        if (player == null) return 0;

        String name = CommandSupport.profileName(player);
        String raw = StringArgumentType.getString(ctx, "mode").trim();
        // team 子句（与模式名共用 greedy 参数）：team / team <队名> / team off
        TeamClause team = parseTeamClause(raw);
        if (team != null) {
            if (team.teamName() == null) {
                return showTeam(source, player, name);
            }
            return setTeam(source, player, name, team.teamName());
        }
        // greedy 参数吞下整行：剥离尾部 keep 选项（模式名本身无空格）
        boolean keep = false;
        int space = raw.lastIndexOf(' ');
        if (space > 0 && raw.substring(space + 1).equalsIgnoreCase("keep")) {
            keep = true;
            raw = raw.substring(0, space).trim();
        }
        String mode = resolveMode(raw);
        if (mode == null) {
            // 未知模式名（含"当前语言没有该别名"，如英文语言下输中文名）：
            // 先行失败返回，避免 null 进 attach 的 switch
            if (!fakePlayerGate.test(player)) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.not_fake", name));
                return 0;
            }
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.unknown_mode", raw, modeList()));
            return 0;
        }

        // 目标必须是 Carpet 原生假人：真人没有 actionPack 接管语义
        if (!fakePlayerGate.test(player)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.not_fake", name));
            return 0;
        }

        if ("off".equals(mode)) {
            if (BrainManager.detach(player)) {
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.brain.detached", name), true);
                return 1;
            }
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.no_brain", name));
            return 0;
        }

        // 狼模式需要一名真人玩家作为主人（仇恨同步的来源）；控制台无法提供。
        // 悦灵也把执行者作为默认交付对象，但控制台挂脑允许无主人：此时只对
        // 有效音符盒交付，无点则等待（规格口径）。
        ServerPlayer owner = ctx.getSource().getPlayer();
        if ("wolf".equals(mode) && owner == null) {
            source.sendFailure(ServerI18n.tr(
                    "carpetprimaryuan.command.brain.need_owner_player"));
            return 0;
        }
        UUID ownerUuid = defaultOwnerUuid(mode, owner);

        try {
            String attached = BrainManager.attach(player, mode, ownerUuid, keep);
            if (attached == null) {
                source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.unknown_mode", raw, modeList()));
                return 0;
            }
            // 状态展示靠头顶名牌后缀（[僵尸] 等，颜色按敌对/中立/不敌对），回执一行带执行者
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.attached", source.getTextName(), name), true);
            // 创造模式的假人不会被任何 AI 索敌、也无法索敌玩家（与原版生物的
            // 目标可见性一致）——最常见的"挂了脑子没反应"原因，主动提示
            if (player.isCreative()) {
                source.sendSystemMessage(ServerI18n.tr(
                        "carpetprimaryuan.command.brain.creative_hint", name));
            }
            return 1;
        } catch (Throwable t) {
            // 挂载失败必须可见：RCON 等来源会把异常吞成一句"unexpected error"，
            // 这里回显异常类型与首帧，并把完整堆栈写进服务器日志
            CarpetPrimaryuanServer.LOGGER.error("brain attach failed for {} ({})", name, mode, t);
            StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
            source.sendFailure(Component.literal("§cbrain attach failed: " + t
                    + (top != null ? " @ " + top : "")));
            return 0;
        }
    }

    /**
     * /player <name> brain team：查询战斗队伍（与脑子独立，未挂脑也可设置）
     */
    private static int showTeam(CommandSourceStack source, ServerPlayer player, String name) {
        if (!fakePlayerGate.test(player)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.not_fake", name));
            return 0;
        }
        String team = BrainTeams.teamOf(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.team_none", name), false);
        } else {
            source.sendSuccess(() -> ServerI18n.tr(
                    "carpetprimaryuan.command.brain.team_status", name, team), false);
        }
        return 1;
    }

    /**
     * /player <name> brain team <队名>：加入或更换；team off：退出。
     * 改队后 BrainTeams 立即撤销同队间既有的非法目标（不重挂脑）。
     */
    private static int setTeam(CommandSourceStack source, ServerPlayer player, String name, String teamName) {
        if (!fakePlayerGate.test(player)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.not_fake", name));
            return 0;
        }
        if (teamName.equalsIgnoreCase(BrainTeams.OFF_KEYWORD)) {
            if (BrainTeams.leave(player.getUUID())) {
                source.sendSuccess(() -> ServerI18n.tr(
                        "carpetprimaryuan.command.brain.team_left", name), true);
                return 1;
            }
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.team_not_in", name));
            return 0;
        }
        if (!BrainTeams.isValidName(teamName)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.brain.team_invalid"));
            return 0;
        }
        BrainTeams.join(player.getUUID(), teamName);
        source.sendSuccess(() -> ServerI18n.tr(
                "carpetprimaryuan.command.brain.team_joined", name, teamName), true);
        return 1;
    }

    /**
     * 模式名解析：接受英文键与当前语言的三语显示名（carpet 语言为中文时
     * 可直接输 僵尸/殭屍 等）；off 在 MODES 表内统一处理。未知返回 null。
     */
    private static String resolveMode(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        if (MODES.contains(raw)) {
            return raw;
        }
        for (String mode : MODES) {
            if (Translations.tr(MODE_KEY_PREFIX + mode).equals(raw)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 可用模式名列表（当前 carpet 语言显示名，" / " 分隔）：
     * 供 unknown_mode 失败提示使用，与 suggestModes 引导输入的写法一致。
     */
    private static String modeList() {
        StringJoiner joiner = new StringJoiner(" / ");
        for (String mode : MODES) {
            joiner.add(Translations.tr(MODE_KEY_PREFIX + mode));
        }
        return joiner.toString();
    }

    /**
     * 补全：carpet 语言为中文（zh*）时建议三语显示名（僵尸/殭屍…），否则英文键；
     * team 子句输入中建议已有队名与 off；已输入模式名后再按 TAB 补全 keep 选项。
     */
    /**
     * carpet 语言是否中文（模式名建议中文显示名）。
     * CarpetSettings 静态初始化依赖服务端上下文，纯 JVM 环境（单测）会失败——
     * 回退英文键；服务端环境不受影响。
     */
    private static boolean isChineseLanguage() {
        try {
            return CarpetSettings.language.startsWith("zh");
        } catch (Throwable t) {
            return false;
        }
    }

    private static CompletableFuture<Suggestions> suggestModes(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        // team 子句：补全已有队名（含 off 退出关键字）
        if (remaining.equalsIgnoreCase(TEAM_KEYWORD)
                || remaining.regionMatches(true, 0, TEAM_KEYWORD + " ", 0, TEAM_KEYWORD.length() + 1)) {
            SuggestionsBuilder teamBuilder = remaining.length() > TEAM_KEYWORD.length()
                    ? builder.createOffset(builder.getStart() + TEAM_KEYWORD.length() + 1)
                    : builder;
            String teamRemaining = teamBuilder.getRemainingLowerCase();
            for (String team : BrainTeams.knownTeams()) {
                if (team.toLowerCase().startsWith(teamRemaining)) {
                    teamBuilder.suggest(team);
                }
            }
            if (BrainTeams.OFF_KEYWORD.startsWith(teamRemaining)) {
                teamBuilder.suggest(BrainTeams.OFF_KEYWORD);
            }
            return teamBuilder.buildFuture();
        }
        int space = remaining.indexOf(' ');
        if (space < 0) {
            // carpet 语言规则字段直读（getRule/getAsString 在 1.21.x carpet 已标记移除，
            // 字段读法 1.21~26.3 一致）
            boolean chinese = isChineseLanguage();
            for (String mode : MODES) {
                String suggestion = mode;
                if (chinese) {
                    suggestion = Translations.tr(MODE_KEY_PREFIX + mode);
                }
                if (suggestion.toLowerCase().startsWith(remaining)
                        || mode.startsWith(remaining)) {
                    builder.suggest(suggestion);
                }
            }
            if (TEAM_KEYWORD.startsWith(remaining)) {
                builder.suggest(TEAM_KEYWORD);
            }
        } else if ("keep".startsWith(remaining.substring(space + 1))) {
            return builder.createOffset(builder.getStart() + space + 1).suggest("keep").buildFuture();
        }
        return builder.buildFuture();
    }
}
