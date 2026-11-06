package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 岩浆上涨：液面从场地下方可控起始高度上涨，接触即虚拟淘汰。
 *
 * <p>两种显示共用同一液面判定：</p>
 * <ul>
 * <li>particles/flood：只向参与者发送液面粒子（局部弧段 + 玩家附近网格），不广播给局外玩家；</li>
 * <li>blocks：只向参与者发送假岩浆方块包，不调用 setBlock，不产生真实流动/燃烧/伤害；</li>
 * <li>清理时按当时真实世界方块状态回发，绝不覆盖正常建造变化。</li>
 * </ul>
 *
 * <p>节奏：基础匀速；持续无人被本玩法淘汰则逐步加速；有人被淘汰后恢复基础速度。</p>
 */
public final class RisingLavaGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final int PARTICLE_INTERVAL = 10;
    private static final int FULL_RING_INTERVAL = 40;
    private static final int ACCELERATE_AFTER_TICKS = 20 * 20;
    private static final int MAX_BLOCKS_PER_PLAYER = 120;

    private double surfaceY;
    private double baseInterval;
    private double minInterval;
    private double interval;
    private String display = "particles";
    private boolean flood;
    private boolean accelerate;
    private double riseCounter;
    private int calmTicks;
    private int particleCounter;
    private int fullRingCounter;
    private int hudCooldown;
    private final Set<BlockPos> fakeBlocks = new LinkedHashSet<>();

    @Override
    public void onStart(GameSession session) {
        display = session.option("display");
        flood = "flood".equals(display);
        accelerate = session.boolOption("accelerate", true);
        baseInterval = Math.max(1, session.intOption("rise_seconds", 5)) * 20.0;
        minInterval = Math.max(1, session.intOption("max_rise_seconds", 2)) * 20.0;
        if (minInterval > baseInterval) {
            minInterval = baseInterval;
        }
        interval = baseInterval;
        riseCounter = 0;
        calmTicks = 0;
        fakeBlocks.clear();
        double min = Double.MAX_VALUE;
        for (ServerPlayer player : session.activePlayers()) {
            min = Math.min(min, player.getY());
        }
        int offset = Math.max(1, session.intOption("start_offset", 6));
        double start = (min == Double.MAX_VALUE ? session.center.getY() : Math.floor(min)) - offset;
        //#if MC >= 12103
        surfaceY = Math.max(session.level.getMinY() + 1, start);
        //#else
        //$$ surfaceY = Math.max(session.level.getMinBuildHeight() + 1, start);
        //#endif
        session.broadcast(ServerI18n.tr(flood
                        ? "carpetprimaryuan.fakegames.lava.start_flood"
                        : "carpetprimaryuan.fakegames.lava.start",
                (int) surfaceY, (int) (baseInterval / 20)));
    }

    @Override
    public void onTick(GameSession session) {
        if (accelerate) {
            calmTicks++;
            if (calmTicks >= ACCELERATE_AFTER_TICKS && interval > minInterval) {
                calmTicks = 0;
                interval = Math.max(minInterval, interval * 0.85);
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.lava.accelerate",
                        interval / 20.0));
            }
        }
        riseCounter++;
        if (riseCounter >= interval) {
            riseCounter = 0;
            surfaceY += 1;
        }
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (player.getY() < surfaceY + 0.1) {
                violators.add(player);
            }
        }
        if (!violators.isEmpty()) {
            session.eliminateBatch(violators, "carpetprimaryuan.fakegames.reason.lava");
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
            resetSpeed(session);
        }
        if (++particleCounter >= PARTICLE_INTERVAL) {
            particleCounter = 0;
            renderNearPlayers(session);
        }
        if (++fullRingCounter >= FULL_RING_INTERVAL) {
            fullRingCounter = 0;
            renderRing(session);
        }
        if ("blocks".equals(display) && session.phaseTicks % 10 == 0) {
            renderFakeBlocks(session);
        }
        if (--hudCooldown <= 0) {
            hudCooldown = 20;
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.lava.hud",
                    (int) surfaceY, interval / 20.0));
        }
    }

    private void resetSpeed(GameSession session) {
        interval = baseInterval;
        calmTicks = 0;
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.lava.reset_speed"));
    }

    /** 局部液面：每名参与者附近 5x5 网格 + 朝向边界的一段弧，只发给本人。 */
    private void renderNearPlayers(GameSession session) {
        double y = surfaceY + 0.15;
        ParticleOptions particle = flood ? ParticleTypes.FALLING_WATER : ParticleTypes.LARGE_SMOKE;
        ParticleOptions accent = flood ? ParticleTypes.BUBBLE : ParticleTypes.LAVA;
        for (ServerPlayer player : session.activePlayers()) {
            int bx = player.getBlockX();
            int bz = player.getBlockZ();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((dx + dz) % 2 == 0) {
                        send(player, particle, bx + dx + 0.5, y, bz + dz + 0.5,
                                1, 0.05, 0.02, 0.05, 0.0);
                    }
                }
            }
            double direction = Math.atan2(player.getZ() - (session.center.getZ() + 0.5),
                    player.getX() - (session.center.getX() + 0.5));
            double halfArc = Math.min(Math.PI, 14.0 / Math.max(8.0, session.radius));
            double step = 2.0 / Math.max(8.0, session.radius);
            for (double angle = direction - halfArc; angle <= direction + halfArc; angle += step) {
                double x = session.center.getX() + 0.5 + session.radius * Math.cos(angle);
                double z = session.center.getZ() + 0.5 + session.radius * Math.sin(angle);
                send(player, accent, x, y, z, 1, 0.1, 0.05, 0.1, 0.0);
            }
        }
    }

    /** 整圈液面（低频、受预算限制）：只发给参与者，每 2 格一个粒子。 */
    private void renderRing(GameSession session) {
        int points = Math.max(16, (int) (2 * Math.PI * session.radius / 2.0));
        ParticleOptions particle = flood ? ParticleTypes.FALLING_WATER : ParticleTypes.LARGE_SMOKE;
        for (ServerPlayer player : session.activePlayers()) {
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                send(player, particle,
                        session.center.getX() + 0.5 + session.radius * Math.cos(angle),
                        surfaceY + 0.15,
                        session.center.getZ() + 0.5 + session.radius * Math.sin(angle),
                        1, 0.05, 0.02, 0.05, 0.0);
            }
        }
    }

    /** 假岩浆方块：仅发包给参与者，每名玩家附近最多 120 个位置，跳过非空气真实方块。 */
    private void renderFakeBlocks(GameSession session) {
        int y = (int) Math.floor(surfaceY);
        BlockState lava = (flood ? Blocks.WATER : Blocks.LAVA).defaultBlockState();
        for (ServerPlayer player : session.activePlayers()) {
            int bx = player.getBlockX();
            int bz = player.getBlockZ();
            int sent = 0;
            for (int radius = 1; radius <= 6 && sent < MAX_BLOCKS_PER_PLAYER; radius++) {
                for (int dx = -radius; dx <= radius && sent < MAX_BLOCKS_PER_PLAYER; dx++) {
                    for (int dz = -radius; dz <= radius && sent < MAX_BLOCKS_PER_PLAYER; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        int x = bx + dx;
                        int z = bz + dz;
                        double ox = x + 0.5 - (session.center.getX() + 0.5);
                        double oz = z + 0.5 - (session.center.getZ() + 0.5);
                        if (ox * ox + oz * oz > (double) session.radius * session.radius) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!session.level.hasChunkAt(pos)) {
                            continue;
                        }
                        BlockState real = session.level.getBlockState(pos);
                        if (!real.isAir()) {
                            continue;
                        }
                        player.connection.send(new ClientboundBlockUpdatePacket(pos, lava));
                        fakeBlocks.add(pos);
                        sent++;
                    }
                }
            }
        }
    }

    /** 把指定玩家看到的假方块恢复为当时真实状态。 */
    private void restoreFor(GameSession session, ServerPlayer player) {
        for (BlockPos pos : fakeBlocks) {
            player.connection.send(new ClientboundBlockUpdatePacket(pos,
                    session.level.getBlockState(pos)));
        }
    }

    private void restoreAll(GameSession session) {
        for (ServerPlayer player : session.players(me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE)) {
            restoreFor(session, player);
        }
        for (ServerPlayer player : session.players(me.primaryuan.carpet.handler.fakegames.MemberState.ELIMINATED)) {
            restoreFor(session, player);
        }
        fakeBlocks.clear();
    }

    @Override
    public void onPlayerLeave(GameSession session, java.util.UUID playerId, ServerPlayer player,
                              boolean eliminated) {
        // 先恢复该玩家客户端上的假方块；其它玩家仍需要看到液面，不整体清理
        if (player != null) {
            restoreFor(session, player);
        }
        if (eliminated) {
            resetSpeed(session);
        }
    }

    @Override
    public void onEnd(GameSession session) {
        restoreAll(session);
    }

    private void send(ServerPlayer player, ParticleOptions particle,
                      double x, double y, double z, int count,
                      double dx, double dy, double dz, double speed) {
        //#if MC >= 12104
        ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(player, particle, true, true, x, y, z, count, dx, dy, dz, speed);
        //#else
        //$$ ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
        //#endif
    }
}
