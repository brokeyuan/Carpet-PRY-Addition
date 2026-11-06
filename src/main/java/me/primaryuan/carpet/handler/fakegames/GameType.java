package me.primaryuan.carpet.handler.fakegames;

import java.util.List;

/**
 * 小游戏类型。id 同时是 /fakegame create 的参数与三语 lang 键片段
 * （carpetprimaryuan.fakegames.game.&lt;id&gt;.name/.desc/.win）。
 */
public enum GameType {
    GRASS_IS_LAVA("grass_is_lava", 2, 16, true, false),
    RISING_LAVA("rising_lava", 2, 16, true, false),
    RED_LIGHT_GREEN_LIGHT("red_light_green_light", 2, 16, true, false),
    RANDOM_BANS("random_bans", 2, 16, true, false),
    SAFE_SPOT("safe_spot", 2, 16, false, false),
    HIDE_AND_SEEK("hide_and_seek", 2, 16, false, false),
    TAG("tag", 3, 16, false, false),
    HOT_POTATO("hot_potato", 3, 16, true, false),
    KING_OF_THE_HILL("king_of_the_hill", 2, 16, false, false),
    CHARADES("charades", 3, 16, false, false),
    SHOOTING("shooting", 1, 8, false, true),
    ELYTRA("elytra", 1, 8, false, true),
    MURDER_MYSTERY("murder_mystery", 4, 8, false, false),
    WEREWOLF("werewolf", 6, 12, false, false),
    SNAKE("snake", 2, 8, false, false),
    DRAW_GUESS("draw_guess", 3, 8, false, false);

    public final String id;
    public final int minPlayers;
    public final int maxPlayers;
    /** 淘汰后自动按“仅剩一人”结算胜负（积分制/无淘汰游戏为 false，由玩法自行收尾） */
    public final boolean autoLastStanding;
    /** 允许单人开始（训练类玩法） */
    public final boolean training;

    GameType(String id, int minPlayers, int maxPlayers, boolean autoLastStanding, boolean training) {
        this.id = id;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.autoLastStanding = autoLastStanding;
        this.training = training;
    }

    public static GameType byId(String id) {
        for (GameType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    public String nameKey() {
        return "carpetprimaryuan.fakegames.game." + id + ".name";
    }

    public String descKey() {
        return "carpetprimaryuan.fakegames.game." + id + ".desc";
    }

    public String winKey() {
        return "carpetprimaryuan.fakegames.game." + id + ".win";
    }

    /** 玩家可调设置；空列表表示无设置。 */
    public List<Setting> settings() {
        return switch (this) {
            case GRASS_IS_LAVA -> List.of(
                    new Setting("mode", "fixed", List.of("fixed", "rotate")),
                    new Setting("hazard", "grass_block", List.of(
                            "grass_block", "dirt", "stone", "sand", "red_sand", "gravel",
                            "oak_planks", "clay", "snow_block", "water")),
                    new Setting("interval", "15", List.of("10", "15", "20", "30")),
                    new Setting("evacuate", "5", List.of("3", "5", "8")));
            case RISING_LAVA -> List.of(
                    new Setting("display", "particles", List.of("particles", "blocks", "flood")),
                    new Setting("start_offset", "6", List.of("4", "6", "8", "12")),
                    new Setting("rise_seconds", "5", List.of("3", "5", "8")),
                    new Setting("accelerate", "true", List.of("true", "false")),
                    new Setting("max_rise_seconds", "2", List.of("1", "2", "3")));
            case RED_LIGHT_GREEN_LIGHT -> List.of(
                    new Setting("start_radius", "6", List.of("5", "6", "8")),
                    new Setting("goal", "auto", List.of("auto", "manual")));
            case RANDOM_BANS -> List.of(
                    new Setting("actions", "all", List.of("all", "basic", "oneshot")),
                    new Setting("round_seconds", "15", List.of("12", "15", "20")));
            case SAFE_SPOT -> List.of(
                    new Setting("spots", "auto", List.of("auto", "1", "2", "3", "4")),
                    new Setting("rounds", "8", List.of("6", "8", "10")),
                    new Setting("round_seconds", "15", List.of("10", "15", "20")));
            case HIDE_AND_SEEK -> List.of(
                    new Setting("mode", "classic", List.of("classic", "shrink", "herb")),
                    new Setting("hide_seconds", "60", List.of("30", "45", "60")),
                    new Setting("seek_seconds", "180", List.of("120", "180", "240")));
            case TAG -> List.of(
                    new Setting("mode", "freeze", List.of("freeze", "infection")),
                    new Setting("rescue_seconds", "1", List.of("0.5", "1", "1.5")),
                    new Setting("duration", "180", List.of("120", "180", "240")));
            case HOT_POTATO -> List.of(
                    new Setting("fuse", "30", List.of("20", "30", "45")),
                    new Setting("pass_lock", "2", List.of("1", "2", "3")));
            case KING_OF_THE_HILL -> List.of(
                    new Setting("mode", "single", List.of("single", "rotate", "simultaneous")),
                    new Setting("points", "1", List.of("1", "2", "3", "4")),
                    new Setting("rotate_seconds", "30", List.of("20", "30", "45")),
                    new Setting("duration", "180", List.of("120", "180", "240")));
            case CHARADES -> List.of(
                    new Setting("rounds", "8", List.of("6", "8", "10")));
            case SHOOTING -> List.of(
                    new Setting("mode", "training", List.of("training", "speed", "accuracy", "versus")),
                    new Setting("targets", "both", List.of("fixed", "moving", "both")),
                    new Setting("duration", "60", List.of("45", "60", "90")));
            case ELYTRA -> List.of(
                    new Setting("mode", "rings", List.of("rings", "collect")),
                    new Setting("duration", "180", List.of("120", "180", "240")),
                    new Setting("rings", "8", List.of("6", "8", "10")));
            case MURDER_MYSTERY -> List.of(
                    new Setting("script", "default", List.of("default")),
                    new Setting("round_seconds", "60", List.of("45", "60", "90")));
            case WEREWOLF -> List.of(
                    new Setting("day_seconds", "60", List.of("45", "60", "90")),
                    new Setting("night_seconds", "30", List.of("20", "30", "45")));
            case SNAKE -> List.of(
                    new Setting("speed", "normal", List.of("normal", "fast")),
                    new Setting("max_length", "20", List.of("10", "20", "30")),
                    new Setting("food_count", "12", List.of("8", "12", "16")));
            case DRAW_GUESS -> List.of(
                    new Setting("mode", "classic", List.of("classic", "relay", "same")),
                    new Setting("rounds", "3", List.of("3", "5")));
        };
    }

    /** 不可用原因（缺依赖/未完成适配）；null 表示可创建。 */
    public String unavailableKey() {
        return switch (this) {
            // 神笔马良仍在独立活动分支、未进入 master，不能把未验收能力带入假游戏
            case DRAW_GUESS -> "carpetprimaryuan.fakegames.unavailable.magicbrush";
            case MURDER_MYSTERY -> "carpetprimaryuan.fakegames.unavailable.script";
            case WEREWOLF -> "carpetprimaryuan.fakegames.unavailable.werewolf";
            default -> null;
        };
    }

    /** 设置定义：choices 非空时值必须从其中选；空表示自由文本（由玩法校验范围）。 */
    public record Setting(String key, String def, List<String> choices) {
        public String labelKey() {
            return "carpetprimaryuan.fakegames.setting." + key;
        }
    }
}
