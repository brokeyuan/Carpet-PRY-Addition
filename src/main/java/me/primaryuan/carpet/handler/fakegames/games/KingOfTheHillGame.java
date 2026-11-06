package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameBoundary;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 占点游戏：管理员/发起者设置 1~4 个点，支持单点、单点轮换、多点同时开启。
 * 每个点独立显示开启/争夺/得分状态；关闭的点不显示也不得分。独占每秒 +1 分，
 * 多人同时在场则争夺暂停得分。时限到按积分排名。
 */
public final class KingOfTheHillGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final double ZONE_RADIUS = 4.0;
    private static final int SCORE_INTERVAL = 20;
    private static final int MARKER_INTERVAL = 10;

    private record Hill(BlockPos pos, int index) {}

    private final List<Hill> hills = new ArrayList<>();
    private String mode = "single";
    private int activeIndex;
    private int rotateTicks;
    private int rotateLeft;
    private int timeLeft;
    private int scoreCounter;
    private int markerCounter;
    private int hudCooldown;

    @Override
    public void onStart(GameSession session) {
        mode = session.option("mode");
        rotateTicks = Math.max(10, session.intOption("rotate_seconds", 30)) * 20;
        rotateLeft = rotateTicks;
        activeIndex = 0;
        timeLeft = Math.max(30, session.intOption("duration", 180)) * 20;
        buildHills(session, Math.max(1, Math.min(4, session.intOption("points", 1))));
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.koth.start",
                hills.size(), mode, timeLeft / 20));
        markAll(session);
    }

    private void buildHills(GameSession session, int count) {
        hills.clear();
        int[][] offsets = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int i = 0; i < count; i++) {
            int x = session.center.getX() + offsets[i][0];
            int z = session.center.getZ() + offsets[i][1];
            int y = session.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (session.level.hasChunkAt(pos)) {
                BlockState ground = session.level.getBlockState(pos.below());
                if (ground.isAir() || !ground.getFluidState().isEmpty()
                        || !session.level.getBlockState(pos).isAir()
                        || !session.level.getBlockState(pos.above()).isAir()) {
                    pos = new BlockPos(x, session.center.getY(), z);
                }
            } else {
                pos = new BlockPos(x, session.center.getY(), z);
            }
            hills.add(new Hill(pos, i));
        }
    }

    @Override
    public void onTick(GameSession session) {
        timeLeft--;
        if (timeLeft <= 0) {
            timeUp(session);
            return;
        }
        if (!"simultaneous".equals(mode) && --rotateLeft <= 0) {
            rotateLeft = rotateTicks;
            int previous = activeIndex;
            activeIndex = (activeIndex + 1) % Math.max(1, hills.size());
            if (previous != activeIndex) {
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.koth.rotate",
                        activeIndex + 1));
            }
        }
        if (++markerCounter >= MARKER_INTERVAL) {
            markerCounter = 0;
            markAll(session);
        }
        if (++scoreCounter >= SCORE_INTERVAL) {
            scoreCounter = 0;
            awardPoints(session);
        }
        if (--hudCooldown <= 0) {
            hudCooldown = 20;
            updateHud(session);
        }
    }

    private boolean active(Hill hill) {
        if ("simultaneous".equals(mode)) {
            return true;
        }
        return hill.index == activeIndex;
    }

    private void markAll(GameSession session) {
        for (Hill hill : hills) {
            if (!active(hill)) {
                continue;
            }
            double x = hill.pos.getX() + 0.5;
            double z = hill.pos.getZ() + 0.5;
            double y = hill.pos.getY();
            for (ServerPlayer player : session.activePlayers()) {
                int points = (int) (2 * Math.PI * ZONE_RADIUS / 2.0);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    FakeGameBoundary.sendTo(player, ParticleTypes.END_ROD,
                            x + ZONE_RADIUS * Math.cos(angle), y + 0.4,
                            z + ZONE_RADIUS * Math.sin(angle), 1, 0.02, 0.1, 0.02, 0.0);
                }
                FakeGameBoundary.sendTo(player, ParticleTypes.HAPPY_VILLAGER,
                        x, y + 1.2, z, 2, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    private void awardPoints(GameSession session) {
        for (Hill hill : hills) {
            if (!active(hill)) {
                continue;
            }
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer player : session.activePlayers()) {
                if (inHill(player, hill)) {
                    inside.add(player);
                }
            }
            if (inside.size() == 1) {
                GameMember member = session.member(inside.get(0).getUUID());
                if (member != null) {
                    member.score++;
                }
            }
        }
    }

    private boolean inHill(ServerPlayer player, Hill hill) {
        double dx = player.getX() - (hill.pos.getX() + 0.5);
        double dz = player.getZ() - (hill.pos.getZ() + 0.5);
        return dx * dx + dz * dz <= ZONE_RADIUS * ZONE_RADIUS
                && Math.abs(player.getY() - hill.pos.getY()) <= 6.0;
    }

    private int insideCount(GameSession session, Hill hill) {
        int count = 0;
        for (ServerPlayer player : session.activePlayers()) {
            if (inHill(player, hill)) {
                count++;
            }
        }
        return count;
    }

    private void updateHud(GameSession session) {
        int seconds = timeLeft / 20;
        for (ServerPlayer player : session.activePlayers()) {
            GameMember member = session.member(player.getUUID());
            int score = member == null ? 0 : member.score;
            Hill nearest = hills.isEmpty() ? null : hills.get(activeIndex % hills.size());
            int inside = nearest == null ? 0 : insideCount(session, nearest);
            boolean in = nearest != null && active(nearest) && inHill(player, nearest);
            FakeGameEffectsBar.send(player, ServerI18n.tr(in
                            ? "carpetprimaryuan.fakegames.koth.hud_in"
                            : "carpetprimaryuan.fakegames.koth.hud_out",
                    score, seconds, inside, activeIndex + 1, hills.size()));
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
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.koth.draw"));
        } else {
            session.finish(bestId);
        }
    }

    @Override
    public void onEnd(GameSession session) {
        hills.clear();
    }

    /** 动作栏发送的轻量封装，避免每个玩法重复实现。 */
    private static final class FakeGameEffectsBar {
        static void send(ServerPlayer player, net.minecraft.network.chat.Component component) {
            me.primaryuan.carpet.handler.fakegames.FakeGameEffects.actionbar(player, component);
        }
    }
}
