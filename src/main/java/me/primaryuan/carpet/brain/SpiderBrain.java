package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLeapAtTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;

/**
 * 蜘蛛模式脑（装配器）：26.3 字节码核实的原版装配——白天中立/夜间敌对。
 *
 * <p>与僵尸模式的差别：目标选择谓词里加入 {@code !level().isDay()} 门控——
 * 原版 {@code SpiderTargetGoal.canUse} 按光照判定（darkness），移植版以
 * {@code !levelIsBrightOutside()} 近似（语义：天光下不索敌）并披露差异
 * （原版受方块光照影响，移植版只看天空光）。白天无攻击目标（转为漫步），
 * 夜晚锁定玩家；目标随昼夜切换自然激活/停用。</p>
 *
 * <p>其余装配（26.3）：规避犰狳（pri 2，6 格）→ 扑击 LeapAtTarget pri 3 已实现→ 近战（pri 4，{@code SpiderAttackGoal(1.25, true)}）→
 * 漫游（pri 5，0.8）→ 看人/张望（pri 6，8 格）；目标链 = 反击（pri 1，
 * 含光照下被打反击）→ 玩家（pri 2，光照门）→ 铁傀儡（pri 3，光照门）。</p>
 */
public class SpiderBrain extends PlayerBrainController {

    public SpiderBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "spider";
    }

    @Override
    protected void assemble() {
        // 被打反击：蜘蛛被打随时还手（不受昼夜门控限制，与原版 HurtByTargetGoal 一致）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 只在夜间（外界不明亮）把玩家当作目标；白天该目标 canUse 恒 false。
        // 范围 = 原版 FOLLOW_RANGE 16（26.3 字节码：Mob.createMobAttributes 基座 16，该生物未覆写）（含铁傀儡目标同款）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !this.prowler.levelIsBrightOutside()));
        // pri 3 铁傀儡（原版 SpiderTargetGoal<IronGolem>，同光照门）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 16.0, 10, true,
                g -> g.isAlive() && !this.prowler.levelIsBrightOutside()));
        // pri 2 规避犰狳（原版 AvoidEntityGoal<Armadillo>(6, 1.0, 1.2)；
        // 犰狳 1.20.5+，包路径全版本一致）
        this.goalSelector.addGoal(2, new PlayerAvoidEntityGoal<>(
                this.prowler, net.minecraft.world.entity.animal.armadillo.Armadillo.class, 6.0F, 1.0, 1.2));
        // 扑击（原版 LeapAtTargetGoal(0.4F) pri 3：距目标 2~4 格 1/5 概率扑跳）
        this.goalSelector.addGoal(3, new PlayerLeapAtTargetGoal(this.prowler));
        // 夜间追击（原版 SpiderAttackGoal(1.25, true)——蜘蛛近战速度 1.25）
        this.goalSelector.addGoal(4, new PlayerMeleeAttackGoal(this.prowler, 1.25, true));
        // 漫游（原版 WaterAvoidingRandomStrollGoal pri 5，0.8）
        this.goalSelector.addGoal(5, new PlayerRandomStrollGoal(this.prowler, 0.8));
        // 空闲看人/随机张望（原版 pri 6，8 格）
        this.goalSelector.addGoal(6, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new PlayerRandomLookAroundGoal(this.prowler));
    }
}