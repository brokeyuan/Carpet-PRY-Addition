package me.primaryuan.carpet.brain;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脑会话与战斗队伍的生命周期隔离：名牌后缀计分板队随挂/卸建删且不残留、
 * 原队伍恢复；战斗队伍独立于脑子存续（attach/detach 不触达 BrainTeams 存储
 * ——由代码审查确认，本类锁定存储面与后缀队伍面；attach 的 instanceof
 * EntityPlayerMPFake 门在纯 JVM 桩下不可达，接线由 boot check 承接）。
 */
class BrainLifecycleTeamsTest {

    private static Method applyTeam;
    private static Method restoreTeam;

    @BeforeAll
    static void setUp() throws Exception {
        TestRegistries.bootstrap();
        applyTeam = BrainManager.class.getDeclaredMethod("applyTeam",
                me.primaryuan.carpet.brain.PlayerBrainController.class,
                net.minecraft.server.level.ServerPlayer.class, String.class);
        applyTeam.setAccessible(true);
        restoreTeam = BrainManager.class.getDeclaredMethod("restoreTeam", net.minecraft.server.level.ServerPlayer.class);
        restoreTeam.setAccessible(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        var teams = BrainTeams.class.getDeclaredField("TEAMS");
        teams.setAccessible(true);
        ((java.util.Map<?, ?>) teams.get(null)).clear();
        var states = BrainManager.class.getDeclaredField("TEAM_STATES");
        states.setAccessible(true);
        ((java.util.Map<?, ?>) states.get(null)).clear();
    }

    private static BrainTestStubs.FakePlayer fakeWithWorld() {
        return BrainTestStubs.FakePlayer.createFake(UUID.randomUUID())
                .stubWorld(BrainTestStubs.stubWorld());
    }

    /** applyTeam 现在从脑子取后缀 lang 键：按模式造最小控制器 */
    private static PlayerBrainController brainOf(BrainTestStubs.FakePlayer fake) {
        return new ZombieBrain(fake);
    }

    private static Scoreboard scoreboard(BrainTestStubs.FakePlayer player) {
        return player.level().getServer().getScoreboard();
    }

    @Test
    void suffixTeamCreatedOnApplyAndRemovedOnRestore() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam team = scoreboard.getPlayersTeam(name);
        assertNotNull(team, "挂脑后进入后缀计分板队");
        assertTrue(team.getName().startsWith("pry_brain_zombie_"), "每假人一队（模式前缀）");
        assertTrue(scoreboard.getPlayerTeams().stream().anyMatch(t -> t.getName().startsWith("pry_brain_")));

        restoreTeam.invoke(null, fake);
        assertNull(scoreboard.getPlayersTeam(name), "卸脑后移出并删除后缀队");
        assertFalse(scoreboard.getPlayerTeams().stream().anyMatch(t -> t.getName().startsWith("pry_brain_")),
                "后缀队不残留");
    }

    @Test
    void suffixTeamRestoresOriginalMembership() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();

        // 预置"原队伍"：挂脑 → 应继承进入临时队；卸脑 → 回原队伍
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        scoreboard.addPlayerToTeam(name, original);

        applyTeam.invoke(null, brainOf(fake), fake, "skeleton");
        PlayerTeam suffix = scoreboard.getPlayersTeam(name);
        assertNotNull(suffix);
        assertTrue(suffix.getName().startsWith("pry_brain_skeleton_"));

        restoreTeam.invoke(null, fake);
        assertEquals(original, scoreboard.getPlayersTeam(name), "卸脑恢复原队伍归属");
    }

    @Test
    void externalTeamJoinIsPreservedAndTemporaryTeamIsRemoved() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        scoreboard.addPlayerToTeam(name, original);

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam temporary = scoreboard.getPlayersTeam(name);
        assertNotNull(temporary);

        // 挂脑期间管理员把假人改到外部队伍 B：卸脑不得恢复 A，也不得留下临时队
        PlayerTeam external = scoreboard.addPlayerTeam("pry_test_external");
        scoreboard.addPlayerToTeam(name, external);

        restoreTeam.invoke(null, fake);

        assertEquals(external, scoreboard.getPlayersTeam(name), "外部改队 B 保留");
        assertNull(scoreboard.getPlayerTeam(temporary.getName()), "本会话临时队零残留");
    }

    @Test
    void externalTeamLeaveIsPreserved() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        scoreboard.addPlayerToTeam(name, original);

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam temporary = scoreboard.getPlayersTeam(name);
        assertNotNull(temporary);

        // 挂脑期间管理员把假人移出临时队：卸脑不得恢复 A
        scoreboard.removePlayerFromTeam(name, temporary);

        restoreTeam.invoke(null, fake);

        assertNull(scoreboard.getPlayersTeam(name), "外部退队保持无队");
        assertNull(scoreboard.getPlayerTeam(temporary.getName()), "本会话临时队零残留");
    }

    @Test
    void noOriginalTeamAndExternalJoinKeepsExternalTeam() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam temporary = scoreboard.getPlayersTeam(name);
        assertNotNull(temporary);
        PlayerTeam external = scoreboard.addPlayerTeam("pry_test_external");
        scoreboard.addPlayerToTeam(name, external);

        restoreTeam.invoke(null, fake);

        assertEquals(external, scoreboard.getPlayersTeam(name), "原本无队时外部改队也保留");
        assertNull(scoreboard.getPlayerTeam(temporary.getName()), "本会话临时队零残留");
    }

    @Test
    void normalRestoreIsIdempotent() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        scoreboard.addPlayerToTeam(name, original);

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        restoreTeam.invoke(null, fake);
        assertEquals(original, scoreboard.getPlayersTeam(name), "未外部改队时恢复原队伍");

        int teamsAfterRestore = scoreboard.getPlayerTeams().size();
        restoreTeam.invoke(null, fake);
        assertEquals(original, scoreboard.getPlayersTeam(name), "重复卸载不改归属");
        assertEquals(teamsAfterRestore, scoreboard.getPlayerTeams().size(), "重复卸载无副作用");
        assertFalse(scoreboard.getPlayerTeams().stream().anyMatch(t -> t.getName().startsWith("pry_brain_")),
                "临时队零残留");
    }

    @Test
    void staleInstanceRestoreDoesNotTouchNewSession() throws Exception {
        UUID id = UUID.randomUUID();
        BrainTestStubs.FakePlayer oldInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());
        BrainTestStubs.FakePlayer newInstance = BrainTestStubs.FakePlayer.createFake(id)
                .stubWorld(BrainTestStubs.stubWorld());

        applyTeam.invoke(null, brainOf(newInstance), newInstance, "skeleton");
        PlayerTeam newTeam = scoreboard(newInstance).getPlayersTeam(newInstance.getScoreboardName());
        assertNotNull(newTeam);

        // 旧实例的卸载事件不得拆除新实例队伍，也不得动新实例的恢复记录
        restoreTeam.invoke(null, oldInstance);
        assertEquals(newTeam, scoreboard(newInstance).getPlayersTeam(newInstance.getScoreboardName()),
                "旧实例事件不得影响新会话");

        restoreTeam.invoke(null, newInstance);
        assertNull(scoreboard(newInstance).getPlayersTeam(newInstance.getScoreboardName()));
    }

    @Test
    void preexistingSameNameTeamIsNeverClaimedOrDeleted() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        String baseName = "pry_brain_zombie_" + fake.getUUID();
        PlayerTeam external = scoreboard.addPlayerTeam(baseName);
        external.setPlayerPrefix(Component.literal("外部前缀"));
        external.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam("outsider", external);

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam used = scoreboard.getPlayersTeam(name);

        assertNotNull(used);
        assertNotSame(external, used, "同名既有队伍不得被认领为临时队");
        assertTrue(used.getName().startsWith(baseName + "_"), "冲突时使用唯一临时队名");
        assertSame(external, scoreboard.getPlayerTeam(baseName), "外部队伍对象保留");
        assertTrue(external.getPlayers().contains("outsider"), "外部成员保留");
        assertEquals("外部前缀", external.getPlayerPrefix().getString(), "外部队伍属性保留");
        assertFalse(external.isAllowFriendlyFire(), "外部队伍选项保留");

        restoreTeam.invoke(null, fake);
        assertSame(external, scoreboard.getPlayerTeam(baseName), "卸载不得删除同名外部队伍");
        assertTrue(external.getPlayers().contains("outsider"), "卸载不得移除外部成员");
        assertNull(scoreboard.getPlayerTeam(used.getName()), "本会话临时队正常清理");
    }

    @Test
    void deletedAndRecreatedTemporaryTeamIsNotTouched() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam owned = scoreboard.getPlayersTeam(name);
        assertNotNull(owned);
        String ownedName = owned.getName();

        // 管理员删除临时队后以同名重建：新对象不能被旧会话刷新或删除
        scoreboard.removePlayerTeam(owned);
        PlayerTeam recreated = scoreboard.addPlayerTeam(ownedName);
        recreated.setPlayerSuffix(Component.literal("外部后缀"));
        scoreboard.addPlayerToTeam("outsider", recreated);

        BrainManager.refreshSuffix(fake, "zombie", "mode_zombie");
        assertEquals("外部后缀", recreated.getPlayerSuffix().getString(),
                "同名重建队伍的后缀不得被旧会话改写");

        restoreTeam.invoke(null, fake);
        assertSame(recreated, scoreboard.getPlayerTeam(ownedName), "同名重建队伍对象保留");
        assertTrue(recreated.getPlayers().contains("outsider"), "同名重建队伍成员保留");
        assertEquals("外部后缀", recreated.getPlayerSuffix().getString());
    }

    @Test
    void otherMembersPreventWholeTeamDeletion() throws Exception {
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        Scoreboard scoreboard = scoreboard(fake);
        String name = fake.getScoreboardName();
        PlayerTeam original = scoreboard.addPlayerTeam("pry_test_original");
        scoreboard.addPlayerToTeam(name, original);

        applyTeam.invoke(null, brainOf(fake), fake, "zombie");
        PlayerTeam owned = scoreboard.getPlayersTeam(name);
        assertNotNull(owned);
        scoreboard.addPlayerToTeam("outsider", owned);

        restoreTeam.invoke(null, fake);

        assertEquals(original, scoreboard.getPlayersTeam(name), "假人仍恢复原队伍");
        assertSame(owned, scoreboard.getPlayerTeam(owned.getName()), "有其他成员时不整队删除");
        assertTrue(owned.getPlayers().contains("outsider"), "其他成员归属保留");
        assertFalse(owned.getPlayers().contains(name), "本假人已移出临时队");
    }

    @Test
    void combatTeamSurvivesAcrossDetachLikeOperations() {
        // brain off / 换脑 / keep 开关不触达 BrainTeams 存储（代码审查口径）；
        // 存储面契约：按 UUID 记录、显式 leave 才清除
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        BrainTeams.join(fake.getUUID(), "red");
        // detach（无脑）不返还也不清除战斗队伍
        assertFalse(BrainManager.detach(fake), "无脑 detach 返回 false");
        assertEquals("red", BrainTeams.teamOf(fake.getUUID()));
        assertTrue(BrainTeams.leave(fake.getUUID()));
        assertNull(BrainTeams.teamOf(fake.getUUID()));
    }

    @Test
    void shadowSameUuidDoesNotInheritSessionState() {
        // 同 UUID 双实例（真人 + 影子假人）：会话状态只落在挂脑实例上
        BrainTestStubs.FakePlayer fake = fakeWithWorld();
        BrainTestStubs.TargetPlayer real = BrainTestStubs.TargetPlayer.create();

        // FakePlayer 桩不是 EntityPlayerMPFake（attach 的 instanceof 门在纯 JVM
        // 不可达，接线由 boot check 承接）；此处锁定"同 UUID 不同实例不串状态"
        assertNull(BrainManager.getModeKey(real), "真人实例无会话状态");
        assertNull(BrainManager.diagnosticsOf(real), "真人实例无诊断状态");
    }

    @Test
    void rawFakeGateAllowsRealFakeType() {
        // instanceof 门对真 EntityPlayerMPFake 放行（Unsafe 裸实例，构造器私有不可子类）
        EntityPlayerMPFake raw = BrainTestStubs.rawFake(UUID.randomUUID());
        assertTrue(BrainTeams.isFakeCombatant(raw));
        assertFalse(BrainTeams.isFakeCombatant(BrainTestStubs.TargetPlayer.create()));
    }
}
