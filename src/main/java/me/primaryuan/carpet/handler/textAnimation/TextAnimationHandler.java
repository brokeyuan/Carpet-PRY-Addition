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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
//#if MC >= 260200
//$$ // 26.2 起实体类型常量迁入 EntityTypes（复数）
//$$ import net.minecraft.world.entity.EntityTypes;
//#endif
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 米塔字幕（textAnimation）：/text 向所有在线真人玩家（carpet 假人除外）广播
 * 逐字弹出对话文本，停留后整句坠落消散——米塔游戏的文字显示效果，聊天形式，
 * 纯服务端实现，控制台/命令方块同样可用。
 *
 * <p>每字一个原版 text_display 实体（不注册新实体类型，播完即删），变换、
 * 插值、透明度经 {@code TextDisplayInvoker}/{@code DisplayInvoker} 驱动
 * （原版无程序化接口，成员签名 1.21~26.3 一致）。每名玩家一份独立会话，
 * 生成点 = 该玩家脚部 + 视线方向 × distance、高度脚部 +1.3（与参考实现
 * maplegrove-misidechat 一致），打字期间整组跟随玩家实时视角、打字完成即冻结；
 * 坠落物理服务端自算（重力/阻力/地面反弹）。</p>
 *
 * <p>防滥用护栏：单句字数上限、全局并发广播上限；实体带 {@link #ENTITY_TAG}
 * 标记，服务器启动时清扫上次崩溃残留的孤儿实体，停服时清空进行中的会话。</p>
 */
public final class TextAnimationHandler {

    /** 会话实体标记：孤儿清扫依据 */
    public static final String ENTITY_TAG = "pry_textanim";

    /** 感叹号整句增益：尾部每连发一个 +0.3（无封顶，感叹号越多字越大） */
    static final float BANG_GAIN_STEP = 0.3f;
    /** 打字节奏：每 N tick 弹出一字（字幕会话与降级 actionbar 同口径） */
    static final int TYPE_INTERVAL_TICKS = 2;
    /** 感叹号距离增益：尾部每连发一个 ×(1+0.15) 生成距离（越多越远，防大字怼脸；
     *  增速低于字号增益，保持"更大"的观感） */
    static final float BANG_DIST_STEP = 0.15f;
    /** spacing 自动派生系数（×最终 scale），防放大后字符重叠 */
    static final float SPACING_PER_SCALE = 0.15f;
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
    /** 全局并发广播上限（每次 /text 一条广播，广播内每名在线玩家一个会话） */
    public static final int MAX_BROADCASTS = 8;
    /** 单条广播直接生成字幕的玩家数上限，超出改用 actionbar 打字机（大服防实体爆炸） */
    static final int MAX_DIRECT_PLAYERS = 12;

    /** 周期清扫间隔（tick）：孤儿实体随区块懒加载出现，SERVER_STARTED 时未必可见 */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    private static final Set<Broadcast> ACTIVE_BROADCASTS = new HashSet<>();
    private static final Map<TextSession, Broadcast> SESSION_TO_BROADCAST = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
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
            if (!ACTIVE_BROADCASTS.isEmpty()) {
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
        for (TextSession session : new ArrayList<>(SESSION_TO_BROADCAST.keySet())) {
            session.killAll();
        }
        SESSION_TO_BROADCAST.clear();
        ACTIVE_BROADCASTS.clear();
        LAST_TEXT.clear();
    }

    /**
     * 每人发送冷却（字幕是全服可见效果，按人限频防单人连发刷屏）：
     * key=发送者 UUID（控制台不记录、不受限），value=上次成功发送的 tick。
     */
    private static final Map<UUID, Long> LAST_TEXT = new HashMap<>();
    private static final int TEXT_COOLDOWN_TICKS = 200;

    /** 每人发送冷却的剩余秒数（0 = 可发送）；控制台（非玩家 source）不受限 */
    public static int cooldownRemainSeconds(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        Long last = LAST_TEXT.get(player.getUUID());
        if (last == null) {
            return 0;
        }
        long remainTicks = TEXT_COOLDOWN_TICKS - (source.getLevel().getServer().getTickCount() - last);
        return remainTicks > 0 ? (int) ((remainTicks + 19) / 20) : 0;
    }

    /** 成功发起后打上冷却时间戳 */
    public static void markSent(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            LAST_TEXT.put(player.getUUID(), (long) source.getLevel().getServer().getTickCount());
        }
    }

    /**
     * 向所有在线真人玩家广播一条字幕（聊天形式：每人各自眼前弹出，carpet 假人除外；
     * 控制台/命令方块同样可用）。每个玩家一份独立会话（以该玩家的位置与朝向为基准）。
     *
     * @return 组数（≥1）；-1 文本为空；-2 超长（{@link #MAX_CHARS}）；-3 并发已满（{@link #MAX_BROADCASTS}）；
     *         -5 当前没有可接收字幕的在线真人（生成点区块未加载的玩家被跳过——原版区块系统会把
     *         加进非 ENTITY_TICKING 区块的实体按 UNLOADED_TO_CHUNK 写回区块，本地 E2E 实证）
     */
    public static int play(CommandSourceStack source, String rawText, TextOptions options) {
        return play(source, null, rawText, options);
    }

    /**
     * 定向字幕：{@code targets} 非空时只发给指定玩家（仍排除假人），null = 全服广播。
     * 每人 10 秒发送冷却（{@link #markSent}），防单人连发刷满全服屏幕。
     */
    public static int play(CommandSourceStack source, java.util.Collection<ServerPlayer> targets,
                           String rawText, TextOptions options) {
        if (ACTIVE_BROADCASTS.size() >= MAX_BROADCASTS) {
            return -3;
        }
        List<Segment> segments = parseText(rawText);
        if (segments.isEmpty()) {
            return -1;
        }
        if (segments.size() > MAX_CHARS) {
            return -2;
        }
        // 感叹号整句增益（无封顶）+ 距离增益 + spacing 自动派生（与最终 scale 等比，防字符重叠）
        int bangs = countTrailingBangs(segments);
        float gain = 1.0f + BANG_GAIN_STEP * bangs;
        float distGain = 1.0f + BANG_DIST_STEP * bangs;
        float effectiveScale = options.scale * gain;
        float effectiveDistance = (float) (options.distance * distGain);
        float effectiveSpacing = options.spacing == TextOptions.SPACING_AUTO
                ? SPACING_PER_SCALE * effectiveScale : options.spacing;
        options = TextOptions.with(options, effectiveDistance, effectiveScale, effectiveSpacing,
                null, null, null, null);

        MinecraftServer server = source.getLevel().getServer();
        List<Group> template = buildGroups(segments);

        List<ServerPlayer> direct = new ArrayList<>();
        List<ServerPlayer> degraded = new ArrayList<>();
        Iterable<ServerPlayer> candidates = targets != null ? targets : server.getPlayerList().getPlayers();
        for (ServerPlayer target : candidates) {
            if (target instanceof EntityPlayerMPFake) {
                continue;
            }
            Vec3 origin = target.position();
            if (!chunkReadyFor(target.level(), origin, target.getYRot(), target.getXRot(), options)) {
                continue;
            }
            // 大服降级：直接字幕会话超过上限后，其余玩家改用 actionbar 打字机（零实体）
            if (direct.size() < MAX_DIRECT_PLAYERS) {
                direct.add(target);
            } else {
                degraded.add(target);
            }
        }
        if (direct.isEmpty() && degraded.isEmpty()) {
            return -5;
        }
        Broadcast broadcast = new Broadcast();
        broadcast.remaining = direct.size() + (degraded.isEmpty() ? 0 : 1);
        for (ServerPlayer target : direct) {
            TextSession session = new TextSession(target, copyGroups(template), options, broadcast);
            SESSION_TO_BROADCAST.put(session, broadcast);
            registerSession(session, () -> onSessionEnded(session));
        }
        if (!degraded.isEmpty()) {
            ActionbarSession actionbar = new ActionbarSession(degraded, segments, broadcast, options.hold);
            registerSession(actionbar, actionbar::finish);
        }
        ACTIVE_BROADCASTS.add(broadcast);
        return template.size();
    }

    /** 首组生成点区块必须处于 ENTITY_TICKING（真人在线时其周围区块由玩家 ticket 保证） */
    private static boolean chunkReadyFor(ServerLevel level, Vec3 origin, float yaw, float pitch,
                                         TextOptions options) {
        Vec3 view = Vec3.directionFromRotation(pitch, yaw);
        double x = origin.x + view.x * options.distance;
        double z = origin.z + view.z * options.distance;
        LevelChunk chunk = level.getChunkSource().getChunkNow((int) x >> 4, (int) z >> 4);
        return chunk != null && chunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING;
    }

    /**
     * 注册会话任务并兜底：调度器对崩溃任务的处置是记日志淘汰、不回调业务——
     * 会话若不在此自行回报结束，Broadcast.remaining 永不归零，并发额度被永久
     * 占用（8 次崩溃后 /text 恒 -3），孤儿清扫也被"有活跃广播"条件抑制。
     * 崩溃回调与正常结束路径共用幂等清理，最多重复一次、无副作用。
     */
    private static void registerSession(ServerTickScheduler.TickTask task, Runnable onCrash) {
        ServerTickScheduler.register(server -> {
            try {
                return task.tick(server);
            } catch (Throwable t) {
                LOGGER.error("[TextAnimation] Session crashed, force-finishing its broadcast", t);
                onCrash.run();
                return false;
            }
        });
    }

    /** 会话自然结束（或停服清理）时通知广播；全部部分结束即销毁广播 */
    static void onSessionEnded(TextSession session) {
        Broadcast broadcast = SESSION_TO_BROADCAST.remove(session);
        if (broadcast != null) {
            broadcast.partFinished();
        }
    }

    /** 一次 /text 广播：直接字幕会话 + 可选降级 actionbar 会话，全部结束即销毁 */
    static final class Broadcast {
        int remaining;

        /** 一部分结束；全部结束时自行从 ACTIVE_BROADCASTS 移除（并发额度回收的唯一收敛点，
         *  覆盖字幕会话与降级 actionbar 会话两条结束路径） */
        void partFinished() {
            if (--remaining <= 0) {
                ACTIVE_BROADCASTS.remove(this);
            }
        }
    }

    /** 每名玩家一份组副本：Group 含逐会话可变状态（typed/holdLeft/生成点），segments 只读共享 */
    private static List<Group> copyGroups(List<Group> template) {
        List<Group> copy = new ArrayList<>(template.size());
        for (Group group : template) {
            copy.add(new Group(group.index, group.segments));
        }
        return copy;
    }

    /**
     * 尾部连续感叹号计数（ASCII ! 与全角 ！）：& 色码解析后对可见字符统计，
     * 末尾空白跳过，被其他字符打断即止——"好!!!" 计 3，"好!!?" 计 0，"好!! " 计 2。
     */
    static int countTrailingBangs(List<Segment> segments) {
        int count = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            char c = segments.get(i).c;
            if (c == '!' || c == '！') {
                count++;
            } else if (c == ' ' || c == '　' || c == '\t') {
                continue;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * & 色码解析：&0~&f 颜色、&l/&o/&n/&m/&k 样式、&r 复位、&& 字面 &（解析在 {@code ColorText}，
     * 多规则共用）。
     */
    static List<Segment> parseText(String raw) {
        List<me.primaryuan.carpet.util.ColorText.Char> parsed =
                me.primaryuan.carpet.util.ColorText.parse(raw);
        List<Segment> out = new ArrayList<>(parsed.size());
        for (var ch : parsed) {
            out.add(new Segment(ch.c(), ch.style()));
        }
        return out;
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
                    parts.add(new Segment(c, Style.EMPTY.withColor(TextColor.fromRgb(me.primaryuan.carpet.util.ColorText.DEFAULT_COLOR))));
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
        /** 打字节流倒计时（tick）：1 = 下个 typing tick 立即弹首字，其后按 TYPE_INTERVAL_TICKS */
        int typeCooldown = 1;
        int holdLeft;
        Phase phase;
        double baseX;
        double baseY;
        double baseZ;
        float yaw;
        float pitch;
        /** 组生成时定下的偏航抖动/抬高：跟随视角重锚时保持不变 */
        float yawJitter;
        float yLift;

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
