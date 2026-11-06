package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.BrainTestStubs;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 恐慌触发条件锁（26.3/1.21.11 字节码对齐）：触发 = 最后伤害来源命中伤害类型
 * 标签（getLastDamageSource 40 tick 窗口），不区分攻击者、不看 getLastHurtByMob。
 * 狼（PANIC_ENVIRONMENTAL_CAUSES）普通挨打/中箭不恐慌、环境伤害才恐慌——
 * 恐慌压制反击的旧缺陷回归防线；猪（默认 PANIC_CAUSES）普通挨打也恐慌。
 * 变异验证：移除 shouldPanic 的标签判定（恢复 getLastHurtByMob 语义）→ 狼挨打
 * 用例失败；改标签 → 触发矩阵翻转。
 */
class PlayerPanicGoalTest {

    private static DamageSource playerAttack;
    private static DamageSource mobAttack;
    private static DamageSource arrow;
    private static DamageSource inFire;
    private static DamageSource lightning;

    @BeforeAll
    static void setUp() {
        TestRegistries.bootstrap();
        // PANIC_ENVIRONMENTAL_CAUSES：26.3 标签数据（cactus/freeze/hot_floor/
        // sulfur_cube_hot/in_fire/lava/lightning_bolt/on_fire）——按真实数据绑定
        playerAttack = new DamageSource(BrainTestStubs.damageType("player_attack",
                DamageTypeTags.PANIC_CAUSES, DamageTypeTags.IS_PLAYER_ATTACK));
        mobAttack = new DamageSource(BrainTestStubs.damageType("mob_attack",
                DamageTypeTags.PANIC_CAUSES));
        arrow = new DamageSource(BrainTestStubs.damageType("arrow",
                DamageTypeTags.PANIC_CAUSES, DamageTypeTags.IS_PROJECTILE));
        inFire = new DamageSource(BrainTestStubs.damageType("in_fire",
                DamageTypeTags.PANIC_CAUSES, DamageTypeTags.IS_FIRE,
                DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        lightning = new DamageSource(BrainTestStubs.damageType("lightning_bolt",
                DamageTypeTags.PANIC_CAUSES, DamageTypeTags.IS_LIGHTNING,
                DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
    }

    /** 狼口径：TamableAnimalPanicGoal(1.5, PANIC_ENVIRONMENTAL_CAUSES) */
    private static PlayerPanicGoal wolfPanic(BrainTestStubs.FakePlayer wolf) {
        return new PlayerPanicGoal(wolf, 1.5, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES);
    }

    @Test
    void wolfDoesNotPanicFromMeleeOrProjectile() {
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);

        wolf.stubLastDamage(playerAttack, 1000L);
        assertFalse(goal.shouldPanic(), "玩家近战打狼不恐慌（走 HurtBy 反击）");
        wolf.stubLastDamage(mobAttack, 1000L);
        assertFalse(goal.shouldPanic(), "生物近战打狼不恐慌");
        wolf.stubLastDamage(arrow, 1000L);
        assertFalse(goal.shouldPanic(), "箭矢打狼不恐慌（远程伤害非环境伤害）");
    }

    @Test
    void wolfPanicsFromEnvironmentalDamage() {
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);

        wolf.stubLastDamage(inFire, 1000L);
        assertTrue(goal.shouldPanic(), "着火伤害触发狼恐慌");
        wolf.stubLastDamage(lightning, 1000L);
        assertTrue(goal.shouldPanic(), "雷击触发狼恐慌");
    }

    @Test
    void panicWindowExpiresAfter40Ticks() {
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);
        var level = BrainTestStubs.stubWorld();
        wolf.stubWorld(level);
        var levelData = (net.minecraft.world.level.storage.PrimaryLevelData)
                BrainTestStubs.readField(level, net.minecraft.world.level.Level.class, "levelData");

        wolf.stubLastDamage(inFire, 0L);
        levelData.setGameTime(0L);
        assertTrue(goal.shouldPanic(), "受伤当刻（gameTime-戳=0 ≤40）恐慌");
        levelData.setGameTime(40L);
        assertTrue(goal.shouldPanic(), "第 40 tick 仍在窗口内（≤40）");
        levelData.setGameTime(41L);
        assertFalse(goal.shouldPanic(), "41 tick 后伤害来源过期（getLastDamageSource 返回 null）");
    }

    @Test
    void pigPanicsFromMeleeAttack() {
        BrainTestStubs.FakePlayer pig = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = new PlayerPanicGoal(pig, 1.25); // 原版 Pig 默认 PANIC_CAUSES

        pig.stubLastDamage(playerAttack, 1000L);
        assertTrue(goal.shouldPanic(), "猪被玩家近战打要恐慌（原版默认标签含玩家/生物攻击）");
        pig.stubLastDamage(inFire, 1000L);
        assertTrue(goal.shouldPanic(), "环境伤害同样触发");
    }

    @Test
    void noDamageRecordedMeansNoPanic() {
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);
        assertFalse(goal.shouldPanic(), "无伤害来源（getLastDamageSource=null）不恐慌");
    }

    @Test
    void sameTeamMeleeDoesNotPanicWolf() {
        // 同队误伤的伤害类型同样是 player_attack：标签语义下狼不恐慌（原版同款），
        // 反击/围攻由 HurtBy 的队伍过滤处理——两层语义正交
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);
        wolf.stubLastDamage(playerAttack, 1000L);
        assertFalse(goal.shouldPanic(), "同队误伤（player_attack）不触发狼恐慌");
    }

    @Test
    void lifecycleAndFlags() {
        BrainTestStubs.FakePlayer wolf = BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
        PlayerPanicGoal goal = wolfPanic(wolf);

        assertEquals(java.util.EnumSet.of(me.primaryuan.carpet.brain.PlayerGoal.Flag.MOVE),
                goal.getFlags(), "原版 PanicGoal 仅 MOVE 旗标");
        assertFalse(goal.canContinueToUse(), "无路径 → 不续行（原版 !navigation.isDone）");
        goal.stop(); // stop 只清运行标志不动导航（原版同款），无异常即通过
    }
}
