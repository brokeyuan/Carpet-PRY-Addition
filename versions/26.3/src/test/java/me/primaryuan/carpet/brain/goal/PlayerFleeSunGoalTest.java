package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 骷髅避日双决策锁（26.3/1.21.11 字节码对齐）：
 * FleeSun canUse 逐条 = 无目标 + isBrightOutside + 着火 + 露天 + 头盔空；
 * 候选 = 10 次 offset(nextInt(20)-10, nextInt(6)-3, nextInt(20)-10)，
 * 接受 = !canSeeSky && 怪物走位价值<0（亮度公式按字节码：0.5 - magic(x)）；
 * RestrictSun = 白天亮 + 头盔空即开寻路避日开关（不要求着火），stop 复位。
 * 变异验证：删 isOnFire 条件 → 白天未着火用例失败；删头盔门 → 戴盔用例失败。
 */
class PlayerFleeSunGoalTest {

    /** 环境面可编程的假人桩（日光/火焰/头盔/露天缝）。
     *  Unsafe 分配（真实构造路径依赖整个服务端栈），默认值在工厂显式补齐 */
    static class SunPlayer extends BrainTestStubs.FakePlayer {
        boolean brightOutside = true;
        boolean onFire = false;
        ItemStack head = ItemStack.EMPTY;
        boolean skyExposed = true;

        static SunPlayer create() {
            SunPlayer player = BrainTestStubs.allocate(SunPlayer.class);
            java.util.UUID id = UUID.randomUUID();
            BrainTestStubs.putField(player, Entity.class, "uuid", id);
            BrainTestStubs.putField(player, Entity.class, "stringUUID", id.toString());
            player.alive = true;
            player.stubHeldItems(null, null);
            player.brightOutside = true;
            player.onFire = false;
            player.head = ItemStack.EMPTY;
            player.skyExposed = true;
            BrainTestStubs.putField(player, Entity.class, "random",
                    net.minecraft.util.RandomSource.create());
            return player;
        }

        @Override
        public boolean levelIsBrightOutside() {
            return this.brightOutside;
        }

        @Override
        public boolean isOnFire() {
            return this.onFire;
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return slot == EquipmentSlot.HEAD ? this.head : ItemStack.EMPTY;
        }
    }

    static class TestFleeSunGoal extends PlayerFleeSunGoal {
        boolean hideSpotAvailable = true;

        TestFleeSunGoal(SunPlayer mob) {
            super(mob);
        }

        @Override
        protected boolean isSkyExposed() {
            return ((SunPlayer) this.mob.asLiving()).skyExposed;
        }

        @Override
        protected boolean isSunAvoidCandidate(net.minecraft.world.level.Level level, BlockPos candidate) {
            return this.hideSpotAvailable;
        }
    }

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
    }

    @Test
    void fleeSunRequiresFireBrightSkyAndNoHelmet() {
        SunPlayer skeleton = SunPlayer.create();
        skeleton.stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        TestFleeSunGoal goal = new TestFleeSunGoal(skeleton);

        // 白天、未着火：不逃（避日由 RestrictSun 承担）
        skeleton.onFire = false;
        assertFalse(goal.canUse(), "白天未着火不触发 FleeSun（RestrictSun 的职责）");

        // 着火 + 露天 + 无头盔 → 触发
        skeleton.onFire = true;
        assertTrue(goal.canUse(), "着火且露天 → 逃生触发");

        // 戴头盔：不逃
        skeleton.head = new ItemStack(Items.IRON_HELMET);
        assertFalse(goal.canUse(), "有头盔不触发（原版头盔挡日照）");
        skeleton.head = ItemStack.EMPTY;

        // 已在荫蔽（不露天）：不逃
        skeleton.skyExposed = false;
        assertFalse(goal.canUse(), "身处非露天位置不触发");
        skeleton.skyExposed = true;

        // 有攻击目标：不逃
        skeleton.stubTarget(BrainTestStubs.TargetMob.create().stubWorld(skeleton.level()));
        assertFalse(goal.canUse(), "有目标不逃（原版 getTarget != null 拒绝）");
    }

    @Test
    void noHideSpotRejectsStart() {
        SunPlayer skeleton = SunPlayer.create();
        skeleton.stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        TestFleeSunGoal goal = new TestFleeSunGoal(skeleton);
        skeleton.onFire = true;
        goal.hideSpotAvailable = false;
        assertFalse(goal.canUse(), "10 次尝试无躲避点 → 不启动（原版 getHidePos null）");
    }

    @Test
    void startNavigatesToHideSpotAndContinuesUntilArrival() {
        SunPlayer skeleton = SunPlayer.create();
        BrainTestStubs.RecordingNavigation nav = new BrainTestStubs.RecordingNavigation(skeleton);
        // getNavigation 走 PryMob 接口——桩里用反射装上录制导航器
        BrainTestStubs.putField(skeleton.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        skeleton.stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        TestFleeSunGoal goal = new TestFleeSunGoal(skeleton);
        skeleton.onFire = true;

        assertTrue(goal.canUse());
        goal.start();
        assertEquals(1, nav.moveToCalls, "start 走向躲避点");
        assertEquals(1.0, nav.lastSpeed, "原版 FleeSunGoal(1.0) 速度");

        nav.done = false;
        assertTrue(goal.canContinueToUse(), "未到达 → 续行（原版 !navigation.isDone）");
        nav.done = true;
        assertFalse(goal.canContinueToUse(), "到达/路径走完 → 结束");
    }

    @Test
    void monsterWalkTargetValueMatchesBytecode() {
        // 0.5 - lerp(ambient, x/(4-3x), 1)，x = 亮度/15（26.3 字节码，1.21 双证）
        assertEquals(0.5F, PlayerFleeSunGoal.monsterWalkTargetValue(0, 0.0F), 1.0E-6F,
                "全黑 → +0.5（走位偏好值高）");
        assertEquals(0.0F, PlayerFleeSunGoal.monsterWalkTargetValue(12, 0.0F), 0.01F,
                "亮度 12 → 0（x=0.8 → magic=0.5）");
        assertTrue(PlayerFleeSunGoal.monsterWalkTargetValue(15, 0.0F) < 0.0F,
                "满亮度 → 负值：字节码语义下 >12 亮度才满足躲避点条件");
        assertTrue(PlayerFleeSunGoal.monsterWalkTargetValue(8, 0.0F) > 0.0F,
                "亮度 8 → 正值：暗处不是躲避点（原版 quirk，按字节码移植）");
    }

    @Test
    void restrictSunTogglesAvoidSunWithoutFire() {
        SunPlayer skeleton = SunPlayer.create();
        BrainTestStubs.RecordingNavigation nav = new BrainTestStubs.RecordingNavigation(skeleton);
        BrainTestStubs.putField(skeleton.fields(), me.primaryuan.carpet.brain.MobFields.class,
                "navigation", nav);
        skeleton.stubWorld(BrainTestStubs.stubWorld())
                .stubBody(new net.minecraft.world.phys.Vec3(0.5, 64, 0.5));
        PlayerRestrictSunGoal goal = new PlayerRestrictSunGoal(skeleton);

        assertEquals(java.util.EnumSet.noneOf(PlayerGoal.Flag.class), goal.getFlags(),
                "原版 RestrictSun 无旗标——与移动类 Goal 并行");
        // 白天未着火即可启动（不要求着火——与 FleeSun 的职责分界）
        skeleton.onFire = false;
        assertTrue(goal.canUse(), "RestrictSun 不要求着火");
        goal.start();
        assertTrue(readAvoidSun(nav), "start 开启寻路避日");
        skeleton.head = new ItemStack(Items.IRON_HELMET);
        assertFalse(goal.canUse(), "戴头盔关闭避日");
        goal.stop();
        assertFalse(readAvoidSun(nav), "stop 复位避日开关（原版 setAvoidSun(false)）");

        skeleton.head = ItemStack.EMPTY;
        skeleton.brightOutside = false;
        assertFalse(goal.canUse(), "夜间/非亮天不启用");
    }

    private static boolean readAvoidSun(BrainTestStubs.RecordingNavigation nav) {
        return (Boolean) BrainTestStubs.readField(nav,
                me.primaryuan.carpet.brain.PlayerPathNavigation.class, "avoidSun");
    }
}
