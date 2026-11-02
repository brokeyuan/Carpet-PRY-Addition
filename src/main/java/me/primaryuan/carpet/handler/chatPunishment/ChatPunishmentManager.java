package me.primaryuan.carpet.handler.chatPunishment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.config.PryJsonStore;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天惩罚（chatPunishment）：管理员经 /chatpunish 对指定玩家施加聊天限制，
 * 每名玩家同时只生效一种模式，支持永久与限时。
 *
 * <p>文本改写只挂在服务端公共聊天解码后的 ChatDecorator 调用参数上（1.21.x
 * 为 method_44900，26.x 为 lambda$handleChat$0），私聊、/say 等命令和系统
 * 消息不经过该回调，不会被改写。装饰结果作为未签名内容随签名消息广播，客户端
 * 默认显示未签名内容；签名正文和原版修改标记不受影响，因此改写默认显示不等于
 * 隐藏原文。禁言经 Fabric ALLOW_CHAT_MESSAGE 取消广播，先按 ChatType.CHAT
 * 过滤，只作用于普通公共聊天。模式在消息实际装饰/广播时读取；排队期间切换、
 * 到期或关闭规则按处理时点生效，不做发送时快照。</p>
 *
 * <p>持久化：{@code <存档>/carpet-pry-addition/chatpunish.json}（WORLD 作用域），
 * 按 UUID 记录 {名称, 模式, 到期时间戳}，到期时间 0 = 永久。按真实时间
 * （epoch millis）判定到期，重启、重连不丢失，不依赖玩家在线或 tick；
 * 到期在读取处惰性判定，落盘时清理。加载前完整校验文件，失败进入本规则只读
 * 故障态并保留原文件；设置/解除先写候选表，落盘成功后才更新内存并反馈成功。</p>
 */
public final class ChatPunishmentManager {

    private static final ChatPunishStore STORE = new ChatPunishStore();

    /** 管理命令结果；命令只在 APPLIED 时反馈成功 */
    public enum Result {
        APPLIED,
        NOT_FOUND,
        READ_ONLY,
        IO_ERROR
    }

    /**
     * 单条惩罚记录。expireAt = 0 表示永久，否则为到期时间（epoch millis）。
     */
    public record Entry(String name, PunishmentMode mode, long expireAt) {
        /** 是否已到期（永久记录永不过期） */
        public boolean expired(long now) {
            return expireAt != 0 && now >= expireAt;
        }
    }

    static final class ChatPunishStore extends PryJsonStore {
        private static final int SCHEMA_VERSION = 1;
        private static final Set<String> ROOT_KEYS = Set.of("version", "data");
        private static final Set<String> DATA_KEYS = Set.of("players");
        private static final Set<String> ENTRY_KEYS = Set.of("name", "mode", "expireAt");

        /** UUID 字符串 → 记录；仅服务器主线程访问（聊天装饰与命令均在主线程） */
        final Map<String, Entry> punishments = new HashMap<>();

        private Path file;
        private boolean writable;

        /** 包可见写入钩子：默认原子写，单测可注入失败；不对外暴露 */
        BiPredicate<Path, JsonObject> writer = this::writeFileAtomic;

        ChatPunishStore() {
            super("ChatPunish", "chatpunish.json", Scope.WORLD, SCHEMA_VERSION, null);
        }

        /** 服务器生命周期入口：解析实际世界路径后加载 */
        void load(MinecraftServer server) {
            loadFrom(server.getWorldPath(LevelResource.ROOT)
                    .resolve(FOLDER).resolve("chatpunish.json"));
        }

        /** 完整校验后加载；任何异常都保留原文件并进入只读故障态 */
        void loadFrom(Path path) {
            punishments.clear();
            file = path;
            writable = false;
            if (Files.notExists(path)) {
                writable = true;
                return;
            }
            JsonObject root = readFileOrNull(path);
            if (root == null) {
                LOGGER.error("[ChatPunish] Cannot read {}; file kept, commands disabled", path);
                return;
            }
            try {
                if (!root.keySet().equals(ROOT_KEYS) || !root.has("version")) {
                    throw new IllegalArgumentException("bad envelope");
                }
                long version = requireIntegral(root.get("version"), "version");
                if (version > SCHEMA_VERSION) {
                    LOGGER.error("[ChatPunish] Config version {} is newer than supported {}; "
                            + "file kept, commands disabled", version, SCHEMA_VERSION);
                    return;
                }
                if (version != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported version " + version);
                }
                JsonElement dataElem = root.get("data");
                if (dataElem == null || !dataElem.isJsonObject()) {
                    throw new IllegalArgumentException("missing data");
                }
                JsonObject data = dataElem.getAsJsonObject();
                if (!data.keySet().equals(DATA_KEYS)) {
                    throw new IllegalArgumentException("unexpected data fields");
                }
                punishments.putAll(parseEntries(data));
                writable = true;
            } catch (RuntimeException e) {
                punishments.clear();
                LOGGER.error("[ChatPunish] Invalid data in {}; file kept, commands disabled: {}",
                        path, e.getMessage());
            }
        }

        /** 服务器关闭：清空内存与路径，单人切换世界不残留 */
        void unload() {
            punishments.clear();
            file = null;
            writable = false;
        }

        /**
         * 设置/覆盖：候选表 → 原子写 → 成功后才发布内存。失败时内存与磁盘不变。
         */
        Result set(UUID id, String name, PunishmentMode mode, long expireAt) {
            if (!writable || file == null) {
                return Result.READ_ONLY;
            }
            long now = System.currentTimeMillis();
            Map<String, Entry> candidate = new HashMap<>(punishments);
            candidate.entrySet().removeIf(entry -> entry.getValue().expired(now));
            candidate.put(id.toString(), new Entry(name, mode, expireAt));
            if (!write(candidate)) {
                return Result.IO_ERROR;
            }
            publish(candidate);
            return Result.APPLIED;
        }

        /**
         * 解除：候选表 → 原子写 → 成功后才发布内存。已到期记录只清理，不报解除成功。
         */
        Result clear(UUID id) {
            if (!writable || file == null) {
                return Result.READ_ONLY;
            }
            Entry target = punishments.get(id.toString());
            if (target == null) {
                return Result.NOT_FOUND;
            }
            long now = System.currentTimeMillis();
            boolean active = !target.expired(now);
            Map<String, Entry> candidate = new HashMap<>(punishments);
            candidate.remove(id.toString());
            candidate.entrySet().removeIf(entry -> entry.getValue().expired(now));
            if (!write(candidate)) {
                return Result.IO_ERROR;
            }
            publish(candidate);
            return active ? Result.APPLIED : Result.NOT_FOUND;
        }

        private boolean write(Map<String, Entry> candidate) {
            if (!writable || file == null) {
                return false;
            }
            return writer.test(file, collectData(candidate));
        }

        private void publish(Map<String, Entry> candidate) {
            punishments.clear();
            punishments.putAll(candidate);
        }

        /** 严格解析：版本相同即完整校验，不允许未知字段被后续保存静默抹掉 */
        private static Map<String, Entry> parseEntries(JsonObject data) {
            JsonElement playersElem = data.get("players");
            if (playersElem == null || !playersElem.isJsonObject()) {
                throw new IllegalArgumentException("players is not an object");
            }
            Map<String, Entry> parsed = new HashMap<>();
            for (Map.Entry<String, JsonElement> raw : playersElem.getAsJsonObject().entrySet()) {
                String key = raw.getKey();
                if (!canonicalUuid(key)) {
                    throw new IllegalArgumentException("invalid uuid " + key);
                }
                if (!raw.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("entry is not an object: " + key);
                }
                JsonObject obj = raw.getValue().getAsJsonObject();
                if (!obj.keySet().equals(ENTRY_KEYS)) {
                    throw new IllegalArgumentException("unexpected entry fields: " + key);
                }
                JsonElement nameElem = obj.get("name");
                JsonElement modeElem = obj.get("mode");
                JsonElement expireElem = obj.get("expireAt");
                if (!nameElem.isJsonPrimitive() || !nameElem.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("invalid name: " + key);
                }
                if (!modeElem.isJsonPrimitive() || !modeElem.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("invalid mode: " + key);
                }
                PunishmentMode mode = PunishmentMode.byId(modeElem.getAsString());
                if (mode == null) {
                    throw new IllegalArgumentException("unknown mode: " + key);
                }
                if (!expireElem.isJsonPrimitive() || !expireElem.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("invalid expireAt: " + key);
                }
                long expireAt = requireIntegral(expireElem, "expireAt");
                if (expireAt < 0) {
                    throw new IllegalArgumentException("negative expireAt: " + key);
                }
                parsed.put(key, new Entry(nameElem.getAsString(), mode, expireAt));
            }
            return parsed;
        }

        /** 只接受十进制整数 JSON 数字；1.5、1e3、字符串和越界值均拒绝 */
        private static long requireIntegral(JsonElement element, String field) {
            if (element == null || !element.isJsonPrimitive()
                    || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("invalid " + field);
            }
            String raw = element.getAsString();
            if (!raw.matches("-?\\d+")) {
                throw new IllegalArgumentException("invalid " + field);
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + field);
            }
        }

        private static boolean canonicalUuid(String value) {
            try {
                return UUID.fromString(value).toString().equals(value);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        /** 供基类抽象钩子保留；本规则加载不走 base.attach */
        @Override
        protected void applyData(JsonObject data) {
            punishments.clear();
            punishments.putAll(parseEntries(data));
        }

        @Override
        protected JsonObject collectData() {
            return collectData(punishments);
        }

        /** 序列化候选/当前表，跳过已到期项；信封仍为 version 1 + data.players */
        private static JsonObject collectData(Map<String, Entry> source) {
            long now = System.currentTimeMillis();
            JsonObject players = new JsonObject();
            for (Map.Entry<String, Entry> entry : source.entrySet()) {
                if (entry.getValue().expired(now)) {
                    continue;
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("name", entry.getValue().name());
                obj.addProperty("mode", entry.getValue().mode().id());
                obj.addProperty("expireAt", entry.getValue().expireAt());
                players.add(entry.getKey(), obj);
            }
            JsonObject data = new JsonObject();
            data.add("players", players);
            JsonObject root = new JsonObject();
            root.addProperty("version", SCHEMA_VERSION);
            root.add("data", data);
            return root;
        }

        @Override
        protected void clearData() {
            punishments.clear();
        }
    }

    private ChatPunishmentManager() {}

    // ===== 生命周期 =====

    /** CarpetPrimaryuanServer.onGameStarted 调用：注册禁言监听（一次性） */
    public static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, parameters) -> {
            // 只处理普通公共聊天；/say、/me、/teammsg 等命令广播不得被禁言
            if (!parameters.chatType().is(ChatType.CHAT)) {
                return true;
            }
            if (modeOf(sender) != PunishmentMode.MUTE) {
                return true;
            }
            sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.chatpunish.muted"));
            return false;
        });
    }

    /** CarpetPrimaryuanServer.onServerLoaded 调用：读入存档级惩罚记录 */
    public static void onServerLoaded(MinecraftServer server) {
        STORE.load(server);
    }

    /** CarpetPrimaryuanServer.onServerClosed 调用：清空内存态（单人切换世界不残留） */
    public static void onServerClosed() {
        STORE.unload();
    }

    // ===== 查询与变更 =====

    /** 玩家当前生效的惩罚模式；规则关闭、无记录或已到期返回 null；发送者可为 null */
    public static PunishmentMode modeOf(ServerPlayer player) {
        if (player == null || !CarpetPrimaryuanSettings.chatPunishment) {
            return null;
        }
        Entry entry = STORE.punishments.get(player.getUUID().toString());
        if (entry == null || entry.expired(System.currentTimeMillis())) {
            return null;
        }
        return entry.mode();
    }

    /** 设置/覆盖惩罚（每玩家仅一种，直接覆盖）；expireAt = 0 表示永久 */
    public static Result set(UUID id, String name, PunishmentMode mode, long expireAt) {
        return STORE.set(id, name, mode, expireAt);
    }

    /** 解除惩罚；只有落盘成功且原记录仍在生效时返回 APPLIED */
    public static Result clear(UUID id) {
        return STORE.clear(id);
    }

    /** 当前未到期的全部记录（按名称排序，供 /chatpunish list） */
    public static List<Entry> activeEntries() {
        long now = System.currentTimeMillis();
        List<Entry> out = new ArrayList<>();
        for (Entry entry : STORE.punishments.values()) {
            if (!entry.expired(now)) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparing(Entry::name));
        return out;
    }

    // ===== 聊天装饰（文本改写） =====

    /**
     * 公共聊天解码回调调用：把发送者可见文本转换为对应模式结果。私聊、命令与
     * 系统消息不走该入口；无惩罚、禁言（广播层处理）与原样文本直接透传。
     */
    public static Component transformPublicChat(ServerPlayer sender, Component message) {
        if (sender == null || message == null) {
            return message;
        }
        PunishmentMode mode = modeOf(sender);
        if (mode == null || mode == PunishmentMode.MUTE) {
            return message;
        }
        String original = message.getString();
        if (original.isEmpty()) {
            return message;
        }
        String transformed = mode.transform(original);
        if (transformed == null || transformed.equals(original)) {
            return message;
        }
        return Component.literal(transformed).setStyle(message.getStyle());
    }

    // ===== 时长（真实时间，与 tick / 在线时长无关） =====

    /** 限时格式 {@code <数字><s|m|h|d>}；非法返回 null */
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    /** 解析限时为毫秒数；格式非法、为 0 或溢出返回 null */
    public static Long parseDuration(String spec) {
        Matcher matcher = DURATION_PATTERN.matcher(spec);
        if (!matcher.matches() || matcher.group(1).length() > 12) {
            return null;
        }
        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (value == 0) {
            return null;
        }
        long unitSeconds = switch (matcher.group(2)) {
            case "s" -> 1;
            case "m" -> 60;
            case "h" -> 3600;
            default -> 86400;
        };
        try {
            return Math.multiplyExact(Math.multiplyExact(value, unitSeconds), 1000L);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** 到期时间 = 当前毫秒 + 时长；溢出抛 ArithmeticException，由命令按非法时长反馈 */
    public static long expiryFrom(long now, long millis) {
        return Math.addExact(now, millis);
    }

    /** 剩余时间的紧凑显示（如 2d3h5m10s，跳过零段；不足 1 秒显示 0s） */
    public static String formatRemaining(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long total = millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
        long d = total / 86400;
        long h = total % 86400 / 3600;
        long m = total % 3600 / 60;
        long s = total % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) {
            out.append(d).append('d');
        }
        if (h > 0) {
            out.append(h).append('h');
        }
        if (m > 0) {
            out.append(m).append('m');
        }
        if (s > 0 || out.isEmpty()) {
            out.append(s).append('s');
        }
        return out.toString();
    }
}
