package me.primaryuan.carpet.config;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.InvalidRuleValueException;
import carpet.api.settings.RuleHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SERVER 类规则的钉值存储（config/carpet-pry-addition/server-rules.json，跟随服务器）。
 *
 * <p>SERVER 类规则不随存档 carpet.conf 漂移：attach 在 onServerLoaded 时调用——
 * carpet 读入存档 carpet.conf（attachServer）之后、世界加载之前——把文件中的值
 * 逐条 set 压回，服务器值永远优先。运行期经 /carpet 修改 SERVER 类规则时由规则
 * 观察者改即持久化（这类规则启动就被钉住，改了不落盘等于改不动）。</p>
 *
 * <p>文件中只接受 SERVER 类规则：未知规则名或非 SERVER 类规则会被忽略并告警，
 * 防止把本应随存档走的规则也钉死在服务器上。</p>
 */
public final class ServerRuleOverrides extends PryJsonStore {

    private static final ServerRuleOverrides INSTANCE = new ServerRuleOverrides();

    /** 规则名 → 规则值字符串（保持文件书写顺序） */
    private final Map<String, String> pinned = new LinkedHashMap<>();

    /**
     * apply 期间的 set 会再次触发规则观察者，用该标志避免观察者反向回写
     */
    private boolean applying;

    /**
     * 观察者激活标志：apply() 完成前静默——carpet 读存档 carpet.conf 时对每条
     * 规则的 set 也是一次"非 null 来源的规则变更"，若在钉值前回写会把存档值
     * 污染进 server-rules.json，钉值随之失效。detach 时关闭。
     */
    private boolean attached;

    private ServerRuleOverrides() {
        super("PryRules", "server-rules.json", Scope.GLOBAL, 1, null);
    }

    /**
     * 加载并钉值。必须在 carpet 读入存档 carpet.conf 之后调用
     * （CarpetPrimaryuanServer.onServerLoaded 时序即满足）。
     * 完成后观察者才开始持久化运行期变更。
     */
    public static void apply(MinecraftServer server) {
        INSTANCE.attach(server);
        INSTANCE.attached = true;
    }

    /** 服务器关闭：停用观察者并清空钉值（下一世界的 conf 加载窗口不再回写） */
    public static void onServerClosed() {
        INSTANCE.attached = false;
        INSTANCE.detach();
    }

    /** 规则观察者入口：SERVER 类规则运行期变更 → 改即持久化 */
    public static void onRuleChanged(CarpetRule<?> rule) {
        if (!rule.categories().contains(CarpetPrimaryuanSettings.SERVER)) {
            return;
        }
        if (!INSTANCE.attached || INSTANCE.applying) {
            return;
        }
        INSTANCE.pinned.put(rule.name(), RuleHelper.toRuleString(rule.value()));
        INSTANCE.save();
    }

    @Override
    protected void applyData(JsonObject data) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                parsed.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        pinned.clear();
        pinned.putAll(parsed);
        pinRules();
    }

    @Override
    protected JsonObject collectData() {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, String> entry : pinned.entrySet()) {
            data.addProperty(entry.getKey(), entry.getValue());
        }
        return data;
    }

    @Override
    protected void clearData() {
        pinned.clear();
    }

    private void pinRules() {
        MinecraftServer server = server();
        if (server == null) {
            return;
        }
        applying = true;
        try {
            for (Map.Entry<String, String> entry : pinned.entrySet()) {
                CarpetRule<?> rule = CarpetServer.settingsManager.getCarpetRule(entry.getKey());
                if (rule == null) {
                    LOGGER.warn("[PryRules] Unknown rule '{}' in server-rules.json, ignoring", entry.getKey());
                    continue;
                }
                if (!rule.categories().contains(CarpetPrimaryuanSettings.SERVER)) {
                    LOGGER.warn("[PryRules] Rule '{}' is not a SERVER rule, ignoring (it follows the world save)",
                            entry.getKey());
                    continue;
                }
                try {
                    rule.set(server.createCommandSourceStack(), entry.getValue());
                } catch (InvalidRuleValueException e) {
                    LOGGER.error("[PryRules] Invalid value '{}' for rule '{}', keeping current value",
                            entry.getValue(), entry.getKey());
                }
            }
        } finally {
            applying = false;
        }
    }
}
