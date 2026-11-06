package me.primaryuan.carpet.handler.fakegames.games;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 安全点唯一归属：每个点至多分配一人，每人至多一个点。
 * 每个点选距离最近且尚未分配的合格玩家；等距时按本轮轮转顺序决定。
 */
final class SafeSpotAllocation {

    record Candidate(UUID id, Vec3 position, boolean onGround) {}

    private SafeSpotAllocation() {}

    static Map<UUID, BlockPos> assign(List<Candidate> players, List<BlockPos> spots, int rotation) {
        Map<UUID, BlockPos> assigned = new LinkedHashMap<>();
        if (players.isEmpty() || spots.isEmpty()) {
            return assigned;
        }
        List<Candidate> order = rotate(players, rotation);
        Set<UUID> used = new HashSet<>();
        for (BlockPos spot : spots) {
            Candidate best = null;
            double bestDistance = Double.MAX_VALUE;
            double centerX = spot.getX() + 0.5;
            double centerZ = spot.getZ() + 0.5;
            for (Candidate candidate : order) {
                if (used.contains(candidate.id())) {
                    continue;
                }
                Vec3 pos = candidate.position();
                if (!SafeSpotGame.standsOnSpot(
                        pos.x - centerX, pos.z - centerZ, pos.y - spot.getY(), candidate.onGround())) {
                    continue;
                }
                double distance = pos.distanceToSqr(centerX, spot.getY(), centerZ);
                if (distance < bestDistance - 1.0E-9) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                assigned.put(best.id(), spot);
                used.add(best.id());
            }
        }
        return assigned;
    }

    private static List<Candidate> rotate(List<Candidate> players, int rotation) {
        int size = players.size();
        int start = Math.floorMod(rotation, size);
        List<Candidate> order = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            order.add(players.get((start + i) % size));
        }
        return order;
    }
}
