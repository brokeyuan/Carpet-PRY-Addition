package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameBoundary;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 123 木头人：准备阶段所有参与者进入起点集合圈；倒计时期间有人离圈或条件失效
 * 会回到准备。绿灯自由移动，红灯静止；第一名到达终点者获胜。
 *
 * <p>终点可为自动选点或 /fg goal 手动设置。自动选点检查距离、地面与两格空间，
 * 失败时提示改用预设或手动设置；到达判定同时检查水平距离与高度差，不能在不同楼层
 * 仅凭水平坐标获胜。</p>
 */
public final class RedLightGreenLightGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    /** 红灯位移容差（格），覆盖网络延迟带来的位置滞后 */
    private static final double MOVE_TOLERANCE = 0.75;
    /** 到达终点的水平判定距离（格） */
    private static final double GOAL_REACH = 2.0;
    /** 到达终点的高度差（格） */
    private static final double GOAL_HEIGHT = 2.5;
    private static final int GREEN_MIN = 60;
    private static final int GREEN_MAX = 140;
    private static final int RED_MIN = 40;
    private static final int RED_MAX = 80;
    private static final int HINT_INTERVAL = 20;

    private boolean redLight;
    private int phaseLeft;
    private int hintCooldown;
    private int startRadius = 6;
    private final Map<UUID, Vec3> redPositions = new HashMap<>();
    private boolean goalAuto = true;

    @Override
    public void onStart(GameSession session) {
        startRadius = Math.max(4, session.intOption("start_radius", 6));
        goalAuto = "auto".equals(session.option("goal"));
        if (session.goalPos == null && goalAuto) {
            session.goalPos = findAutoGoal(session);
        }
        if (session.goalPos == null) {
            session.cancel("cancel_no_goal");
            return;
        }
        redLight = false;
        phaseLeft = GREEN_MIN + session.level.getRandom().nextInt(GREEN_MAX - GREEN_MIN + 1);
        redPositions.clear();
        hintCooldown = 0;
        markStart(session);
        markGoal(session);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.rlg.start",
                session.goalPos.getX(), session.goalPos.getY(), session.goalPos.getZ()));
        enterGreen(session);
    }

    @Override
    public Component countdownIssue(GameSession session) {
        // 起点圈必须每 tick 可见，提醒玩家不要提前离开
        if (session.phaseTicks % 10 == 0) {
            markStart(session);
            markGoal(session);
        }
        for (ServerPlayer player : session.activePlayers()) {
            double dx = player.getX() - (session.center.getX() + 0.5);
            double dz = player.getZ() - (session.center.getZ() + 0.5);
            double dy = player.getY() - session.center.getY();
            if (dx * dx + dz * dz > (double) startRadius * startRadius || Math.abs(dy) > 4.0) {
                return ServerI18n.tr("carpetprimaryuan.fakegames.rlg.leave_circle",
                        player.getName().getString(), startRadius);
            }
        }
        return null;
    }

    @Override
    public void onTick(GameSession session) {
        if (session.goalPos == null) {
            return;
        }
        if (--phaseLeft <= 0) {
            switchLight(session);
        }
        if (redLight) {
            checkMovement(session);
            if (session.phase != me.primaryuan.carpet.handler.fakegames.SessionPhase.RUNNING) {
                return;
            }
        } else {
            checkGoalReach(session);
        }
        if (--hintCooldown <= 0) {
            hintCooldown = HINT_INTERVAL;
            int distance = (int) Math.sqrt(session.center.distSqr(session.goalPos));
            session.actionbarActive(ServerI18n.tr(redLight
                            ? "carpetprimaryuan.fakegames.rlg.red"
                            : "carpetprimaryuan.fakegames.rlg.green",
                    distance));
            if (session.phaseTicks % 20 == 0) {
                markGoal(session);
                markStart(session);
            }
        }
    }

    private void switchLight(GameSession session) {
        redLight = !redLight;
        if (redLight) {
            phaseLeft = RED_MIN + session.level.getRandom().nextInt(RED_MAX - RED_MIN + 1);
            redPositions.clear();
            for (ServerPlayer player : session.activePlayers()) {
                redPositions.put(player.getUUID(), player.position());
            }
            session.titleActive(Component.literal("§c" + ServerI18n.tr(
                            "carpetprimaryuan.fakegames.rlg.red_title").getString()),
                    Component.empty(), 0, 15, 5);
            for (ServerPlayer player : session.activePlayers()) {
                me.primaryuan.carpet.handler.fakegames.FakeGameEffects.sound(player,
                        SoundEvents.NOTE_BLOCK_PLING, 1.0f, 0.6f);
            }
        } else {
            enterGreen(session);
        }
    }

    private void enterGreen(GameSession session) {
        redLight = false;
        phaseLeft = GREEN_MIN + session.level.getRandom().nextInt(GREEN_MAX - GREEN_MIN + 1);
        redPositions.clear();
        session.titleActive(Component.literal("§a" + ServerI18n.tr(
                                "carpetprimaryuan.fakegames.rlg.green_title").getString()),
                Component.empty(), 0, 15, 5);
        for (ServerPlayer player : session.activePlayers()) {
            me.primaryuan.carpet.handler.fakegames.FakeGameEffects.sound(player,
                    SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.8f);
        }
    }

    /**
     * 红灯违规纯判定：快照差 &gt; 容差且无外力豁免（击退/液体/攀爬）。
     * 供单测复用；豁免命中时调用方应顺带刷新快照。
     */
    static boolean redViolation(double dx, double dy, double dz,
                                boolean hurt, boolean inWater, boolean inLava, boolean climbing) {
        if (dx * dx + dy * dy + dz * dz <= MOVE_TOLERANCE * MOVE_TOLERANCE) {
            return false;
        }
        return !hurt && !inWater && !inLava && !climbing;
    }

    /** 红灯位移判定（每 tick）：收集全部违规者后同批淘汰。 */
    private void checkMovement(GameSession session) {
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            Vec3 snapshot = redPositions.get(player.getUUID());
            if (snapshot == null) {
                continue;
            }
            Vec3 now = player.position();
            if (redViolation(now.x - snapshot.x, now.y - snapshot.y, now.z - snapshot.z,
                    player.hurtTime > 0, player.isInWater(), player.isInLava(),
                    player.onClimbable())) {
                violators.add(player);
            } else if (player.hurtTime > 0 || player.isInWater() || player.isInLava()
                    || player.onClimbable()) {
                redPositions.put(player.getUUID(), now);
            }
        }
        if (!violators.isEmpty()) {
            session.eliminateBatch(violators, "carpetprimaryuan.fakegames.reason.moved");
        }
    }

    private void checkGoalReach(GameSession session) {
        BlockPos goal = session.goalPos;
        for (ServerPlayer player : session.activePlayers()) {
            double dx = player.getX() - (goal.getX() + 0.5);
            double dz = player.getZ() - (goal.getZ() + 0.5);
            double dy = player.getY() - goal.getY();
            if (dx * dx + dz * dz <= GOAL_REACH * GOAL_REACH && Math.abs(dy) <= GOAL_HEIGHT) {
                session.finish(player.getUUID());
                return;
            }
        }
    }

    /** 自动终点：沿八个方向找距离足够、地面实心且有两格空间的位置；失败返回 null。 */
    private BlockPos findAutoGoal(GameSession session) {
        int distance = Math.min(Math.max(16, session.radius - 8), 64);
        BlockPos best = null;
        double bestDistance = 0;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4.0;
            int x = session.center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = session.center.getZ() + (int) Math.round(Math.sin(angle) * distance);
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
            double dx = x + 0.5 - (session.center.getX() + 0.5);
            double dz = z + 0.5 - (session.center.getZ() + 0.5);
            double horizontal = dx * dx + dz * dz;
            if (horizontal > (double) session.radius * session.radius) {
                continue;
            }
            if (horizontal > bestDistance) {
                bestDistance = horizontal;
                best = pos;
            }
        }
        return best;
    }

    private void markStart(GameSession session) {
        FakeGameBoundary.markCircle(session, session.center.getX() + 0.5,
                session.center.getZ() + 0.5, session.center.getY(),
                startRadius, ParticleTypes.HAPPY_VILLAGER);
    }

    private void markGoal(GameSession session) {
        if (session.goalPos == null) {
            return;
        }
        double x = session.goalPos.getX() + 0.5;
        double y = session.goalPos.getY();
        double z = session.goalPos.getZ() + 0.5;
        FakeGameBoundary.markCircle(session, x, z, y, 1.5, ParticleTypes.END_ROD);
    }

    @Override
    public void onEnd(GameSession session) {
        redPositions.clear();
    }
}
