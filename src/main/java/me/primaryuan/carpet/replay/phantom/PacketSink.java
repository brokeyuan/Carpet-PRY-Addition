package me.primaryuan.carpet.replay.phantom;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 幻影包发送端口：把"发给哪些观察者"与"发什么"解耦，便于单测与版本分支隔离。
 * 实现：遍历同世界玩家（含幻影距离过滤），调 connection.send。
 */
public interface PacketSink
{
    void send(ServerPlayer observer, Packet<?> packet);

    /** 当前全部观察者 */
    List<ServerPlayer> observers();

    /** 观察者离开（维度切换/退出）时清理内部状态 */
    void forget(ServerPlayer observer);
}
