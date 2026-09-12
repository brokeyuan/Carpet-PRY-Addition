package me.primaryuan.carpet;

import me.primaryuan.carpet.settings.Rule;

import static carpet.api.settings.RuleCategory.*;

public class CarpetPrimaryuanSettings {
    public static final String PRIMARYUAN = "PRIMARYUAN";
    public static final String BOT = "BOT";
    public static final String PORTING = "PORTING";
    public static final String COMMAND = "COMMAND";
    public static final String BUGFIX = "BUGFIX";

    @Rule(
            categories = {PRIMARYUAN, BOT, COMMAND}
    )
    public static boolean TppFakePlayer = false;

    // 假人名补全建议列表（逗号分隔），空 = 关闭（保持 Carpet 原版建议）
    @Rule(
            options = {"Steve,Alex", "Pry,hsds", "Pry,hsds,Firework,Food", ""},
            strict = false,
            categories = {PRIMARYUAN, BOT}
    )
    public static String fakePlayerNameSuggestions = "Steve,Alex";

    @Rule(
            options = {"default", "summon", "same_skin"},
            strict = false,
            categories = {PRIMARYUAN, BOT}
    )
    public static String fakePlayerSkinMode = "default";

    @Rule(
            options = {"Brokeyuan", "hsds", ""},
            strict = false,
            categories = {PRIMARYUAN, BOT}
    )
    public static String fakePlayerSkinSet = "Brokeyuan";

    @Rule(
            categories = {PRIMARYUAN, BUGFIX}
    )
    public static boolean FixXaeroLib = false;

    @Rule(
            categories = {PRIMARYUAN, BUGFIX}
    )
    public static boolean FixBluemap = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean invisibleInTallGrass = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, COMMAND}
    )
    public static boolean playerhat = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean ridingPlayers = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean pickupPlayers = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean betterSnowBall = false;

    @Rule(
            options = {"16", "32"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static int ridingPlayersPickUpLimit = 16;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean ridingPlayersDismountOnGameModeChange = false;

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE, CLIENT}
    )
    public static boolean ridingPlayersClientAllowInteractions = true;

    @Rule(
            categories = {PRIMARYUAN, PORTING, SURVIVAL}
    )
    public static boolean sleepingDuringTheDay = false;

    @Rule(
            categories = {PRIMARYUAN, PORTING}
    )
    public static boolean unicodeArgumentsSupport = false;

    @Rule(
            categories = {PRIMARYUAN, BOT, COMMAND}
    )
    public static boolean fakePlayerDropStackModifiers = false;

    // 控制假人物品流链接功能（/player <name> sendto <target>）及其命令的可用性，默认关闭
    @Rule(
            categories = {PRIMARYUAN, BOT, COMMAND}
    )
    public static boolean fakePlayerSendto = false;

    @Rule(
            options = {"false", "true", "self", "everyone"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, FEATURE, COMMAND}
    )
    public static String playerScale = "false";

    @Rule(
            options = {"0.1", "0.01", "0.25", "0.5"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, COMMAND}
    )
    public static double playerScaleMin = 0.1;

    @Rule(
            options = {"1.5", "2.0", "5.0", "10.0", "16.0"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, COMMAND}
    )
    public static double playerScaleMax = 1.5;

    @Rule(
            options = {"false", "true", "safety", "strict"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static String realisticPlayerScale = "false";

    @Rule(
            categories = {PRIMARYUAN, SURVIVAL, FEATURE}
    )
    public static boolean playerScaleLinkedEntities = false;

    // 和平的玩家：启用 /pvp 指令，按玩家开关 PVP
    @Rule(
            options = {"false", "true", "self", "everyone"},
            strict = false,
            categories = {PRIMARYUAN, SURVIVAL, COMMAND}
    )
    public static String peacefulPlayers = "false";
}
