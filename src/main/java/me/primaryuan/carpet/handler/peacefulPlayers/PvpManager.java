package me.primaryuan.carpet.handler.peacefulPlayers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.command.CommandSupport;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 和平的玩家（peacefulPlayers）核心管理器。
 *
 * 规则本身不改变任何原版行为，仅启用 /pvp 指令；PVP 的关闭完全由指令按玩家驱动：
 * - 全服默认状态（default）+ 每玩家覆盖状态（playerStates），未单独设置的玩家跟随默认；
 * - /pvp set &lt;玩家&gt; on|off：单独设置某个玩家（仅在线玩家）；
 * - /pvp set @a on|off：全服总开关——更新默认状态并覆盖所有已登记玩家；
 * - /pvp list：列出所有 PVP 为 off 的玩家（含离线已登记玩家）。
 *
 * 拦截方式：Fabric ServerLivingEntityEvents.ALLOW_DAMAGE（LivingEntity.hurt/hurtServer
 * 入口处、护甲等减伤计算前触发），规则开启时玩家间伤害的双方任一处于 off 即取消，
 * 自伤（伤害来源为自身）不受限制。不使用 mixin，无跨版本签名问题。
 *
 * 持久化：config/carpet-pry-pvp.json（UTF-8，临时文件原子替换，模式同 TppConfigManager），
 * 玩家状态跨重启、跨重登保留；玩家加入时按当前默认状态登记，保证 /pvp list 完整。
 */
public final class PvpManager {

    /** PVP 状态取值：开启 / 关闭（关闭 = 和平状态） */
    public static final String STATE_ON = "on";
    public static final String STATE_OFF = "off";

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 配置文件路径（统一以 UTF-8 读写，避免 Windows 默认 GBK 编码导致玩家名乱码） */
    private static final Path CONFIG_FILE = Path.of("config/carpet-pry-pvp.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 全服默认 PVP 状态（仅 /pvp set @a 时更新；此后新登记玩家跟随此值） */
    private static String defaultState = STATE_ON;

    /**
     * 玩家名 → PVP 状态（"on"/"off"）。
     * 仅服务器主线程访问（Carpet 命令与 Fabric 服务器事件均在主线程），普通集合即可。
     */
    private static final Map<String, String> playerStates = new HashMap<>();

    private static boolean initialized = false;

    private PvpManager() {}

    // ===== 初始化 =====

    /**
     * 初始化：加载持久化状态，注册 PVP 伤害拦截与玩家加入登记。
     * 由 CarpetPrimaryuanServer.onGameStarted 调用一次。
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        load();

        // 玩家间伤害拦截：规则开启（非 false 模式）时，双方任一玩家 PVP 为 off 即取消（双向保护，自伤除外）
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if ("false".equalsIgnoreCase(CarpetPrimaryuanSettings.peacefulPlayers)) return true;
            if (!(entity instanceof ServerPlayer victim)) return true;
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
            if (attacker == victim) return true;
            return isPvpOn(victim) && isPvpOn(attacker);
        });

        // 玩家加入时按当前默认状态登记，保证 /pvp list 能列出所有 PVP 关闭的玩家（含之后离线的）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!"false".equalsIgnoreCase(CarpetPrimaryuanSettings.peacefulPlayers)) {
                registerPlayer(handler.player);
            }
        });
    }

    // ===== 状态查询 =====

    /**
     * 玩家当前是否允许 PVP（未单独设置时跟随全服默认状态）。
     */
    public static boolean isPvpOn(ServerPlayer player) {
        return STATE_ON.equals(playerStates.getOrDefault(CommandSupport.profileName(player), defaultState));
    }

    /**
     * 获取全服默认 PVP 状态（"on"/"off"）。
     */
    public static String getDefaultState() {
        return defaultState;
    }

    /**
     * 列出所有 PVP 为 off 的玩家名（在线玩家 + 已登记的离线玩家，按名称排序）。
     */
    public static List<String> listPvpOffPlayers(MinecraftServer server) {
        List<String> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isPvpOn(player)) {
                result.add(CommandSupport.profileName(player));
            }
        }
        for (Map.Entry<String, String> entry : playerStates.entrySet()) {
            if (STATE_OFF.equals(entry.getValue())
                    && server.getPlayerList().getPlayerByName(entry.getKey()) == null) {
                result.add(entry.getKey());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    // ===== 状态变更（变更后自动持久化） =====

    /**
     * 登记玩家（按当前默认状态）；已登记则忽略。
     */
    public static void registerPlayer(ServerPlayer player) {
        String name = CommandSupport.profileName(player);
        if (!playerStates.containsKey(name)) {
            playerStates.put(name, defaultState);
            save();
        }
    }

    /**
     * 设置单个玩家的 PVP 状态（"on"/"off"）。
     */
    public static void setPlayerState(String playerName, String state) {
        playerStates.put(playerName, state);
        save();
    }

    /**
     * 全服总开关：更新默认状态，并把所有已登记玩家（含离线）覆盖为同一状态。
     */
    public static void setAllStates(String state) {
        defaultState = state;
        for (Map.Entry<String, String> entry : playerStates.entrySet()) {
            entry.setValue(state);
        }
        save();
    }

    // ===== 持久化 =====

    private static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                LOGGER.warn("[PVP] State file is empty, using defaults");
                return;
            }

            playerStates.clear();
            if (json.has("default") && isValidState(json.get("default").getAsString())) {
                defaultState = json.get("default").getAsString();
            }

            JsonElement playersElem = json.get("players");
            if (playersElem != null && playersElem.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : playersElem.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && isValidState(entry.getValue().getAsString())) {
                        playerStates.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[PVP] Failed to load state file", e);
        }
    }

    private static boolean isValidState(String state) {
        return STATE_ON.equals(state) || STATE_OFF.equals(state);
    }

    private static void save() {
        JsonObject config = new JsonObject();
        config.addProperty("default", defaultState);

        JsonObject playersObj = new JsonObject();
        for (Map.Entry<String, String> entry : playerStates.entrySet()) {
            playersObj.addProperty(entry.getKey(), entry.getValue());
        }
        config.add("players", playersObj);

        try {
            // 原子写：先写临时文件再原子替换，避免写一半崩溃/断电留下损坏的 JSON
            Path tmp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("[PVP] Failed to save state file", e);
        }
    }
}
