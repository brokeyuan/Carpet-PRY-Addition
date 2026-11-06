package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameEffects;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 热土豆：虚拟炸弹随机指定初始持有者；只有有效攻击命中其他存活玩家才传递。
 * 传递后只短暂禁止立即传回上一名持有者，仍可传给其他合法目标；引线不因传递重置。
 * 倒计时归零时持有者虚拟淘汰，下一轮在剩余玩家中重新随机持有并缩短倒计时，
 * 直到决出胜者。不生成真实 TNT、不产生真实爆炸伤害。
 */
public final class HotPotatoGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final int FIRST_ROUND_TICKS = 20 * 30;
    private static final int MIN_ROUND_TICKS = 20 * 10;

    private UUID holder;
    private UUID lastHolder;
    private int fuseLeft;
    private int passLockTicks;
    private int passLockSetting = 2;
    private int round;

    @Override
    public void onStart(GameSession session) {
        round = 0;
        passLockSetting = Math.max(1, session.intOption("pass_lock", 2));
        newRound(session, Math.max(20, session.intOption("fuse", 30)) * 20);
    }

    @Override
    public void onTick(GameSession session) {
        fuseLeft--;
        if (passLockTicks > 0) {
            passLockTicks--;
        }
        if (fuseLeft <= 0) {
            explode(session);
            return;
        }
        updateHud(session);
    }

    /** 有效攻击命中：持有者攻击另一名存活成员 → 传递；打回上一名持有者受短暂锁定。 */
    @Override
    public boolean onAttack(GameSession session, ServerPlayer attacker, ServerPlayer victim) {
        if (session.phase != SessionPhase.RUNNING || holder == null
                || !holder.equals(attacker.getUUID()) || victim == null
                || attacker.getUUID().equals(victim.getUUID())) {
            return false;
        }
        if (session.member(victim.getUUID()) == null
                || session.member(victim.getUUID()).state != me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE) {
            return false;
        }
        if (passLockTicks > 0 && victim.getUUID().equals(lastHolder)) {
            attacker.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.potato.pass_locked"));
            return true;
        }
        lastHolder = attacker.getUUID();
        holder = victim.getUUID();
        passLockTicks = passLockSetting * 20;
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.potato.passed",
                attacker.getName().getString(), victim.getName().getString(),
                Math.max(0, fuseLeft / 20)));
        victim.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.fakegames.potato.you_hold"));
        FakeGameEffects.sound(victim, SoundEvents.NOTE_BLOCK_PLING, 1.0f, 0.5f);
        return true;
    }

    private void newRound(GameSession session, int duration) {
        List<ServerPlayer> actives = session.activePlayers();
        if (actives.size() < 2) {
            return;
        }
        holder = actives.get(session.level.getRandom().nextInt(actives.size())).getUUID();
        lastHolder = null;
        fuseLeft = duration;
        passLockTicks = 0;
        round++;
        ServerPlayer holderPlayer = session.player(holder);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.potato.round",
                round, holderPlayer == null ? "?" : holderPlayer.getName().getString(),
                duration / 20));
    }

    /** 引爆：持有者虚拟淘汰，剩余玩家进入下一轮（淘汰后触发自动结算/开轮）。 */
    private void explode(GameSession session) {
        ServerPlayer holderPlayer = session.player(holder);
        boolean holderActive = holderPlayer != null
                && session.member(holder) != null
                && session.member(holder).state
                == me.primaryuan.carpet.handler.fakegames.MemberState.ACTIVE;
        if (holderActive) {
            Vec3 pos = holderPlayer.position();
            session.level.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.2, 0.2, 0.0);
            session.level.sendParticles(ParticleTypes.LAVA,
                    pos.x, pos.y + 1.0, pos.z, 16, 0.6, 0.6, 0.6, 0.0);
            session.eliminate(holderPlayer, "carpetprimaryuan.fakegames.reason.boom");
        } else {
            newRound(session, Math.max(MIN_ROUND_TICKS, fuseLeft));
        }
    }

    private void updateHud(GameSession session) {
        ServerPlayer holderPlayer = session.player(holder);
        if (holderPlayer == null) {
            return;
        }
        Vec3 pos = holderPlayer.position();
        session.level.sendParticles(ParticleTypes.FLAME,
                pos.x, pos.y + 2.0, pos.z, 3, 0.2, 0.2, 0.2, 0.01);
        int seconds = Math.max(0, fuseLeft / 20);
        FakeGameEffects.actionbar(holderPlayer, ServerI18n.tr(
                "carpetprimaryuan.fakegames.potato.hud_holder", seconds));
        if (fuseLeft % 20 == 0 && seconds <= 5 && seconds > 0) {
            FakeGameEffects.title(holderPlayer,
                    Component.literal("§c" + seconds).withStyle(net.minecraft.ChatFormatting.BOLD),
                    Component.empty(), 0, 15, 0);
            FakeGameEffects.sound(holderPlayer, SoundEvents.NOTE_BLOCK_HAT, 1.0f, 1.5f);
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        // 持有者离开（退出/掉线/被其他规则淘汰）：炸弹转交并开新一轮
        if (playerId.equals(holder) && session.phase == SessionPhase.RUNNING) {
            newRound(session, Math.max(MIN_ROUND_TICKS, fuseLeft));
        }
    }

    @Override
    public void onEnd(GameSession session) {
        holder = null;
        lastHolder = null;
    }
}
