package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * F09：音符盒监听随脑会话存在（不依赖收集 Goal 是否运行），跨维从旧世界
 * 注销再注册并清空旧维度喜好点，卸脑注销。
 */
class AllayNoteListenerTest {

    static class TestAllayBrain extends AllayBrain {
        int registrations;
        int removals;

        TestAllayBrain(BrainTestStubs.FakePlayer player, UUID owner) {
            super(player, owner);
        }

        @Override
        protected void registerNoteListener() {
            this.registrations++;
        }

        @Override
        protected void unregisterNoteListener() {
            this.removals++;
        }
    }

    private BrainTestStubs.FakePlayer player;
    private TestAllayBrain brain;

    @BeforeEach
    void setUp() throws Exception {
        TestRegistries.bootstrap();
        player = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new Vec3(0.5, 64, 0.5));
        brain = new TestAllayBrain(player, player.getUUID());
    }

    @Test
    void listenerFollowsSessionNotGoal() {
        brain.onAttach();
        assertEquals(1, brain.registrations, "挂脑即注册监听，收集 Goal 未运行也生效");
        assertNotNull(brain.collectGoalForTest(), "会话持有收集目标实例");

        brain.onDetach();
        assertEquals(1, brain.removals, "卸脑注销监听");
    }

    @Test
    void dimensionChangeUnregistersOldWorldAndClearsLikedPoint() throws Exception {
        brain.onAttach();
        BrainTestStubs.FakePlayer other = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        Field field = AllayBrain.class.getDeclaredField("noteListenerLevel");
        field.setAccessible(true);
        field.set(brain, other.level());

        BlockPos liked = new BlockPos(3, 64, 3);
        brain.collectGoalForTest().noteHeard(liked, player.level().dimension(),
                player.level().getGameTime() + 600);
        brain.syncNoteListenerWorld();

        assertEquals(1, brain.removals, "跨维先从旧世界注销");
        assertEquals(2, brain.registrations, "再向新世界注册");
        assertNull(brain.collectGoalForTest().likedNoteBlockForTest(), "旧维度喜好点失效");
    }
}
