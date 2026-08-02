package me.primaryuan.carpet;

import me.primaryuan.carpet.settings.Rule;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static carpet.api.settings.RuleCategory.*;

public class CarpetPrimaryuanSettings {
    public static final String PRIMARYUAN = "PRIMARYUAN";
    public static final String BOT = "BOT";
    public static final String PORTING = "PORTING";
    public static final String COMMAND = "COMMAND";
    public static final String BUGFIX = "BUGFIX";

    @Rule(
            options = {"false", "true"},
            categories = {PRIMARYUAN, BOT, COMMAND}
    )
    public static boolean TppFakePlayer = false;

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
            options = {"16", "32", ""},
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

    public static String fakePlayerTpStations = "";

    public static Map<String, String> tppPlayerAliases = new ConcurrentHashMap<>();
}
