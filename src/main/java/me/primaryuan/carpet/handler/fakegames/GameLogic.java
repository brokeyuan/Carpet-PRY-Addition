package me.primaryuan.carpet.handler.fakegames;

import me.primaryuan.carpet.handler.fakegames.games.CharadesGame;
import me.primaryuan.carpet.handler.fakegames.games.ElytraGame;
import me.primaryuan.carpet.handler.fakegames.games.GrassIsLavaGame;
import me.primaryuan.carpet.handler.fakegames.games.HideAndSeekGame;
import me.primaryuan.carpet.handler.fakegames.games.HotPotatoGame;
import me.primaryuan.carpet.handler.fakegames.games.KingOfTheHillGame;
import me.primaryuan.carpet.handler.fakegames.games.RandomBansGame;
import me.primaryuan.carpet.handler.fakegames.games.RedLightGreenLightGame;
import me.primaryuan.carpet.handler.fakegames.games.RisingLavaGame;
import me.primaryuan.carpet.handler.fakegames.games.SafeSpotGame;
import me.primaryuan.carpet.handler.fakegames.games.ShootingGame;
import me.primaryuan.carpet.handler.fakegames.games.SnakeGame;
import me.primaryuan.carpet.handler.fakegames.games.TagGame;
import me.primaryuan.carpet.handler.fakegames.games.UnavailableGame;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 单个游戏的玩法逻辑。生命周期：会话进入 RUNNING 时 onStart，之后每 tick onTick，
 * 玩家被淘汰/退出时 onPlayerLeave，会话结束时 onEnd（含取消）。
 *
 * <p>成员管理与淘汰收口在 GameSession，玩法实现只写判定与表现。</p>
 */
public interface GameLogic {

    default void onStart(GameSession session) {
    }

    /** 每服务端 tick 推进一次（仅 RUNNING 阶段） */
    default void onTick(GameSession session) {
    }

    /** 倒计时阶段每 tick 检查；返回非 null 时回到准备阶段并显示该原因。 */
    default Component countdownIssue(GameSession session) {
        return null;
    }

    /** 玩家离开本局（虚拟淘汰或主动/客观退出）后的玩法侧清理；离线时 player 为 null。 */
    default void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
    }

    /** 有效攻击命中（游戏内玩家对玩家）。返回 true 表示玩法已处理并消费该次攻击。 */
    default boolean onAttack(GameSession session, ServerPlayer attacker, ServerPlayer victim) {
        return false;
    }

    /** 原版换手动作（服务端 packet HEAD）。返回 true 表示消费，不执行原版换手。 */
    default boolean onSwap(GameSession session, ServerPlayer player) {
        return false;
    }

    /** 会话结束（含取消）后的清理；必须可重复调用。 */
    default void onEnd(GameSession session) {
    }

    /** /fg guess 提交猜测（你演我猜使用），返回是否命中 */
    default boolean onGuess(GameSession session, ServerPlayer guesser, String message) {
        return false;
    }

    /** 按类型构造玩法实例（每局一份，无跨局状态） */
    static GameLogic create(GameType type) {
        return switch (type) {
            case GRASS_IS_LAVA -> new GrassIsLavaGame();
            case RISING_LAVA -> new RisingLavaGame();
            case RED_LIGHT_GREEN_LIGHT -> new RedLightGreenLightGame();
            case RANDOM_BANS -> new RandomBansGame();
            case SAFE_SPOT -> new SafeSpotGame();
            case HIDE_AND_SEEK -> new HideAndSeekGame();
            case TAG -> new TagGame();
            case HOT_POTATO -> new HotPotatoGame();
            case KING_OF_THE_HILL -> new KingOfTheHillGame();
            case CHARADES -> new CharadesGame();
            case SHOOTING -> new ShootingGame();
            case ELYTRA -> new ElytraGame();
            case MURDER_MYSTERY -> new UnavailableGame();
            case WEREWOLF -> new UnavailableGame();
            case SNAKE -> new SnakeGame();
            case DRAW_GUESS -> new UnavailableGame();
        };
    }
}
