package me.primaryuan.carpet.command;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import me.primaryuan.carpet.brain.AllayBrain;
import me.primaryuan.carpet.brain.BrainManager;
import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerBrainController;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命令到管理器的挂载接线：玩家执行 {@code brain allay} 时，执行者 UUID 必须
 * 经 setMode → BrainManager.attach → AllayBrain 进入收集 Goal；控制台挂悦灵
 * 允许无主人，控制台挂狼仍拒绝。另锁定 keep 记录复用同一 owner。
 *
 * <p>纯 JVM 无 Mixin，用桩身份门放行 FakePlayer；生产门恒为 Carpet 假人。</p>
 */
class BrainCommandWiringTest {

    private ServerLevel level;
    private BrainTestStubs.FakePlayer target;
    private BrainTestStubs.FakePlayer executor;
    private CommandSourceStack playerSource;
    private CommandSourceStack consoleSource;

    /** 仅按名字解析目标，getPlayers 为空：避免 Scoreboard 向无连接桩广播 */
    private static ServerPlayer lookupTarget;

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @BeforeEach
    void setUp() throws Exception {
        openProductionGates();
        level = (ServerLevel) BrainTestStubs.stubWorld();
        target = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(level)
                .stubBody(new Vec3(0.5, 64, 0.5));
        BrainTestStubs.putField(target, Player.class, "gameProfile",
                new GameProfile(target.getUUID(), "brain_target"));
        lookupTarget = target;
        TargetOnlyPlayerList playerList = BrainTestStubs.allocate(TargetOnlyPlayerList.class);
        BrainTestStubs.putField(playerList, net.minecraft.server.players.PlayerList.class,
                "players", new ArrayList<ServerPlayer>());
        BrainTestStubs.putField(playerList, net.minecraft.server.players.PlayerList.class,
                "playersByUUID", new HashMap<UUID, ServerPlayer>());
        BrainTestStubs.putField(level.getServer(), MinecraftServer.class, "playerList", playerList);

        executor = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(level)
                .stubBody(Vec3.ZERO);
        BrainTestStubs.putField(executor, Player.class, "gameProfile",
                new GameProfile(executor.getUUID(), "brain_executor"));
        playerSource = new CommandSourceStack(new RecordingSource(), Vec3.ZERO, Vec2.ZERO, level,
                PermissionSet.NO_PERMISSIONS, level.getServer(), executor);
        consoleSource = new CommandSourceStack(new RecordingSource(), Vec3.ZERO, Vec2.ZERO, level,
                PermissionSet.NO_PERMISSIONS, Component.literal("server"), level.getServer());
    }

    @AfterEach
    void tearDown() throws Exception {
        BrainManager.detachAll();
        brainMap().clear();
        keepMap().clear();
        brainTeamStates().clear();
        PlayerBrainCommand.fakePlayerGate = p -> p instanceof EntityPlayerMPFake;
        attachGateField().set(null, (Predicate<ServerPlayer>) (p -> p instanceof EntityPlayerMPFake));
    }

    @Test
    void playerAllayPassesExecutorAsDefaultDeliveryOwner() throws Exception {
        assertEquals(1, setMode(context(playerSource, "allay")), "玩家挂悦灵成功");

        PlayerBrainController brain = brainMap().get(target.getUUID());
        assertNotNull(brain, "会话已建立");
        assertTrue(brain instanceof AllayBrain, "管理器构造悦灵脑");
        assertEquals(executor.getUUID(), assembledOwner((AllayBrain) brain),
                "命令执行者 UUID 进入 AllayBrain（默认交付对象）");
        assertEquals("allay", BrainManager.getModeKey(target));
    }

    @Test
    void consoleAllayIsAllowedWithNullOwner() throws Exception {
        assertEquals(1, setMode(context(consoleSource, "allay")), "控制台挂悦灵允许");

        PlayerBrainController brain = brainMap().get(target.getUUID());
        assertNotNull(brain, "控制台会话已建立");
        assertNull(assembledOwner((AllayBrain) brain), "控制台无默认交付对象，等待音符盒");
    }

    @Test
    void consoleWolfIsStillRejected() throws Exception {
        assertEquals(0, setMode(context(consoleSource, "wolf")), "控制台挂狼拒绝");
        assertNull(BrainManager.getModeKey(target), "拒绝后不得残留会话");
        assertNull(brainMap().get(target.getUUID()));
    }

    @Test
    void allayKeepStoresExecutorOwnerForReattach() throws Exception {
        assertEquals(1, setMode(context(playerSource, "allay keep")));

        Object record = keepMap().get(target.getUUID());
        assertNotNull(record, "keep 记录已保存");
        Field owner = record.getClass().getDeclaredField("owner");
        owner.setAccessible(true);
        assertEquals(executor.getUUID(), owner.get(record), "keep 补挂复用执行者 owner");
    }

    @Test
    void consoleAllayKeepStoresNullOwner() throws Exception {
        assertEquals(1, setMode(context(consoleSource, "allay keep")));

        Object record = keepMap().get(target.getUUID());
        assertNotNull(record, "控制台 keep 记录已保存");
        Field owner = record.getClass().getDeclaredField("owner");
        owner.setAccessible(true);
        assertNull(owner.get(record), "控制台 keep 保持无主人，重上后继续等待音符盒");
    }

    // ===== 测试夹具 =====

    private int setMode(CommandContext<CommandSourceStack> context) throws Exception {
        Method method = PlayerBrainCommand.class.getDeclaredMethod("setMode", CommandContext.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, context);
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> context(CommandSourceStack source, String mode)
            throws Exception {
        CommandContext<CommandSourceStack> context =
                (CommandContext<CommandSourceStack>) unsafe(CommandContext.class);
        Map<String, ParsedArgument<CommandSourceStack, ?>> arguments = new HashMap<>();
        arguments.put("player", new ParsedArgument<>(0, 12, "brain_target"));
        arguments.put("mode", new ParsedArgument<>(0, mode.length(), mode));
        setField(context, "source", source);
        setField(context, "input", "player brain_target brain " + mode);
        setField(context, "arguments", arguments);
        setField(context, "command", null);
        setField(context, "rootNode", null);
        setField(context, "nodes", List.of());
        setField(context, "range", null);
        setField(context, "child", null);
        setField(context, "modifier", null);
        setField(context, "forks", false);
        return context;
    }

    private static UUID assembledOwner(AllayBrain brain) throws Exception {
        Field owner = AllayBrain.class.getDeclaredField("assembledOwnerUuid");
        owner.setAccessible(true);
        return (UUID) owner.get(brain);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, PlayerBrainController> brainMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("BRAINS");
        field.setAccessible(true);
        return (Map<UUID, PlayerBrainController>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> keepMap() throws Exception {
        Field field = BrainManager.class.getDeclaredField("KEEP");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ?> brainTeamStates() throws Exception {
        Field field = BrainManager.class.getDeclaredField("TEAM_STATES");
        field.setAccessible(true);
        return (Map<UUID, ?>) field.get(null);
    }

    private static void openProductionGates() throws Exception {
        PlayerBrainCommand.fakePlayerGate = p -> true;
        attachGateField().set(null, (Predicate<ServerPlayer>) (p -> true));
    }

    @SuppressWarnings("unchecked")
    private static Field attachGateField() throws Exception {
        Field field = BrainManager.class.getDeclaredField("attachGate");
        field.setAccessible(true);
        return field;
    }

    private static Object unsafe(Class<?> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** 命令解析只按名字找目标；广播玩家列表为空（桩没有网络连接） */
    static final class TargetOnlyPlayerList extends DedicatedPlayerList {
        @SuppressWarnings("unused")
        private TargetOnlyPlayerList() {
            super(null, null, null);
        }

        @Override
        public ServerPlayer getPlayerByName(String name) {
            // 命令解析用档案名；计分板按 getScoreboardName 回调时不命中，避免
            // 纯 JVM 桩缺少 waypoint manager
            return "brain_target".equals(name) ? lookupTarget : null;
        }

        @Override
        public List<ServerPlayer> getPlayers() {
            return List.of();
        }
    }

    private static final class RecordingSource implements CommandSource {
        final List<String> sent = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            sent.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
