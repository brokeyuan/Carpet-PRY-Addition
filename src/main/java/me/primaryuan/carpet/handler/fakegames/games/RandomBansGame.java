package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 随机行为指令：每轮公布一条持续要求或限时完成一次的要求。
 *
 * <p>持续要求：不准跳跃、不准停下、必须潜行、必须保持移动。限时完成：跳一次、
 * 抬头、低头，执行时限内完成即安全，超时淘汰。每轮只出一条，避免互相冲突；
 * 公布后有反应宽限，限时要求有清楚的完成状态。</p>
 */
public final class RandomBansGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private enum Ban {
        NO_JUMP(true), NO_STOP(true), MUST_SNEAK(true), KEEP_MOVING(true), MUST_STOP(true),
        ONCE_JUMP(false), LOOK_UP(false), LOOK_DOWN(false);

        final boolean sustained;

        Ban(boolean sustained) {
            this.sustained = sustained;
        }
    }

    private static final int SWITCH_GRACE_TICKS = 40;
    private static final int ONESHOT_TICKS = 20 * 10;
    private static final int STOP_TICKS = 20;
    private static final double STOP_STEP_TOLERANCE = 0.02;
    private static final double STOP_PATH_BUDGET = 0.10;
    private static final double MUST_STOP_PATH_BUDGET = 1.5;

    private Ban ban = Ban.NO_JUMP;
    private int banLeft;
    private int graceTicks;
    private int hudCooldown;
    private String actionMode = "all";
    private final StillnessTracker stillness = new StillnessTracker(
            STOP_STEP_TOLERANCE, STOP_TICKS, STOP_PATH_BUDGET);
    private final MovementWindow movement = new MovementWindow(20, MUST_STOP_PATH_BUDGET);
    private final Map<UUID, Double> lastY = new HashMap<>();
    private final Set<UUID> completed = new LinkedHashSet<>();

    @Override
    public void onStart(GameSession session) {
        actionMode = session.option("actions");
        ban = pick(session, null);
        announceBan(session);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.bans.start"));
    }

    @Override
    public void onTick(GameSession session) {
        if (banLeft > 0) {
            banLeft--;
        }
        if (banLeft <= 0) {
            if (!ban.sustained) {
                eliminateIncomplete(session);
                if (session.phase != me.primaryuan.carpet.handler.fakegames.SessionPhase.RUNNING) {
                    return;
                }
            }
            switchBan(session);
        }
        if (graceTicks > 0) {
            graceTicks--;
            if (graceTicks == 0) {
                lastY.clear();
                stillness.clear();
                movement.clear();
            }
            return;
        }
        switch (ban) {
            case NO_JUMP -> checkNoJump(session);
            case NO_STOP, KEEP_MOVING -> checkStillness(session);
            case MUST_SNEAK -> checkMustSneak(session);
            case MUST_STOP -> checkMustStop(session);
            case ONCE_JUMP -> checkOnceJump(session);
            case LOOK_UP -> checkLook(session, true);
            case LOOK_DOWN -> checkLook(session, false);
        }
        if (session.phase != me.primaryuan.carpet.handler.fakegames.SessionPhase.RUNNING) {
            return;
        }
        if (++hudCooldown >= 20) {
            hudCooldown = 0;
            sendHud(session);
        }
    }

    private Ban pick(GameSession session, Ban previous) {
        List<Ban> pool = new ArrayList<>();
        switch (actionMode) {
            case "basic" -> {
                pool.add(Ban.NO_JUMP);
                pool.add(Ban.NO_STOP);
                pool.add(Ban.MUST_SNEAK);
                pool.add(Ban.KEEP_MOVING);
                pool.add(Ban.MUST_STOP);
            }
            case "oneshot" -> {
                pool.add(Ban.ONCE_JUMP);
                pool.add(Ban.LOOK_UP);
                pool.add(Ban.LOOK_DOWN);
            }
            default -> {
                for (Ban value : Ban.values()) {
                    pool.add(value);
                }
            }
        }
        if (pool.size() > 1 && previous != null) {
            pool.remove(previous);
        }
        return pool.get(session.level.getRandom().nextInt(pool.size()));
    }

    private void switchBan(GameSession session) {
        ban = pick(session, ban);
        announceBan(session);
    }

    private void announceBan(GameSession session) {
        graceTicks = SWITCH_GRACE_TICKS;
        banLeft = ban.sustained
                ? Math.max(10, session.intOption("round_seconds", 15)) * 20
                : ONESHOT_TICKS;
        lastY.clear();
        stillness.clear();
        movement.clear();
        completed.clear();
        hudCooldown = 0;
        session.titleActive(Component.literal("§6" + ServerI18n.tr(
                        banKey() + ".name").getString()).withStyle(ChatFormatting.BOLD),
                ServerI18n.tr(banKey() + ".rule"), 5, 40, 10);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.bans.announce",
                ServerI18n.tr(banKey() + ".name").getString(),
                ban.sustained ? tr("bans.sustained") : tr("bans.oneshot"),
                banLeft / 20));
        for (ServerPlayer player : session.activePlayers()) {
            FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }
    }

    private String banKey() {
        return "carpetprimaryuan.fakegames.bans." + ban.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String tr(String key) {
        return ServerI18n.tr("carpetprimaryuan.fakegames." + key).getString();
    }

    /** 离地上升即跳跃（外力/载具/液体/攀爬/鞘翅豁免）；纯判定供单测复用 */
    static boolean jumpViolation(boolean onGround, boolean passenger, double dy,
                                 boolean hurt, boolean inWater, boolean inLava,
                                 boolean climbing, boolean fallFlying) {
        if (onGround || passenger || dy <= 0.05) {
            return false;
        }
        return !hurt && !inWater && !inLava && !climbing && !fallFlying;
    }

    private void checkNoJump(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            UUID id = player.getUUID();
            Double prevY = lastY.put(id, player.getY());
            if (prevY == null) {
                continue;
            }
            if (jumpViolation(player.onGround(), player.isPassenger(),
                    player.getY() - prevY, player.hurtTime > 0,
                    player.isInWater(), player.isInLava(),
                    player.onClimbable(), player.isFallFlying())) {
                violators.add(player);
            }
        }
        eliminate(session, violators);
    }

    private void checkStillness(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            boolean eligible = player.onGround()
                    && !player.isPassenger()
                    && !player.isInWater()
                    && !player.isInLava()
                    && !player.onClimbable()
                    && !player.isFallFlying()
                    && player.hurtTime <= 0;
            if (stillness.update(player.getUUID(), player.position(), eligible)) {
                violators.add(player);
            }
        }
        eliminate(session, violators);
    }

    private void checkMustSneak(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (!player.isShiftKeyDown() && !player.isPassenger()) {
                violators.add(player);
            }
        }
        eliminate(session, violators);
    }

    private void checkMustStop(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            boolean eligible = player.onGround()
                    && !player.isPassenger()
                    && !player.isInWater()
                    && !player.isInLava()
                    && !player.onClimbable()
                    && !player.isFallFlying()
                    && player.hurtTime <= 0;
            if (movement.movedTooMuch(player.getUUID(), player.position(), eligible)) {
                violators.add(player);
            }
        }
        eliminate(session, violators);
    }

    private void checkOnceJump(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            UUID id = player.getUUID();
            Double prevY = lastY.put(id, player.getY());
            if (prevY != null && jumpViolation(player.onGround(), player.isPassenger(),
                    player.getY() - prevY, player.hurtTime > 0,
                    player.isInWater(), player.isInLava(),
                    player.onClimbable(), player.isFallFlying())) {
                completed.add(id);
            }
        }
        sendOneShotHud(session, "jump");
    }

    private void checkLook(GameSession session, boolean up) {
        for (ServerPlayer player : session.activePlayers()) {
            float pitch = player.getXRot();
            if (up ? pitch <= -60.0f : pitch >= 60.0f) {
                completed.add(player.getUUID());
            }
        }
        sendOneShotHud(session, up ? "look_up" : "look_down");
    }

    private void eliminateIncomplete(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (!completed.contains(player.getUUID())) {
                violators.add(player);
            }
        }
        if (!violators.isEmpty()) {
            session.eliminateBatch(violators, banKey() + ".violated");
        }
    }

    private void eliminate(GameSession session, List<ServerPlayer> violators) {
        if (!violators.isEmpty()) {
            session.eliminateBatch(violators, banKey() + ".violated");
        }
    }

    private void sendHud(GameSession session) {
        int seconds = Math.max(0, banLeft / 20);
        if (ban.sustained) {
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.bans.hud",
                    ServerI18n.tr(banKey() + ".name").getString(), seconds));
        } else {
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.bans.hud_once",
                    ServerI18n.tr(banKey() + ".name").getString(), seconds,
                    completed.size(), session.activeCount()));
        }
    }

    private void sendOneShotHud(GameSession session, String action) {
        if (session.phaseTicks % 20 == 0) {
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.bans.hud_once",
                    ServerI18n.tr(banKey() + ".name").getString(),
                    Math.max(0, banLeft / 20), completed.size(), session.activeCount()));
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        lastY.remove(playerId);
        stillness.remove(playerId);
        movement.remove(playerId);
        completed.remove(playerId);
    }

    @Override
    public void onEnd(GameSession session) {
        lastY.clear();
        stillness.clear();
        movement.clear();
        completed.clear();
    }

    /** 20 tick 滑动窗口内的实际路径，超过预算视为“没有停下”。 */
    private static final class MovementWindow {
        private final int window;
        private final double budget;
        private final Map<UUID, Vec3> last = new HashMap<>();
        private final Map<UUID, Deque<Double>> steps = new HashMap<>();

        MovementWindow(int window, double budget) {
            this.window = window;
            this.budget = budget;
        }

        boolean movedTooMuch(UUID id, Vec3 position, boolean eligible) {
            if (!eligible) {
                remove(id);
                return false;
            }
            Vec3 previous = last.put(id, position);
            if (previous == null) {
                steps.put(id, new ArrayDeque<>());
                return false;
            }
            double step = Math.sqrt((position.x - previous.x) * (position.x - previous.x)
                    + (position.z - previous.z) * (position.z - previous.z));
            if (step > 0.5) {
                // 传送/复活等位置跳变不作为连续移动
                steps.put(id, new ArrayDeque<>());
                return false;
            }
            Deque<Double> queue = steps.computeIfAbsent(id, ignored -> new ArrayDeque<>());
            queue.addLast(step);
            while (queue.size() > window) {
                queue.removeFirst();
            }
            double total = 0;
            for (double value : queue) {
                total += value;
            }
            if (total > budget) {
                queue.clear();
                return true;
            }
            return false;
        }

        void remove(UUID id) {
            last.remove(id);
            steps.remove(id);
        }

        void clear() {
            last.clear();
            steps.clear();
        }
    }
}
