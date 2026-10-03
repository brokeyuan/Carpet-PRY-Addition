package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 红包份额切分：纯函数（无世界/玩家依赖），总量守恒由构造保证（有 JUnit 契约测试）。
 *
 * <p>拼手气/口令：每种物品的个数按"星与条"随机组成（shares-1 个随机切点，均匀覆盖
 * 全部非负组成）；个数小于份数时改为随机抽若干不同份额各拿 1 个，其余为空。
 * 普通：每种物品每份 base 个，余数随机落给 rem 个不同份额各 +1。</p>
 */
public final class RedPacketSplitter {

    private RedPacketSplitter() {}

    /** 拼手气/口令分配：随机组成，总量守恒 */
    public static List<List<ItemStack>> lucky(List<ItemStack> payload, int shares, Random random) {
        List<List<ItemStack>> out = emptyRows(shares);
        for (ItemStack original : payload) {
            int total = original.getCount();
            int[] parts = new int[shares];
            if (total < shares) {
                // 不足则随机抽 total 个不同份额各拿 1 个
                List<Integer> order = shuffledIndices(shares, random);
                for (int i = 0; i < total; i++) {
                    parts[order.get(i)] = 1;
                }
            } else {
                randomComposition(total, shares, random, parts);
            }
            for (int i = 0; i < shares; i++) {
                if (parts[i] > 0) {
                    out.get(i).add(original.copyWithCount(parts[i]));
                }
            }
        }
        return out;
    }

    /** 普通分配：种类和数量平分，余数随机落给不同份额 */
    public static List<List<ItemStack>> even(List<ItemStack> payload, int shares, Random random) {
        List<List<ItemStack>> out = emptyRows(shares);
        for (ItemStack original : payload) {
            int total = original.getCount();
            int base = total / shares;
            int rem = total % shares;
            boolean[] bonus = new boolean[shares];
            if (rem > 0) {
                List<Integer> order = shuffledIndices(shares, random);
                for (int i = 0; i < rem; i++) {
                    bonus[order.get(i)] = true;
                }
            }
            for (int i = 0; i < shares; i++) {
                int count = base + (bonus[i] ? 1 : 0);
                if (count > 0) {
                    out.get(i).add(original.copyWithCount(count));
                }
            }
        }
        return out;
    }

    /** 专属：不分不随机，整包给一份 */
    public static List<List<ItemStack>> whole(List<ItemStack> payload) {
        List<List<ItemStack>> out = new ArrayList<>(1);
        out.add(new ArrayList<>(payload));
        return out;
    }

    /** 星与条：shares-1 个 [0,total] 随机切点排序后取差，得到均匀的随机非负组成（和恒为 total） */
    private static void randomComposition(int total, int shares, Random random, int[] parts) {
        int[] cuts = new int[shares - 1];
        for (int i = 0; i < cuts.length; i++) {
            cuts[i] = random.nextInt(total + 1);
        }
        java.util.Arrays.sort(cuts);
        int prev = 0;
        for (int i = 0; i < cuts.length; i++) {
            parts[i] = cuts[i] - prev;
            prev = cuts[i];
        }
        parts[shares - 1] = total - prev;
    }

    private static List<Integer> shuffledIndices(int size, Random random) {
        List<Integer> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, random);
        return order;
    }

    private static List<List<ItemStack>> emptyRows(int shares) {
        List<List<ItemStack>> out = new ArrayList<>(shares);
        for (int i = 0; i < shares; i++) {
            out.add(new ArrayList<>());
        }
        return out;
    }
}
