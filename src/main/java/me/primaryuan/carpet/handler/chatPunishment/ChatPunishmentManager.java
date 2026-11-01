package me.primaryuan.carpet.handler.chatPunishment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.config.PryJsonStore;
import me.primaryuan.carpet.handler.whoCalledMe.WhoCalledMeHandler;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天惩罚（chatPunishment）：管理员经 /chatpunish 对指定玩家施加聊天限制，
 * 每名玩家同时只生效一种模式，支持永久与限时。
 *
 * <p>文本改写走原版 ChatDecorator 扩展点（原版滤嘴同款）：装饰结果作为未签名
 * 内容随签名消息广播，客户端显示未签名内容，签名校验不受影响。装饰器由
 * whoCalledMe.MinecraftServerMixin 的接管点组合调用——本规则先转换文本，
 * whoCalledMe 名字高亮后跑（看到的即转换后文本）。禁言不走文本转换：经
 * Fabric ALLOW_CHAT_MESSAGE（注入点在 PlayerList.broadcastChatMessage 的
 * HEAD，覆盖签名与非签名公共聊天）取消广播，仅给发送者回提示；命令与私聊
 * 不经过该路径，不受影响。</p>
 *
 * <p>持久化：{@code <存档>/carpet-pry-addition/chatpunish.json}（WORLD 作用域），
 * 按 UUID 记录 {名称, 模式, 到期时间戳}，到期时间 0 = 永久。按真实时间
 * （epoch millis）判定到期，重启、重连不丢失，不依赖玩家在线或 tick；
 * 到期在读取处惰性判定，落盘时清理。规则关闭不改聊天、不清记录，重新开启
 * 后未到期惩罚继续生效。</p>
 */
public final class ChatPunishmentManager {

    private static final ChatPunishStore STORE = new ChatPunishStore();

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
        /** UUID 字符串 → 记录；仅服务器主线程访问（聊天装饰与命令均在主线程） */
        final Map<String, Entry> punishments = new HashMap<>();

        ChatPunishStore() {
            super("ChatPunish", "chatpunish.json", Scope.WORLD, 1, null);
        }

        @Override
        protected void applyData(JsonObject data) {
            // 临时结构全部解析成功后再原子提交内存态：坏文件中途抛异常时
            // 不清空现有记录，避免之后任意一次 save() 把空表写回磁盘
            Map<String, Entry> parsed = new HashMap<>();
            JsonElement playersElem = data.get("players");
            if (playersElem != null && playersElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : playersElem.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    PunishmentMode mode = PunishmentMode.byId(obj.has("mode") && obj.get("mode").isJsonPrimitive()
                            ? obj.get("mode").getAsString() : "");
                    if (mode == null) {
                        continue;
                    }
                    parsed.put(entry.getKey(), new Entry(
                            obj.has("name") && obj.get("name").isJsonPrimitive() ? obj.get("name").getAsString() : "",
                            mode,
                            obj.has("expireAt") && obj.get("expireAt").isJsonPrimitive()
                                    ? obj.get("expireAt").getAsLong() : 0));
                }
            }
            punishments.clear();
            punishments.putAll(parsed);
        }

        @Override
        protected JsonObject collectData() {
            long now = System.currentTimeMillis();
            JsonObject data = new JsonObject();
            JsonObject players = new JsonObject();
            for (Map.Entry<String, Entry> entry : punishments.entrySet()) {
                if (entry.getValue().expired(now)) {
                    continue;
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("name", entry.getValue().name());
                obj.addProperty("mode", entry.getValue().mode().id());
                obj.addProperty("expireAt", entry.getValue().expireAt());
                players.add(entry.getKey(), obj);
            }
            data.add("players", players);
            return data;
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
            if (modeOf(sender) != PunishmentMode.MUTE) {
                return true;
            }
            sender.sendSystemMessage(ServerI18n.tr("carpetprimaryuan.chatpunish.muted"));
            return false;
        });
    }

    /** CarpetPrimaryuanServer.onServerLoaded 调用：读入存档级惩罚记录 */
    public static void onServerLoaded(net.minecraft.server.MinecraftServer server) {
        STORE.attach(server);
    }

    /** CarpetPrimaryuanServer.onServerClosed 调用：清空内存态（单人切换世界不残留） */
    public static void onServerClosed() {
        STORE.detach();
    }

    // ===== 查询与变更 =====

    /** 玩家当前生效的惩罚模式；规则关闭、无记录或已到期返回 null */
    public static PunishmentMode modeOf(ServerPlayer player) {
        if (!CarpetPrimaryuanSettings.chatPunishment) {
            return null;
        }
        Entry entry = STORE.punishments.get(player.getUUID().toString());
        if (entry == null || entry.expired(System.currentTimeMillis())) {
            return null;
        }
        return entry.mode();
    }

    /** 设置/覆盖惩罚（每玩家仅一种，直接覆盖）；expireAt = 0 表示永久 */
    public static void set(UUID id, String name, PunishmentMode mode, long expireAt) {
        STORE.punishments.put(id.toString(), new Entry(name, mode, expireAt));
        STORE.save();
    }

    /** 解除惩罚；无生效记录返回 false */
    public static boolean clear(UUID id) {
        Entry removed = STORE.punishments.remove(id.toString());
        if (removed == null) {
            return false;
        }
        STORE.save();
        return true;
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
     * MinecraftServerMixin 调用：返回组合装饰器——先做本规则的文本转换，
     * 再交 whoCalledMe 名字高亮；两规则各自关闭时逐层透传，行为与原来一致。
     */
    public static ChatDecorator wrapChatDecorator(ChatDecorator original) {
        ChatDecorator mention = WhoCalledMeHandler.wrapChatDecorator(original);
        return (sender, message) -> mention.decorate(sender, punished(sender, message));
    }

    /** 被惩罚玩家的聊天文本转换为对应模式结果；无惩罚或禁言原样透传 */
    private static Component punished(ServerPlayer sender, Component message) {
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
        long seconds = value * unitSeconds;
        if (seconds / unitSeconds != value) {
            return null;
        }
        return seconds * 1000L;
    }

    /** 剩余时间的紧凑显示（如 2d3h5m10s，跳过零段；不足 1 秒显示 0s） */
    public static String formatRemaining(long millis) {
        long total = (millis + 999) / 1000;
        long d = total / 86400;
        long h = total % 86400 / 3600;
        long m = total % 3600 / 60;
        long s = total % 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) {
            out.append(d).append("d");
        }
        if (h > 0) {
            out.append(h).append("h");
        }
        if (m > 0) {
            out.append(m).append("m");
        }
        if (s > 0 || out.isEmpty()) {
            out.append(s).append("s");
        }
        return out.toString();
    }
}
