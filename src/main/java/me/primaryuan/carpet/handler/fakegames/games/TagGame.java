package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.handler.fakegames.SustainCounter;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 鬼抓人：冰冻与感染两种模式共用触碰检测（距鬼 1.8 格内持续 5 tick）。
 *
 * <p>冰冻：被触碰者冻结（游戏逻辑状态，不修改任何玩家属性）；自由玩家靠近冻结
 * 队友并持续接触一小段时间才解救，接触中断立即清零，不跨目标累计。鬼与冻结者
 * 有明确粒子/标题标记。全部被冻结 → 鬼胜，时限到仍有自由玩家 → 逃跑方胜。</p>
 *
 * <p>感染：被触碰者加入鬼阵营；全部感染 → 鬼胜，时限到仍有幸存者 → 幸存方胜。</p>
 */
public final class TagGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final double TOUCH_DISTANCE = 1.8;
    private static final int TOUCH_SUSTAIN = 5;
    private static final int DURATION_TICKS = 20 * 180;
    private static final double FROZEN_MOVE_LIMIT = 2.0;
    private static final int HINT_INTERVAL = 40;

    private boolean infection;
    private UUID firstGhost;
    private final List<UUID> ghosts = new ArrayList<>();
    private final Map<UUID, Vec3> frozen = new HashMap<>();
    private final SustainCounter touchTicks = new SustainCounter(TOUCH_SUSTAIN);
    private final Map<UUID, SustainCounter> rescueCounters = new HashMap<>();
    private int rescueRequired = 20;
    private int timeLeft;
    private int hintCooldown;
    private final Set<UUID> glowing = new LinkedHashSet<>();
    private final Map<UUID, Boolean> originalGlow = new HashMap<>();

    @Override
    public void onStart(GameSession session) {
        infection = "infection".equals(session.option("mode"));
        int rescueSeconds = (int) Math.ceil(session.doubleOption("rescue_seconds", 1.0) * 20);
        rescueRequired = Math.max(1, rescueSeconds);
        rescueCounters.clear();
        timeLeft = Math.max(30, session.intOption("duration", 180)) * 20;
        List<ServerPlayer> actives = session.activePlayers();
        ghosts.clear();
        firstGhost = actives.get(session.level.getRandom().nextInt(actives.size())).getUUID();
        ghosts.add(firstGhost);
        touchTicks.clear();
        frozen.clear();
        glowing.clear();
        originalGlow.clear();
        String modeKey = infection ? "infection" : "freeze";
        ServerPlayer ghostPlayer = session.player(firstGhost);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.tag.start_" + modeKey,
                ghostPlayer == null ? "?" : ghostPlayer.getName().getString(),
                timeLeft / 20, rescueRequired / 20));
        if (ghostPlayer != null) {
            ghostPlayer.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.tag.you_ghost_" + modeKey));
        }
        markGhosts(session);
    }

    @Override
    public void onTick(GameSession session) {
        if (--timeLeft <= 0) {
            timeUp(session);
            return;
        }
        if (checkTouches(session)) {
            return;
        }
        checkFrozenMovement(session);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        checkRescue(session);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        if (--hintCooldown <= 0) {
            hintCooldown = HINT_INTERVAL;
            sendHints(session);
        }
    }

    /** 触碰检测（每 tick）：同一目标连续 5 tick 处于任一鬼的范围内才触发。 */
    private boolean checkTouches(GameSession session) {
        List<ServerPlayer> ghostPlayers = new ArrayList<>();
        Set<UUID> ghostIds = new HashSet<>(ghosts);
        for (UUID id : ghosts) {
            ServerPlayer ghost = session.player(id);
            if (ghost != null && ghost.level() == session.level
                    && session.member(id) != null
                    && session.member(id).state == me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE) {
                ghostPlayers.add(ghost);
            }
        }
        Set<UUID> contacts = new LinkedHashSet<>();
        Map<UUID, ServerPlayer> byGhost = new LinkedHashMap<>();
        for (ServerPlayer player : session.activePlayers()) {
            UUID id = player.getUUID();
            if (ghostIds.contains(id) || frozen.containsKey(id) || player.level() != session.level) {
                continue;
            }
            for (ServerPlayer ghost : ghostPlayers) {
                if (ghost.getUUID().equals(id)) {
                    continue;
                }
                if (ghost.distanceToSqr(player) <= TOUCH_DISTANCE * TOUCH_DISTANCE
                        && ghost.hasLineOfSight(player)) {
                    contacts.add(id);
                    byGhost.putIfAbsent(id, ghost);
                    break;
                }
            }
        }
        Set<UUID> ready = touchTicks.update(contacts);
        if (ready.isEmpty()) {
            return false;
        }
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (ready.contains(player.getUUID())) {
                targets.add(player);
            }
        }
        for (ServerPlayer target : targets) {
            UUID id = target.getUUID();
            if (session.member(id) == null
                    || session.member(id).state != me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE
                    || ghosts.contains(id) || frozen.containsKey(id)) {
                touchTicks.remove(id);
                continue;
            }
            ServerPlayer byGhostPlayer = byGhost.get(id);
            String ghostName = byGhostPlayer == null ? "?" : byGhostPlayer.getName().getString();
            if (infection) {
                ghosts.add(id);
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.tag.infected",
                        ghostName, target.getName().getString(), ghosts.size()));
                markGhost(session, target);
                sendRoleTitles(session);
            } else {
                frozen.put(id, target.position());
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.tag.frozen",
                        ghostName, target.getName().getString(), freeRunnerCount(session)));
                target.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.tag.frozen_hint",
                        rescueRequired / 20));
            }
            touchTicks.remove(id);
            Vec3 pos = target.position();
            session.level.sendParticles(ParticleTypes.SNOWFLAKE,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.3, 0.5, 0.3, 0.02);
        }
        checkEnd(session);
        return session.phase != SessionPhase.RUNNING;
    }

    /** 冻结者自行移动超限 → 淘汰（本局规则的移动惩罚） */
    private void checkFrozenMovement(GameSession session) {
        if (infection) {
            return;
        }
        List<ServerPlayer> moved = new ArrayList<>();
        for (Map.Entry<UUID, Vec3> entry : new ArrayList<>(frozen.entrySet())) {
            ServerPlayer player = session.player(entry.getKey());
            if (player == null) {
                continue;
            }
            Vec3 anchor = entry.getValue();
            double dx = player.getX() - anchor.x;
            double dz = player.getZ() - anchor.z;
            double dy = player.getY() - anchor.y;
            if (dx * dx + dy * dy + dz * dz > FROZEN_MOVE_LIMIT * FROZEN_MOVE_LIMIT) {
                moved.add(player);
            }
        }
        if (!moved.isEmpty()) {
            session.eliminateBatch(moved, "carpetprimaryuan.fakegames.reason.frozen_moved");
            if (session.phase == SessionPhase.RUNNING) {
                checkEnd(session);
            }
        }
    }

    /**
     * 解救：自由玩家靠近冻结队友并持续接触达到阈值才解除；每 tick 用当前接触集合
     * 推进，中断立即清零，且每个冻结目标独立计数。
     */
    private void checkRescue(GameSession session) {
        if (infection || frozen.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> contactsByTarget = new LinkedHashMap<>();
        for (Map.Entry<UUID, Vec3> entry : frozen.entrySet()) {
            ServerPlayer frozenPlayer = session.player(entry.getKey());
            if (frozenPlayer == null) {
                continue;
            }
            List<UUID> contacts = new ArrayList<>();
            for (ServerPlayer player : session.activePlayers()) {
                UUID rescuerId = player.getUUID();
                if (rescuerId.equals(entry.getKey())
                        || ghosts.contains(rescuerId)
                        || frozen.containsKey(rescuerId)
                        || player.level() != session.level) {
                    continue;
                }
                if (player.distanceToSqr(frozenPlayer) <= TOUCH_DISTANCE * TOUCH_DISTANCE) {
                    contacts.add(rescuerId);
                }
            }
            contactsByTarget.put(entry.getKey(), contacts);
        }
        for (Map.Entry<UUID, List<UUID>> entry : contactsByTarget.entrySet()) {
            SustainCounter counter = rescueCounters.computeIfAbsent(entry.getKey(),
                    ignored -> new SustainCounter(rescueRequired));
            Set<UUID> ready = counter.update(entry.getValue());
            if (!ready.isEmpty()) {
                ServerPlayer frozenPlayer = session.player(entry.getKey());
                frozen.remove(entry.getKey());
                rescueCounters.remove(entry.getKey());
                if (frozenPlayer != null) {
                    session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.tag.rescued",
                            session.nameOf(ready.iterator().next()), frozenPlayer.getName().getString()));
                }
            }
        }
    }

    private void sendHints(GameSession session) {
        int seconds = timeLeft / 20;
        for (ServerPlayer player : session.activePlayers()) {
            if (ghosts.contains(player.getUUID())) {
                FakeGameEffects.actionbar(player, ServerI18n.tr(
                        infection ? "carpetprimaryuan.fakegames.tag.hud_ghost_inf"
                                : "carpetprimaryuan.fakegames.tag.hud_ghost",
                        seconds, ghosts.size(), freeRunnerCount(session)));
            } else if (frozen.containsKey(player.getUUID())) {
                FakeGameEffects.actionbar(player, ServerI18n.tr(
                        "carpetprimaryuan.fakegames.tag.hud_frozen", seconds,
                        rescueRequired / 20));
            } else {
                FakeGameEffects.actionbar(player, ServerI18n.tr(
                        "carpetprimaryuan.fakegames.tag.hud_runner", seconds));
            }
        }
    }

    private void sendRoleTitles(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            boolean ghost = ghosts.contains(player.getUUID());
            FakeGameEffects.title(player,
                    ServerI18n.tr(infection ? "carpetprimaryuan.fakegames.tag.role_ghost_inf"
                                    : "carpetprimaryuan.fakegames.tag.role_ghost"),
                    ServerI18n.tr(ghost ? "carpetprimaryuan.fakegames.tag.now_ghost"
                            : "carpetprimaryuan.fakegames.tag.still_runner"),
                    5, 40, 10);
        }
    }

    private void markGhosts(GameSession session) {
        for (UUID id : ghosts) {
            ServerPlayer player = session.player(id);
            if (player != null) {
                originalGlow.putIfAbsent(id, player.hasGlowingTag());
                player.setGlowingTag(true);
                glowing.add(id);
            }
        }
    }

    private void markGhost(GameSession session, ServerPlayer player) {
        originalGlow.putIfAbsent(player.getUUID(), player.hasGlowingTag());
        player.setGlowingTag(true);
        glowing.add(player.getUUID());
    }

    private int freeRunnerCount(GameSession session) {
        int count = 0;
        for (ServerPlayer player : session.activePlayers()) {
            if (!ghosts.contains(player.getUUID()) && !frozen.containsKey(player.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private void checkEnd(GameSession session) {
        if (infection) {
            if (freeRunnerCount(session) == 0) {
                session.finishTeam(ghosts,
                        ServerI18n.tr("carpetprimaryuan.fakegames.tag.ghosts_win"));
            }
        } else if (freeRunnerCount(session) == 0) {
            session.finish(firstGhost, ServerI18n.tr("carpetprimaryuan.fakegames.tag.ghosts_win"));
        }
    }

    private void timeUp(GameSession session) {
        List<String> survivors = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (!ghosts.contains(player.getUUID()) && !frozen.containsKey(player.getUUID())) {
                survivors.add(player.getName().getString());
            }
        }
        String key = survivors.isEmpty()
                ? "carpetprimaryuan.fakegames.tag.ghosts_win"
                : (infection ? "carpetprimaryuan.fakegames.tag.survivors_win_inf"
                : "carpetprimaryuan.fakegames.tag.survivors_win");
        if (survivors.isEmpty()) {
            session.finishTeam(ghosts, ServerI18n.tr(key));
        } else {
            List<UUID> winners = new ArrayList<>();
            for (ServerPlayer player : session.activePlayers()) {
                if (survivors.contains(player.getName().getString())) {
                    winners.add(player.getUUID());
                }
            }
            session.finishTeam(winners, ServerI18n.tr(key, String.join(", ", survivors)));
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        ghosts.remove(playerId);
        frozen.remove(playerId);
        touchTicks.remove(playerId);
        rescueCounters.remove(playerId);
        restoreGlow(playerId, player);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        if (ghosts.isEmpty()) {
            timeUp(session);
            return;
        }
        checkEnd(session);
    }

    private void restoreGlow(UUID id, ServerPlayer player) {
        if (!glowing.remove(id)) {
            return;
        }
        if (player != null) {
            player.setGlowingTag(Boolean.TRUE.equals(originalGlow.get(id)));
        }
        originalGlow.remove(id);
    }

    @Override
    public void onEnd(GameSession session) {
        for (UUID id : new ArrayList<>(glowing)) {
            restoreGlow(id, session.player(id));
        }
        ghosts.clear();
        frozen.clear();
        touchTicks.clear();
        rescueCounters.clear();
        glowing.clear();
        originalGlow.clear();
    }
}
