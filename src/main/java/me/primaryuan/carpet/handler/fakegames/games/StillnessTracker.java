package me.primaryuan.carpet.handler.fakegames.games;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 连续静止跟踪：每 tick 与上一位置比较；单 tick 位移超过容差立即清零。
 * 另外累计窗口内的实际路径长度，超过预算也清零——低于单 tick 容差的持续慢移
 * （例如 0.015 格/tick）和多 tick 绕圈不会因端点接近被误判为静止。
 * 豁免状态同样清零并刷新位置。
 */
final class StillnessTracker {

    private static final double DEFAULT_MOVEMENT_BUDGET = 0.10;

    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    private final Map<UUID, Double> movementPath = new HashMap<>();
    private final double toleranceSquared;
    private final int requiredTicks;
    private final double movementBudget;

    StillnessTracker(double tolerance, int requiredTicks) {
        this(tolerance, requiredTicks, DEFAULT_MOVEMENT_BUDGET);
    }

    StillnessTracker(double tolerance, int requiredTicks, double movementBudget) {
        if (tolerance <= 0 || requiredTicks <= 0 || movementBudget <= 0) {
            throw new IllegalArgumentException("tolerance, requiredTicks and movementBudget must be positive");
        }
        this.toleranceSquared = tolerance * tolerance;
        this.requiredTicks = requiredTicks;
        this.movementBudget = movementBudget;
    }

    /** 推进一名玩家；达到连续静止阈值返回 true。 */
    boolean update(UUID id, Vec3 position, boolean eligible) {
        Vec3 previous = lastPositions.put(id, position);
        if (!eligible) {
            reset(id);
            return false;
        }
        if (previous == null) {
            stillTicks.put(id, 1);
            movementPath.put(id, 0.0);
            return requiredTicks <= 1;
        }
        double dx = position.x - previous.x;
        double dz = position.z - previous.z;
        double stepSquared = dx * dx + dz * dz;
        if (stepSquared > toleranceSquared) {
            stillTicks.put(id, 0);
            movementPath.put(id, 0.0);
            return false;
        }
        double path = movementPath.getOrDefault(id, 0.0) + Math.sqrt(stepSquared);
        if (path > movementBudget) {
            stillTicks.put(id, 0);
            movementPath.put(id, 0.0);
            return false;
        }
        movementPath.put(id, path);
        int ticks = stillTicks.merge(id, 1, Integer::sum);
        return ticks >= requiredTicks;
    }

    int count(UUID id) {
        return stillTicks.getOrDefault(id, 0);
    }

    private void reset(UUID id) {
        stillTicks.put(id, 0);
        movementPath.put(id, 0.0);
    }

    void remove(UUID id) {
        lastPositions.remove(id);
        stillTicks.remove(id);
        movementPath.remove(id);
    }

    void clear() {
        lastPositions.clear();
        stillTicks.clear();
        movementPath.clear();
    }
}
