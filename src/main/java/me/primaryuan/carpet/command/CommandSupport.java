package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 命令层共享工具：目标玩家解析、在线玩家名补全、档案名获取与管理员判定。
 *
 * 供 /player <name> 扩展命令（dropall / sendto，见
 * {@link PlayerCommandExtension} 与 {@link PlayerSendtoCommandExtension}）
 * 以及 /tpp、/scale 系列命令复用，避免各命令类各自维护相同实现。
 */
public final class CommandSupport {

    private CommandSupport() {}

    /**
     * 从 CommandContext 中解析目标 ServerPlayer。
     * 依赖外层命令树注入的 "player" 字符串参数（Carpet /player <name> 风格）。
     *
     * @return 目标玩家；参数缺失或玩家不在线时返回 null（已向来源发送失败提示）
     */
    public static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx) {
        String playerName;
        try {
            playerName = StringArgumentType.getString(ctx, "player");
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.command.support.missing_player"));
            return null;
        }
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(ServerI18n.tr("carpetprimaryuan.command.support.player_not_found", playerName));
        }
        return player;
    }

    /**
     * 自动补全：从在线玩家列表中补全所有玩家名（按档案名，不受显示名修饰影响）。
     */
    public static CompletableFuture<Suggestions> suggestOnlinePlayers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : context.getSource().getServer().getPlayerList().getPlayers()) {
            names.add(profileName(p));
        }
        suggestMatching(builder, names);
        return builder.buildFuture();
    }

    /**
     * 按忽略大小写的前缀过滤候选并写入 builder（regionMatches 替代 toLowerCase，零分配）。
     */
    public static void suggestMatching(SuggestionsBuilder builder, Iterable<String> candidates) {
        String remaining = builder.getRemainingLowerCase();
        for (String candidate : candidates) {
            if (candidate.regionMatches(true, 0, remaining, 0, remaining.length())) {
                builder.suggest(candidate);
            }
        }
    }

    /**
     * 获取玩家档案名（封装跨版本 GameProfile 访问器差异：
     * 1.21.10+ 为 record 访问器 name()，早期版本为 getName()）。
     */
    public static String profileName(ServerPlayer player) {
        //#if MC < 12110
        //$$ return player.getGameProfile().getName();
        //#else
        return player.getGameProfile().name();
        //#endif
    }

    /**
     * 判定命令来源是否为管理员（等级 4 / LEVEL_OWNERS）；控制台视为管理员。
     * 封装跨版本权限检查 API 差异。
     */
    public static boolean isAdmin(CommandSourceStack source) {
        if (!source.isPlayer()) return true;
        //#if MC <= 12110
        //$$ return source.hasPermission(4);
        //#else
        return Commands.LEVEL_OWNERS.check(source.permissions());
        //#endif
    }
}
