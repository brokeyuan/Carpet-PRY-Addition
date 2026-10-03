package me.primaryuan.carpet.handler.textAnimation;

import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
//#if MC >= 260200
//$$ // 26.2 起实体类型常量迁入 EntityTypes（复数）
//$$ import net.minecraft.world.entity.EntityTypes;
//#endif
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 米塔字幕（textAnimation）：/text 在执行者眼前逐字弹出对话文本，
 * 停留后整句坠落消散——米塔游戏的文字显示效果，纯服务端实现。
 *
 * <p>每字一个原版 text_display 实体（不注册新实体类型，播完即删），变换、
 * 插值、透明度经 {@code TextDisplayInvoker}/{@code DisplayInvoker} 驱动
 * （原版无程序化接口，成员签名 1.21~26.3 一致）。出生点 = 执行者脚部 +
 * 视线方向 × distance、高度脚部 +1.3，与参考实现（maplegrove-misidechat）
 * 一致；坠落物理服务端自算（重力/阻力/地面反弹）。</p>
 *
 * <p>防滥用护栏：单句字数上限、全局并发会话上限；实体带 {@link #ENTITY_TAG}
 * 标记，服务器启动时清扫上次崩溃残留的孤儿实体，停服时清空进行中的会话。</p>
 */
public final class TextAnimationHandler {

    /** 会话实体标记：孤儿清扫依据 */
    public static final String ENTITY_TAG = "pry_textanim";

    /** 默认字色 #ffff55（米塔黄） */
    static final int DEFAULT_COLOR = 0xFFFF55;
    /** ASCII 字宽（宽度单位） */
    static final float NARROW_WIDTH = 1.15f;
    /** 全宽字宽（中日韩等，宽度单位） */
    static final float WIDE_WIDTH = 1.4f;
    /** 单组最大字数 */
    static final int GROUP_MAX_CHARS = 25;
    /** 分组断点向后找标点的格数 */
    private static final int GROUP_SPLIT_LOOKAHEAD = 10;
    /** 组间连接符（非末组追加） */
    private static final String GROUP_CONNECTOR = " - ";
    /** 断句标点 */
    private static final String SPLIT_CHARS = " ,.，。;；:：、！？!?…";
    /** 单句字数上限 */
    public static final int MAX_CHARS = 128;
    /** 全局并发会话上限 */
    public static final int MAX_SESSIONS = 8;

    /** 周期清扫间隔（tick）：孤儿实体随区块懒加载出现，SERVER_STARTED 时未必可见 */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    private static final Set<TextSession> SESSIONS = new HashSet<>();
    private static boolean registered = false;

    /** 26.2 起实体类型常量从 EntityType 迁入 EntityTypes（复数），此处统一收敛为一个字段 */
    //#if MC >= 260200
    //$$ static final EntityType<Display.TextDisplay> TEXT_DISPLAY_TYPE = EntityTypes.TEXT_DISPLAY;
    //#else
    static final EntityType<Display.TextDisplay> TEXT_DISPLAY_TYPE = EntityType.TEXT_DISPLAY;
    //#endif

    private TextAnimationHandler() {}

    /** CarpetPrimaryuanServer.onGameStarted 调用：孤儿清扫 + 停服清理（一次性） */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        ServerLifecycleEvents.SERVER_STARTED.register(TextAnimationHandler::sweepOrphans);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopAll());
        ServerTickScheduler.register(server -> {
            if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
                return true;
            }
            // 有活跃会话时跳过：会话持有并自行管理自己的实体，
            // 此时清扫会把正在播放的字形当孤儿误杀
            if (!SESSIONS.isEmpty()) {
                return true;
            }
            sweepOrphans(server);
            return true;
        });
    }

    /**
     * 崩溃残留清扫：动画中停服/崩溃会把带标记的 text_display 留在存档里。
     * 区块按需加载（1.21.2 起无出生点常载区块），孤儿实体随区块懒加载才可见，
     * 故启动扫一次 + 每 100 tick 兜底扫一遍（只遍历已加载实体，代价可忽略）。
     */
    private static void sweepOrphans(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            //#if MC >= 260102
            //$$ // 26.1.2 起命令标签读取改名 entityTags
            //$$ for (Entity entity : level.getEntities(TEXT_DISPLAY_TYPE, e -> e.entityTags().contains(ENTITY_TAG))) {
            //$$     entity.discard();
            //$$ }
            //#else
            for (Entity entity : level.getEntities(TEXT_DISPLAY_TYPE, e -> e.getTags().contains(ENTITY_TAG))) {
                entity.discard();
            }
            //#endif
        }
    }

    private static void stopAll() {
        for (TextSession session : new ArrayList<>(SESSIONS)) {
            session.killAll();
        }
        SESSIONS.clear();
    }

    /**
     * 播放一条字幕。
     *
     * @return 组数（≥1）；-1 文本为空；-2 超长（{@link #MAX_CHARS}）；-3 并发已满（{@link #MAX_SESSIONS}）；
     *         -5 生成点区块未加载（控制台/命令方块在无玩家加载区域执行——原版区块系统会把
     *         加进非 ENTITY_TICKING 区块的实体按 UNLOADED_TO_CHUNK 写回区块，本地 E2E 实证）
     */
    public static int play(CommandSourceStack source, String rawText, TextOptions options) {
        if (SESSIONS.size() >= MAX_SESSIONS) {
            return -3;
        }
        List<Segment> segments = parseText(rawText);
        if (segments.isEmpty()) {
            return -1;
        }
        if (segments.size() > MAX_CHARS) {
            return -2;
        }
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        Vec2 rotation = source.getRotation();
        if (!chunkReadyFor(level, origin, rotation.y, rotation.x, options)) {
            return -5;
        }
        List<Group> groups = buildGroups(segments);

        TextSession session = new TextSession(level, origin, rotation.y, rotation.x, groups, options);
        SESSIONS.add(session);
        ServerTickScheduler.register(session);
        return groups.size();
    }

    /** 首组生成点区块必须处于 ENTITY_TICKING（玩家执行时恒成立；index=0 无随机抖动，位置确定） */
    private static boolean chunkReadyFor(ServerLevel level, Vec3 origin, float yaw, float pitch,
                                         TextOptions options) {
        Vec3 view = Vec3.directionFromRotation(pitch, yaw);
        double x = origin.x + view.x * options.distance;
        double z = origin.z + view.z * options.distance;
        LevelChunk chunk = level.getChunkSource().getChunkNow((int) x >> 4, (int) z >> 4);
        return chunk != null && chunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING;
    }

    /** 会话自然结束（或停服清理）时从活动集移除 */
    static void onSessionEnded(TextSession session) {
        SESSIONS.remove(session);
    }

    /**
     * & 色码解析：&0~&f 颜色、&l/&o/&n/&m/&k 样式、&r 复位、&& 字面 &。
     * 色码按原版 § 语义重置已积累的样式标志。
     */
    static List<Segment> parseText(String raw) {
        List<Segment> out = new ArrayList<>();
        int color = DEFAULT_COLOR;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                if (code == '&') {
                    out.add(new Segment('&', styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
                    i++;
                    continue;
                }
                ChatFormatting format = ChatFormatting.getByCode(code);
                if (format != null) {
                    // 颜色判定走 TextColor.fromLegacyFormat（26.2 起 ChatFormatting
                    // 自身的 isColor/getColor 被移除，而此方法全版本可用）
                    TextColor legacy = TextColor.fromLegacyFormat(format);
                    if (legacy != null) {
                        color = legacy.getValue();
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    } else if (format == ChatFormatting.RESET) {
                        color = DEFAULT_COLOR;
                        bold = italic = underlined = strikethrough = obfuscated = false;
                    } else if (format == ChatFormatting.BOLD) {
                        bold = true;
                    } else if (format == ChatFormatting.ITALIC) {
                        italic = true;
                    } else if (format == ChatFormatting.UNDERLINE) {
                        underlined = true;
                    } else if (format == ChatFormatting.STRIKETHROUGH) {
                        strikethrough = true;
                    } else if (format == ChatFormatting.OBFUSCATED) {
                        obfuscated = true;
                    }
                    i++;
                    continue;
                }
                // 非法码：按字面 & 处理，下一个字符照常解析
                out.add(new Segment('&', styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
                continue;
            }
            out.add(new Segment(c, styleOf(color, bold, italic, underlined, strikethrough, obfuscated)));
        }
        return out;
    }

    private static Style styleOf(int color, boolean bold, boolean italic, boolean underlined,
                                 boolean strikethrough, boolean obfuscated) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));
        if (bold) {
            style = style.withBold(true);
        }
        if (italic) {
            style = style.withItalic(true);
        }
        if (underlined) {
            style = style.withUnderlined(true);
        }
        if (strikethrough) {
            style = style.withStrikethrough(true);
        }
        if (obfuscated) {
            style = style.withObfuscated(true);
        }
        return style;
    }

    /** 长文本按标点断组（≤25 字/组，断点向后 lookahead 找标点），非末组追加 " - " 连接符 */
    private static List<Group> buildGroups(List<Segment> segments) {
        List<Group> groups = new ArrayList<>();
        int start = 0;
        while (start < segments.size()) {
            int end = Math.min(start + GROUP_MAX_CHARS, segments.size());
            if (end < segments.size()) {
                int limit = Math.min(end + GROUP_SPLIT_LOOKAHEAD, segments.size());
                for (int i = end; i < limit; i++) {
                    if (SPLIT_CHARS.indexOf(segments.get(i).c) >= 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            List<Segment> parts = new ArrayList<>(segments.subList(start, end));
            if (end < segments.size()) {
                for (char c : GROUP_CONNECTOR.toCharArray()) {
                    parts.add(new Segment(c, Style.EMPTY.withColor(TextColor.fromRgb(DEFAULT_COLOR))));
                }
            }
            groups.add(new Group(groups.size(), parts));
            start = end;
        }
        return groups;
    }

    /** 一个待显示字符：字形 + 样式 + 宽度 + 组内居中偏移（宽度单位） */
    static final class Segment {
        final char c;
        final Style style;
        final float width;
        float centerOffset;

        Segment(char c, Style style) {
            this.c = c;
            this.style = style;
            this.width = c <= 127 ? NARROW_WIDTH : WIDE_WIDTH;
        }
    }

    /** 一组（一行）字幕：生成时赋随机偏航/抬高，逐字从左到右弹出 */
    static final class Group {
        enum Phase { TYPING, HOLDING }

        final int index;
        final List<Segment> segments;
        final List<Glyph> glyphs = new ArrayList<>();
        int typed;
        int holdLeft;
        Phase phase;
        double baseX;
        double baseY;
        double baseZ;
        float yaw;
        float pitch;

        Group(int index, List<Segment> segments) {
            this.index = index;
            this.segments = segments;
            float total = 0.0f;
            for (Segment segment : segments) {
                total += segment.width;
            }
            float cum = 0.0f;
            for (Segment segment : segments) {
                segment.centerOffset = cum + segment.width / 2.0f - total / 2.0f;
                cum += segment.width;
            }
        }
    }
}
