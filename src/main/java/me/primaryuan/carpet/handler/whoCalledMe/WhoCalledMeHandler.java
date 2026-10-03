package me.primaryuan.carpet.handler.whoCalledMe;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Locale;

/**
 * 谁在叫我（whoCalledMe）：聊天消息中出现其他玩家的名字时，给被点名的玩家
 * 弹出 title 显示消息原文，并连响三声提示音（叮 叮 叮）。
 *
 * <p>纯服务端实现，零 Mixin：挂在 Fabric ServerMessageEvents.CHAT_MESSAGE——
 * 注入点在 PlayerManager.broadcast(SignedMessage, …) 的 HEAD，签名与非签名
 * 聊天共同汇聚于此，离线服的未签名聊天同样触发。名字匹配大小写不敏感的
 * 纯子串（只要对话出现名字即提醒，名字紧邻字母/数字同样命中；接受同名字
 * 前缀玩家间的误伤，不漏报优先）；自己发消息提到自己也提醒，仅排除假人。</p>
 *
 * <p>提示音为 ClientboundSoundPacket 定向单发（发声点在被听者头顶，仅本人
 * 可闻），首声当 tick 末尾、其余按间隔经 ServerTickScheduler 排出；title
 * 先发动画参数（0.25s 淡入 / 3s 停留 / 0.5s 淡出）再发标题，标题文本即
 * 聊天框打出的原文。</p>
 */
public class WhoCalledMeHandler {

    /** 相邻两声叮的间隔（tick，300ms） */
    private static final int DING_INTERVAL_TICKS = 6;

    /** 叮的次数 */
    private static final int DING_COUNT = 3;

    /** title 动画：淡入/停留/淡出（tick） */
    private static final int TITLE_FADE_IN_TICKS = 5;
    private static final int TITLE_STAY_TICKS = 60;
    private static final int TITLE_FADE_OUT_TICKS = 10;

    private static boolean registered = false;

    /** CarpetPrimaryuanServer.onGameStarted 调用：注册聊天监听（一次性） */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        ServerMessageEvents.CHAT_MESSAGE.register(WhoCalledMeHandler::onChatMessage);
    }

    private static void onChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound parameters) {
        if (!CarpetPrimaryuanSettings.whoCalledMe) {
            return;
        }
        String content = message.signedContent();
        if (content == null || content.isBlank()) {
            Component unsigned = message.unsignedContent();
            if (unsigned == null) {
                return;
            }
            content = unsigned.getString();
        }
        String lowered = content.toLowerCase(Locale.ROOT);
        MinecraftServer server = sender.level().getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 自己发消息提到自己同样提醒；carpet 假人（作为被点名者）不提醒
            if (player instanceof EntityPlayerMPFake) {
                continue;
            }
            String nameLower = player.getName().getString().toLowerCase(Locale.ROOT);
            if (mentionIndex(lowered, nameLower) >= 0) {
                notifyMentioned(player, content, nameLower);
            }
        }
    }

    /** 子串匹配（大小写不敏感）：返回第一个命中的起始下标，未命中 -1（包可见供单测）。
     * 只要对话里出现名字即提醒，不做词边界——名字紧邻字母/数字（如 Brokeyuan1）同样命中；
     * 接受误伤（服内同时有 Tim/Timy 时 "timy" 也会提醒 Tim），不漏报优先 */
    static int mentionIndex(String loweredContent, String loweredName) {
        if (loweredName.isEmpty()) {
            return -1;
        }
        return loweredContent.indexOf(loweredName);
    }

    /** 子串匹配：命中即 true（包可见供单测） */
    static boolean mentionsName(String loweredContent, String loweredName) {
        return mentionIndex(loweredContent, loweredName) >= 0;
    }

    /** 名字高亮色：原版金 */
    private static final int NAME_GOLD = 0xFFAA00;

    private static void notifyMentioned(ServerPlayer player, String content, String nameLower) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(buildTitle(content, nameLower)));
        // 首声同步发（不依赖调度），其余按间隔排出
        sendDing(player, DING_PITCHES[0]);
        for (int i = 1; i < DING_COUNT; i++) {
            final float pitch = DING_PITCHES[i];
            final int delay = i * DING_INTERVAL_TICKS;
            ServerTickScheduler.registerDelayed(delay, server -> {
                if (player.hasDisconnected()) {
                    return false;
                }
                sendDing(player, pitch);
                return false;
            });
        }
    }

    /** title 分色：被点名者自己的名字金黄加粗、正文白——一眼看到是谁在叫 */
    private static Component buildTitle(String content, String nameLower) {
        int idx = mentionIndex(content.toLowerCase(Locale.ROOT), nameLower);
        if (idx < 0) {
            return Component.literal(content);
        }
        int end = idx + nameLower.length();
        return Component.literal(content.substring(0, idx)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(content.substring(idx, end))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(content.substring(end)).withStyle(ChatFormatting.WHITE));
    }

    /** 三声叮的音高阶梯（递进感，对齐摸摸头已验证可听的音效配方） */
    private static final float[] DING_PITCHES = {1.2f, 1.6f, 2.0f};

    private static void sendDing(ServerPlayer player, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                0.8F, pitch, player.getRandom().nextLong()));
    }
}
