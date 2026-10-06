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
import net.minecraft.sounds.SoundEvent;
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
 * 聊天框打出的原文——原版 title 单行不换行，超宽按显示宽度截断（保名优先）。</p>
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

    /** title 单行不换行，超宽溢出屏幕外；按显示宽度截断——半角 1、CJK/全角 2，
     *  40 单位约为 1080p 默认缩放下接近满宽（目测初值，可在游戏内微调） */
    private static final int TITLE_MAX_WIDTH = 40;
    private static final String ELLIPSIS = "…";

    /** 名字前保留的上下文宽度（单位），保名窗口向左取文时用 */
    private static final int TITLE_WINDOW_LEFT_CONTEXT = 6;

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
        // 全体在线真人名（保持原文大小写；匹配在 allHits 内做大小写不敏感，
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
            List<int[]> hits = uncoveredHits(content, needle, allNeedles);
            if (!hits.isEmpty()) {
                notifyMentioned(player, content, hits);
            }
        }
    }

    /**
     * 名字在消息中的全部未被更长玩家名命中覆盖的区间（大小写不敏感，原文坐标，
     * 包可见供单测）：提醒判定（列表非空）与 title 高亮（全部区间着色）共用，
     * "Brokeyuan 123 Brokeyuan" 两处命中都高亮；mention 模式 needle 含 @，
     * 区间为 @+名字 整体。
     */
    static List<int[]> uncoveredHits(String content, String name, List<String> allNames) {
        List<int[]> own = allHits(content, name);
        if (own.isEmpty()) {
            return own;
        }
        List<int[]> others = new ArrayList<>();
        for (String other : allNames) {
            if (other.isEmpty() || other.equalsIgnoreCase(name)) {
                continue;
            }
            others.addAll(allHits(content, other));
        }
        List<int[]> out = new ArrayList<>();
        for (int[] hit : own) {
            boolean covered = false;
            for (int[] range : others) {
                if (range[0] <= hit[0] && range[1] >= hit[1]) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                out.add(hit);
            }
        }
        return out;
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
        List<int[]> hits = uncoveredHits(content, name, allNames);
        return hits.isEmpty() ? -1 : hits.get(0)[0];
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
     * 聊天装饰：消息文本中出现的在线玩家名渲染为高亮色（规则可选，默认水蓝，
     * 支持逐字符彩虹渐变；贪心最长优先的非重叠区间，与提醒同一套名字集），
     * 其余文本保留原样式；无命中或规则关闭时原样透传。
     */
    private static Component decorateChat(ServerPlayer sender, Component message) {
        String mode = CarpetPrimaryuanSettings.whoCalledMe;
        if ("false".equals(mode) || highlightOff() || decoratedServer == null) {
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
        // 逐段构建：非名字段保留原样式，名字段经 nameComponent 着色
        MutableComponent out = Component.empty().setStyle(message.getStyle());
        int cursor = 0;
        for (int[] range : ranges) {
            if (range[0] > cursor) {
                out.append(Component.literal(text.substring(cursor, range[0])).setStyle(message.getStyle()));
            }
            out.append(nameComponent(text, range[0], range[1], false));
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

    private static void notifyMentioned(ServerPlayer player, String content, List<int[]> hits) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS));
        player.connection.send(new ClientboundSetTitleTextPacket(buildTitle(content, hits)));
        String soundOption = CarpetPrimaryuanSettings.whoCalledMeSound;
        if ("false".equals(soundOption)) {
            return;
        }
        // 首声同步发（不依赖调度），其余按间隔排出
        Holder<SoundEvent> sound = soundOf(soundOption);
        sendSound(player, sound, DING_PITCHES[0]);
        for (int i = 1; i < DING_COUNT; i++) {
            final float pitch = DING_PITCHES[i];
            final int delay = i * DING_INTERVAL_TICKS;
            ServerTickScheduler.registerDelayed(delay, server -> {
                if (player.hasDisconnected()) {
                    return false;
                }
                sendSound(player, sound, pitch);
                return false;
            });
        }
    }

    /** 音效选项映射（包可见供单测）；true 为旧开关兼容值，未知值回落 ding。
     *  字段实名 11 版本 javap 核实一致：levelup/bell/hit 为 SoundEvent（direct 包装），
     *  pling 本身即 Holder.Reference 直接传 */
    static Holder<SoundEvent> soundOf(String option) {
        return switch (option) {
            case "levelup" -> Holder.direct(SoundEvents.PLAYER_LEVELUP);
            case "bell" -> Holder.direct(SoundEvents.BELL_BLOCK);
            case "pling" -> SoundEvents.NOTE_BLOCK_PLING;
            case "hit" -> Holder.direct(SoundEvents.ARROW_HIT_PLAYER);
            default -> Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP);
        };
    }

    /** 单色高亮映射；false（关闭）与 rainbow（走逐字符路径）返回 null，
     *  未知值（含旧值 none）回落默认 aqua */
    static ChatFormatting highlightColor() {
        return switch (CarpetPrimaryuanSettings.whoCalledMeHighlight) {
            case "white" -> ChatFormatting.WHITE;
            case "black" -> ChatFormatting.BLACK;
            case "yellow" -> ChatFormatting.YELLOW;
            case "gold" -> ChatFormatting.GOLD;
            case "green" -> ChatFormatting.GREEN;
            case "dark_green" -> ChatFormatting.DARK_GREEN;
            case "aqua" -> ChatFormatting.AQUA;
            case "dark_aqua" -> ChatFormatting.DARK_AQUA;
            case "blue" -> ChatFormatting.BLUE;
            case "dark_blue" -> ChatFormatting.DARK_BLUE;
            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;
            case "dark_purple" -> ChatFormatting.DARK_PURPLE;
            case "red" -> ChatFormatting.RED;
            case "dark_red" -> ChatFormatting.DARK_RED;
            case "gray" -> ChatFormatting.GRAY;
            case "dark_gray" -> ChatFormatting.DARK_GRAY;
            case "false", "rainbow" -> null;
            default -> ChatFormatting.AQUA;
        };
    }

    /** 高亮关闭（false；旧值 none 已不作别名，按未知值回落默认色） */
    private static boolean highlightOff() {
        return "false".equals(CarpetPrimaryuanSettings.whoCalledMeHighlight);
    }

    /** rainbow 渐变：名字逐字符循环色板 */
    private static boolean highlightRainbow() {
        return "rainbow".equals(CarpetPrimaryuanSettings.whoCalledMeHighlight);
    }

    /** rainbow 色板：每个命中区间从头起循环 */
    private static final ChatFormatting[] RAINBOW_PALETTE = {
            ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
            ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE};

    /** rainbow 第 index 个字符的颜色（循环色板，包可见供单测） */
    static ChatFormatting rainbowColor(int index) {
        return RAINBOW_PALETTE[index % RAINBOW_PALETTE.length];
    }

    /** 名字段组件：rainbow 时逐字符循环色板（按码点切分，防代理对劈开），
     *  否则整段单色；title 侧加粗。聊天框与 title 共用，避免两处着色漂移 */
    private static MutableComponent nameComponent(String text, int start, int end, boolean bold) {
        if (highlightRainbow()) {
            MutableComponent seg = Component.empty();
            int i = start;
            for (int cpIndex = 0; i < end; cpIndex++) {
                int cp = text.codePointAt(i);
                MutableComponent ch = Component.literal(new String(Character.toChars(cp)))
                        .withStyle(rainbowColor(cpIndex));
                if (bold) {
                    ch = ch.withStyle(ChatFormatting.BOLD);
                }
                seg.append(ch);
                i += Character.charCount(cp);
            }
            return seg;
        }
        MutableComponent seg = Component.literal(text.substring(start, end)).withStyle(highlightColor());
        return bold ? seg.withStyle(ChatFormatting.BOLD) : seg;
    }

    /** title 分色：被点名者自己的名字按规则色加粗、正文白——一眼看到是谁在叫。
     *  hits 为全部未覆盖命中区间（原文坐标；mention 模式含 @ 前缀整体）。
     *  长消息先按显示宽度截断（与配色无关，false/none 同样截断），命中区间随
     *  窗口平移，被切侧补 …（包可见供单测） */
    static Component buildTitle(String content, List<int[]> hits) {
        if (hits.isEmpty()) {
            return Component.literal(content);
        }
        int[] win = titleWindow(content, hits, TITLE_MAX_WIDTH);
        boolean cutLeft = win[0] > 0;
        boolean cutRight = win[1] < content.length();
        String text = (cutLeft ? ELLIPSIS : "") + content.substring(win[0], win[1])
                + (cutRight ? ELLIPSIS : "");
        if (highlightOff()) {
            return Component.literal(text).withStyle(ChatFormatting.WHITE);
        }
        // 命中区间平移进窗口坐标系（左侧 … 占 text 首位）
        int leftPad = cutLeft ? ELLIPSIS.length() : 0;
        List<int[]> shifted = new ArrayList<>();
        for (int[] range : hits) {
            int start = Math.max(range[0], win[0]) - win[0] + leftPad;
            int end = Math.min(range[1], win[1]) - win[0] + leftPad;
            if (start < end) {
                shifted.add(new int[]{start, end});
            }
        }
        MutableComponent out = Component.empty();
        int cursor = 0;
        for (int[] range : shifted) {
            if (range[0] > cursor) {
                out.append(Component.literal(text.substring(cursor, range[0])).withStyle(ChatFormatting.WHITE));
            }
            out.append(nameComponent(text, range[0], range[1], true));
            cursor = range[1];
        }
        if (cursor < text.length()) {
            out.append(Component.literal(text.substring(cursor)).withStyle(ChatFormatting.WHITE));
        }
        return out;
    }

    /**
     * title 截断窗口（原文坐标 [start,end)，包可见供单测）：宽不超限全文；
     * 超限取预算前缀，首个命中被切掉时改以该命中为中心取窗口（名字前最多留
     * TITLE_WINDOW_LEFT_CONTEXT 单位上下文），名字本身超宽则截名字。
     */
    static int[] titleWindow(String content, List<int[]> hits, int maxWidth) {
        if (displayWidth(content) <= maxWidth) {
            return new int[]{0, content.length()};
        }
        int budget = maxWidth - 1;  // 右缘 … 占 1 位
        int prefixEnd = limitIndex(content, 0, budget);
        int[] first = hits.isEmpty() ? null : hits.get(0);
        if (first == null || first[1] <= prefixEnd) {
            return new int[]{0, prefixEnd};
        }
        // 保名窗口：名字前最多留数单位上下文，右侧取满预算（左 … 时再让 1 位，
        // 防 左 …+窗口+右 … 总宽超限）
        int start = first[0];
        int left = 0;
        while (start > 0) {
            int prev = start - Character.charCount(content.codePointBefore(start));
            int w = isWide(content.codePointAt(prev)) ? 2 : 1;
            if (left + w > TITLE_WINDOW_LEFT_CONTEXT) {
                break;
            }
            left += w;
            start = prev;
        }
        return new int[]{start, limitIndex(content, start, budget - (start > 0 ? 1 : 0))};
    }

    /** 显示宽度：CJK/全角 2，其余 1（包可见供单测）。服务端无客户端字体表，
     *  以字符数估宽 */
    static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    /** 宽字符判定：CJK 统一表/扩展、假名、谚文、全角形式 */
    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)          // 谚文字母
                || (cp >= 0x2E80 && cp <= 0xA4CF)      // CJK 部首/汉字/假名
                || (cp >= 0xAC00 && cp <= 0xD7A3)      // 谚文音节
                || (cp >= 0xF900 && cp <= 0xFAFF)      // CJK 兼容表意
                || (cp >= 0xFE30 && cp <= 0xFE4F)      // CJK 兼容形式
                || (cp >= 0xFF00 && cp <= 0xFF60)      // 全角形式
                || (cp >= 0xFFE0 && cp <= 0xFFE6)      // 全角符号
                || (cp >= 0x20000 && cp <= 0x3FFFD);   // CJK 扩展 B 起
    }

    /** content 内 [from, …) 不超 budget 宽的最大边界（至少含一个字符） */
    private static int limitIndex(String content, int from, int budget) {
        int width = 0;
        int i = from;
        while (i < content.length()) {
            int cp = content.codePointAt(i);
            int w = isWide(cp) ? 2 : 1;
            if (width + w > budget) {
                break;
            }
            width += w;
            i += Character.charCount(cp);
        }
        return Math.max(i, from + 1);
    }

    /** 三声叮的音高阶梯（递进感，对齐摸摸头已验证可听的音效配方） */
    private static final float[] DING_PITCHES = {1.2f, 1.6f, 2.0f};

    private static void sendSound(ServerPlayer player, Holder<SoundEvent> sound, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                sound, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                0.8F, pitch, player.getRandom().nextLong()));
    }
}
