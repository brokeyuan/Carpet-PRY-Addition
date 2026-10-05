package me.primaryuan.carpet.handler.textAnimation;

import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 大服降级会话：单条广播直接生成字幕的玩家超过上限（{@code TextAnimationHandler#MAX_DIRECT_PLAYERS}）
 * 时，超出部分改用 actionbar 打字机（零实体、纯包）呈现同一文本。
 *
 * <p>打字阶段每 tick 发一次"前 k 字符"（样式同字幕解析）；打完进入保持期，每 10 tick
 * 重发完整文本对抗 actionbar 自动淡出；玩家断线/死亡/换维度即移出，全部结束或无人剩余时
 * 通知广播销毁。</p>
 */
final class ActionbarSession implements ServerTickScheduler.TickTask {

    private static final int KEEPALIVE_TICKS = 10;

    private final List<ServerPlayer> players;
    private final List<TextAnimationHandler.Segment> segments;
    private final TextAnimationHandler.Broadcast broadcast;
    private int typed;
    private int holdLeft;
    private boolean done;

    ActionbarSession(List<ServerPlayer> players, List<TextAnimationHandler.Segment> segments,
                     TextAnimationHandler.Broadcast broadcast, int hold) {
        this.players = new ArrayList<>(players);
        this.segments = segments;
        this.broadcast = broadcast;
        this.holdLeft = hold;
    }

    @Override
    public boolean tick(MinecraftServer server) {
        players.removeIf(p -> p.hasDisconnected() || p.isDeadOrDying());
        if (players.isEmpty()) {
            finish();
            return false;
        }

        if (typed < segments.size()) {
            typed++;
            broadcastToAll(buildComponent(typed));
            return true;
        }

        // 打完：保持期对抗 actionbar 自动淡出
        holdLeft--;
        if (holdLeft <= 0) {
            finish();
            return false;
        }
        if (holdLeft % KEEPALIVE_TICKS == 0) {
            broadcastToAll(buildComponent(segments.size()));
        }
        return true;
    }

    /** 结束并回报广播（done 幂等）；崩溃兜底（TextAnimationHandler.registerSession）也走这里 */
    void finish() {
        if (!done) {
            done = true;
            broadcast.partFinished();
        }
    }

    /** 前 count 个字符的文本树（字幕同款 & 色码样式） */
    private Component buildComponent(int count) {
        MutableComponent out = Component.empty();
        int end = Math.min(count, segments.size());
        for (int i = 0; i < end; i++) {
            TextAnimationHandler.Segment seg = segments.get(i);
            out.append(Component.literal(String.valueOf(seg.c)).setStyle(seg.style));
        }
        return out;
    }

    private void broadcastToAll(Component component) {
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);
        Iterator<ServerPlayer> it = players.iterator();
        while (it.hasNext()) {
            ServerPlayer player = it.next();
            if (player.hasDisconnected()) {
                it.remove();
                continue;
            }
            player.connection.send(packet);
        }
    }
}
