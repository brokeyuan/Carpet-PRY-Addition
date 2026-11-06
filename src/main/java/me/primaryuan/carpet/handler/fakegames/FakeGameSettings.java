package me.primaryuan.carpet.handler.fakegames;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.RuleHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.config.PryJsonStore;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 假游戏管理员配置（服务器级，config/carpet-pry-addition/fake-games.json）。
 *
 * <p>只保存管理员策略：局内临时规则总开关、区域半径上限、边界刷新周期与场地预设。
 * 读取失败保留磁盘文件并用默认值运行；版本超前只读；写入使用基类原子写。</p>
 */
public final class FakeGameSettings extends PryJsonStore {

    private static final FakeGameSettings INSTANCE = new FakeGameSettings();

    /** 硬上限：无论配置文件写多少，区域半径不得超过该值 */
    public static final int HARD_MAX_RADIUS = 128;
    public static final int MIN_RADIUS = 8;

    public record Preset(String name, int radius, int minY, int maxY) {}

    private boolean allowTemporaryRules;
    private int maxRadius = 64;
    private int defaultRadius = 32;
    private int boundaryIntervalTicks = 10;
    private final Map<String, Preset> presets = new LinkedHashMap<>();

    private boolean active;

    private FakeGameSettings() {
        super("FakeGames", "fake-games.json", Scope.GLOBAL, 1, null);
        resetDefaults();
    }

    public static FakeGameSettings get() {
        return INSTANCE;
    }

    public static void onServerLoaded(MinecraftServer server) {
        INSTANCE.attach(server);
        INSTANCE.active = true;
    }

    public static void onServerClosed() {
        INSTANCE.active = false;
        INSTANCE.detach();
    }

    // ==================== 查询 ====================

    public static boolean temporaryRulesAllowed() {
        return INSTANCE.allowTemporaryRules;
    }

    public static int maxRadius() {
        return Math.min(HARD_MAX_RADIUS, Math.max(MIN_RADIUS, INSTANCE.maxRadius));
    }

    public static int defaultRadius() {
        return clampRadius(INSTANCE.defaultRadius);
    }

    public static int boundaryIntervalTicks() {
        return Math.max(4, Math.min(40, INSTANCE.boundaryIntervalTicks));
    }

    public static Preset preset(String name) {
        Preset preset = INSTANCE.presets.get(name == null ? "" : name);
        return preset != null ? preset : INSTANCE.presets.get("standard");
    }

    public static Preset defaultPreset() {
        return preset("standard");
    }

    public static java.util.List<String> presetNames() {
        return java.util.List.copyOf(INSTANCE.presets.keySet());
    }

    public static java.util.List<Preset> presets() {
        return java.util.List.copyOf(INSTANCE.presets.values());
    }

    public static int clampRadius(int radius) {
        return Math.max(MIN_RADIUS, Math.min(maxRadius(), radius));
    }

    /** 依赖规则是否开启；规则不存在按未开启处理（用于准备页检查）。 */
    public static boolean ruleEnabled(String ruleName) {
        try {
            CarpetRule<?> rule = CarpetServer.settingsManager.getCarpetRule(ruleName);
            if (rule == null) {
                return false;
            }
            String value = RuleHelper.toRuleString(rule.value());
            return value != null && !"false".equalsIgnoreCase(value);
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ==================== 管理员修改 ====================

    public static boolean setTemporaryRules(boolean allowed) {
        INSTANCE.allowTemporaryRules = allowed;
        INSTANCE.saveIfActive();
        return true;
    }

    public static boolean setMaxRadius(int radius) {
        if (radius < MIN_RADIUS || radius > HARD_MAX_RADIUS) {
            return false;
        }
        INSTANCE.maxRadius = radius;
        INSTANCE.defaultRadius = Math.min(INSTANCE.defaultRadius, radius);
        INSTANCE.saveIfActive();
        return true;
    }

    public static boolean setBoundaryInterval(int ticks) {
        if (ticks < 4 || ticks > 40) {
            return false;
        }
        INSTANCE.boundaryIntervalTicks = ticks;
        INSTANCE.saveIfActive();
        return true;
    }

    public static boolean addPreset(String name, int radius, int minY, int maxY) {
        if (name == null || !name.matches("[a-z0-9_]{1,16}")) {
            return false;
        }
        if (radius < MIN_RADIUS || radius > maxRadius() || minY >= maxY
                || minY < -64 || maxY > 128) {
            return false;
        }
        INSTANCE.presets.put(name, new Preset(name, clampRadius(radius), minY, maxY));
        INSTANCE.saveIfActive();
        return true;
    }

    private void saveIfActive() {
        if (active) {
            save();
        }
    }

    // ==================== 存储 ====================

    private void resetDefaults() {
        allowTemporaryRules = false;
        maxRadius = 64;
        defaultRadius = 32;
        boundaryIntervalTicks = 10;
        presets.clear();
        presets.put("small", new Preset("small", 24, -8, 24));
        presets.put("standard", new Preset("standard", 32, -8, 32));
        presets.put("large", new Preset("large", 48, -8, 48));
    }

    @Override
    protected void applyData(JsonObject data) {
        boolean parsedAllow = false;
        int parsedMax = 64;
        int parsedDefault = 32;
        int parsedInterval = 10;
        Map<String, Preset> parsedPresets = new LinkedHashMap<>();
        JsonElement allow = data.get("allowTemporaryRules");
        if (allow != null && allow.isJsonPrimitive()) {
            parsedAllow = allow.getAsBoolean();
        }
        JsonElement max = data.get("maxRadius");
        if (max != null && max.isJsonPrimitive()) {
            try {
                parsedMax = Math.max(MIN_RADIUS, Math.min(HARD_MAX_RADIUS, max.getAsInt()));
            } catch (RuntimeException ignored) {
            }
        }
        JsonElement def = data.get("defaultRadius");
        if (def != null && def.isJsonPrimitive()) {
            try {
                parsedDefault = Math.max(MIN_RADIUS, Math.min(parsedMax, def.getAsInt()));
            } catch (RuntimeException ignored) {
            }
        }
        JsonElement interval = data.get("boundaryIntervalTicks");
        if (interval != null && interval.isJsonPrimitive()) {
            try {
                parsedInterval = Math.max(4, Math.min(40, interval.getAsInt()));
            } catch (RuntimeException ignored) {
            }
        }
        JsonElement list = data.get("presets");
        if (list != null && list.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : list.getAsJsonObject().entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject object = entry.getValue().getAsJsonObject();
                try {
                    int radius = object.get("radius").getAsInt();
                    int minY = object.get("minY").getAsInt();
                    int maxY = object.get("maxY").getAsInt();
                    if (radius >= MIN_RADIUS && radius <= HARD_MAX_RADIUS && minY < maxY) {
                        parsedPresets.put(entry.getKey(), new Preset(entry.getKey(),
                                Math.max(MIN_RADIUS, Math.min(parsedMax, radius)), minY, maxY));
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        allowTemporaryRules = parsedAllow;
        maxRadius = parsedMax;
        defaultRadius = parsedDefault;
        boundaryIntervalTicks = parsedInterval;
        presets.clear();
        if (parsedPresets.isEmpty()) {
            presets.put("small", new Preset("small", 24, -8, 24));
            presets.put("standard", new Preset("standard", 32, -8, 32));
            presets.put("large", new Preset("large", 48, -8, 48));
        } else {
            presets.putAll(parsedPresets);
        }
    }

    @Override
    protected JsonObject collectData() {
        JsonObject data = new JsonObject();
        data.addProperty("allowTemporaryRules", allowTemporaryRules);
        data.addProperty("maxRadius", maxRadius);
        data.addProperty("defaultRadius", defaultRadius);
        data.addProperty("boundaryIntervalTicks", boundaryIntervalTicks);
        JsonObject list = new JsonObject();
        for (Preset preset : presets.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("radius", preset.radius());
            object.addProperty("minY", preset.minY());
            object.addProperty("maxY", preset.maxY());
            list.add(preset.name(), object);
        }
        data.add("presets", list);
        return data;
    }

    @Override
    protected void clearData() {
        resetDefaults();
    }
}
