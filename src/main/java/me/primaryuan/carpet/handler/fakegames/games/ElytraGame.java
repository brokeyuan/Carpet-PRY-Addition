package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameBoundary;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 鞘翅游戏：使用玩家自己的鞘翅，穿过虚拟圆环或收集虚拟金币，不生成真实物品。
 *
 * <p>高速经过用“上一 tick 位置 → 当前 tick 位置”的线段与目标球体求交，避免只采样
 * 当前位置导致漏判；圆环按顺序计分、金币每个只计一次。耐久、烟花与飞行事故仍按
 * 正常生存处理。</p>
 */
public final class ElytraGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final double RING_RADIUS = 2.5;
    private static final double COIN_RADIUS = 1.2;

    private final List<Vec3> rings = new ArrayList<>();
    private final List<Vec3> coins = new ArrayList<>();
    private final Set<Integer> collected = new LinkedHashSet<>();
    private final Map<UUID, Integer> nextRing = new HashMap<>();
    private final Map<UUID, Vec3> previous = new HashMap<>();
    private String mode = "rings";
    private int timeLeft;
    private int hudCooldown;

    @Override
    public void onStart(GameSession session) {
        mode = session.option("mode");
        timeLeft = Math.max(30, session.intOption("duration", 180)) * 20;
        rings.clear();
        coins.clear();
        collected.clear();
        nextRing.clear();
        previous.clear();
        if ("collect".equals(mode)) {
            buildCoins(session, Math.max(4, session.intOption("rings", 8)) * 3);
        } else {
            buildRings(session, Math.max(3, session.intOption("rings", 8)));
            for (ServerPlayer player : session.activePlayers()) {
                nextRing.put(player.getUUID(), 0);
            }
        }
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.elytra.start",
                ServerI18n.tr("carpetprimaryuan.fakegames.elytra.mode." + mode).getString(),
                timeLeft / 20));
        renderAll(session);
    }

    private void buildRings(GameSession session, int count) {
        double startAngle = Math.toRadians(session.activePlayers().isEmpty()
                ? 0 : session.activePlayers().get(0).getYRot() + 90.0);
        double loopRadius = Math.max(16.0, Math.min(session.radius - 6.0, 32.0));
        double baseY = Math.min(session.maxY - 6.0, session.center.getY() + 10.0);
        for (int i = 0; i < count; i++) {
            double angle = startAngle + Math.PI * 2 * i / count;
            double x = session.center.getX() + 0.5 + Math.cos(angle) * loopRadius;
            double z = session.center.getZ() + 0.5 + Math.sin(angle) * loopRadius;
            double y = Math.max(session.minY + 4.0,
                    Math.min(session.maxY - 4.0, baseY + Math.sin(i * 0.9) * 5.0));
            rings.add(new Vec3(x, y, z));
        }
    }

    private void buildCoins(GameSession session, int count) {
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 40; attempt++) {
                double angle = session.level.getRandom().nextDouble() * Math.PI * 2;
                double distance = 8 + session.level.getRandom().nextDouble()
                        * Math.max(8.0, session.radius - 10.0);
                double x = session.center.getX() + 0.5 + Math.cos(angle) * distance;
                double z = session.center.getZ() + 0.5 + Math.sin(angle) * distance;
                double y = session.minY + 6.0 + session.level.getRandom().nextDouble()
                        * Math.max(8.0, session.maxY - session.minY - 12.0);
                Vec3 pos = new Vec3(x, y, z);
                boolean tooClose = false;
                for (Vec3 other : coins) {
                    if (other.distanceToSqr(pos) < 6.0 * 6.0) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    coins.add(pos);
                    break;
                }
            }
        }
    }

    @Override
    public void onTick(GameSession session) {
        if (--timeLeft <= 0) {
            timeUp(session);
            return;
        }
        for (ServerPlayer player : session.activePlayers()) {
            Vec3 now = player.position();
            Vec3 before = previous.put(player.getUUID(), now);
            if (before == null) {
                continue;
            }
            if ("collect".equals(mode)) {
                collectCoins(session, player, before, now);
            } else {
                passRings(session, player, before, now);
            }
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
        }
        if (--hudCooldown <= 0) {
            hudCooldown = 20;
            sendHud(session);
            renderAll(session);
        }
    }

    private void passRings(GameSession session, ServerPlayer player, Vec3 before, Vec3 now) {
        Integer index = nextRing.get(player.getUUID());
        if (index == null) {
            return;
        }
        if (index >= rings.size()) {
            return;
        }
        Vec3 ring = rings.get(index);
        if (segmentHitsSphere(before, now, ring, RING_RADIUS)) {
            index++;
            nextRing.put(player.getUUID(), index);
            GameMember member = session.member(player.getUUID());
            if (member != null) {
                member.score++;
            }
            session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.elytra.ring",
                    player.getName().getString(), index, rings.size()));
            for (ServerPlayer viewer : session.activePlayers()) {
                FakeGameBoundary.sendTo(viewer, ParticleTypes.END_ROD,
                        ring.x, ring.y, ring.z, 12, 0.4, 0.4, 0.4, 0.05);
            }
            if (index >= rings.size()) {
                session.finish(player.getUUID());
            }
        }
    }

    private void collectCoins(GameSession session, ServerPlayer player, Vec3 before, Vec3 now) {
        for (int i = 0; i < coins.size(); i++) {
            if (collected.contains(i)) {
                continue;
            }
            Vec3 coin = coins.get(i);
            if (segmentHitsSphere(before, now, coin, COIN_RADIUS)) {
                collected.add(i);
                GameMember member = session.member(player.getUUID());
                if (member != null) {
                    member.score++;
                }
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.elytra.coin",
                        player.getName().getString(), member == null ? 0 : member.score));
                for (ServerPlayer viewer : session.activePlayers()) {
                    FakeGameBoundary.sendTo(viewer, ParticleTypes.HAPPY_VILLAGER,
                            coin.x, coin.y, coin.z, 10, 0.3, 0.3, 0.3, 0.02);
                }
                if (collected.size() >= coins.size()) {
                    session.finish(player.getUUID());
                    return;
                }
            }
        }
    }

    /** 线段到球心最短距离 <= 半径（含端点），用于高速穿环/收集。 */
    public static boolean segmentHitsSphere(Vec3 start, Vec3 end, Vec3 center, double radius) {
        Vec3 segment = end.subtract(start);
        double lengthSq = segment.lengthSqr();
        if (lengthSq <= 1.0E-9) {
            return start.distanceToSqr(center) <= radius * radius;
        }
        double t = center.subtract(start).dot(segment) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec3 closest = start.add(segment.scale(t));
        return closest.distanceToSqr(center) <= radius * radius;
    }

    private void renderAll(GameSession session) {
        if ("collect".equals(mode)) {
            for (Vec3 coin : coins) {
                if (collected.contains(coins.indexOf(coin))) {
                    continue;
                }
                for (ServerPlayer player : session.activePlayers()) {
                    FakeGameBoundary.sendTo(player, ParticleTypes.END_ROD,
                            coin.x, coin.y, coin.z, 2, 0.1, 0.1, 0.1, 0.0);
                    FakeGameBoundary.sendTo(player, ParticleTypes.HAPPY_VILLAGER,
                            coin.x, coin.y, coin.z, 1, 0.2, 0.2, 0.2, 0.0);
                }
            }
        } else {
            for (int i = 0; i < rings.size(); i++) {
                Vec3 center = rings.get(i);
                Vec3 next = rings.get((i + 1) % rings.size());
                Vec3 direction = next.subtract(center).normalize();
                Vec3 side = direction.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1.0E-6) {
                    side = new Vec3(1, 0, 0);
                }
                side = side.normalize();
                Vec3 up = side.cross(direction).normalize();
                for (ServerPlayer player : session.activePlayers()) {
                    Integer nextIndex = nextRing.get(player.getUUID());
                    if (nextIndex != null && nextIndex > i) {
                        continue;
                    }
                    for (int p = 0; p < 24; p++) {
                        double angle = Math.PI * 2 * p / 24;
                        Vec3 point = center.add(side.scale(Math.cos(angle) * RING_RADIUS))
                                .add(up.scale(Math.sin(angle) * RING_RADIUS));
                        FakeGameBoundary.sendTo(player, ParticleTypes.END_ROD,
                                point.x, point.y, point.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    private void sendHud(GameSession session) {
        int seconds = timeLeft / 20;
        for (ServerPlayer player : session.activePlayers()) {
            GameMember member = session.member(player.getUUID());
            int score = member == null ? 0 : member.score;
            if ("collect".equals(mode)) {
                FakeGameEffectsBar.send(player, ServerI18n.tr(
                        "carpetprimaryuan.fakegames.elytra.hud_collect",
                        score, collected.size(), coins.size(), seconds,
                        player.isFallFlying() ? "" : ServerI18n.tr(
                                "carpetprimaryuan.fakegames.elytra.glide_hint").getString()));
            } else {
                int index = nextRing.getOrDefault(player.getUUID(), 0);
                FakeGameEffectsBar.send(player, ServerI18n.tr(
                        "carpetprimaryuan.fakegames.elytra.hud_rings",
                        index, rings.size(), seconds,
                        player.isFallFlying() ? "" : ServerI18n.tr(
                                "carpetprimaryuan.fakegames.elytra.glide_hint").getString()));
            }
        }
    }

    private void timeUp(GameSession session) {
        int best = -1;
        UUID bestId = null;
        boolean tie = false;
        for (GameMember member : session.allMembers()) {
            if (member.score > best) {
                best = member.score;
                bestId = member.id;
                tie = false;
            } else if (member.score == best) {
                tie = true;
            }
        }
        if (bestId == null || best <= 0 || tie) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.elytra.draw"));
        } else {
            session.finish(bestId);
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        nextRing.remove(playerId);
        previous.remove(playerId);
        if (session.phase == SessionPhase.RUNNING && session.activeCount() == 0) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.elytra.draw"));
        }
    }

    @Override
    public void onEnd(GameSession session) {
        rings.clear();
        coins.clear();
        collected.clear();
        nextRing.clear();
        previous.clear();
    }

    private static final class FakeGameEffectsBar {
        static void send(ServerPlayer player, net.minecraft.network.chat.Component component) {
            me.primaryuan.carpet.handler.fakegames.FakeGameEffects.actionbar(player, component);
        }
    }
}
