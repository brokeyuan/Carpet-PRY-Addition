package me.primaryuan.carpet.brain;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 脑子单测桩：Unsafe 绕过构造（原版构造器依赖整个服务端栈），被测路径触达的
 * 字段经 Unsafe 直写（isRemoved/level 为 final 方法，只能走字段）；PryMob 假面
 * 由桩直接实现并转发到真实 MobFields——测试环境无 Mixin，无法经
 * ServerPlayerMobMixin 嫁接。
 *
 * <p>{@link #stubWorld()} 提供 detach/restoreTeam 路径所需的最小世界夹具：
 * 空壳 ServerLevel + 空壳 MinecraftServer + 真实例 ServerScoreboard（记分板
 * 操作落在真实映射上）。</p>
 */
public final class BrainTestStubs {

    private BrainTestStubs() {}

    /** 实现 PryMob 的假人桩（会话/controller 测试用）；实例须经 {@link #createFake} 工厂分配 */
    public static class FakePlayer extends ServerPlayer implements PryMob {
        @SuppressWarnings("unused")
        protected FakePlayer() {
            super(null, null, new GameProfile(UUID.randomUUID(), "stub"), null);
        }

        /** Unsafe 分配 + 直写 uuid/stringUUID（构造器被跳过，字段默认全 null）。
         *  字段初始化器同样被跳过——本应非空的默认值（存活/持物/随机源）在此补齐 */
        public static FakePlayer createFake(UUID id) {
            return createFakeOf(FakePlayer.class, id);
        }

        /** 同 createFake，允许测试桩子类复用整套实体基础装配 */
        public static <T extends FakePlayer> T createFakeOf(Class<T> type, UUID id) {
            FakePlayer player = allocate(type);
            putField(player, Entity.class, "uuid", id);
            putField(player, Entity.class, "stringUUID", id.toString());
            assignEntityBasics(player);
            player.alive = true;                                            // isAlive 默认值
            player.mainHand = net.minecraft.world.item.ItemStack.EMPTY;     // 持物默认空手
            player.offHand = net.minecraft.world.item.ItemStack.EMPTY;
            player.useItemCalls = new java.util.ArrayList<>();
            player.breakCalls = new java.util.ArrayList<>();
            putField(player, net.minecraft.world.entity.LivingEntity.class, "useItem",
                    net.minecraft.world.item.ItemStack.EMPTY);
            putField(player, net.minecraft.world.entity.LivingEntity.class, "activeEffects",
                    new java.util.HashMap<>());
            return (T) player;
        }

        /** 挂接最小世界夹具（detach→restoreTeam 链路用） */
        public FakePlayer stubWorld(Level level) {
            putField(this, Entity.class, "level", level);
            return this;
        }

        /** 直写 Entity.position/Entity.blockPosition 缓存（距离与方块几何判定用） */
        public FakePlayer stubBody(net.minecraft.world.phys.Vec3 pos) {
            putField(this, Entity.class, "position", pos);
            putField(this, Entity.class, "blockPosition",
                    net.minecraft.core.BlockPos.containing(pos));
            return this;
        }

        @Override
        public String getScoreboardName() {
            return "stubName";
        }

        @Override
        public boolean isAlive() {
            return this.alive; // 原版走 entityData（桩无 data watcher），默认恒存活
        }

        /** isAlive 开关（目标失效场景用） */
        public boolean alive = true;

        @Override
        public boolean isSpectator() {
            return false; // 原版走 gameMode（桩无 gameMode）
        }

        @Override
        public boolean isCreative() {
            return false; // 原版走 gameMode（桩无 gameMode）
        }

        @Override
        public net.minecraft.network.chat.Component getName() {
            return Component.literal("stubName");
        }

        @Override
        public void setSprinting(boolean sprinting) {
            // no-op：detach 的复位路径会写 SynchedEntityData（桩无 data watcher）
        }

        // ===== PryMob：与 ServerPlayerMobMixin 相同的 MobFields 转发 =====

        @Override
        public MobFields fields() {
            if (this.pry$mobFields == null) {
                this.pry$mobFields = new MobFields((ServerPlayer) (Object) this);
            }
            return this.pry$mobFields;
        }

        private MobFields pry$mobFields;

        @Override
        public PlayerPathNavigation getNavigation() {
            return this.fields().getNavigation();
        }

        /** 直写 onGround（扑击 Goal 的地面判定；boolean 原始字段走 putBoolean） */
        public FakePlayer stubOnGround(boolean onGround) {
            putBoolean(this, Entity.class, "onGround", onGround);
            return this;
        }

        /** 直写移动动量（扑击 start() 的动量叠加项） */
        public FakePlayer stubDeltaMovement(net.minecraft.world.phys.Vec3 delta) {
            putField(this, Entity.class, "deltaMovement", delta);
            return this;
        }

        /** 直写眼睛朝向（FollowOwner/Beg 的 lookControl 输入面） */
        public FakePlayer stubRotations(float xRot, float yRot) {
            putField(this, Entity.class, "xRot", xRot);
            putField(this, Entity.class, "yRot", yRot);
            return this;
        }

        /** 直写 tickCount（选择器奇偶门相位） */
        public FakePlayer stubTickCount(int tickCount) {
            putInt(this, Entity.class, "tickCount", tickCount);
            return this;
        }

        @Override
        public PlayerMoveControl getMoveControl() {
            return this.fields().getMoveControl();
        }

        @Override
        public PlayerLookControl getLookControl() {
            return this.fields().getLookControl();
        }

        @Override
        public PlayerSensing getSensing() {
            return this.fields().getSensing();
        }

        @Override
        public PlayerGoalSelector getGoalSelector() {
            return this.fields().goalSelector();
        }

        @Override
        public PlayerGoalSelector getTargetSelector() {
            return this.fields().targetSelector();
        }

        @Override
        public LivingEntity getTarget() {
            return this.fields().getTarget();
        }

        @Override
        public void setTarget(LivingEntity target) {
            this.fields().setTarget(target);
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            return false;
        }

        // ===== 受击来源桩（26.3 的 lastHurtByMob 为 EntityReference，桩直给对象）=====

        private LivingEntity stubHurtBy;

        @Override
        public LivingEntity getLastHurtByMob() {
            return this.stubHurtBy;
        }

        public FakePlayer stubHurtBy(LivingEntity attacker) {
            this.stubHurtBy = attacker;
            return this;
        }

        // ===== 持物桩（乞食/引诱/武器形态的 getMainHandItem/getOffhandItem 面）=====

        private net.minecraft.world.item.ItemStack mainHand = net.minecraft.world.item.ItemStack.EMPTY;
        private net.minecraft.world.item.ItemStack offHand = net.minecraft.world.item.ItemStack.EMPTY;

        @Override
        public net.minecraft.world.item.ItemStack getMainHandItem() {
            if (this.realInventoryMode) {
                return this.getInventory().getSelectedItem(); // 农民链路走真实背包选中位
            }
            return this.mainHand;
        }

        @Override
        public net.minecraft.world.item.ItemStack getOffhandItem() {
            return this.offHand;
        }

        // ===== 玩家身体动作记录（右键方块 / 即时破坏 / 挥手）=====

        /** 已记录的右键动作（pos 顺序表；Unsafe 分配跳过初始化器，工厂里建表） */
        public java.util.List<net.minecraft.core.BlockPos> useItemCalls;
        /** 已记录的破坏动作（pos 顺序表） */
        public java.util.List<net.minecraft.core.BlockPos> breakCalls;
        public int swingCalls;

        @Override
        public boolean pryUseItemOnBlock(net.minecraft.core.BlockPos pos,
                                         net.minecraft.world.InteractionHand hand) {
            this.useItemCalls.add(pos);
            return true;
        }

        @Override
        public boolean pryOpenContainer(net.minecraft.core.BlockPos pos,
                                        net.minecraft.world.InteractionHand hand) {
            this.useItemCalls.add(pos);
            return true;
        }

        @Override
        public boolean pryBreakBlock(net.minecraft.core.BlockPos pos) {
            this.breakCalls.add(pos);
            return true;
        }

        @Override
        public boolean pryUseItemInHand(net.minecraft.world.InteractionHand hand) {
            this.swingCalls++;
            return true;
        }

        @Override
        public void prySwingMainHand() {
            this.swingCalls++;
        }

        /** 真实背包模式：注入原版 Inventory 并让 getMainHandItem 走选中位（农民用） */
        public FakePlayer stubRealInventory() {
            putField(this, net.minecraft.world.entity.player.Player.class, "inventory",
                    new net.minecraft.world.entity.player.Inventory(this,
                            new net.minecraft.world.entity.EntityEquipment()));
            this.realInventoryMode = true;
            return this;
        }

        private boolean realInventoryMode;

        /** 直写主/副手持物（null = 空手） */
        public FakePlayer stubHeldItems(net.minecraft.world.item.ItemStack main,
                                        net.minecraft.world.item.ItemStack off) {
            this.mainHand = main != null ? main : net.minecraft.world.item.ItemStack.EMPTY;
            this.offHand = off != null ? off : net.minecraft.world.item.ItemStack.EMPTY;
            return this;
        }

        /** 直写 LivingEntity.lastDamageSource/lastDamageStamp（恐慌触发条件面） */
        public FakePlayer stubLastDamage(net.minecraft.world.damagesource.DamageSource source, long stamp) {
            putField(this, LivingEntity.class, "lastDamageSource", source);
            putLong(this, LivingEntity.class, "lastDamageStamp", stamp);
            return this;
        }

        /** 直写攻击目标（HurtBy/Nearest/扑击的目标面） */
        public FakePlayer stubTarget(LivingEntity target) {
            setTarget(target);
            return this;
        }
    }

    /** 非玩家攻击者桩（HurtBy 复仇链的敌方来源；isAlive 覆写绕开 data watcher） */
    public static class TargetMob extends net.minecraft.world.entity.monster.zombie.Zombie {
        @SuppressWarnings("unused")
        private TargetMob() {
            super(null, null);
        }

        public static TargetMob create() {
            TargetMob mob = allocate(TargetMob.class);
            assignEntityBasics(mob);
            return mob;
        }

        /** 挂接最小世界夹具（biteable 等同维判定用） */
        public TargetMob stubWorld(Level level) {
            putField(this, Entity.class, "level", level);
            return this;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public java.util.UUID getUUID() {
            return java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ee");
        }

        /** 直写 Entity.position 与 blockPosition 缓存（距离/方块几何用） */
        public TargetMob putPosition(net.minecraft.world.phys.Vec3 pos) {
            putField(this, Entity.class, "position", pos);
            putField(this, Entity.class, "blockPosition",
                    net.minecraft.core.BlockPos.containing(pos));
            return this;
        }
    }

    /** 被攻击目标桩：isAlive 可切换；removalReason/level 经 Unsafe 直写 */
    public static class TargetPlayer extends ServerPlayer {
        public boolean alive = true;

        @SuppressWarnings("unused")
        private TargetPlayer() {
            super(null, null, new GameProfile(UUID.randomUUID(), "target"), null);
        }

        public static TargetPlayer create() {
            return allocate(TargetPlayer.class);
        }

        @Override
        public boolean isAlive() {
            return this.alive;
        }

        @Override
        public String getScoreboardName() {
            return "targetName";
        }

        @Override
        public net.minecraft.network.chat.Component getName() {
            return Component.literal("targetName");
        }

        /** 直写 Entity.level（final 方法不可覆写，ServerPlayer.level() 读同字段） */
        public void stubLevel(Level level) {
            putField(this, Entity.class, "level", level);
        }

        /** 直写 Entity.removalReason（isRemoved() 为 final，读此字段） */
        public void stubRemoved(Entity.RemovalReason reason) {
            putField(this, Entity.class, "removalReason", reason);
        }

        /** 直写 Entity.position/Entity.blockPosition 缓存（距离与名字判定用） */
        public TargetPlayer stubBody(net.minecraft.world.phys.Vec3 pos) {
            putField(this, Entity.class, "position", pos);
            putField(this, Entity.class, "blockPosition",
                    net.minecraft.core.BlockPos.containing(pos));
            return this;
        }
    }

    /** 行为可编程的 Goal 桩（选择器调度语义用） */
    public static class StubGoal extends PlayerGoal {
        boolean canUseResult;
        boolean canContinueResult = true;
        int startCount;
        int stopCount;
        int tickCount;

        StubGoal(Flag... flags) {
            EnumSet<Flag> set = EnumSet.noneOf(Flag.class);
            for (Flag flag : flags) {
                set.add(flag);
            }
            this.setFlags(set);
        }

        @Override
        public boolean canUse() {
            return this.canUseResult;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canContinueResult;
        }

        @Override
        public void start() {
            this.startCount++;
        }

        @Override
        public void stop() {
            this.stopCount++;
        }

        @Override
        public void tick() {
            this.tickCount++;
        }
    }

    /** 行为可编程的脑子会话桩：tick 行为由 Runnable 注入 */
    public static class ScriptedController extends PlayerBrainController {
        Runnable tickAction = () -> {};

        ScriptedController(FakePlayer player) {
            super(player);
        }

        @Override
        public String modeKey() {
            return "zombie";
        }

        @Override
        public void tick() {
            this.tickAction.run();
        }
    }

    /** 裸 EntityPlayerMPFake 桩：构造器私有且重依赖，Unsafe 分配后仅直写 uuid——
     *  isTeamFriendly 的 instanceof 门在此类型上放行（队伍判定矩阵用） */
    static EntityPlayerMPFake rawFake(UUID id) {
        EntityPlayerMPFake fake = allocate(EntityPlayerMPFake.class);
        putField(fake, Entity.class, "uuid", id);
        putField(fake, Entity.class, "stringUUID", id.toString());
        return fake;
    }

    /** 可录制移动指令的寻路器桩：Goal 的 moveTo/isDone 面可断言、不触世界 */
    public static class RecordingNavigation extends PlayerPathNavigation {
        private final ServerPlayer owner;
        public double lastX;
        public double lastY;
        public double lastZ;
        public double lastSpeed;
        public Object lastMoveTarget;
        public int moveToCalls;
        public boolean done = true;
        /** 前 N 次坐标寻路返回失败（模拟 A* 无路） */
        public int failNextMoves;

        public RecordingNavigation(ServerPlayer player) {
            super(player);
            this.owner = player;
        }

        @Override
        public boolean moveTo(net.minecraft.world.entity.LivingEntity target, double speed) {
            this.lastMoveTarget = target;
            this.lastSpeed = speed;
            this.moveToCalls++;
            return true;
        }

        @Override
        public boolean moveTo(double x, double y, double z, double speed) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastSpeed = speed;
            this.moveToCalls++;
            if (this.failNextMoves > 0) {
                this.failNextMoves--;
                return false;
            }
            return true;
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

        @Override
        public Level level() {
            return this.owner.level(); // MobFields 惰性导航器的同维判定跟随本尊世界
        }
    }

    /** 空壳世界夹具：detach→restoreTeam 的 level().getServer().getScoreboard() 链可用 */
    public static Level stubWorld() {
        try {
            // MinecraftServer 为抽象类，用具体子类 DedicatedServer 作空壳；
            // ServerScoreboard 的通知钩子走 server.getPlayerList()——挂一个
            // 空成员表 PlayerList 桩让广播循环 no-op
            ServerLevel level = allocate(ServerLevel.class);
            DedicatedServer server = allocate(DedicatedServer.class);
            ServerScoreboard scoreboard = new ServerScoreboard(server);
            var playerList = allocate(net.minecraft.server.dedicated.DedicatedPlayerList.class);
            putField(playerList, net.minecraft.server.players.PlayerList.class, "players", new java.util.ArrayList<>());
            putField(playerList, net.minecraft.server.players.PlayerList.class, "playersByUUID", new java.util.HashMap<>());
            putField(server, MinecraftServer.class, "scoreboard", scoreboard);
            putField(server, MinecraftServer.class, "playerList", playerList);
            putField(server, MinecraftServer.class, "levels", new java.util.HashMap<>());
            putField(level, ServerLevel.class, "server", server);
            // LevelData：难度门（biteable 的和平判定）需要 levelData.getDifficulty()
            var levelData = new net.minecraft.world.level.storage.PrimaryLevelData(
                    new net.minecraft.world.level.LevelSettings(
                            "stub", net.minecraft.world.level.GameType.SURVIVAL,
                            net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT,
                            false, net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    net.minecraft.world.level.storage.PrimaryLevelData.SpecialWorldProperty.NONE,
                    com.mojang.serialization.Lifecycle.stable());
            putField(level, Level.class, "levelData", levelData);
            // 维度键（铜傀儡等跨维判定用）
            putField(level, Level.class, "dimension", net.minecraft.world.level.Level.OVERWORLD);
            // 1.21.11+ 的 getGameRules 走 ServerLevel.serverLevelData 链
            putField(level, ServerLevel.class, "serverLevelData", levelData);
            // 容器点击校验 enabledFeatures 走 server.getWorldData()
            putField(server, MinecraftServer.class, "worldData", levelData);
            // 空区块源：AllayBrain 会话监听器注册时查区块，返回“未加载”即可
            putField(level, ServerLevel.class, "chunkSource", allocate(EmptyServerChunkCache.class));
            // dimensionTypeRegistration 不注入：DIMENSIONS 层注册表是数据包驱动的，
            // 纯 JVM bootstrap 不可达；谓词几何用例走静态语义路径（见
            // BrainNavigationGeometryTest），不做真实世界读块
            return level;
        } catch (Exception e) {
            throw new IllegalStateException("stub world bootstrap failed", e);
        }
    }

    /** 空区块源：GameEventListener 的区块注册查询一律返回“未加载”。 */
    public static class EmptyServerChunkCache extends net.minecraft.server.level.ServerChunkCache {
        @SuppressWarnings("unused")
        private EmptyServerChunkCache() {
            super(null, null, null, null, null, null, 0, 0, false, null);
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x, int z,
                net.minecraft.world.level.chunk.status.ChunkStatus status, boolean load) {
            return null;
        }

        @Override
        public void tick(java.util.function.BooleanSupplier hasTimeLeft, boolean tickChunks) {
        }

        @Override
        public String gatherStats() {
            return "stub";
        }

        @Override
        public int getLoadedChunksCount() {
            return 0;
        }

        @Override
        public net.minecraft.server.level.ThreadedLevelLightEngine getLightEngine() {
            return null;
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ENTITY_ID =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /** Unsafe 分配后的实体基础字段补齐：唯一 id（Entity.equals/getId 需要）+ 随机源 */
    public static void assignEntityBasics(net.minecraft.world.entity.Entity entity) {
        putInt(entity, Entity.class, "id", NEXT_ENTITY_ID.getAndIncrement());
        putField(entity, Entity.class, "random", net.minecraft.util.RandomSource.create());
    }

    public static <T> T allocate(Class<T> type) {
        try {
            return type.cast(unsafe().allocateInstance(type));
        } catch (InstantiationException e) {
            throw new IllegalStateException("allocate failed: " + type, e);
        }
    }

    public static void putField(Object owner, Class<?> ownerClass, String fieldName, Object value) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            unsafe().putObject(owner, unsafe().objectFieldOffset(field), value);
        } catch (Exception e) {
            throw new IllegalStateException("field write failed: " + fieldName, e);
        }
    }

    public static void putInt(Object owner, Class<?> ownerClass, String fieldName, int value) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            unsafe().putInt(owner, unsafe().objectFieldOffset(field), value);
        } catch (Exception e) {
            throw new IllegalStateException("field write failed: " + fieldName, e);
        }
    }

    public static void putBoolean(Object owner, Class<?> ownerClass, String fieldName, boolean value) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            unsafe().putBoolean(owner, unsafe().objectFieldOffset(field), value);
        } catch (Exception e) {
            throw new IllegalStateException("field write failed: " + fieldName, e);
        }
    }

    public static void putLong(Object owner, Class<?> ownerClass, String fieldName, long value) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            unsafe().putLong(owner, unsafe().objectFieldOffset(field), value);
        } catch (Exception e) {
            throw new IllegalStateException("field write failed: " + fieldName, e);
        }
    }

    /** 读实例字段（测试直读内部状态用） */
    public static Object readField(Object owner, Class<?> ownerClass, String fieldName) {
        try {
            Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(owner);
        } catch (Exception e) {
            throw new IllegalStateException("field read failed: " + fieldName, e);
        }
    }

    /**
     * 给注册表 Holder 绑定标签：纯 JVM 未装载数据包标签，Holder.is(TagKey) 恒 false，
     * 伤害类型标签（恐慌触发条件）测试须手动挂上（对齐服务端装载数据包后的效果）。
     */
    public static <T> void bindTag(Holder<T> holder, net.minecraft.tags.TagKey<T> tag) {
        try {
            Field field = null;
            for (Class<?> c = holder.getClass(); c != null && field == null; c = c.getSuperclass()) {
                try {
                    field = c.getDeclaredField("tags");
                } catch (NoSuchFieldException ignored) {
                    // 标签字段声明在父类（Holder.Reference 体系），逐层上溯
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("tags not found on " + holder.getClass());
            }
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<net.minecraft.tags.TagKey<T>> tags =
                    (java.util.Set<net.minecraft.tags.TagKey<T>>) field.get(holder);
            // 标签集可能未初始化（standalone Holder）或为不可变集（物品 Holder 的
            // Set.of()）：统一换成可变副本再挂，保留既有标签
            java.util.Set<net.minecraft.tags.TagKey<T>> mutable =
                    tags != null ? new java.util.HashSet<>(tags) : new java.util.HashSet<>();
            mutable.add(tag);
            field.set(holder, mutable);
        } catch (Exception e) {
            throw new IllegalStateException("bindTag failed: " + tag, e);
        }
    }

    /**
     * 构造测试用 DamageType Holder：26.x 起 damage_type 是数据包驱动注册表
     * （纯 JVM 静态层不含它），用 createStandAlone 独立 Holder + 反射绑定值与标签，
     * 使 {@code DamageSource.is(TagKey)} 与服务端装载数据包后同语义。
     */
    @SafeVarargs
    public static Holder<net.minecraft.world.damagesource.DamageType> damageType(
            String name, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType>... tags) {
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.Identifier.withDefaultNamespace(name));
        Holder.Reference<net.minecraft.world.damagesource.DamageType> holder =
                Holder.Reference.createStandAlone(new HolderOwner<net.minecraft.world.damagesource.DamageType>() {},
                        key);
        try {
            var bindValue = holder.getClass().getDeclaredMethod("bindValue",
                    Object.class);
            bindValue.setAccessible(true);
            bindValue.invoke(holder, new net.minecraft.world.damagesource.DamageType(
                    name, net.minecraft.world.damagesource.DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));
        } catch (Exception e) {
            throw new IllegalStateException("damage type value bind failed: " + name, e);
        }
        for (net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag : tags) {
            bindTag(holder, tag);
        }
        return holder;
    }

    static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("unsafe unavailable", e);
        }
    }
}
