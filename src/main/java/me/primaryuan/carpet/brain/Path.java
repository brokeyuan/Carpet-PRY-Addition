package me.primaryuan.carpet.brain;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 自研 A* 寻路的计算结果（纯数据载体，不含任何状态机）。
 *
 * <p>对齐原版 {@code Path} 的最小语义：一条由路径点组成的序列。
 * 玩家（宽 0.6、高 1.8）能钻 1 格宽的巷道，因此路径精度做到"块级"即可。
 * 路径点坐标是"可以站立的格子"，路径跟随（节点推进、卡死重算等）
 * 由 {@link PlayerPathNavigation} 负责。</p>
 */
public final class Path {

    /** 从起点之后的第一个节点到终点的全部路径点（含终点） */
    public final List<BlockPos> nodes;

    public Path(List<BlockPos> nodes) {
        this.nodes = nodes;
    }

    /** 是否已无节点可走（到达终点） */
    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public BlockPos first() {
        return this.nodes.get(0);
    }
}