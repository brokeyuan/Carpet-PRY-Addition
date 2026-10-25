package me.primaryuan.carpet.handler.countdown;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.EnumSet;
import java.util.List;

/**
 * 倒计时显示层：唯一 ServerBossEvent 实例 + Title/Subtitle/ActionBar 定向数据包。
 *
 * <p>BossBar 全程自管（加人/移除/清空均经本类），根据观众列表同步可见性，不存在
 * 不可见残留实例；Title/Subtitle/ActionBar 为一次性包，仅在观众集合内发送，刷新
 * 节奏（每秒一次/暂停保活）由 CountdownManager 控制。刷新用 fadeIn 0 / stay 覆盖
 * 刷新间隔 / fadeOut 0 的动画参数，避免逐秒重发造成闪烁。停用渠道或清理时只移除
 * 本系统自己的显示，不向非观众发送清除包，也不清除其他功能的 Title/ActionBar。</p>
 */
final class CountdownDisplay {

    /** 常规刷新的 title 停留 tick，覆盖 1 秒刷新间隔 */
    private static final int TITLE_STAY_TICKS = 25;
    private static final int COMPLETION_FADE_IN_TICKS = 5;
    private static final int COMPLETION_STAY_TICKS = 40;
    private static final int COMPLETION_FADE_OUT_TICKS = 10;
    /** 节拍提示的 title 动画：约 2 秒存在感 */
    private static final int MILESTONE_FADE_IN_TICKS = 5;
    private static final int MILESTONE_STAY_TICKS = 35;
    private static final int MILESTONE_FADE_OUT_TICKS = 10;

    /* 音效配方沿用 whoCalledMe 已验证的 Holder 方案（音效常量跨版本一致） */
    private static final Holder<SoundEvent> MILESTONE_SOUND = Holder.direct(SoundEvents.BELL_BLOCK);
    private static final Holder<SoundEvent> FINISH_SOUND = Holder.direct(SoundEvents.PLAYER_LEVELUP);

    private ServerBossEvent bossbar;

    /** 渠道变更后同步：BossBar 渠道关闭时移除实例（开启时惰性创建于 updateBossbar） */
    void applyChannels(EnumSet<CountdownChannel> channels) {
        if (!channels.contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar = null;
        }
    }

    /** 更新 BossBar 文本与进度并同步观众（每秒一次；对象/渠道变更时也走这里） */
    void updateBossbar(Component name, float progress, List<ServerPlayer> audience) {
        if (bossbar == null) {
            bossbar = createBossbar(name);
        } else {
            bossbar.setName(name);
        }
        bossbar.setProgress(progress);
        syncBossbar(audience);
    }

    private static ServerBossEvent createBossbar(Component name) {
        //#if MC >= 260102
        //$$ // 26.1.2 起 ServerBossEvent 构造器增加 UUID 参数
        //$$ ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), name,
        //$$         BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        //#else
        ServerBossEvent bar = new ServerBossEvent(name, BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        //#endif
        // 纯进度条：不改天色、不放 boss 音乐、不起雾
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        return bar;
    }

    /** 观众差量同步：新观众加包、退出/不再属于目标的观众移包 */
    private void syncBossbar(List<ServerPlayer> audience) {
        if (bossbar == null) {
            return;
        }
        for (ServerPlayer player : audience) {
            if (!bossbar.getPlayers().contains(player)) {
                bossbar.addPlayer(player);
            }
        }
        for (ServerPlayer player : List.copyOf(bossbar.getPlayers())) {
            if (!audience.contains(player)) {
                bossbar.removePlayer(player);
            }
        }
    }

    /** 观众加入（动态全服或快照命中）：立即上 BossBar 并补发当前中央显示 */
    void viewerJoined(ServerPlayer player, CountdownSession session, long now) {
        if (session.channels().contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.addPlayer(player);
        }
        sendCountdown(session, now, List.of(player));
    }

    /** 观众退出：移除 BossBar；Title/ActionBar 为一次性包自然淡出，无需补发清除 */
    void viewerLeft(ServerPlayer player) {
        if (bossbar != null) {
            bossbar.removePlayer(player);
        }
    }

    /** 常规每秒刷新：BossBar 文本/进度 + 中央窗口 + ActionBar */
    void sendCountdown(CountdownSession session, long now, List<ServerPlayer> audience) {
        int sec = session.remainingCeilSeconds(now);
        if (session.channels().contains(CountdownChannel.BOSSBAR)) {
            updateBossbar(Component.literal(session.label() + " " + CountdownTime.formatClock(sec)),
                    (float) session.progress(now), audience);
        }
        sendCentral(session, sec, audience, false, null);
        sendActionBar(session, sec, audience, false, null);
    }

    /** 完成提示：已启用渠道显示"时间到！"，BossBar 保留至管理器 2 秒后收尾移除 */
    void showCompletion(CountdownSession session, List<ServerPlayer> audience, String doneText) {
        if (session.channels().contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.setName(Component.literal(doneText));
            bossbar.setProgress(0f);
        }
        sendCentral(session, 0, audience, true, doneText);
        sendActionBar(session, 0, audience, true, doneText);
    }

    // ==================== 节拍与音效 ====================

    /** 长程节拍提示：中央大字显示节拍值（约 2 秒），节拍触发即显示，独立于 title 渠道的常规刷新 */
    void showMilestone(long beatSeconds, List<ServerPlayer> audience) {
        MutableComponent text = Component.literal(CountdownTime.formatTitleNumber(beatSeconds));
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(
                    MILESTONE_FADE_IN_TICKS, MILESTONE_STAY_TICKS, MILESTONE_FADE_OUT_TICKS));
            player.connection.send(new ClientboundSetTitleTextPacket(text));
        }
    }

    /** 节拍钟声（一声） */
    void playMilestoneSound(List<ServerPlayer> audience) {
        playSound(audience, MILESTONE_SOUND, 1.0F);
    }

    /** 10..1 逐秒倒数音：pitch 按半音阶递增（10→1.0，1→约 1.68） */
    void playTickSound(List<ServerPlayer> audience, int sec) {
        playSound(audience, SoundEvents.NOTE_BLOCK_PLING, (float) Math.pow(2.0, (10 - sec) / 12.0));
    }

    /** 归零强音 */
    void playFinishSound(List<ServerPlayer> audience) {
        playSound(audience, FINISH_SOUND, 1.0F);
    }

    /** 定向单发（发声点在听者头顶，仅本人可闻）——whoCalledMe 同款配方 */
    private static void playSound(List<ServerPlayer> audience, Holder<SoundEvent> sound, float pitch) {
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSoundPacket(
                    sound, SoundSource.PLAYERS,
                    player.getX(), player.getY(), player.getZ(),
                    0.8F, pitch, player.getRandom().nextLong()));
        }
    }

    /** 停用渠道的残留清除（仅在渠道切换时一次性发送，不逐秒清除他人内容） */
    void clearChannels(EnumSet<CountdownChannel> removed, List<ServerPlayer> audience) {
        boolean title = removed.contains(CountdownChannel.TITLE);
        boolean subtitle = removed.contains(CountdownChannel.SUBTITLE);
        boolean actionbar = removed.contains(CountdownChannel.ACTIONBAR);
        if (removed.contains(CountdownChannel.BOSSBAR)) {
            removeBossbar();
        }
        if (!title && !subtitle && !actionbar) {
            return;
        }
        for (ServerPlayer player : audience) {
            if (title) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            }
            if (subtitle) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            }
            if (actionbar) {
                player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
            }
        }
    }

    /** 终局清理：移除 BossBar 观众（Title/ActionBar 自然淡出） */
    void hide() {
        removeBossbar();
    }

    private void removeBossbar() {
        if (bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar = null;
        }
    }

    /**
     * 中央显示组合：
     * Title+Subtitle → Title=label、Subtitle=MM:SS；Title 单独 → 剩余秒数（>59s 用 MM:SS）；
     * Subtitle 单独 → 空 Title 配合 + MM:SS。完成时主文案换"时间到！"（Title+Subtitle 时
     * Subtitle 置空）。非完成态受 last 窗口约束，窗口外整段跳过。
     */
    private void sendCentral(CountdownSession session, int sec, List<ServerPlayer> audience,
                             boolean finished, String doneText) {
        EnumSet<CountdownChannel> channels = session.channels();
        boolean title = channels.contains(CountdownChannel.TITLE);
        boolean subtitle = channels.contains(CountdownChannel.SUBTITLE);
        if (!title && !subtitle) {
            return;
        }
        MutableComponent titleText = null;
        MutableComponent subtitleText = null;
        int fadeIn = 0;
        int stay = TITLE_STAY_TICKS;
        int fadeOut = 0;
        if (finished) {
            fadeIn = COMPLETION_FADE_IN_TICKS;
            stay = COMPLETION_STAY_TICKS;
            fadeOut = COMPLETION_FADE_OUT_TICKS;
            if (title) {
                titleText = Component.literal(doneText);
            }
            if (subtitle) {
                subtitleText = title ? Component.empty() : Component.literal(doneText);
            }
        } else {
            if (!session.inLastWindow(sec)) {
                return;
            }
            if (title) {
                titleText = subtitle
                        ? Component.literal(session.label())
                        : Component.literal(CountdownTime.formatTitleNumber(sec));
            }
            if (subtitle) {
                subtitleText = Component.literal(CountdownTime.formatClock(sec));
            }
        }
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            // 原版契约：ClientboundSetSubtitleTextPacket 只在客户端存储副标题，需随附 Title 包触发显示——
            // title 单开时 Title 包本身即触发；subtitle 单开时补空 Title（title+subtitle 时同理已覆盖）
            player.connection.send(new ClientboundSetTitleTextPacket(
                    titleText != null ? titleText : Component.empty()));
            if (subtitle) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
            }
        }
    }

    /** ActionBar：全程"剩余 MM:SS"，完成时单发"时间到！"；调用方控制每秒一次的节奏 */
    private void sendActionBar(CountdownSession session, int sec, List<ServerPlayer> audience,
                               boolean finished, String doneText) {
        if (!session.channels().contains(CountdownChannel.ACTIONBAR)) {
            return;
        }
        Component text = finished
                ? Component.literal(doneText)
                : ServerI18n.tr("carpetprimaryuan.countdown.remaining", CountdownTime.formatClock(sec));
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(text);
        for (ServerPlayer player : audience) {
            player.connection.send(packet);
        }
    }
}
