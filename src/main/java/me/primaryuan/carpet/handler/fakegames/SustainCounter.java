package me.primaryuan.carpet.handler.fakegames;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 连续接触计数：每个目标独立累计，本 tick 未接触立即清零。
 * 达到阈值的目标一次性返回，调用方再统一处理，避免边遍历边改状态。
 */
public final class SustainCounter {

    private final Map<UUID, Integer> ticks = new HashMap<>();
    private final int threshold;

    public SustainCounter(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
    }

    /** 用本 tick 的接触集合推进一次；返回本 tick 达到阈值的目标（按接触集合顺序）。 */
    public Set<UUID> update(Collection<UUID> contacts) {
        Set<UUID> unique = new LinkedHashSet<>(contacts);
        ticks.keySet().retainAll(unique);
        Set<UUID> ready = new LinkedHashSet<>();
        for (UUID id : unique) {
            int count = ticks.merge(id, 1, Integer::sum);
            if (count >= threshold) {
                ready.add(id);
            }
        }
        return ready;
    }

    public int count(UUID id) {
        return ticks.getOrDefault(id, 0);
    }

    public void remove(UUID id) {
        ticks.remove(id);
    }

    public void clear() {
        ticks.clear();
    }
}
