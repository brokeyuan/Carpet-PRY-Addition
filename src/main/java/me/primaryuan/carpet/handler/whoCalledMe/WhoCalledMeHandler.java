package me.primaryuan.carpet.handler.whoCalledMe;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.Holder;
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
 * 聊天共同汇聚于此，离线服的未签名聊天同样触发。名字匹配大小写不敏感，
 * 并按玩家名字符集（字母/数字/下划线，离线服中文名同为字母）取词边界，
 * 避免名字是另一玩家名字子串时被误伤；自己发消息提到自己与 carpet 假人
 * （作为被点名者）的区分——真人含发送者本人均提醒，仅排除假人。</p>
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
            if (mentionsName(lowered, player.getName().getString().toLowerCase(Locale.ROOT))) {
                notifyMentioned(player, content);
            }
        }
    }

    /** 词边界匹配：命中处紧邻字符不再构成玩家名的一部分才算点名（包可见供单测） */
    static boolean mentionsName(String loweredContent, String loweredName) {
        if (loweredName.isEmpty()) {
            return false;
        }
        int from = 0;
        int idx;
        while ((idx = loweredContent.indexOf(loweredName, from)) >= 0) {
            int end = idx + loweredName.length();
            boolean leftOk = idx == 0 || !isNameChar(loweredContent.charAt(idx - 1));
            boolean rightOk = end == loweredContent.length() || !isNameChar(loweredContent.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }

    /** 玩家名字符集：原版名即 a-zA-Z0-9_。只按 ASCII 取词边界——中文无空格分词，
     * 名字与汉字粘连（"来一下Alex"/"小明哥"）是点名意图而非子串，不挡命中 */
    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static void notifyMentioned(ServerPlayer player, String content) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(content)));
        // 首声同步发（不依赖调度），其余按间隔排出
        sendDing(player);
        for (int i = 1; i < DING_COUNT; i++) {
            ServerTickScheduler.registerDelayed(i * DING_INTERVAL_TICKS, server -> {
                if (player.hasDisconnected()) {
                    return false;
                }
                sendDing(player);
                return false;
            });
        }
    }

    private static void sendDing(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(
                Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                1.0F, 2.0F, player.getRandom().nextLong()));
    }
}
