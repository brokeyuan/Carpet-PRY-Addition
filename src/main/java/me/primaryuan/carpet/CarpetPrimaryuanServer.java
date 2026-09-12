package me.primaryuan.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.utils.CommandHelper;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.primaryuan.carpet.command.HatCommand;
import me.primaryuan.carpet.command.PvpCommand;
import me.primaryuan.carpet.command.RidingCommand;
import me.primaryuan.carpet.command.ScaleCommand;
import me.primaryuan.carpet.command.TppCommand;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import me.primaryuan.carpet.handler.peacefulPlayers.PvpManager;
import me.primaryuan.carpet.settings.CarpetRuleRegistrar;
import me.primaryuan.carpet.util.SendtoLinkManager;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CarpetPrimaryuanServer implements CarpetExtension {
    private static final CarpetPrimaryuanServer INSTANCE = new CarpetPrimaryuanServer();
    public static final String shortName = "pry";
    public static final String name = CarpetPrimaryuanMod.getModId();
    public static final String fancyName = "Carpet Primaryuan";
    public static final Logger LOGGER = LogManager.getLogger(fancyName);

    /**
     * 控制命令可见性的规则名集合（规则名 = Settings 字段名）。
     * 这些规则变更时需要刷新玩家命令树，使命令"不开启则不显示"立即生效。
     */
    private static final Set<String> COMMAND_VISIBILITY_RULES = Set.of(
            "TppFakePlayer",                  // /tpp /tppset
            "playerhat",                      // /hat
            "ridingPlayers",                  // /riding
            "pickupPlayers",                  // /picking
            "fakePlayerDropStackModifiers",   // /player <name> dropall
            "playerScale",                    // /scale
            "fakePlayerSendto",               // /player <name> sendto
            "peacefulPlayers"                 // /pvp
    );

    @Override
    public String version() {
        return name;
    }

    public static CarpetPrimaryuanServer getInstance() {
        return INSTANCE;
    }

    public static void init() {
        CarpetServer.manageExtension(INSTANCE);
    }

    @Override
    public void onGameStarted() {
        LOGGER.info(fancyName + " " + CarpetPrimaryuanMod.getVersion() + " loaded");
        CarpetRuleRegistrar.register(CarpetPrimaryuanSettings.class);
        TppCommand.register();
        HatCommand.register();
        TppConfigManager.load();
        RidingCommand.register();
        ScaleCommand.register();
        PvpCommand.register();

        // 和平的玩家（pvp）：加载持久化状态、注册 PVP 伤害拦截与玩家加入登记
        PvpManager.init();

        // 假人背包链接（sendto）：初始化 tick 转移调度、假人下线清理与服务器停止清空监听
        SendtoLinkManager.init();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (CarpetPrimaryuanSettings.ridingPlayers) {
                EntitiesRidingPlayersHandler.onLogOut(handler.player);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (CarpetPrimaryuanSettings.ridingPlayers) {
                InteractionResult rideResult = EntitiesRidingPlayersHandler.rideEntity(player, entity, level, hand);
                if (rideResult != InteractionResult.PASS) {
                    return rideResult;
                }
            }

            if (CarpetPrimaryuanSettings.pickupPlayers) {
                return EntitiesRidingPlayersHandler.pickUpEntity(player, entity, level, hand);
            }

            return InteractionResult.PASS;
        });

        // 规则变更时刷新命令树，使所有受控命令的可见性立即随对应规则切换
        CarpetServer.settingsManager.registerRuleObserver((source, changedRule, userInput) -> {
            if (COMMAND_VISIBILITY_RULES.contains(changedRule.name())) {
                CommandHelper.notifyPlayersCommandsChanged(source.getServer());
            }
        });
    }

    /** 语言翻译缓存：canHasTranslations 会被反复调用，按语言缓存解析结果 */
    private static final Map<String, Map<String, String>> TRANSLATION_CACHE = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return TRANSLATION_CACHE.computeIfAbsent(lang.toLowerCase(), this::loadTranslations);
    }

    private Map<String, String> loadTranslations(String lang) {
        Map<String, String> translations = Maps.newHashMap();
        String langFile = "/assets/" + CarpetPrimaryuanMod.getModId() + "/lang/" + lang.toLowerCase() + ".json";
        try (InputStream is = CarpetPrimaryuanServer.class.getResourceAsStream(langFile)) {
            if (is == null) {
                return translations;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            LOGGER.debug("No translation found for language: " + lang);
        }
        return translations;
    }
}
