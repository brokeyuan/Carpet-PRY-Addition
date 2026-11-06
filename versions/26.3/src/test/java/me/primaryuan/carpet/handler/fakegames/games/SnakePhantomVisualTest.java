package me.primaryuan.carpet.handler.fakegames.games;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 幻影对象自身生命周期：ID 唯一、清理可重复。 */
class SnakePhantomVisualTest {

    @Test
    void phantomIdsAreUnique() {
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            SnakePhantomVisual visual = new SnakePhantomVisual(Vec3.ZERO);
            assertTrue(ids.add(visual.entityId()), "duplicate phantom entity id");
            visual.despawn();
        }
    }

    @Test
    void despawnIsIdempotent() {
        SnakePhantomVisual visual = new SnakePhantomVisual(Vec3.ZERO);
        assertFalse(visual.isDespawned());
        visual.despawn();
        assertTrue(visual.isDespawned());
        visual.despawn();
        assertTrue(visual.isDespawned());
    }
}
