package me.primaryuan.carpet.brain;

import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** brain off 的 keep 清理：无脑子时也必须清（否则规则自动卸载后 off 会被扫描复活） */
class BrainKeepCleanupTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void offWithoutBrainStillClearsKeep() throws Exception {
        Map<UUID, Object> keep = keepMap();
        keep.clear();

        UUID fakeId = UUID.randomUUID();
        Constructor<?> recordCtor = Class.forName("me.primaryuan.carpet.brain.BrainManager$KeepRecord")
                .getDeclaredConstructor(String.class, UUID.class);
        recordCtor.setAccessible(true);
        keep.put(fakeId, recordCtor.newInstance("zombie", null));

        ServerPlayer stub = stubPlayer(fakeId);
        // 自动卸载语义：keep 保留（下线重上后应自动恢复）
        assertFalse(BrainManager.detach(stub, false));
        assertTrue(keep.containsKey(fakeId), "自动卸载不得清除 keep");

        // brain off 语义：当前无脑子也清 keep，扫描不得复活
        assertFalse(BrainManager.detach(stub, true));
        assertFalse(keep.containsKey(fakeId), "off 必须清除 keep，即使当前无脑子");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> keepMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("KEEP");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static ServerPlayer stubPlayer(UUID id) throws Exception {
        StubPlayer player = (StubPlayer) unsafe().allocateInstance(StubPlayer.class);
        player.stubId = id;
        return player;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class StubPlayer extends ServerPlayer {
        private UUID stubId;
        private StubPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "测试"), null); }
        @Override public UUID getUUID() { return stubId; }
    }
}
