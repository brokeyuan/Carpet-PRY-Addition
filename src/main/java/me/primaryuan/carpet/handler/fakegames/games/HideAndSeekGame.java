package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.FakeGameTempEffects;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.handler.fakegames.SustainCounter;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 躲猫猫：经典、缩小感染、隐身草三种模式。
 *
 * <p>寻找者在躲藏阶段留在起点等待区：越过边界先预警并给宽限，持续越界则取消本局，
 * 不再反复强制传送或清零运动；抓捕需要距离 1.8 格内持续 0.5 秒且无墙体遮挡。
 * 缩小感染模式中被攻击命中的躲藏者恢复此前大小并转为抓捕者，按阵营和时间结算；
 * 退出/结束只撤销本局添加的临时修饰符与标记。</p>
 */
public final class HideAndSeekGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final int CAPTURE_SUSTAIN = 10;
    private static final double CAPTURE_DISTANCE = 1.8;
    private static final double WAIT_RADIUS = 3.0;
    private static final int WAIT_GRACE_TICKS = 60;
    private static final int HINT_INTERVAL = 20;

    private String mode = "classic";
    private UUID seeker;
    private final Set<UUID> hunters = new LinkedHashSet<>();
    private final Map<UUID, Vec3> frozen = new HashMap<>();
    private final SustainCounter touchTicks = new SustainCounter(CAPTURE_SUSTAIN);
    private final Set<UUID> scaled = new LinkedHashSet<>();
    private final Set<UUID> glowing = new LinkedHashSet<>();
    private final Set<UUID> herbInvisible = new LinkedHashSet<>();
    private Vec3 seekerAnchor;
    private int hideTicks;
    private int seekTicks;
    private int phaseLeft;
    private boolean seeking;
    private int hintCooldown;
    private int hudCooldown;
    private int seekerOutsideTicks;

    @Override
    public void onStart(GameSession session) {
        mode = session.option("mode");
        hideTicks = Math.max(10, session.intOption("hide_seconds", 60)) * 20;
        seekTicks = Math.max(30, session.intOption("seek_seconds", 180)) * 20;
        List<ServerPlayer> actives = session.activePlayers();
        seeker = actives.get(session.level.getRandom().nextInt(actives.size())).getUUID();
        hunters.clear();
        hunters.add(seeker);
        frozen.clear();
        touchTicks.clear();
        scaled.clear();
        glowing.clear();
        herbInvisible.clear();
        seeking = false;
        seekerOutsideTicks = 0;
        phaseLeft = hideTicks;
        ServerPlayer seekerPlayer = session.player(seeker);
        seekerAnchor = seekerPlayer == null ? null : seekerPlayer.position();
        markSeeker(session, seekerPlayer);
        if ("shrink".equals(mode)) {
            for (ServerPlayer player : session.activePlayers()) {
                if (!player.getUUID().equals(seeker)) {
                    scaleDown(player);
                }
            }
        }
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.hns.start_" + mode,
                seekerPlayer == null ? "?" : seekerPlayer.getName().getString(),
                hideTicks / 20, seekTicks / 20));
        if (seekerPlayer != null) {
            seekerPlayer.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.hns.you_seeker"));
        }
    }

    @Override
    public void onTick(GameSession session) {
        if (phaseLeft > 0) {
            phaseLeft--;
            if (!seeking) {
                tickSeekerWait(session);
                if (phaseLeft == 0) {
                    beginSeeking(session);
                    return;
                }
            } else if (phaseLeft == 0) {
                timeUp(session);
                return;
            }
        }
        if (seeking) {
            if (!"shrink".equals(mode)) {
                checkCapture(session);
            }
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
            if ("herb".equals(mode)) {
                updateHerbInvisibility(session);
            }
        }
        if (--hintCooldown <= 0) {
            hintCooldown = HINT_INTERVAL;
            if (!seeking) {
                session.actionbarActive(ServerI18n.tr(
                        "carpetprimaryuan.fakegames.hns.hiding", phaseLeft / 20 + 1));
            }
        }
        if (--hudCooldown <= 0) {
            hudCooldown = 40;
            if (seeking) {
                session.actionbarActive(ServerI18n.tr(
                        "carpetprimaryuan.fakegames.hns.seeking", phaseLeft / 20 + 1,
                        hiderCount(session)));
            }
        }
    }

    /** 躲藏阶段：绘制等待区，越界先预警，持续 3 秒越界则取消本局。 */
    private void tickSeekerWait(GameSession session) {
        ServerPlayer seekerPlayer = session.player(seeker);
        if (seekerPlayer == null || seekerAnchor == null || seekerPlayer.level() != session.level) {
            return;
        }
        if (session.phaseTicks % 10 == 0) {
            me.primaryuan.carpet.handler.fakegames.FakeGameBoundary.markCircle(
                    session, seekerAnchor.x, seekerAnchor.z, seekerAnchor.y,
                    WAIT_RADIUS, ParticleTypes.HAPPY_VILLAGER);
        }
        double dx = seekerPlayer.getX() - seekerAnchor.x;
        double dz = seekerPlayer.getZ() - seekerAnchor.z;
        double dy = seekerPlayer.getY() - seekerAnchor.y;
        boolean outside = outsideWaitArea(dx, dy, dz);
        if (!outside) {
            seekerOutsideTicks = 0;
            return;
        }
        seekerOutsideTicks++;
        if (seekerOutsideTicks % 20 == 0) {
            int remain = Math.max(0, (WAIT_GRACE_TICKS - seekerOutsideTicks + 19) / 20);
            FakeGameEffects.actionbar(seekerPlayer, ServerI18n.tr(
                    "carpetprimaryuan.fakegames.hns.wait_outside", remain));
        }
        if (seekerOutsideTicks >= WAIT_GRACE_TICKS) {
            session.cancel("cancel_seeker_left");
        }
    }

    /** 等待区判定：水平半径 3、垂直 3 格；供单测与 tick 共用。 */
    static boolean outsideWaitArea(double dx, double dy, double dz) {
        return dx * dx + dz * dz > WAIT_RADIUS * WAIT_RADIUS || Math.abs(dy) > 3.0;
    }

    private void beginSeeking(GameSession session) {
        seeking = true;
        phaseLeft = seekTicks;
        touchTicks.clear();
        session.titleActive(Component.literal("§c" + ServerI18n.tr(
                        "carpetprimaryuan.fakegames.hns.seek_title").getString()),
                Component.empty(), 5, 40, 10);
        for (ServerPlayer player : session.activePlayers()) {
            FakeGameEffects.sound(player, SoundEvents.NOTE_BLOCK_PLING, 1.0f, 0.7f);
        }
    }

    /** 抓捕：同一目标连续 10 tick 在寻找者 1.8 格内且视线可达。 */
    private void checkCapture(GameSession session) {
        ServerPlayer seekerPlayer = session.player(seeker);
        if (seekerPlayer == null || !seekerPlayer.isAlive() || seekerPlayer.level() != session.level) {
            touchTicks.clear();
            return;
        }
        Set<UUID> contacts = new LinkedHashSet<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (player.getUUID().equals(seeker) || hunters.contains(player.getUUID())
                    || player.level() != session.level) {
                continue;
            }
            if (player.distanceToSqr(seekerPlayer) <= CAPTURE_DISTANCE * CAPTURE_DISTANCE
                    && seekerPlayer.hasLineOfSight(player)) {
                contacts.add(player.getUUID());
            }
        }
        Set<UUID> ready = touchTicks.update(contacts);
        if (ready.isEmpty()) {
            return;
        }
        List<ServerPlayer> caught = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (ready.contains(player.getUUID())) {
                caught.add(player);
            }
        }
        for (ServerPlayer player : caught) {
            Vec3 pos = player.position();
            session.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    pos.x, pos.y + 1.0, pos.z, 12, 0.3, 0.5, 0.3, 0.05);
        }
        if ("shrink".equals(mode)) {
            for (ServerPlayer player : caught) {
                if (session.member(player.getUUID()) != null
                        && session.member(player.getUUID()).state
                        == me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE) {
                    convertToHunter(session, player);
                }
            }
        } else {
            session.eliminateBatch(caught, "carpetprimaryuan.fakegames.reason.captured");
        }
    }

    /** 缩小感染：有效攻击命中躲藏者才转换身份，不产生真实伤害。 */
    @Override
    public boolean onAttack(GameSession session, ServerPlayer attacker, ServerPlayer victim) {
        if (!"shrink".equals(mode) || !seeking || session.phase != SessionPhase.RUNNING) {
            return false;
        }
        if (attacker == null || victim == null
                || !hunters.contains(attacker.getUUID())
                || hunters.contains(victim.getUUID())) {
            return false;
        }
        GameMember member = session.member(victim.getUUID());
        if (member == null || member.state != me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE) {
            return false;
        }
        convertToHunter(session, victim);
        return true;
    }

    /** 缩小感染：目标恢复此前大小并成为抓捕者。 */
    private void convertToHunter(GameSession session, ServerPlayer player) {
        restoreScale(player);
        hunters.add(player.getUUID());
        touchTicks.remove(player.getUUID());
        markSeeker(session, player);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.hns.converted",
                player.getName().getString(), hiderCount(session)));
        FakeGameEffects.title(player,
                ServerI18n.tr("carpetprimaryuan.fakegames.hns.now_hunter"),
                ServerI18n.tr("carpetprimaryuan.fakegames.hns.hunter_hint"), 5, 40, 10);
        if (hiderCount(session) == 0) {
            session.finishTeam(hunters,
                    ServerI18n.tr("carpetprimaryuan.fakegames.hns.hunters_win"));
        }
    }

    private void updateHerbInvisibility(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            if (hunters.contains(player.getUUID())) {
                continue;
            }
            boolean inGrass = isInTallGrass(player);
            if (inGrass) {
                // 玩家本来已隐身（外部旗帜/效果）时不接管，也不在结束时清除
                if (!herbInvisible.contains(player.getUUID()) && !player.isInvisible()) {
                    herbInvisible.add(player.getUUID());
                    player.setInvisible(true);
                }
            } else if (herbInvisible.remove(player.getUUID()) && player.isInvisible()) {
                player.setInvisible(false);
            }
        }
    }

    private boolean isInTallGrass(ServerPlayer player) {
        return player.level().getBlockState(player.blockPosition()).is(Blocks.TALL_GRASS)
                || player.level().getBlockState(player.blockPosition()).is(Blocks.SHORT_GRASS);
    }

    private void scaleDown(ServerPlayer player) {
        if (FakeGameTempEffects.applyShrink(player)) {
            scaled.add(player.getUUID());
        }
    }

    private void restoreScale(ServerPlayer player) {
        if (scaled.remove(player.getUUID())) {
            FakeGameTempEffects.removeShrink(player);
        }
    }

    private void markSeeker(GameSession session, ServerPlayer player) {
        if (player == null || player.hasGlowingTag()) {
            return;
        }
        player.setGlowingTag(true);
        glowing.add(player.getUUID());
    }

    private int hiderCount(GameSession session) {
        int count = 0;
        for (ServerPlayer player : session.activePlayers()) {
            if (!hunters.contains(player.getUUID())) {
                count++;
            }
        }
        return count;
    }

    private void timeUp(GameSession session) {
        List<UUID> winners = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (!hunters.contains(player.getUUID())) {
                winners.add(player.getUUID());
                names.add(player.getName().getString());
            }
        }
        if (winners.isEmpty()) {
            session.finishTeam(hunters,
                    ServerI18n.tr("carpetprimaryuan.fakegames.hns.hunters_win"));
        } else {
            session.finishTeam(winners,
                    ServerI18n.tr("carpetprimaryuan.fakegames.hns.hiders_win",
                            String.join(", ", names)));
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        touchTicks.remove(playerId);
        hunters.remove(playerId);
        frozen.remove(playerId);
        restoreScaleFor(playerId, player);
        restoreGlow(playerId, player);
        restoreHerb(playerId, player);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        if (playerId.equals(seeker)) {
            timeUp(session);
        } else if (hiderCount(session) == 0) {
            session.finishTeam(hunters,
                    ServerI18n.tr("carpetprimaryuan.fakegames.hns.hunters_win"));
        }
    }

    private void restoreScaleFor(UUID id, ServerPlayer player) {
        if (player != null) {
            restoreScale(player);
        } else {
            // 玩家实体已离线：transient modifier 不写入存档，重进后自然不存在
            scaled.remove(id);
        }
    }

    private void restoreGlow(UUID id, ServerPlayer player) {
        if (glowing.remove(id) && player != null && player.hasGlowingTag()) {
            player.setGlowingTag(false);
        }
    }

    private void restoreHerb(UUID id, ServerPlayer player) {
        if (herbInvisible.remove(id) && player != null && player.isInvisible()) {
            player.setInvisible(false);
        }
    }

    @Override
    public void onEnd(GameSession session) {
        for (UUID id : new ArrayList<>(scaled)) {
            restoreScaleFor(id, session.player(id));
        }
        for (UUID id : new ArrayList<>(glowing)) {
            restoreGlow(id, session.player(id));
        }
        for (UUID id : new ArrayList<>(herbInvisible)) {
            restoreHerb(id, session.player(id));
        }
        hunters.clear();
        frozen.clear();
        touchTicks.clear();
        seekerOutsideTicks = 0;
    }
}
