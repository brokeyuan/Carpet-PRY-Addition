package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;

/**
 * 游戏区域边界表现：只向本局参与者发送粒子，按参与者附近弧段绘制，数量受预算限制。
 * 判定仍由 {@link GameSession#inRegion} 统一完成，显示与判定共用同一半径/高度。
 */
public final class FakeGameBoundary {

    private FakeGameBoundary() {}

    /** 每 tick 调用（由 GameSession 在阶段推进末尾调用）。 */
    public static void tick(GameSession session) {
        int interval = FakeGameSettings.boundaryIntervalTicks();
        if (session.phase == SessionPhase.RUNNING) {
            if (session.phaseTicks % interval == 0) {
                renderAll(session);
            }
        } else if (session.phase == SessionPhase.WAITING || session.phase == SessionPhase.COUNTDOWN) {
            if (session.phaseTicks % 20 == 0) {
                renderAll(session);
            }
        }
    }

    /** 准备阶段的集合圈（123木头人起点等）。 */
    public static void markCircle(GameSession session, double centerX, double centerZ,
                                  double y, double radius, ParticleOptions particle) {
        int points = Math.max(12, (int) (2 * Math.PI * radius / 1.5));
        for (ServerPlayer player : session.activePlayers()) {
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                send(player, particle, centerX + radius * Math.cos(angle), y + 0.1,
                        centerZ + radius * Math.sin(angle), 1, 0, 0, 0, 0);
            }
        }
    }

    private static void renderAll(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            renderFor(session, player);
        }
    }

    private static void renderFor(GameSession session, ServerPlayer player) {
        double cx = session.center.getX() + 0.5;
        double cz = session.center.getZ() + 0.5;
        double dx = player.getX() - cx;
        double dz = player.getZ() - cz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double direction = distance < 0.01
                ? Math.toRadians(player.getYRot() + 90.0)
                : Math.atan2(dz, dx);

        // 只绘制玩家附近约 1.5 弧度的一段弧，避免整圈广播；半径越大弧段越长，但采样间距固定
        double halfArc = Math.min(Math.PI, 18.0 / Math.max(8.0, session.radius));
        double spacing = 2.0;
        double step = spacing / Math.max(8.0, session.radius);
        double baseY = clamp(player.getY() - 0.5, session.minY, session.maxY);
        double topY = Math.min(session.maxY, baseY + 3.0);

        for (double angle = direction - halfArc; angle <= direction + halfArc; angle += step) {
            double x = cx + session.radius * Math.cos(angle);
            double z = cz + session.radius * Math.sin(angle);
            send(player, ParticleTypes.END_ROD, x, baseY, z, 1, 0.02, 0.02, 0.02, 0.0);
            send(player, ParticleTypes.HAPPY_VILLAGER, x, topY, z, 1, 0.05, 0.05, 0.05, 0.0);
            if (player.tickCount % 4 == 0) {
                send(player, ParticleTypes.END_ROD, x, (baseY + topY) * 0.5, z,
                        1, 0.02, 0.3, 0.02, 0.0);
            }
        }

        // 垂直高度范围（仅飞行类玩法）：接近上下限时各画一条弧，明确显示判定高度
        if (session.checkHeight) {
            if (player.getY() - session.minY < 6) {
                drawLevelArc(session, player, direction, halfArc, step, session.minY, ParticleTypes.FLAME);
            }
            if (session.maxY - player.getY() < 6) {
                drawLevelArc(session, player, direction, halfArc, step, session.maxY, ParticleTypes.FLAME);
            }
        }

        // 接近边界：脚下红色警示粒子（每人每轮刷新一次，数量固定）
        if (session.nearBoundary(player) && player.tickCount % 10 == 0) {
            send(player, ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 0.2,
                    player.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    private static void drawLevelArc(GameSession session, ServerPlayer player, double direction,
                                     double halfArc, double step, double y, ParticleOptions particle) {
        double cx = session.center.getX() + 0.5;
        double cz = session.center.getZ() + 0.5;
        for (double angle = direction - halfArc; angle <= direction + halfArc; angle += step * 2) {
            double x = cx + session.radius * Math.cos(angle);
            double z = cz + session.radius * Math.sin(angle);
            send(player, particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void send(ServerPlayer player, ParticleOptions particle,
                             double x, double y, double z, int count,
                             double dx, double dy, double dz, double speed) {
        sendTo(player, particle, x, y, z, count, dx, dy, dz, speed);
    }

    /** 只发给指定玩家的粒子；供各玩法复用同一跨版本调用。 */
    public static void sendTo(ServerPlayer player, ParticleOptions particle,
                              double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
        //#if MC >= 12104
        ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(player, particle, true, true, x, y, z, count, dx, dy, dz, speed);
        //#else
        //$$ ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
        //#endif
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
