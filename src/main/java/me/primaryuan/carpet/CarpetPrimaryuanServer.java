package me.primaryuan.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.utils.CommandHelper;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.command.HatCommand;
import me.primaryuan.carpet.command.PvpCommand;
import me.primaryuan.carpet.command.RedPacketCommand;
import me.primaryuan.carpet.command.PatNodCommand;
import me.primaryuan.carpet.command.RidingCommand;
import me.primaryuan.carpet.command.ScaleCommand;
import me.primaryuan.carpet.command.TextCommand;
import me.primaryuan.carpet.command.TppCommand;
import me.primaryuan.carpet.handler.clickThrough.ClickThroughHandler;
import me.primaryuan.carpet.handler.entitiesRidingPlayers.EntitiesRidingPlayersHandler;
import me.primaryuan.carpet.handler.patPatPlayers.PatPatPlayersHandler;
import me.primaryuan.carpet.handler.peacefulPlayers.PvpManager;
import me.primaryuan.carpet.handler.redPacket.RedPacketManager;
import me.primaryuan.carpet.handler.textAnimation.TextAnimationHandler;
import me.primaryuan.carpet.handler.whoCalledMe.WhoCalledMeHandler;
import me.primaryuan.carpet.settings.CarpetRuleRegistrar;
import me.primaryuan.carpet.util.MixinSanityCheck;
import me.primaryuan.carpet.util.SendtoLinkManager;
//#if MC < 12111
//$$ import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
//#endif
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
            "fakePlayerTpp",                  // /tpp /tppset
            "playerHat",                      // /hat
            "ridingPlayers",                  // /riding
            "pickupPlayers",                  // /picking
            "fakePlayerDropAll",   // /player <name> dropall
            "fakePlayerSendto",               // /player <name> sendto
            "fakePlayerBrain",                // /player <name> brain
            "playerScale",                    // /scale
            "patPatPlayers",                  // /patnod
            "peacefulPlayers",                // /pvp
            "textAnimation",                  // /text
            "redPacket"                       // /redpacket
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
        PatNodCommand.register();
        TextCommand.register();
        RedPacketCommand.register();

        // 和平的玩家（pvp）：加载持久化状态、注册 PVP 伤害拦截与玩家加入登记
        PvpManager.init();

        // 骑乘/捡起许可表的下线清理兜底扫描（假人在默认配置下不触发 DISCONNECT）
        EntitiesRidingPlayersHandler.init();

        // 摸摸头：过期冷却条目的周期清理
        PatPatPlayersHandler.init();

        // 谁在叫我：注册聊天监听（聊天中出现其他玩家名字时提示音+title 点名）
        WhoCalledMeHandler.init();

        // 米塔字幕：孤儿实体清扫（启动）与停服清理
        TextAnimationHandler.init();

        // 红包：口令聊天监听、上线补发、过期检查与停服清理
        RedPacketManager.init();

        // 假人背包链接（sendto）：初始化 tick 转移调度、假人下线清理与服务器停止清空监听
        SendtoLinkManager.init();

        // 假人脑子（brain）：初始化会话周期扫描与停服清理
        // （跨维度不在此处重挂——由 MobFields 检测维度变化重建寻路器并作废旧路径）
        BrainManager.init();

        // require=0 注入点自检：规则开启但注入被静默跳过时以 error 点名（MixinSanityCheck）
        MixinSanityCheck.init();

        // 不按规则门控：onLogOut 除骑乘下车外还清理骑乘/捡起两张许可表，
        // 只开 pickupPlayers 的服务器同样需要下线清理，否则同名重进继承旧许可
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            EntitiesRidingPlayersHandler.onLogOut(handler.player);
        });

        // 穿透点击：右键墙告示牌/挂墙木牌/墙横幅直接打开背后容器（展示框/画在下方 UseEntityCallback 内）
        UseBlockCallback.EVENT.register(ClickThroughHandler::useBlock);

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            // 穿透点击最先处理：展示框/发光展示框/画命中且背后有容器时消费交互（打开背后容器），
            // 原版旋转/放置不再走、其余监听短路；PASS 时自然落到骑乘/摸摸头（目标类型互斥）
            if (CarpetPrimaryuanSettings.clickThrough) {
                InteractionResult clickResult = ClickThroughHandler.useEntity(player, level, hand, entity, hitResult);
                if (clickResult != InteractionResult.PASS) {
                    return clickResult;
                }
            }

            if (CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers) {
                InteractionResult rideResult =
                        EntitiesRidingPlayersHandler.rideOrPickUp(player, entity, level, hand, hitResult);
                // 仅在真正处理了交互（非 PASS）时短路；PASS 必须放行，
                // 否则任一规则开启时后续监听（摸摸头）永远不可达
                if (rideResult != InteractionResult.PASS) {
                    return rideResult;
                }
            }

            // 摸摸头最后处理：只做效果、恒不消费交互（火后不管），原版行为与其它模组的
            // 同名事件监听不受影响；骑乘/捡起在前面已按点击部位（头/腿脚/躯干）自然分流
            PatPatPlayersHandler.patPlayer(player, level, hand, entity, hitResult);

            return InteractionResult.PASS;
        });

        //#if MC < 12111
        //$$ // 白日做梦（1.21~1.21.10）：白天入睡检查由 fabric-entity-events-v1 的
        //$$ // ALLOW_SLEEP_TIME 钩子接管（该检查点在旧版本分别位于 Level.isDay /
        //$$ // (Server)Level.isBrightOutside，fabric 已按版本适配注入点，本模组直接
        //$$ // @Redirect 同一调用会冲突）。规则开启时返回 SUCCESS 放行白天入睡，
        //$$ // 关闭时 PASS 走原版；怪物检测等其他入睡条件不受影响。
        //$$ // 1.21.11+ 由 sleepingDuringTheDay.MixinPlayer 的 BedRule.canSleep
        //$$ // redirect 处理（fabric 在该版本段未占用此调用点）。
        //$$ EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, sleepingPos, vanillaResult) ->
        //$$         CarpetPrimaryuanSettings.sleepingDuringTheDay
        //$$                 ? InteractionResult.SUCCESS
        //$$                 : InteractionResult.PASS);
        //#endif

        // 规则变更时刷新命令树，使所有受控命令的可见性立即随对应规则切换
        CarpetServer.settingsManager.registerRuleObserver((source, changedRule, userInput) -> {
            if (COMMAND_VISIBILITY_RULES.contains(changedRule.name())) {
                CommandHelper.notifyPlayersCommandsChanged(source.getServer());
            }
            MixinSanityCheck.onRuleChanged(changedRule.name());
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
