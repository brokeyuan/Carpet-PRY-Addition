package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 抢安全点：反应类先到得分。每轮出现 1~N 个安全点，第一个有效进入者立即得分并
 * 关闭该点；倒计时结束时已领取的归属不再重新分配，没被领取的点直接关闭。
 * 不淘汰玩家、不套用“最后一人获胜”，固定轮数后按总分排名。
 *
 * <p>同 tick 多人进入同一点：按与点中心的距离升序，再按 UUID 字符串稳定比较，
 * 只把点判给第一人；不声称能区分低于服务端 tick 精度的反应先后。</p>
 */
public final class SafeSpotGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    /** 每个点每轮最多被领取一次 */
    private static final double CLAIM_DISTANCE = 1.5;
    private static final double SPOT_SPACING = 5.0;
    private static final int MAX_SAMPLES_PER_SPOT = 40;

    private static final class Spot {
        final BlockPos pos;
        UUID claimedBy;
        boolean claimed;

        Spot(BlockPos pos) {
            this.pos = pos;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private final Set<UUID> claimedThisRound = new HashSet<>();
    private int roundLeft;
    private int round;
    private int rounds;
    private int roundTicks;
    private int markerCounter;

    @Override
    public void onStart(GameSession session) {
        round = 0;
        rounds = Math.max(1, session.intOption("rounds", 8));
        roundTicks = Math.max(5, session.intOption("round_seconds", 15)) * 20;
        startRound(session);
    }

    @Override
    public void onTick(GameSession session) {
        if (--roundLeft <= 0) {
            endRound(session);
            return;
        }
        if (++markerCounter >= 10) {
            markerCounter = 0;
            markSpots(session);
        }
        checkClaims(session);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        if (roundLeft % 20 == 0) {
            int claimed = claimedCount();
            int best = 0;
            for (ServerPlayer player : session.activePlayers()) {
                GameMember member = session.member(player.getUUID());
                best = Math.max(best, member == null ? 0 : member.score);
            }
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.hud",
                    roundLeft / 20, claimed, spots.size(), best));
        }
    }

    private void startRound(GameSession session) {
        if (session.activeCount() == 0) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.draw"));
            return;
        }
        round++;
        spots.clear();
        claimedThisRound.clear();
        int wanted = desiredSpots(session);
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i < wanted; i++) {
            BlockPos spot = sampleSpot(session, found);
            if (spot != null) {
                found.add(spot);
            }
        }
        for (BlockPos pos : found) {
            spots.add(new Spot(pos));
        }
        roundLeft = roundTicks;
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.round",
                round, rounds, spots.size(), roundTicks / 20));
        if (spots.isEmpty()) {
            // 地形无法生成点时，直接进入下一轮或结束，不把整局卡死
            if (round >= rounds) {
                finishByScore(session);
            } else {
                roundLeft = 20;
            }
            return;
        }
        markSpots(session);
    }

    private int desiredSpots(GameSession session) {
        String setting = session.option("spots");
        if ("auto".equals(setting)) {
            return Math.max(1, Math.min(4, session.activeCount() / 2));
        }
        try {
            return Math.max(1, Math.min(4, Integer.parseInt(setting)));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** 采样一个合法安全点：地面实心、上方两格空气、非流体、与既有安全点保持间距。 */
    private BlockPos sampleSpot(GameSession session, List<BlockPos> existing) {
        for (int attempt = 0; attempt < MAX_SAMPLES_PER_SPOT; attempt++) {
            int x = session.center.getX() + session.level.getRandom().nextInt(session.radius * 2 + 1)
                    - session.radius;
            int z = session.center.getZ() + session.level.getRandom().nextInt(session.radius * 2 + 1)
                    - session.radius;
            double dx = x + 0.5 - (session.center.getX() + 0.5);
            double dz = z + 0.5 - (session.center.getZ() + 0.5);
            if (dx * dx + dz * dz > (double) session.radius * session.radius) {
                continue;
            }
            int y = session.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!session.level.hasChunkAt(pos)) {
                continue;
            }
            BlockState ground = session.level.getBlockState(pos.below());
            if (ground.isAir() || !ground.getFluidState().isEmpty()) {
                continue;
            }
            if (!session.level.getBlockState(pos).isAir()
                    || !session.level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (Math.abs(y - session.center.getY()) > 16) {
                continue;
            }
            boolean tooClose = false;
            for (BlockPos other : existing) {
                if (other.distSqr(pos) < SPOT_SPACING * SPOT_SPACING) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                return pos;
            }
        }
        return null;
    }

    private void markSpots(GameSession session) {
        for (Spot spot : spots) {
            if (spot.claimed) {
                continue;
            }
            double x = spot.pos.getX() + 0.5;
            double z = spot.pos.getZ() + 0.5;
            double y = spot.pos.getY();
            for (ServerPlayer player : session.activePlayers()) {
                me.primaryuan.carpet.handler.fakegames.FakeGameBoundary.sendTo(player,
                        ParticleTypes.HAPPY_VILLAGER, x, y + 0.5, z, 4, 0.4, 0.2, 0.4, 0.0);
                me.primaryuan.carpet.handler.fakegames.FakeGameBoundary.sendTo(player,
                        ParticleTypes.END_ROD, x, y + 1.6, z, 2, 0.1, 0.3, 0.1, 0.0);
            }
        }
    }

    /** 先到先得：同 tick 按距离、再按 UUID 稳定排序。 */
    private void checkClaims(GameSession session) {
        List<ServerPlayer> candidates = new ArrayList<>(session.activePlayers());
        candidates.sort((a, b) -> {
            int cmp = a.getUUID().toString().compareTo(b.getUUID().toString());
            return cmp;
        });
        for (Spot spot : spots) {
            if (spot.claimed) {
                continue;
            }
            ServerPlayer winner = null;
            double bestDistance = Double.MAX_VALUE;
            for (ServerPlayer player : candidates) {
                if (claimedThisRound.contains(player.getUUID())) {
                    continue;
                }
                double dx = player.getX() - (spot.pos.getX() + 0.5);
                double dz = player.getZ() - (spot.pos.getZ() + 0.5);
                double dy = player.getY() - spot.pos.getY();
                if (!standsOnSpot(dx, dz, dy, player.onGround())) {
                    continue;
                }
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance - 1.0E-9
                        || (Math.abs(distance - bestDistance) <= 1.0E-9
                        && winner != null && player.getUUID().toString()
                        .compareTo(winner.getUUID().toString()) < 0)) {
                    bestDistance = distance;
                    winner = player;
                }
            }
            if (winner != null) {
                spot.claimed = true;
                spot.claimedBy = winner.getUUID();
                claimedThisRound.add(winner.getUUID());
                GameMember member = session.member(winner.getUUID());
                if (member != null) {
                    member.score++;
                }
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.claimed",
                        winner.getName().getString(), spot.pos.getX(), spot.pos.getY(), spot.pos.getZ()));
                for (ServerPlayer player : session.activePlayers()) {
                    me.primaryuan.carpet.handler.fakegames.FakeGameEffects.sound(player,
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.6f);
                }
            }
        }
    }

    private void endRound(GameSession session) {
        int claimed = claimedCount();
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.round_end",
                round, claimed, spots.size()));
        if (round >= rounds) {
            finishByScore(session);
        } else {
            startRound(session);
        }
    }

    private void finishByScore(GameSession session) {
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
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
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.draw"));
        } else {
            session.finish(bestId);
        }
    }

    private int claimedCount() {
        int count = 0;
        for (Spot spot : spots) {
            if (spot.claimed) {
                count++;
            }
        }
        return count;
    }

    /** 占点纯判定：水平距离内、高度差 [-0.5, 2]、在地面（供单测复用） */
    static boolean standsOnSpot(double dx, double dz, double dy, boolean onGround) {
        return dx * dx + dz * dz <= CLAIM_DISTANCE * CLAIM_DISTANCE
                && dy >= -0.5 && dy <= 2.0
                && onGround;
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        for (Spot spot : spots) {
            if (playerId.equals(spot.claimedBy)) {
                // 已领取的归属保留，不在倒计时结束时重新分配
                spot.claimed = true;
            }
        }
        if (session.phase == SessionPhase.RUNNING && session.activeCount() == 0) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.safespot.draw"));
        }
    }

    @Override
    public void onEnd(GameSession session) {
        spots.clear();
        claimedThisRound.clear();
    }
}
