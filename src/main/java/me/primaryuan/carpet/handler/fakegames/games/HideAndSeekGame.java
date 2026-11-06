package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.handler.fakegames.SustainCounter;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <p>寻找者在躲藏阶段被锁在起点（离开即拉回），不能用移动跟踪；抓捕需要距离
 * 1.8 格内持续 0.5 秒且无墙体遮挡。缩小感染模式中被抓者恢复此前大小并转为
 * 抓捕者，按阵营和时间结算；退出/结束只恢复本局改过的状态。</p>
 */
public final class HideAndSeekGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final int CAPTURE_SUSTAIN = 10;
    private static final double CAPTURE_DISTANCE = 1.8;
    private static final int HINT_INTERVAL = 20;

    private String mode = "classic";
    private UUID seeker;
    private final Set<UUID> hunters = new LinkedHashSet<>();
    private final Map<UUID, Vec3> frozen = new HashMap<>();
    private final SustainCounter touchTicks = new SustainCounter(CAPTURE_SUSTAIN);
    private final Map<UUID, Double> originalScale = new HashMap<>();
    private final Set<UUID> scaled = new LinkedHashSet<>();
    private final Set<UUID> glowing = new LinkedHashSet<>();
    private final Map<UUID, Boolean> originalGlow = new HashMap<>();
    private final Set<UUID> herbInvisible = new LinkedHashSet<>();
    private final Map<UUID, Boolean> originalInvisible = new HashMap<>();
    private Vec3 seekerAnchor;
    private int hideTicks;
    private int seekTicks;
    private int phaseLeft;
    private boolean seeking;
    private int hintCooldown;
    private int hudCooldown;

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
        originalScale.clear();
        scaled.clear();
        glowing.clear();
        originalGlow.clear();
        herbInvisible.clear();
        originalInvisible.clear();
        seeking = false;
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
                lockSeeker(session);
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
            checkCapture(session);
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

    /** 躲藏阶段寻找者锁定在起点，移动超过 1 格立即拉回。 */
    private void lockSeeker(GameSession session) {
        ServerPlayer seekerPlayer = session.player(seeker);
        if (seekerPlayer == null || seekerAnchor == null || seekerPlayer.level() != session.level) {
            return;
        }
        if (seekerPlayer.position().distanceToSqr(seekerAnchor) > 1.0) {
            seekerPlayer.teleportTo(seekerAnchor.x, seekerAnchor.y, seekerAnchor.z);
            seekerPlayer.setDeltaMovement(Vec3.ZERO);
            FakeGameEffects.actionbar(seekerPlayer, ServerI18n.tr(
                    "carpetprimaryuan.fakegames.hns.wait_anchor"));
        }
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
            if (inGrass && herbInvisible.add(player.getUUID())) {
                originalInvisible.putIfAbsent(player.getUUID(), player.isInvisible());
                player.setInvisible(true);
            } else if (!inGrass && herbInvisible.remove(player.getUUID())) {
                player.setInvisible(Boolean.TRUE.equals(originalInvisible.remove(player.getUUID())));
            }
        }
    }

    private boolean isInTallGrass(ServerPlayer player) {
        return player.level().getBlockState(player.blockPosition()).is(Blocks.TALL_GRASS)
                || player.level().getBlockState(player.blockPosition()).is(Blocks.SHORT_GRASS);
    }

    private void scaleDown(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.SCALE);
        if (attribute == null) {
            return;
        }
        if (scaled.add(player.getUUID())) {
            originalScale.putIfAbsent(player.getUUID(), attribute.getBaseValue());
        }
        attribute.setBaseValue(0.6);
    }

    private void restoreScale(ServerPlayer player) {
        if (!scaled.remove(player.getUUID())) {
            return;
        }
        var attribute = player.getAttribute(Attributes.SCALE);
        if (attribute != null) {
            attribute.setBaseValue(originalScale.getOrDefault(player.getUUID(), 1.0));
        }
        originalScale.remove(player.getUUID());
    }

    private void markSeeker(GameSession session, ServerPlayer player) {
        if (player == null) {
            return;
        }
        originalGlow.putIfAbsent(player.getUUID(), player.hasGlowingTag());
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
            scaled.remove(id);
            originalScale.remove(id);
        }
    }

    private void restoreGlow(UUID id, ServerPlayer player) {
        if (glowing.remove(id) && player != null) {
            player.setGlowingTag(Boolean.TRUE.equals(originalGlow.get(id)));
        }
        originalGlow.remove(id);
    }

    private void restoreHerb(UUID id, ServerPlayer player) {
        if (herbInvisible.remove(id) && player != null) {
            player.setInvisible(Boolean.TRUE.equals(originalInvisible.get(id)));
        }
        originalInvisible.remove(id);
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
        originalScale.clear();
        originalGlow.clear();
        originalInvisible.clear();
    }
}
