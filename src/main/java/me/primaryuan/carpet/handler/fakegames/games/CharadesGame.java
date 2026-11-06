package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.MemberState;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 你演我猜：内置双语题库，每轮随机一名表演者私发题目，表演者用原版动作
 * 表达，其他参与者用 /fg guess &lt;词&gt; 提交猜测（只处理参与者的明确猜测，
 * 不拦截公共聊天）。首位猜对者 +1 分并提前结束本轮，轮空则公布答案。
 * 默认 8 轮，表演者按成员顺序轮换，最终按积分结算。
 */
public final class CharadesGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private record Word(String zh, String en) {}

    /** 内置题库：日常事物，zh 为主答案、en 为可接受别名 */
    private static final Word[] WORDS = {
            new Word("猫", "cat"), new Word("狗", "dog"), new Word("大象", "elephant"),
            new Word("兔子", "rabbit"), new Word("企鹅", "penguin"), new Word("猴子", "monkey"),
            new Word("游泳", "swim"), new Word("睡觉", "sleep"), new Word("钓鱼", "fishing"),
            new Word("挖掘", "dig"), new Word("爬树", "climb"), new Word("跳舞", "dance"),
            new Word("僵尸", "zombie"), new Word("苦力怕", "creeper"), new Word("末影人", "enderman"),
            new Word("铁傀儡", "iron golem"), new Word("龙", "dragon"), new Word("蜜蜂", "bee"),
            new Word("降落伞", "parachute"), new Word("宇航员", "astronaut"),
            new Word("机器人", "robot"), new Word("吸血鬼", "vampire"),
            new Word("打篮球", "basketball"), new Word("弹吉他", "guitar"),
            new Word("开飞机", "pilot"), new Word("照相", "photo"),
            new Word("理发", "haircut"), new Word("刷牙", "brush teeth"),
            new Word("地震", "earthquake"), new Word("彩虹", "rainbow"),
            new Word("火箭", "rocket"), new Word("风筝", "kite"),
    };

    private static final int ROUND_TICKS = 20 * 60;

    private int totalRounds = 8;

    private final List<UUID> performers = new ArrayList<>();
    private int round;
    private int roundLeft;
    private Word current;
    private UUID performer;

    @Override
    public void onStart(GameSession session) {
        round = 0;
        totalRounds = Math.max(1, session.intOption("rounds", 8));
        performers.clear();
        performers.addAll(activePerformers(session.allMembers()));
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.charades.start",
                totalRounds));
        nextRound(session);
    }

    /** 只允许 ACTIVE 成员进入表演名单；退出者不得在开局时被重新加入。 */
    static List<UUID> activePerformers(java.util.Collection<GameMember> members) {
        List<UUID> result = new ArrayList<>();
        for (GameMember member : members) {
            if (member.state == MemberState.ACTIVE) {
                result.add(member.id);
            }
        }
        return result;
    }

    private void nextRound(GameSession session) {
        // 防御性过滤：等待期/运行期退出的成员、以及当前不在线的成员都不再表演
        performers.removeIf(id -> {
            GameMember member = session.member(id);
            return member == null || member.state != MemberState.ACTIVE || session.player(id) == null;
        });
        if (phaseNotRunning(session) || round >= totalRounds
                || performers.size() < 2 || session.activeCount() < 2) {
            session.finish(null);
            return;
        }
        round++;
        roundLeft = ROUND_TICKS;
        performer = performers.get((round - 1) % performers.size());
        Word[] pool = WORDS;
        current = pool[session.level.getRandom().nextInt(pool.length)];
        ServerPlayer performerPlayer = session.player(performer);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.charades.round",
                round, totalRounds,
                performerPlayer == null ? "?" : performerPlayer.getName().getString()));
        if (performerPlayer != null) {
            performerPlayer.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.charades.you_perform",
                    current.zh, current.en));
        }
    }

    private static boolean phaseNotRunning(GameSession session) {
        return session.phase != SessionPhase.RUNNING;
    }

    @Override
    public void onTick(GameSession session) {
        roundLeft--;
        if (roundLeft <= 0) {
            session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.charades.reveal",
                    current == null ? "?" : current.zh));
            nextRound(session);
            return;
        }
        if (roundLeft % 20 == 0) {
            session.actionbarActive(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.charades.hud", roundLeft / 20));
        }
    }

    @Override
    public boolean onGuess(GameSession session, ServerPlayer guesser, String message) {
        if (current == null || session.phase != SessionPhase.RUNNING) {
            return false;
        }
        // 只有存活成员的猜测参与判定；局外/已淘汰玩家仅收到提示
        GameMember member = session.member(guesser.getUUID());
        if (member == null || member.state != MemberState.ACTIVE) {
            guesser.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.not_member"));
            return true;
        }
        if (guesser.getUUID().equals(performer)) {
            guesser.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.charades.performer_no_guess"));
            return true;
        }
        if (!matches(message)) {
            // 错误猜测只反馈本人，不广播刷屏
            guesser.sendSystemMessage(ServerI18n.tr(
                    "carpetprimaryuan.fakegames.charades.wrong"));
            return true;
        }
        member.score++;
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.charades.correct",
                guesser.getName().getString(), current.zh));
        nextRound(session);
        return true;
    }

    /** 词匹配纯判定：trim + 小写归一后与中/英文答案比对（供单测复用） */
    static boolean wordMatches(String input, String zh, String en) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return normalized.equals(zh) || normalized.equals(en.toLowerCase(Locale.ROOT));
    }

    private boolean matches(String message) {
        return wordMatches(message, current.zh, current.en);
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        performers.remove(playerId);
        if (session.phase != SessionPhase.RUNNING) {
            return;
        }
        // 活跃人数不足两人时无法继续猜词，直接结束
        if (session.activeCount() < 2) {
            session.finish(null);
            return;
        }
        // 表演者离开：公布答案并立即换人开新一轮
        if (playerId.equals(performer)) {
            session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.charades.reveal",
                    current == null ? "?" : current.zh));
            nextRound(session);
        }
    }

    @Override
    public void onEnd(GameSession session) {
        performers.clear();
        current = null;
    }
}
