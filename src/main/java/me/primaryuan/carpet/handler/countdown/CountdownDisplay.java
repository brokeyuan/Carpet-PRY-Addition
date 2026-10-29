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
 * 计时显示层：唯一 ServerBossEvent 实例 + Title/Subtitle/ActionBar 定向数据包。
 *
 * <p>两条渲染路径共用 BossBar 与音效设施：旧 /countdown 会话按渠道组合渲染
 * （原语义保留）；新 /timer 会话按 {@link TimerFrame} 差量渲染——顶部名称、
 * 进度、颜色分开比较，无变化不发包；中央连续帧 fadeIn 0 / stay 25 / fadeOut 0
 * 避免逐秒重新淡入，一次性阶段（闪烁 5/30/5、结果 5/50/5）才用淡入淡出。
 * 渠道约束先于效果：帧里没有的渠道绝不发包，清理时只移除自己的 BossBar，
 * 不向他人发送清除包、不清除其他功能的 Title/ActionBar。</p>
 */
final class CountdownDisplay {

    /** 常规刷新的 title 停留 tick，覆盖 1 秒刷新间隔 */
    private static final int TITLE_STAY_TICKS = 25;
    /** 一次性闪烁（开始/暂停/恢复/节点，约 2 秒） */
    private static final int FLASH_FADE_IN_TICKS = 5;
    private static final int FLASH_STAY_TICKS = 30;
    private static final int FLASH_FADE_OUT_TICKS = 5;
    /** 结果阶段（3 秒窗口） */
    private static final int RESULT_FADE_IN_TICKS = 5;
    private static final int RESULT_STAY_TICKS = 50;
    private static final int RESULT_FADE_OUT_TICKS = 5;
    /** 旧入口完成提示与节拍提示的动画（沿用原节奏） */
    private static final int COMPLETION_FADE_IN_TICKS = 5;
    private static final int COMPLETION_STAY_TICKS = 40;
    private static final int COMPLETION_FADE_OUT_TICKS = 10;
    private static final int MILESTONE_FADE_IN_TICKS = 5;
    private static final int MILESTONE_STAY_TICKS = 35;
    private static final int MILESTONE_FADE_OUT_TICKS = 10;

    /* 旧入口音效配方保留（节拍钟声、归零提示音） */
    private static final Holder<SoundEvent> LEGACY_MILESTONE_SOUND = Holder.direct(SoundEvents.BELL_BLOCK);
    private static final Holder<SoundEvent> LEGACY_FINISH_SOUND = Holder.direct(SoundEvents.PLAYER_LEVELUP);

    private ServerBossEvent bossbar;
    /** 顶部差量基线（名称 / 颜色 / 进度，无变化不重发） */
    private String lastBarName;
    private TimerColor lastBarColor;
    private float lastProgress = -1;

    // ==================== 渠道与观众 ====================

    /** 渠道变更后同步：BossBar 渠道关闭时移除实例（开启时惰性创建于 ensureBossbar） */
    void applyChannels(EnumSet<CountdownChannel> channels) {
        if (!channels.contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar = null;
        }
    }

    /** 会话切换/布局变更后清空顶部差量基线，强制下一帧全量同步 */
    void resetDiff() {
        lastBarName = null;
        lastBarColor = null;
        lastProgress = -1;
    }

    /** 观众加入：BossBar 补挂（新会话当前帧由管理器组帧补发） */
    void addBarViewer(ServerPlayer player, boolean barChannel) {
        if (barChannel && bossbar != null) {
            bossbar.addPlayer(player);
        }
    }

    /** 观众退出：移除 BossBar；Title/ActionBar 为一次性包自然淡出，无需补发清除 */
    void viewerLeft(ServerPlayer player) {
        if (bossbar != null) {
            bossbar.removePlayer(player);
        }
    }

    /** 终局清理：移除 BossBar 观众（Title/ActionBar 自然淡出） */
    void hide() {
        removeBossbar();
        resetDiff();
    }

    private void removeBossbar() {
        if (bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar = null;
        }
    }

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

    // ==================== 新入口帧渲染 ====================

    /**
     * 差量渲染一帧：顶部（名称/颜色/进度分项比较）、中央（动画按帧的 Anim 阶段）、
     * 动作栏。帧内为 null 的字段跳过；渠道不允许的渠道整体跳过。
     */
    void renderFrame(TimerFrame frame, EnumSet<CountdownChannel> channels, List<ServerPlayer> audience) {
        if (frame.barText() != null && channels.contains(CountdownChannel.BOSSBAR)) {
            updateBar(styled(frame.barText(), frame.barColor()), frame.barColor(), frame.barProgress(), audience);
        }
        if (frame.actionText() != null && channels.contains(CountdownChannel.ACTIONBAR)) {
            ClientboundSetActionBarTextPacket packet =
                    new ClientboundSetActionBarTextPacket(Component.literal(frame.actionText()));
            for (ServerPlayer player : audience) {
                player.connection.send(packet);
            }
        }
        if (frame.hasCentral() && channels.contains(CountdownChannel.TITLE)) {
            int fadeIn = 0;
            int stay = TITLE_STAY_TICKS;
            int fadeOut = 0;
            if (frame.anim() == TimerFrame.Anim.FLASH) {
                fadeIn = FLASH_FADE_IN_TICKS;
                stay = FLASH_STAY_TICKS;
                fadeOut = FLASH_FADE_OUT_TICKS;
            } else if (frame.anim() == TimerFrame.Anim.RESULT) {
                fadeIn = RESULT_FADE_IN_TICKS;
                stay = RESULT_STAY_TICKS;
                fadeOut = RESULT_FADE_OUT_TICKS;
            }
            sendCentral(frame.titleText(), frame.subtitleText(), fadeIn, stay, fadeOut,
                    channels.contains(CountdownChannel.SUBTITLE), audience);
        }
    }

    /** 结果/暂停保活：仅顶部与动作栏重发（中央一次性动画已覆盖窗口，不重发重淡入） */
    void renderKeepalive(TimerFrame frame, EnumSet<CountdownChannel> channels, List<ServerPlayer> audience) {
        if (frame.barText() != null && channels.contains(CountdownChannel.BOSSBAR)) {
            updateBar(styled(frame.barText(), frame.barColor()), frame.barColor(), frame.barProgress(), audience);
        }
        if (frame.actionText() != null && channels.contains(CountdownChannel.ACTIONBAR)) {
            ClientboundSetActionBarTextPacket packet =
                    new ClientboundSetActionBarTextPacket(Component.literal(frame.actionText()));
            for (ServerPlayer player : audience) {
                player.connection.send(packet);
            }
        }
    }

    /** 仅刷新 BossBar 进度（10 Hz 平滑进度，名称不动） */
    void updateProgressOnly(Float progress, List<ServerPlayer> audience) {
        if (bossbar == null || progress == null || progress == lastProgress) {
            return;
        }
        bossbar.setProgress(progress);
        lastProgress = progress;
        syncBossbar(audience);
    }

    private void updateBar(String name, TimerColor color, Float progress, List<ServerPlayer> audience) {
        boolean changed = false;
        if (bossbar == null) {
            bossbar = createBossbar(Component.literal(name), color);
            changed = true;
        } else {
            if (!name.equals(lastBarName)) {
                bossbar.setName(Component.literal(name));
                changed = true;
            }
            if (color != lastBarColor) {
                bossbar.setColor(mapColor(color));
                changed = true;
            }
        }
        if (progress != null && progress != lastProgress) {
            bossbar.setProgress(progress);
            changed = true;
        }
        lastBarName = name;
        lastBarColor = color;
        if (progress != null) {
            lastProgress = progress;
        }
        if (changed) {
            syncBossbar(audience);
        }
    }

    private static ServerBossEvent createBossbar(Component name, TimerColor color) {
        //#if MC >= 260102
        //$$ // 26.1.2 起 ServerBossEvent 构造器增加 UUID 参数
        //$$ ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), name,
        //$$         mapColor(color), BossEvent.BossBarOverlay.PROGRESS);
        //#else
        ServerBossEvent bar = new ServerBossEvent(name, mapColor(color), BossEvent.BossBarOverlay.PROGRESS);
        //#endif
        // 纯进度条：不改天色、不放 boss 音乐、不起雾
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        return bar;
    }

    private static BossEvent.BossBarColor mapColor(TimerColor color) {
        // 原版 BossBar 无青色，CYAN 取最接近的 BLUE；GOLD 并入 YELLOW
        return switch (color) {
            case WHITE -> BossEvent.BossBarColor.WHITE;
            case CYAN -> BossEvent.BossBarColor.BLUE;
            case GOLD, YELLOW -> BossEvent.BossBarColor.YELLOW;
            case RED -> BossEvent.BossBarColor.RED;
            case GREEN -> BossEvent.BossBarColor.GREEN;
            case PURPLE -> BossEvent.BossBarColor.PURPLE;
        };
    }

    /** 顶部/中央文本着色：WHITE 不加码（默认白），其余加对应 § 色号 */
    private static String styled(String text, TimerColor color) {
        String code = switch (color == null ? TimerColor.WHITE : color) {
            case CYAN -> "§b";
            case GOLD -> "§6";
            case YELLOW -> "§e";
            case RED -> "§c";
            case GREEN -> "§a";
            default -> null;
        };
        return code == null ? text : code + text;
    }

    /**
     * 中央显示组合：统一"大字时间 + 小字名称/状态"。原版契约：Subtitle 包只在客户端
     * 存储副标题，需随附 Title 包触发显示——副标题始终随 Title 一并下发（无内容时发
     * 空串），跨阶段切换不残留上一条副标题。
     */
    private static void sendCentral(String title, String subtitle, int fadeIn, int stay, int fadeOut,
                                    boolean subtitleChannel, List<ServerPlayer> audience) {
        MutableComponent titleText = Component.literal(title);
        MutableComponent subtitleText = Component.literal(subtitle == null ? "" : subtitle);
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            player.connection.send(new ClientboundSetTitleTextPacket(titleText));
            if (subtitleChannel) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
            }
        }
    }

    // ==================== 音效 ====================

    /** 新入口配方播放（发声点在听者头顶，仅本人可闻） */
    void playCue(TimerSoundPlan.Cue cue, List<ServerPlayer> audience) {
        Holder<SoundEvent> sound = switch (cue.id()) {
            case OPEN_CHIME, MILESTONE_CHIME -> CHIME_SOUND;
            case MILESTONE_BELL -> LEGACY_MILESTONE_SOUND;
            case TICK_HAT -> HAT_SOUND;
            case TICK_PLING -> PLING_SOUND;
            case FINISH_BELL -> BELL_SOUND;
            case FINISH_LEVELUP -> LEGACY_FINISH_SOUND;
        };
        playSound(audience, sound, cue.volume(), cue.pitch());
    }

    /* NOTE_BLOCK_* 常量各版本均为 Holder.Reference<SoundEvent>，直接引用；
       BELL_BLOCK / PLAYER_LEVELUP 为裸 SoundEvent，经 Holder.direct 包装 */
    private static final Holder<SoundEvent> CHIME_SOUND = SoundEvents.NOTE_BLOCK_CHIME;
    private static final Holder<SoundEvent> HAT_SOUND = SoundEvents.NOTE_BLOCK_HAT;
    private static final Holder<SoundEvent> PLING_SOUND = SoundEvents.NOTE_BLOCK_PLING;
    private static final Holder<SoundEvent> BELL_SOUND = SoundEvents.NOTE_BLOCK_BELL;

    /** 定向单发 */
    private static void playSound(List<ServerPlayer> audience, Holder<SoundEvent> sound, float volume, float pitch) {
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSoundPacket(
                    sound, SoundSource.PLAYERS,
                    player.getX(), player.getY(), player.getZ(),
                    volume, pitch, player.getRandom().nextLong()));
        }
    }

    // ==================== 旧入口渲染路径（原语义保留） ====================

    /** 更新 BossBar 文本与进度并同步观众（每秒一次；对象/渠道变更时也走这里） */
    void updateBossbar(Component name, float progress, List<ServerPlayer> audience) {
        if (bossbar == null) {
            bossbar = createBossbar(name, TimerColor.PURPLE);
        } else {
            bossbar.setName(name);
        }
        bossbar.setProgress(progress);
        syncBossbar(audience);
    }

    /** 观众加入（动态全服或快照命中）：立即上 BossBar 并补发当前中央显示 */
    void viewerJoinedLegacy(ServerPlayer player, CountdownSession session, long now) {
        if (session.channels().contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.addPlayer(player);
        }
        sendCountdownLegacy(session, now, List.of(player));
    }

    /** 常规每秒刷新（旧路径）：BossBar 文本/进度 + 中央窗口 + ActionBar */
    void sendCountdownLegacy(CountdownSession session, long now, List<ServerPlayer> audience) {
        int sec = session.remainingCeilSeconds(now);
        if (session.channels().contains(CountdownChannel.BOSSBAR)) {
            updateBossbar(Component.literal(session.label() + " " + CountdownTime.formatClock(sec)),
                    (float) session.progress(now), audience);
        }
        sendCentralLegacy(session, sec, audience, false, null);
        sendActionBarLegacy(session, sec, audience, false, null);
    }

    /** 完成提示（旧路径）：已启用渠道显示"时间到！"，BossBar 保留至收尾移除 */
    void showCompletionLegacy(CountdownSession session, List<ServerPlayer> audience, String doneText) {
        if (session.channels().contains(CountdownChannel.BOSSBAR) && bossbar != null) {
            bossbar.setName(Component.literal(doneText));
            bossbar.setProgress(0f);
        }
        sendCentralLegacy(session, 0, audience, true, doneText);
        sendActionBarLegacy(session, 0, audience, true, doneText);
    }

    /** 旧入口长程节拍提示：中央大字显示节拍值（约 2 秒），仅 TITLE 渠道允许时发送 */
    void showMilestoneLegacy(long beatSeconds, List<ServerPlayer> audience) {
        MutableComponent text = Component.literal(CountdownTime.formatTitleNumber(beatSeconds));
        for (ServerPlayer player : audience) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(
                    MILESTONE_FADE_IN_TICKS, MILESTONE_STAY_TICKS, MILESTONE_FADE_OUT_TICKS));
            player.connection.send(new ClientboundSetTitleTextPacket(text));
        }
    }

    /** 旧入口节拍钟声（一声） */
    void playMilestoneSoundLegacy(List<ServerPlayer> audience) {
        playSound(audience, LEGACY_MILESTONE_SOUND, 1.0F, 1.0F);
    }

    /** 旧入口 10..1 逐秒倒数音：pitch 按半音阶递增（10→1.0，1→约 1.68） */
    void playTickSoundLegacy(List<ServerPlayer> audience, int sec) {
        playSound(audience, PLING_SOUND, 0.8F, (float) Math.pow(2.0, (10 - sec) / 12.0));
    }

    /** 旧入口归零强音 */
    void playFinishSoundLegacy(List<ServerPlayer> audience) {
        playSound(audience, LEGACY_FINISH_SOUND, 0.8F, 1.0F);
    }

    /** 停用渠道的残留清除（仅在旧入口渠道切换时一次性发送，不逐秒清除他人内容） */
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

    /**
     * 旧路径中央显示组合：
     * Title+Subtitle → Title=label、Subtitle=MM:SS；Title 单独 → 剩余秒数（>59s 用 MM:SS）；
     * Subtitle 单独 → 空 Title 配合 + MM:SS。完成时主文案换"时间到！"（Title+Subtitle 时
     * Subtitle 置空）。非完成态受 last 窗口约束，窗口外整段跳过。
     */
    private void sendCentralLegacy(CountdownSession session, int sec, List<ServerPlayer> audience,
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

    /** 旧路径 ActionBar：全程"剩余 MM:SS"，完成时单发"时间到！"；调用方控制每秒一次的节奏 */
    private void sendActionBarLegacy(CountdownSession session, int sec, List<ServerPlayer> audience,
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
