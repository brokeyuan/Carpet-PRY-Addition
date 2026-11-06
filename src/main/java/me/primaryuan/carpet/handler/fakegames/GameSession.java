package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一局小游戏的会话：阶段推进、成员管理、区域判定与虚拟淘汰全部收口于此。
 *
 * <p>阶段流：WAITING（准备，发起者点击开始）→ COUNTDOWN（3 秒，条件失效回到准备）
 * → RUNNING → FINISHED。成员校验（掉线/真实死亡/跨维度/旁观）与区域越界每 10 tick
 * 执行一次；真实死亡仅使玩家退出本局，死亡流程完全交给原版与现有模块。</p>
 *
 * <p>虚拟淘汰 = 实体事件 35 图腾动画 + Title 原因 + 成员聊天通报，随后停止该成员
 * 的本局判定；血量、背包、经验、模式均不受影响。结束流程逐段隔离异常，任何表现层
 * 失败都不会阻止玩法清理与战绩入账。</p>
 */
public final class GameSession {
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 默认游戏区域半径（管理员预设缺失时的兜底） */
    public static final int DEFAULT_RADIUS = 32;
    /** 准备阶段无操作最长等待（tick），超时取消 */
    public static final int WAITING_TIMEOUT_TICKS = 20 * 600;
    /** 开局倒计时时长（tick） */
    public static final int COUNTDOWN_TICKS = 60;
    /** 成员/区域校验周期（tick） */
    private static final int VALIDATE_INTERVAL = 10;
    /** 越界宽限（tick），期间每 2 秒警告一次 */
    private static final int OUT_OF_BOUNDS_GRACE_TICKS = 100;

    /** 本局唯一 ID：所有聊天按钮都携带，旧按钮不会命中新一局 */
    public final String id;
    public final GameType type;
    /** 发起者；退出后在存活成员间转移（开始/结束权限随之转移） */
    public UUID host;
    public final ServerLevel level;
    public final BlockPos center;
    public final int radius;
    public final int minY;
    public final int maxY;
    public final boolean checkHeight;
    public final String presetName;

    public SessionPhase phase = SessionPhase.WAITING;
    public int phaseTicks;
    /** 玩家在准备阶段指定的关键点（如木头人终点），未指定为 null */
    public BlockPos goalPos;
    /** 兼容旧调用的附加参数；新代码统一读取 options */
    public String option;

    /** 本局设置（由 GameType.settings 的默认值初始化，准备阶段可调） */
    public final Map<String, String> options = new LinkedHashMap<>();

    final Map<UUID, GameMember> members = new LinkedHashMap<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final GameLogic logic;
    private final PlayerLookup playerLookup;

    private int validateCounter;
    private final Map<UUID, Integer> outOfBoundsTicks = new LinkedHashMap<>();
    private boolean ended;

    /** 仅本规则内部使用的玩家查找入口；测试可注入轻量映射。 */
    @FunctionalInterface
    interface PlayerLookup {
        ServerPlayer get(UUID id);
    }

    GameSession(GameType type, ServerPlayer host, GameLogic logic) {
        this(type, host, logic, id -> {
            MinecraftServer server = host.level().getServer();
            return server == null ? null : server.getPlayerList().getPlayer(id);
        }, null, null, null);
    }

    GameSession(GameType type, ServerPlayer host, GameLogic logic, PlayerLookup playerLookup) {
        this(type, host, logic, playerLookup, null, null, null);
    }

    GameSession(GameType type, ServerPlayer host, GameLogic logic, PlayerLookup playerLookup,
                String presetName, FakeGameSettings.Preset preset, Map<String, String> initialOptions) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.host = host.getUUID();
        this.level = (ServerLevel) host.level();
        this.center = host.blockPosition();
        this.logic = logic;
        this.playerLookup = playerLookup;
        FakeGameSettings.Preset resolved = preset != null
                ? preset : FakeGameSettings.preset(presetName);
        this.presetName = resolved.name();
        this.radius = FakeGameSettings.clampRadius(resolved.radius());
        this.minY = center.getY() + resolved.minY();
        this.maxY = center.getY() + resolved.maxY();
        this.checkHeight = type == GameType.ELYTRA;
        for (GameType.Setting setting : type.settings()) {
            options.put(setting.key(), setting.def());
        }
        if (initialOptions != null) {
            for (Map.Entry<String, String> entry : initialOptions.entrySet()) {
                if (options.containsKey(entry.getKey())) {
                    options.put(entry.getKey(), entry.getValue());
                }
            }
        }
        this.option = options.get("mode");
    }

    // ==================== 成员管理 ====================

    /** WAITING 阶段加入（发起者创建时即加入；曾退出者允许重新加入） */
    public boolean addPlayer(ServerPlayer player) {
        if (phase != SessionPhase.WAITING) {
            return false;
        }
        GameMember existing = members.get(player.getUUID());
        if (existing != null && existing.state != MemberState.LEFT) {
            return false;
        }
        if (joinRejection(player) != null) {
            return false;
        }
        if (activeCount() >= type.maxPlayers) {
            return false;
        }
        if (existing != null) {
            existing.state = MemberState.ACTIVE;
            existing.clearElimination();
            broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.player_joined_chat",
                    player.getName().getString(), activeCount(), members.size()));
            playJoinSound();
            return true;
        }
        members.put(player.getUUID(), new GameMember(player.getUUID()));
        broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.player_joined_chat",
                player.getName().getString(), activeCount(), members.size()));
        playJoinSound();
        return true;
    }

    private void playJoinSound() {
        soundToActive(SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.5f);
    }

    /** 加入资格检查；返回拒绝原因的 lang 键片段，合格返回 null。 */
    public String joinRejection(ServerPlayer player) {
        if (player.level() != level) {
            return "join_dimension";
        }
        if (player.isSpectator()) {
            return "join_spectator";
        }
        if (!player.isAlive() || player.isDeadOrDying()) {
            return "join_dead";
        }
        if (phase != SessionPhase.WAITING) {
            return "join_too_late";
        }
        if (!inRegion(player)) {
            return "join_region";
        }
        return null;
    }

    /** 退出本局（主动离开或客观原因），返回是否确有退出发生 */
    public boolean removePlayer(ServerPlayer player, String reasonChatKey, Object... args) {
        Map<UUID, Component> messages = new LinkedHashMap<>();
        if (reasonChatKey != null) {
            messages.put(player.getUUID(), ServerI18n.tr(reasonChatKey, args));
        }
        return removeMembers(List.of(player.getUUID()), messages);
    }

    /**
     * 同批移出成员：先更新全部状态，再逐个通报并回调玩法，最后统一结算。
     * 离线成员没有 ServerPlayer，玩法按 UUID 清理，不伪造实体。
     */
    private boolean removeMembers(Collection<UUID> ids, Map<UUID, Component> selfMessages) {
        List<UUID> removing = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            GameMember member = members.get(id);
            if (member != null && member.state != MemberState.LEFT) {
                removing.add(id);
            }
        }
        if (removing.isEmpty()) {
            return false;
        }
        boolean wasActive = false;
        for (UUID id : removing) {
            GameMember member = members.get(id);
            if (member.state == MemberState.ACTIVE) {
                wasActive = true;
            }
            member.state = MemberState.LEFT;
            outOfBoundsTicks.remove(id);
        }
        for (UUID id : removing) {
            ServerPlayer player = player(id);
            Component message = selfMessages.get(id);
            if (player != null && message != null) {
                player.sendSystemMessage(message);
            }
            broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.player_left_chat", nameOf(id)));
            try {
                logic.onPlayerLeave(this, id, player, false);
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] onPlayerLeave failed for {}", id, t);
            }
        }
        boolean hostRemoved = removing.contains(host);
        if (hostRemoved) {
            if (phase == SessionPhase.WAITING || phase == SessionPhase.COUNTDOWN) {
                cancel("cancel_host");
                return true;
            }
            if (phase == SessionPhase.RUNNING) {
                transferHost();
            }
        }
        if (phase == SessionPhase.RUNNING && activeCount() == 0) {
            cancel("cancel_all_left");
            return true;
        }
        if (wasActive && phase == SessionPhase.RUNNING && type.autoLastStanding) {
            checkRemaining();
        }
        return true;
    }

    private void transferHost() {
        for (GameMember member : members.values()) {
            if (member.state == MemberState.ACTIVE) {
                host = member.id;
                broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.host_changed", nameOf(member.id)));
                return;
            }
        }
    }

    public GameMember member(UUID id) {
        return members.get(id);
    }

    public ServerPlayer player(UUID id) {
        return playerLookup.get(id);
    }

    public String nameOf(UUID id) {
        ServerPlayer player = player(id);
        return player != null ? player.getName().getString() : id.toString().substring(0, 8);
    }

    /** 指定状态且在线的成员 */
    public List<ServerPlayer> players(MemberState state) {
        List<ServerPlayer> result = new ArrayList<>();
        for (GameMember member : members.values()) {
            if (member.state != state) {
                continue;
            }
            ServerPlayer player = player(member.id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    public List<ServerPlayer> activePlayers() {
        return players(MemberState.ACTIVE);
    }

    public int activeCount() {
        int count = 0;
        for (GameMember member : members.values()) {
            if (member.state == MemberState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    /** ACTIVE 成员 UUID 快照；离线成员也包含在内，用于成员校验。 */
    private List<UUID> activeMemberIds() {
        List<UUID> ids = new ArrayList<>();
        for (GameMember member : members.values()) {
            if (member.state == MemberState.ACTIVE) {
                ids.add(member.id);
            }
        }
        return ids;
    }

    // ==================== 设置与开始 ====================

    public String option(String key) {
        return options.getOrDefault(key, "");
    }

    public int intOption(String key, int fallback) {
        try {
            return Integer.parseInt(options.getOrDefault(key, Integer.toString(fallback)));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public double doubleOption(String key, double fallback) {
        try {
            return Double.parseDouble(options.getOrDefault(key, Double.toString(fallback)));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public boolean boolOption(String key, boolean fallback) {
        String value = options.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    /** 准备阶段修改设置；只接受 GameType 声明的键与选项。 */
    public boolean setOption(String key, String value) {
        if (phase != SessionPhase.WAITING) {
            return false;
        }
        for (GameType.Setting setting : type.settings()) {
            if (!setting.key().equals(key)) {
                continue;
            }
            if (!setting.choices().isEmpty() && !setting.choices().contains(value)) {
                return false;
            }
            options.put(key, value);
            if ("mode".equals(key)) {
                option = value;
            }
            return true;
        }
        return false;
    }

    /** 发起者/管理员点击开始；返回 null 表示进入倒计时，否则返回错误代码。 */
    public String begin(ServerPlayer actor, boolean admin) {
        if (phase != SessionPhase.WAITING) {
            return "not_waiting";
        }
        if (actor == null || (!admin && !host.equals(actor.getUUID()))) {
            return "not_host";
        }
        if (type.unavailableKey() != null) {
            return "unavailable";
        }
        // 局内临时能力：规则本身开启可直接用；规则关闭时只有管理员总开关打开才允许局部应用
        if (type == GameType.HIDE_AND_SEEK) {
            String mode = option("mode");
            if ("shrink".equals(mode) && !FakeGameSettings.ruleEnabled("playerScale")
                    && !FakeGameSettings.temporaryRulesAllowed()) {
                return "unavailable";
            }
            if ("herb".equals(mode) && !FakeGameSettings.ruleEnabled("invisibleInTallGrass")
                    && !FakeGameSettings.temporaryRulesAllowed()) {
                return "unavailable";
            }
        }
        if (activeCount() < type.minPlayers) {
            return "min_players";
        }
        if (activeCount() > type.maxPlayers) {
            return "max_players";
        }
        for (ServerPlayer player : activePlayers()) {
            if (player.isDeadOrDying() || player.isSpectator() || player.level() != level
                    || !inRegion(player)) {
                return "invalid_member";
            }
        }
        phase = SessionPhase.COUNTDOWN;
        phaseTicks = 0;
        validateCounter = 0;
        broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.countdown_started",
                type.minPlayers, activeCount()));
        for (ServerPlayer player : activePlayers()) {
            FakeGameEffects.title(player,
                    ServerI18n.tr("carpetprimaryuan.fakegames.get_ready"),
                    Component.empty(), 0, 40, 10);
        }
        return null;
    }

    /** 倒计时中条件失效：回到准备阶段并说明原因。 */
    private void backToWaiting(Component reason) {
        if (phase != SessionPhase.COUNTDOWN) {
            return;
        }
        phase = SessionPhase.WAITING;
        phaseTicks = 0;
        validateCounter = 0;
        broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.countdown_back", reason.getString()));
        soundToActive(SoundEvents.NOTE_BLOCK_BASS, 0.8f, 0.7f);
    }

    // ==================== 淘汰与结算 ====================

    /** 虚拟淘汰：图腾动画 + Title 原因 + 聊天通报，随后停止该成员的本局判定。 */
    public void eliminate(ServerPlayer player, String reasonKey, Object... reasonArgs) {
        eliminateBatch(List.of(player), reasonKey, reasonArgs);
    }

    /** 同批淘汰：先更新全部成员状态，再逐个播放表现并回调玩法，最后只结算一次。 */
    public void eliminateBatch(Collection<ServerPlayer> players, String reasonKey, Object... reasonArgs) {
        if (phase != SessionPhase.RUNNING) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        Map<UUID, ServerPlayer> online = new LinkedHashMap<>();
        for (ServerPlayer player : players) {
            ids.add(player.getUUID());
            online.putIfAbsent(player.getUUID(), player);
            outOfBoundsTicks.remove(player.getUUID());
        }
        Component reason = ServerI18n.tr(reasonKey, reasonArgs);
        List<GameMember> eliminated = EliminationBatch.apply(members, ids, reason, member -> {
            member.eliminationKey = reasonKey;
            ServerPlayer player = online.get(member.id);
            if (player == null) {
                player = player(member.id);
            }
            if (player != null) {
                try {
                    FakeGameEffects.playTotemAnimation(player);
                    FakeGameEffects.title(player,
                            ServerI18n.tr("carpetprimaryuan.fakegames.eliminated_title"),
                            reason, 5, 40, 15);
                    player.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.eliminated_self"));
                } catch (Throwable t) {
                    LOGGER.error("[FakeGames] elimination effect failed", t);
                }
            }
            outOfBoundsTicks.remove(member.id);
            broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.eliminated_chat",
                    nameOf(member.id), reason.getString()));
            try {
                logic.onPlayerLeave(this, member.id, player, true);
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] onPlayerLeave(eliminated) failed for {}", member.id, t);
            }
        });
        if (!eliminated.isEmpty() && phase == SessionPhase.RUNNING && type.autoLastStanding) {
            checkRemaining();
        }
    }

    private void checkRemaining() {
        if (phase != SessionPhase.RUNNING) {
            return;
        }
        int active = MemberRules.activeCount(members.values());
        if (active <= 1) {
            finish(active == 0 ? null : MemberRules.soleActive(members));
        }
    }

    public void finish(UUID winner) {
        finish(winner, null);
    }

    public void finish(UUID winner, Component customSubtitle) {
        List<UUID> winners = winner == null ? List.of() : List.of(winner);
        finishWinners(winners, customSubtitle);
    }

    /** 阵营/多人获胜：所有给出 UUID 都记胜场。 */
    public void finishTeam(Collection<UUID> winners, Component customSubtitle) {
        finishWinners(new ArrayList<>(winners), customSubtitle);
    }

    /** 平局：没有胜者，但正常入账参与场次。 */
    public void finishDraw(Component customSubtitle) {
        finishWinners(List.of(), customSubtitle);
    }

    private void finishWinners(List<UUID> winners, Component customSubtitle) {
        if (phase != SessionPhase.RUNNING) {
            return;
        }
        boolean scored = members.values().stream().anyMatch(m -> m.score != 0);
        Component subtitle;
        if (customSubtitle != null) {
            subtitle = customSubtitle;
        } else if (winners.size() == 1 && !scored) {
            subtitle = ServerI18n.tr("carpetprimaryuan.fakegames.winner_subtitle", nameOf(winners.get(0)));
        } else if (scored) {
            subtitle = ServerI18n.tr("carpetprimaryuan.fakegames.final_score", scoreboardText());
        } else {
            subtitle = ServerI18n.tr("carpetprimaryuan.fakegames.draw_subtitle");
        }
        endSession(false, winners,
                ServerI18n.tr("carpetprimaryuan.fakegames.finished_title"), subtitle);
    }

    /** 异常终止（人数不足/超时/规则关闭/发起者退出/强制结束等） */
    public void cancel(String reasonKey, Object... reasonArgs) {
        if (phase == SessionPhase.FINISHED) {
            return;
        }
        endSession(true, List.of(),
                ServerI18n.tr("carpetprimaryuan.fakegames.cancelled_title"),
                ServerI18n.tr("carpetprimaryuan.fakegames.cancelled_reason",
                        ServerI18n.tr("carpetprimaryuan.fakegames." + reasonKey, reasonArgs).getString()));
    }

    private void endSession(boolean cancelled, List<UUID> winners, Component title, Component subtitle) {
        if (phase == SessionPhase.FINISHED) {
            return;
        }
        phase = SessionPhase.FINISHED;
        try {
            for (ServerPlayer player : players(MemberState.ACTIVE)) {
                try {
                    FakeGameEffects.title(player, title, subtitle, 5, 60, 20);
                    FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_BELL, 1.0f,
                            cancelled ? 0.6f : 1.2f);
                } catch (Throwable t) {
                    LOGGER.error("[FakeGames] finish effect failed", t);
                }
            }
            for (ServerPlayer player : players(MemberState.ELIMINATED)) {
                try {
                    player.sendSystemMessage(subtitle);
                } catch (Throwable t) {
                    LOGGER.error("[FakeGames] finish message failed", t);
                }
            }
            if (!cancelled && scored()) {
                broadcast(Component.literal("§e" + scoreboardText()));
            }
            broadcast(subtitle);
        } catch (Throwable t) {
            LOGGER.error("[FakeGames] finish broadcast failed", t);
        } finally {
            try {
                logic.onEnd(this);
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] logic.onEnd failed", t);
            } finally {
                ended = true;
                try {
                    FakeGameResults.record(this, cancelled, winners);
                } catch (Throwable t) {
                    LOGGER.error("[FakeGames] result recording failed", t);
                }
            }
        }
    }

    /** 停服清理：即使取消流程抛异常也必须执行玩法清理；可重复调用。 */
    public void shutdown() {
        if (phase != SessionPhase.FINISHED) {
            try {
                cancel("cancel_server_stop");
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] shutdown cancel failed", t);
            }
        }
        if (!ended) {
            ended = true;
            try {
                logic.onEnd(this);
            } catch (Throwable t) {
                LOGGER.error("[FakeGames] shutdown cleanup failed", t);
            }
        }
        phase = SessionPhase.FINISHED;
    }

    private boolean scored() {
        return members.values().stream().anyMatch(m -> m.score != 0);
    }

    private String scoreboardText() {
        List<GameMember> ranked = new ArrayList<>(members.values());
        ranked.sort(Comparator.comparingInt((GameMember m) -> m.score).reversed());
        StringBuilder sb = new StringBuilder();
        for (GameMember member : ranked) {
            if (sb.length() > 0) {
                sb.append("§7, §f");
            }
            sb.append(nameOf(member.id));
            if (member.score != 0) {
                sb.append("§7: ").append(member.score);
            }
        }
        return sb.toString();
    }

    // ==================== 消息与表现 ====================

    /** 发给全部在线成员（含已淘汰/已退出，便于观战了解进展） */
    public void broadcast(Component message) {
        for (GameMember member : members.values()) {
            ServerPlayer player = player(member.id);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** 只发给存活成员 */
    public void chatActive(Component message) {
        for (ServerPlayer player : activePlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /** 存活成员 ActionBar（当前规则/积分等持续提示） */
    public void actionbarActive(Component message) {
        for (ServerPlayer player : activePlayers()) {
            FakeGameEffects.actionbar(player, message);
        }
    }

    public void titleActive(Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        for (ServerPlayer player : activePlayers()) {
            FakeGameEffects.title(player, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public void soundToActive(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                              float volume, float pitch) {
        for (ServerPlayer player : activePlayers()) {
            FakeGameEffects.sound(player, sound, volume, pitch);
        }
    }

    /** 全部成员视图（跨包只读遍历用） */
    public Collection<GameMember> allMembers() {
        return java.util.Collections.unmodifiableCollection(members.values());
    }

    public boolean isHost(UUID id) {
        return host.equals(id);
    }

    /** /fg guess 的转发入口（玩法侧 onGuess） */
    public boolean tryGuess(ServerPlayer player, String message) {
        return logic.onGuess(this, player, message);
    }

    /** 有效攻击命中的转发入口 */
    public boolean onAttack(ServerPlayer attacker, ServerPlayer victim) {
        return logic.onAttack(this, attacker, victim);
    }

    // ==================== 区域与校验 ====================

    /** 同维度且水平距离在半径内；飞行类玩法同时限制高度。 */
    public boolean inRegion(ServerPlayer player) {
        if (player.level() != level) {
            return false;
        }
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        if (dx * dx + dz * dz > (double) radius * radius) {
            return false;
        }
        return !checkHeight || (player.getY() >= minY && player.getY() <= maxY);
    }

    /** 接近边界（用于提示，不参与判定）。 */
    public boolean nearBoundary(ServerPlayer player) {
        if (player.level() != level) {
            return false;
        }
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) > radius - 4.0) {
            return true;
        }
        return checkHeight && (player.getY() - minY < 4.0 || maxY - player.getY() < 4.0);
    }

    public Component regionText() {
        if (checkHeight) {
            return ServerI18n.tr("carpetprimaryuan.fakegames.region.height",
                    presetName, radius, minY, maxY);
        }
        return ServerI18n.tr("carpetprimaryuan.fakegames.region.ground", presetName, radius);
    }

    /** 准备页检查项；ok=false 时 detail 说明原因。 */
    public record CheckItem(String key, boolean ok, String detail) {}

    public List<CheckItem> checklist() {
        List<CheckItem> checks = new ArrayList<>();
        checks.add(new CheckItem("players_min", activeCount() >= type.minPlayers,
                activeCount() + "/" + type.minPlayers));
        checks.add(new CheckItem("players_max", activeCount() <= type.maxPlayers,
                activeCount() + "/" + type.maxPlayers));
        int invalid = 0;
        for (ServerPlayer player : activePlayers()) {
            if (player.isDeadOrDying() || player.isSpectator() || player.level() != level
                    || !inRegion(player)) {
                invalid++;
            }
        }
        checks.add(new CheckItem("members_valid", invalid == 0, Integer.toString(invalid)));
        String unavailable = type.unavailableKey();
        checks.add(new CheckItem("available", unavailable == null,
                unavailable == null ? "" : ServerI18n.tr(unavailable).getString()));
        if (type == GameType.HIDE_AND_SEEK && "shrink".equals(option("mode"))) {
            boolean scale = FakeGameSettings.ruleEnabled("playerScale");
            checks.add(new CheckItem("rule_playerScale", scale, "playerScale"));
        }
        return checks;
    }

    /** 排行榜/战绩分组键：游戏 + 关键模式/难度，不把不同玩法混为同一榜。 */
    public String resultKey() {
        StringBuilder key = new StringBuilder(type.id);
        String mode = options.getOrDefault("mode", "");
        if (!mode.isEmpty()) {
            key.append('|').append(mode);
        }
        String targets = options.getOrDefault("targets", "");
        if (!targets.isEmpty()) {
            key.append('|').append(targets);
        }
        if (type == GameType.RISING_LAVA) {
            key.append('|').append(options.getOrDefault("display", "particles"));
        }
        return key.toString();
    }

    // ==================== tick 推进 ====================

    /** 由 FakeGameManager 每服务端 tick 调用；返回 false 表示会话已结束 */
    boolean tick(MinecraftServer server) {
        phaseTicks++;
        switch (phase) {
            case WAITING -> tickWaiting();
            case COUNTDOWN -> tickCountdown();
            case RUNNING -> tickRunning();
            case FINISHED -> {
                return false;
            }
        }
        return phase != SessionPhase.FINISHED;
    }

    private void tickWaiting() {
        if (++validateCounter >= VALIDATE_INTERVAL) {
            validateCounter = 0;
            validateLobbyMembers();
            if (phase != SessionPhase.WAITING) {
                return;
            }
        }
        if (phaseTicks > WAITING_TIMEOUT_TICKS) {
            cancel("cancel_timeout");
            return;
        }
        FakeGameBoundary.tick(this);
    }

    private void tickCountdown() {
        if (++validateCounter >= VALIDATE_INTERVAL) {
            validateCounter = 0;
            validateLobbyMembers();
            if (phase != SessionPhase.COUNTDOWN) {
                return;
            }
        }
        if (activeCount() < type.minPlayers) {
            backToWaiting(ServerI18n.tr("carpetprimaryuan.fakegames.error.min_players"));
            return;
        }
        Component issue;
        try {
            issue = logic.countdownIssue(this);
        } catch (Throwable t) {
            LOGGER.error("[FakeGames] countdownIssue failed", t);
            issue = ServerI18n.tr("carpetprimaryuan.fakegames.error.internal");
        }
        if (issue != null) {
            backToWaiting(issue);
            return;
        }
        int remaining = COUNTDOWN_TICKS - phaseTicks;
        int seconds = (remaining + 19) / 20;
        if (remaining % 20 == 0 && seconds > 0) {
            for (ServerPlayer player : players(MemberState.ACTIVE)) {
                FakeGameEffects.title(player,
                        Component.literal("§e" + seconds).withStyle(ChatFormatting.BOLD),
                        Component.empty(), 0, 20, 5);
                FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }
        FakeGameBoundary.tick(this);
        if (remaining <= 0) {
            beginRunning();
        }
    }

    private void beginRunning() {
        validateLobbyMembers();
        if (phase != SessionPhase.COUNTDOWN) {
            return;
        }
        if (activeCount() < type.minPlayers) {
            backToWaiting(ServerI18n.tr("carpetprimaryuan.fakegames.error.min_players"));
            return;
        }
        phase = SessionPhase.RUNNING;
        phaseTicks = 0;
        validateCounter = 0;
        outOfBoundsTicks.clear();
        participants.clear();
        for (GameMember member : members.values()) {
            if (member.state == MemberState.ACTIVE) {
                participants.add(member.id);
            }
        }
        for (ServerPlayer player : players(MemberState.ACTIVE)) {
            FakeGameEffects.title(player,
                    ServerI18n.tr("carpetprimaryuan.fakegames.go_title"),
                    Component.empty(), 0, 20, 10);
        }
        soundToActive(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.8f);
        try {
            logic.onStart(this);
        } catch (Throwable t) {
            LOGGER.error("[FakeGames] logic.onStart failed", t);
            cancel("cancel_error");
        }
    }

    private void tickRunning() {
        validateCounter++;
        if (validateCounter >= VALIDATE_INTERVAL) {
            validateCounter = 0;
            validateMembers();
            if (phase != SessionPhase.RUNNING) {
                return;
            }
        }
        try {
            logic.onTick(this);
        } catch (Throwable t) {
            LOGGER.error("[FakeGames] logic.onTick failed", t);
            cancel("cancel_error");
            return;
        }
        FakeGameBoundary.tick(this);
    }

    /** WAITING/COUNTDOWN：死亡、离线、跨维度、旁观立即移出；不校验区域（等待期允许走动）。 */
    private void validateLobbyMembers() {
        Map<UUID, Component> messages = new LinkedHashMap<>();
        for (UUID id : activeMemberIds()) {
            ServerPlayer player = player(id);
            if (player == null) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_offline"));
            } else if (player.isDeadOrDying()) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_death"));
            } else if (player.level() != level) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_dimension"));
            } else if (player.isSpectator()) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_spectator"));
            }
        }
        if (!messages.isEmpty()) {
            removeMembers(messages.keySet(), messages);
        }
    }

    /** 掉线/真实死亡/跨维度/旁观 → 退出本局；越界 → 警告后按同批结算淘汰。 */
    private void validateMembers() {
        Map<UUID, Component> messages = new LinkedHashMap<>();
        for (UUID id : activeMemberIds()) {
            ServerPlayer player = player(id);
            if (player == null) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_offline"));
            } else if (player.isDeadOrDying()) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_death"));
            } else if (player.level() != level) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_dimension"));
            } else if (player.isSpectator()) {
                messages.put(id, ServerI18n.tr("carpetprimaryuan.fakegames.left_spectator"));
            }
        }
        if (!messages.isEmpty()) {
            removeMembers(messages.keySet(), messages);
            if (phase != SessionPhase.RUNNING) {
                return;
            }
        }
        List<ServerPlayer> outOfBounds = new ArrayList<>();
        for (ServerPlayer player : activePlayers()) {
            if (!inRegion(player)) {
                int ticks = outOfBoundsTicks.merge(player.getUUID(), VALIDATE_INTERVAL, Integer::sum);
                if (ticks == VALIDATE_INTERVAL) {
                    FakeGameEffects.actionbar(player, ServerI18n.tr(
                            "carpetprimaryuan.fakegames.out_of_bounds_warn",
                            OUT_OF_BOUNDS_GRACE_TICKS / 20));
                } else if (ticks >= OUT_OF_BOUNDS_GRACE_TICKS) {
                    outOfBounds.add(player);
                }
            } else {
                outOfBoundsTicks.remove(player.getUUID());
                if (nearBoundary(player) && phaseTicks % 20 == 0) {
                    double distance = Math.sqrt(
                            Math.pow(player.getX() - center.getX() - 0.5, 2)
                                    + Math.pow(player.getZ() - center.getZ() - 0.5, 2));
                    FakeGameEffects.actionbar(player, ServerI18n.tr(
                            "carpetprimaryuan.fakegames.boundary_near",
                            Math.max(0, (int) (radius - distance))));
                }
            }
        }
        if (!outOfBounds.isEmpty()) {
            eliminateBatch(outOfBounds, "carpetprimaryuan.fakegames.reason.out_of_bounds");
        }
    }
}
