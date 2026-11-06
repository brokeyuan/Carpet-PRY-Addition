package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameBoundary;
import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 射击训练与玩家竞争：准星瞄准，换手键发射。
 *
 * <p>不绑定物理键码：每 tick 比较主手/副手物品，检测到原版换手结果（两格内容互换）
 * 即视为按下玩家自己的“换手”键，因此跟随客户端改键。虚拟射线检查距离、方块遮挡
 * 与目标半径；发射频率限制为每 5 tick 一次。不发放枪械/弹药，不消耗手持物品。</p>
 */
public final class ShootingGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final double MAX_RANGE = 48.0;
    private static final double TARGET_RADIUS = 0.75;
    private static final int FIRE_COOLDOWN = 5;
    private static final int TARGET_RENDER_INTERVAL = 10;

    private static final class Target {
        final boolean moving;
        final Vec3 a;
        final Vec3 b;
        Vec3 pos;
        double phase;

        Target(boolean moving, Vec3 a, Vec3 b) {
            this.moving = moving;
            this.a = a;
            this.b = b;
            this.pos = a;
        }
    }

    private static final class Shooter {
        int shots;
        int hits;
        int score;
        int cooldown;
        ItemStack main = ItemStack.EMPTY;
        ItemStack off = ItemStack.EMPTY;
        boolean initialized;
    }

    private final List<Target> targets = new ArrayList<>();
    private final Map<UUID, Shooter> shooters = new LinkedHashMap<>();
    private String mode = "training";
    private int timeLeft;
    private int targetCounter;
    private int hudCooldown;

    @Override
    public void onStart(GameSession session) {
        mode = session.option("mode");
        timeLeft = Math.max(20, session.intOption("duration", 60)) * 20;
        shooters.clear();
        targets.clear();
        for (ServerPlayer player : session.activePlayers()) {
            shooters.put(player.getUUID(), new Shooter());
        }
        buildTargets(session, session.option("targets"));
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.shooting.start",
                ServerI18n.tr("carpetprimaryuan.fakegames.shooting.mode." + mode).getString(),
                timeLeft / 20, ServerI18n.tr("carpetprimaryuan.fakegames.shooting.fire_hint").getString()));
        renderTargets(session);
    }

    private void buildTargets(GameSession session, String setting) {
        boolean fixed = !"moving".equals(setting);
        boolean moving = !"fixed".equals(setting);
        int fixedCount = fixed ? 4 : 0;
        int movingCount = moving ? 2 : 0;
        for (int i = 0; i < fixedCount; i++) {
            double angle = Math.PI * 2 * i / Math.max(1, fixedCount);
            Vec3 pos = surfaceTarget(session, angle, 10 + i * 2);
            targets.add(new Target(false, pos, pos));
        }
        for (int i = 0; i < movingCount; i++) {
            double angle = Math.PI * 2 * i / Math.max(1, movingCount) + 0.5;
            Vec3 a = surfaceTarget(session, angle, 14);
            Vec3 b = surfaceTarget(session, angle + 0.8, 14);
            targets.add(new Target(true, a, b));
        }
        if (targets.isEmpty()) {
            Vec3 fallback = Vec3.atCenterOf(session.center).add(0, 2, 8);
            targets.add(new Target(false, fallback, fallback));
        }
    }

    private Vec3 surfaceTarget(GameSession session, double angle, double distance) {
        double x = session.center.getX() + 0.5 + Math.cos(angle) * distance;
        double z = session.center.getZ() + 0.5 + Math.sin(angle) * distance;
        double y = session.center.getY() + 2.0;
        return new Vec3(x, y, z);
    }

    @Override
    public void onTick(GameSession session) {
        if (--timeLeft <= 0) {
            timeUp(session);
            return;
        }
        for (Shooter shooter : shooters.values()) {
            if (shooter.cooldown > 0) {
                shooter.cooldown--;
            }
        }
        updateMovingTargets(session);
        if (++targetCounter >= TARGET_RENDER_INTERVAL) {
            targetCounter = 0;
            renderTargets(session);
        }
        handleSwapFire(session);
        if (--hudCooldown <= 0) {
            hudCooldown = 20;
            sendHud(session);
        }
    }

    private void updateMovingTargets(GameSession session) {
        for (Target target : targets) {
            if (!target.moving) {
                continue;
            }
            target.phase += 0.02;
            double t = (Math.sin(target.phase) + 1.0) * 0.5;
            target.pos = target.a.lerp(target.b, t);
        }
    }

    /** 检测换手：主手/副手内容与上一 tick 互换即视为按下换手键。 */
    private void handleSwapFire(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            Shooter shooter = shooters.computeIfAbsent(player.getUUID(), ignored -> new Shooter());
            ItemStack currentMain = player.getMainHandItem();
            ItemStack currentOff = player.getOffhandItem();
            if (!shooter.initialized) {
                shooter.main = currentMain.copy();
                shooter.off = currentOff.copy();
                shooter.initialized = true;
                continue;
            }
            boolean changed = !ItemStack.matches(currentMain, shooter.main)
                    || !ItemStack.matches(currentOff, shooter.off);
            boolean swapped = changed && ItemStack.matches(currentMain, shooter.off)
                    && ItemStack.matches(currentOff, shooter.main);
            if (swapped && shooter.cooldown <= 0) {
                shooter.cooldown = FIRE_COOLDOWN;
                fire(session, player, shooter);
            }
            shooter.main = currentMain.copy();
            shooter.off = currentOff.copy();
        }
    }

    private void fire(GameSession session, ServerPlayer player, Shooter shooter) {
        shooter.shots++;
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0f);
        Vec3 end = eye.add(direction.scale(MAX_RANGE));
        BlockHitResult block = session.level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDistance = block.getType() == HitResult.Type.MISS
                ? MAX_RANGE : block.getLocation().distanceTo(eye);

        Target best = null;
        double bestDistance = blockDistance;
        for (Target target : targets) {
            Vec3 toTarget = target.pos.subtract(eye);
            double projection = toTarget.dot(direction);
            if (projection <= 0 || projection > bestDistance) {
                continue;
            }
            double perpendicular = toTarget.subtract(direction.scale(projection)).length();
            if (perpendicular <= TARGET_RADIUS && projection < bestDistance) {
                best = target;
                bestDistance = projection;
            }
        }
        if (best == null) {
            FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_HAT, 0.5f, 0.8f);
            return;
        }
        shooter.hits++;
        shooter.score += best.moving ? 2 : 1;
        Vec3 hit = best.pos;
        for (ServerPlayer viewer : session.activePlayers()) {
            for (int i = 0; i < 8; i++) {
                FakeGameBoundary.sendTo(viewer, ParticleTypes.CRIT, hit.x, hit.y, hit.z,
                        1, 0.2, 0.2, 0.2, 0.0);
            }
        }
        FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.8f);
    }

    private void renderTargets(GameSession session) {
        for (Target target : targets) {
            for (ServerPlayer player : session.activePlayers()) {
                int points = 20;
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2 * i / points;
                    FakeGameBoundary.sendTo(player, ParticleTypes.END_ROD,
                            target.pos.x + Math.cos(angle) * 0.8,
                            target.pos.y + Math.sin(angle) * 0.8,
                            target.pos.z, 1, 0, 0, 0, 0);
                }
                FakeGameBoundary.sendTo(player, ParticleTypes.HAPPY_VILLAGER,
                        target.pos.x, target.pos.y, target.pos.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    private void sendHud(GameSession session) {
        int seconds = timeLeft / 20;
        for (ServerPlayer player : session.activePlayers()) {
            Shooter shooter = shooters.get(player.getUUID());
            if (shooter == null) {
                continue;
            }
            int accuracy = shooter.shots == 0 ? 0 : (int) Math.round(100.0 * shooter.hits / shooter.shots);
            FakeGameEffects.actionbar(player, ServerI18n.tr(
                    "carpetprimaryuan.fakegames.shooting.hud",
                    shooter.score, shooter.hits, shooter.shots, accuracy, seconds));
        }
    }

    private void timeUp(GameSession session) {
        int best = -1;
        UUID bestId = null;
        boolean tie = false;
        for (Map.Entry<UUID, Shooter> entry : shooters.entrySet()) {
            int score = entry.getValue().score;
            GameMember member = session.member(entry.getKey());
            if (member != null) {
                member.score = score;
            }
            if (score > best) {
                best = score;
                bestId = entry.getKey();
                tie = false;
            } else if (score == best) {
                tie = true;
            }
        }
        if (bestId == null || best <= 0 || tie) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.shooting.draw"));
        } else {
            session.finish(bestId);
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        shooters.remove(playerId);
        if (session.phase == SessionPhase.RUNNING && shooters.isEmpty()) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.shooting.draw"));
        }
    }

    @Override
    public void onEnd(GameSession session) {
        shooters.clear();
        targets.clear();
    }
}
