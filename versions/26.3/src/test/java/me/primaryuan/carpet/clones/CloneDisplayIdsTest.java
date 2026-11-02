package me.primaryuan.carpet.clones;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 显示 UUID 只由真实 UUID + cloneId 决定，重连/重启后必须可复算且互不冲突。 */
class CloneDisplayIdsTest {

    @Test
    void deriveIsStableAcrossCalls() {
        UUID real = UUID.nameUUIDFromBytes("clone-display-real".getBytes());
        assertEquals(CloneDisplayIds.derive(real, 2), CloneDisplayIds.derive(real, 2));
    }

    @Test
    void differentCloneIdsProduceDifferentDisplayIds() {
        UUID real = UUID.nameUUIDFromBytes("clone-display-real".getBytes());
        assertNotEquals(CloneDisplayIds.derive(real, 2), CloneDisplayIds.derive(real, 3));
    }
}
