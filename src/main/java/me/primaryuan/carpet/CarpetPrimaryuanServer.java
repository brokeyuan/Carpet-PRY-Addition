package me.primaryuan.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.utils.CommandHelper;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.primaryuan.carpet.command.HatCommand;
import me.primaryuan.carpet.command.RidingCommand;
import me.primaryuan.carpet.command.ScaleCommand;
import me.primaryuan.carpet.command.TppCommand;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import me.primaryuan.carpet.settings.CarpetRuleRegistrar;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CarpetPrimaryuanServer implements CarpetExtension {
    private static final CarpetPrimaryuanServer INSTANCE = new CarpetPrimaryuanServer();
    public static final String shortName = "pry";
    public static final String name = CarpetPrimaryuanMod.getModId();
    public static final String fancyName = "Carpet Primaryuan";
    public static final Logger LOGGER = LogManager.getLogger(fancyName);

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
        LOGGER.info("fakePlayerNameSuggestions feature enabled");
        TppCommand.register();
        LOGGER.info("tpp command registered");
        HatCommand.register();
        LOGGER.info("hat command registered");
        TppConfigManager.load();
        LOGGER.info("TPP config loaded");

        RidingCommand.register();
        LOGGER.info("riding command registered");

        ScaleCommand.register();
        LOGGER.info("scale command registered");

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (CarpetPrimaryuanSettings.ridingPlayers) {
                EntitiesRidingPlayersHandler.onLogOut(handler.player);
            }
        });
        LOGGER.info("ridingPlayers disconnect listener registered");

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
        LOGGER.info("ridingPlayers use entity listener registered");

        // 规则变更时刷新命令树，使 dropall / scale 可见性立即随对应规则切换
        CarpetServer.settingsManager.registerRuleObserver((source, changedRule, userInput) -> {
            String ruleName = changedRule.name();
            if ("fakePlayerDropStackModifiers".equals(ruleName)
                    || "playerScaleModifiers".equals(ruleName)) {
                CommandHelper.notifyPlayersCommandsChanged(source.getServer());
            }
        });
        LOGGER.info("fakePlayerDropStackModifiers & playerScaleModifiers rule observer registered");
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        Map<String, String> translations = Maps.newHashMap();
        String langFile = "/assets/" + CarpetPrimaryuanMod.getModId() + "/lang/" + lang.toLowerCase() + ".json";
        try (InputStream is = CarpetPrimaryuanServer.class.getResourceAsStream(langFile)) {
            if (is == null) {
                return translations;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString();
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                translations.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            LOGGER.debug("No translation found for language: " + lang);
        }
        return translations;
    }
}
