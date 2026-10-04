package me.primaryuan.carpet.handler.whoCalledMe;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 谁在叫我（whoCalledMe）：聊天消息中出现其他玩家的名字时，给被点名的玩家
 * 弹出 title 显示消息原文，并连响三声提示音（叮 叮 叮）。
 *
 * <p>纯服务端实现，零 Mixin：挂在 Fabric ServerMessageEvents.CHAT_MESSAGE——
 * 注入点在 PlayerManager.broadcast(SignedMessage, …) 的 HEAD，签名与非签名
 * 聊天共同汇聚于此，离线服的未签名聊天同样触发。名字匹配大小写不敏感的
 * 子串 + 最长名优先（只要对话出现名字即提醒，名字紧邻字母/数字同样命中；
 * 命中若被更长玩家名覆盖则不提醒——Tim/Timy 同服时 "timy" 只提醒 Timy）；
 * 自己发消息提到自己也提醒，仅排除假人。</p>
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
        // 聊天装饰需要服务器实例（在线玩家名集合）；停服置空防悬挂引用
        ServerLifecycleEvents.SERVER_STARTED.register(server -> decoratedServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> decoratedServer = null);
    }

    private static void onChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound parameters) {
        String mode = CarpetPrimaryuanSettings.whoCalledMe;
        if ("false".equals(mode)) {
            return;
        }
        boolean mentionMode = "mention".equals(mode);
        String content = message.signedContent();
        if (content == null || content.isBlank()) {
            Component unsigned = message.unsignedContent();
            if (unsigned == null) {
                return;
            }
            content = unsigned.getString();
        }
        MinecraftServer server = sender.level().getServer();
        // 全体在线真人名（保持原文大小写；匹配在 mentionIndex 内做大小写不敏感，
        // 不经 toLowerCase——İ 等字符小写化会变长，下标会漂移，见 allHits）
        List<String> allNames = new ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!(online instanceof EntityPlayerMPFake)) {
                allNames.add(online.getName().getString());
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 自己发消息提到自己同样提醒；carpet 假人（作为被点名者）不提醒
            if (player instanceof EntityPlayerMPFake) {
                continue;
            }
            String name = player.getName().getString();
            // mention 模式：仅 @名字 触发（needle 含 @）；true 模式：子串 + 最长名优先
            String needle = mentionMode ? "@" + name : name;
            List<String> allNeedles = mentionMode
                    ? allNames.stream().map(n -> "@" + n).toList() : allNames;
            int idx = mentionIndex(content, needle, allNeedles);
            if (idx >= 0) {
                // @ 命中下标指向 @ 字符，高亮从名字本体开始
                notifyMentioned(player, content, name, mentionMode ? idx + 1 : idx);
            }
        }
    }

    /**
     * 子串匹配 + 最长名优先（大小写不敏感）：返回第一个未被更长玩家名命中覆盖的
     * 命中下标，无有效命中 -1（包可见供单测）。content/name 均为原文，
     * 返回下标恒为原文下标。
     *
     * <p>名字紧邻字母/数字（如 Brokeyuan1）同样命中；服内同时有 Tim/Timy 时，
     * "timy 来一下" 的 [0,3) 命中属于 Timy 的 [0,4) 覆盖范围，Tim 不提醒、Timy
     * 提醒（优先完整的名字）；而 "timy tim" 中 Tim 的独立第二命中仍提醒。</p>
     */
    static int mentionIndex(String content, String name, List<String> allNames) {
        if (name.isEmpty()) {
            return -1;
        }
        List<int[]> own = allHits(content, name);
        if (own.isEmpty()) {
            return -1;
        }
        List<int[]> others = new ArrayList<>();
        for (String other : allNames) {
            if (other.isEmpty() || other.equalsIgnoreCase(name)) {
                continue;
            }
            others.addAll(allHits(content, other));
        }
        for (int[] hit : own) {
            boolean covered = false;
            for (int[] range : others) {
                if (range[0] <= hit[0] && range[1] >= hit[1]) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return hit[0];
            }
        }
        return -1;
    }

    /**
     * 大小写不敏感地在原文上找 name 的全部命中区间。不经 toLowerCase：İ（U+0130）
     * 等字符小写化会变长（i + 组合附点），在小写串上算出的下标拿回原文切分时会
     * 错位甚至越界；regionMatches 逐字符比较，下标恒为原文下标（包可见供单测）。
     */
    static List<int[]> allHits(String content, String name) {
        List<int[]> hits = new ArrayList<>();
        if (name.isEmpty() || name.length() > content.length()) {
            return hits;
        }
        for (int i = 0; i <= content.length() - name.length(); i++) {
            if (content.regionMatches(true, i, name, 0, name.length())) {
                hits.add(new int[]{i, i + name.length()});
            }
        }
        return hits;
    }

    /** 大小写不敏感子串匹配：命中即 true（包可见供单测） */
    static boolean mentionsName(String content, String name) {
        return !name.isEmpty() && !allHits(content, name).isEmpty();
    }

    // ==================== 聊天名字高亮（ChatDecorator） ====================

    private static final ChatDecorator MENTION_DECORATOR = WhoCalledMeHandler::decorateChat;
    private static MinecraftServer decoratedServer;

    /** MinecraftServerMixin 调用：返回接管后的聊天装饰器（始终接管，规则开关在装饰器内部判定） */
    public static ChatDecorator wrapChatDecorator(ChatDecorator original) {
        return MENTION_DECORATOR;
    }

    /**
     * 聊天装饰：消息文本中出现的在线玩家名渲染为金色（贪心最长优先的非重叠区间，
     * 与提醒同一套名字集），其余文本保留原样式；无命中或规则关闭时原样透传。
     */
    private static Component decorateChat(ServerPlayer sender, Component message) {
        String mode = CarpetPrimaryuanSettings.whoCalledMe;
        if ("false".equals(mode) || "none".equals(CarpetPrimaryuanSettings.whoCalledMeHighlight)
                || decoratedServer == null) {
            return message;
        }
        boolean mentionMode = "mention".equals(mode);
        String text = message.getString();
        if (text.isEmpty()) {
            return message;
        }
        List<String> allNames = new ArrayList<>();
        for (ServerPlayer online : decoratedServer.getPlayerList().getPlayers()) {
            if (!(online instanceof EntityPlayerMPFake)) {
                allNames.add(online.getName().getString());
            }
        }
        List<int[]> ranges = new ArrayList<>();
        for (String name : allNames) {
            // mention 模式高亮 @+名字 整体；true 模式只高亮名字本体
            String needle = mentionMode ? "@" + name : name;
            ranges.addAll(allHits(text, needle));
        }
        ranges = nonOverlappingLongestFirst(ranges);
        if (ranges.isEmpty()) {
            return message;
        }
        ChatFormatting highlight = highlightColor();
        if (highlight == null) {
            return message;
        }
        // 逐段构建：非名字段保留原样式，名字段金色（样式基线取原文）
        MutableComponent out = Component.empty().setStyle(message.getStyle());
        int cursor = 0;
        for (int[] range : ranges) {
            if (range[0] > cursor) {
                out.append(Component.literal(text.substring(cursor, range[0])).setStyle(message.getStyle()));
            }
            out.append(Component.literal(text.substring(range[0], range[1])).withStyle(highlight));
            cursor = range[1];
        }
        if (cursor < text.length()) {
            out.append(Component.literal(text.substring(cursor)).setStyle(message.getStyle()));
        }
        return out;
    }

    /** 贪心选区间：起点排序、长者优先，丢弃与已选重叠的（"timy tim"→各自独立命中、同位重叠取最长） */
    static List<int[]> nonOverlappingLongestFirst(List<int[]> ranges) {
        List<int[]> sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(b[1], a[1]));
        List<int[]> out = new ArrayList<>();
        int lastEnd = -1;
        for (int[] range : sorted) {
            if (range[0] >= lastEnd) {
                out.add(range);
                lastEnd = range[1];
            }
        }
        return out;
    }

    private static void notifyMentioned(ServerPlayer player, String content, String nameLower, int hitIndex) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(buildTitle(content, nameLower, hitIndex)));
        if (!CarpetPrimaryuanSettings.whoCalledMeSound) {
            return;
        }
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

    /** 高亮色映射；none 返回 null（不高亮，title 全白） */
    static ChatFormatting highlightColor() {
        return switch (CarpetPrimaryuanSettings.whoCalledMeHighlight) {
            case "yellow" -> ChatFormatting.YELLOW;
            case "aqua" -> ChatFormatting.AQUA;
            case "green" -> ChatFormatting.GREEN;
            case "red" -> ChatFormatting.RED;
            case "none" -> null;
            default -> ChatFormatting.GOLD;
        };
    }

    /** title 分色：被点名者自己的名字按规则色加粗、正文白——一眼看到是谁在叫。
     *  name 为原文，hitIndex 为原文下标（与 content 同一坐标系） */
    private static Component buildTitle(String content, String name, int hitIndex) {
        if (hitIndex < 0 || hitIndex + name.length() > content.length()) {
            return Component.literal(content);
        }
        ChatFormatting highlight = highlightColor();
        if (highlight == null) {
            return Component.literal(content).withStyle(ChatFormatting.WHITE);
        }
        int idx = hitIndex;
        int end = idx + name.length();
        return Component.literal(content.substring(0, idx)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(content.substring(idx, end))
                        .withStyle(highlight, ChatFormatting.BOLD))
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
