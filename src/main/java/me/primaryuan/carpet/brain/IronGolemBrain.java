package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;

/**
 * 铁傀儡模式脑（装配器）：守护与清怪。
 *
 * <p>目标链（26.3 字节码核实）：DefendVillage（pri 1，村庄上下文 → 披露）→
 * 反击（pri 2，{@code HurtByTargetGoal}）→ 玩家（pri 3，mustSee，愤怒制——
 * {@code isAngryAt}，无村庄上下文时仅报复面生效）→ 敌对生物（pri 3，
 * {@code Monster}，interval 5、mustSee=false 透墙、选择器 = Enemy 且非苦力怕）。
 * 敌对假人口径 = {@link BrainManager#isHostileFake}（原版 {@code Enemy} 标记
 * 接口的 14 种脑间等价）。范围 = FOLLOW_RANGE 16（基座，未覆写）。</p>
 */
public class IronGolemBrain extends PlayerBrainController {

    public IronGolemBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "irongolem";
    }

    @Override
    protected void assemble() {
        // pri 2 反击（原版 HurtByTargetGoal pri 2）
        this.targetSelector.addGoal(2, new PlayerHurtByTargetGoal(this.prowler, null));
        // pri 3 敌对玩家（原版愤怒制的报复面：攻击过本假人的敌对假人；mustSee；
        // 范围 = 原版 FOLLOW_RANGE 16（26.3 字节码：Mob.createMobAttributes 基座 16，该生物未覆写））
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, ServerPlayer.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && BrainManager.isHostileFake(p)));
        // pri 3 清怪（原版 NearestAttackableTargetGoal<Mob>：interval 5、
        // mustSee=false 透墙、选择器 = Enemy 且非苦力怕）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Monster.class, 16.0, 5, false,
                m -> m.isAlive() && !(m instanceof Creeper)));
        // 行为：近战（原版 MeleeAttackGoal pri 1, (1.0, true)）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 空闲漫游（原版 GolemRandomStrollInVillage pri 4 无村庄引擎——普通漫步替代）
        this.goalSelector.addGoal(4, new PlayerRandomStrollGoal(this.prowler, 0.6));
        // 空闲看人/随机张望（原版 pri 7/8，看人 6 格）
        this.goalSelector.addGoal(7, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new PlayerRandomLookAroundGoal(this.prowler));
    }
}