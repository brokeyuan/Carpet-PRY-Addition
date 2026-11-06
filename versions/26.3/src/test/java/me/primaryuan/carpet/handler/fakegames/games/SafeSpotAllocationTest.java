package me.primaryuan.carpet.handler.fakegames.games;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 安全点唯一归属：同点多人只救一人、点数上限、等距轮转稳定。 */
class SafeSpotAllocationTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void sameSpotOnlySavesOnePlayer() {
        BlockPos spot = new BlockPos(0, 64, 0);
        List<SafeSpotAllocation.Candidate> players = List.of(
                candidate(A, 0.1, 64.0, 0.1),
                candidate(B, -0.1, 64.0, -0.1));

        Map<UUID, BlockPos> assigned = SafeSpotAllocation.assign(players, List.of(spot), 0);

        assertEquals(1, assigned.size());
        assertTrue(assigned.containsKey(A), "等距时轮转顺序靠前者优先");
    }

    @Test
    void atMostOnePlayerPerSpotAndOneSpotPerPlayer() {
        List<BlockPos> spots = List.of(new BlockPos(0, 64, 0), new BlockPos(10, 64, 0));
        List<SafeSpotAllocation.Candidate> players = List.of(
                candidate(A, 0.0, 64.0, 0.0),
                candidate(B, 0.0, 64.0, 0.0),
                candidate(C, 10.0, 64.0, 0.0));

        Map<UUID, BlockPos> assigned = SafeSpotAllocation.assign(players, spots, 0);

        assertEquals(2, assigned.size());
        assertEquals(1, assigned.values().stream().filter(spots.get(0)::equals).count());
        assertEquals(1, assigned.values().stream().filter(spots.get(1)::equals).count());
    }

    @Test
    void nMinusOneSpotsSaveAtMostNMinusOnePlayers() {
        List<BlockPos> spots = List.of(new BlockPos(0, 64, 0));
        List<SafeSpotAllocation.Candidate> players = List.of(
                candidate(A, 0.0, 64.0, 0.0),
                candidate(B, 0.0, 64.0, 0.0));

        Map<UUID, BlockPos> assigned = SafeSpotAllocation.assign(players, spots, 0);

        assertEquals(1, assigned.size());
    }

    @Test
    void rotationBreaksExactTiesDeterministically() {
        BlockPos spot = new BlockPos(0, 64, 0);
        List<SafeSpotAllocation.Candidate> players = List.of(
                candidate(A, 0.0, 64.0, 0.0),
                candidate(B, 0.0, 64.0, 0.0));

        Map<UUID, BlockPos> first = SafeSpotAllocation.assign(players, List.of(spot), 0);
        Map<UUID, BlockPos> second = SafeSpotAllocation.assign(players, List.of(spot), 1);

        assertTrue(first.containsKey(A));
        assertTrue(second.containsKey(B));
    }

    @Test
    void playerOutsideClaimRadiusIsNotAssigned() {
        BlockPos spot = new BlockPos(0, 64, 0);
        List<SafeSpotAllocation.Candidate> players = List.of(candidate(A, 3.0, 64.0, 0.0));

        assertTrue(SafeSpotAllocation.assign(players, List.of(spot), 0).isEmpty());
    }

    private static SafeSpotAllocation.Candidate candidate(UUID id, double x, double y, double z) {
        return new SafeSpotAllocation.Candidate(id, new Vec3(x, y, z), true);
    }
}
