package me.primaryuan.carpet.brain;

import java.util.Set;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 12105
import net.minecraft.world.entity.animal.wolf.Wolf;
//#else
//$$ import net.minecraft.world.entity.animal.Wolf;
//#endif
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//$$ import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 凋灵骷髅模式脑（装配器）：26.3 字节码核实的原版装配。
 *
 * <p>目标链 = 反击（pri 1）→ 玩家（pri 2，mustSee）→ 猪灵类（pri 3：真实
 * {@code AbstractPiglin} 实体 + piglin/piglinbrute 脑假人，对应原版
 * WitherSkeleton 的 AbstractPiglin 目标）→ 幼年海龟（pri 3，骷髅基座同款）。
 * 行为链 = 弓/近战同位 pri 4（原版 {@code reassessWeaponGoal} 双武器装配的
 * 持械判定等价：持弩/弓时先评远程、持剑近战兜底）→ 规避狼（pri 3，骷髅基座：
 * 被狼追咬会逃跑）→ 漫游 pri 5。</p>
 *
 * <p>不做：攻击附带凋零效果（原版 {@code doHurtTarget} 的身体附伤，红线——
 * 假人攻击管线锁定玩家原生 {@code Player.attack}，手动补状态效果 = 凭空造物）。
 * 凋灵骷髅不惧日光（原版免疫），故无骷髅基座的避日 Goal。</p>
 */
public class WitherSkeletonBrain extends PlayerBrainController {

    /** 猪灵类假人的模式名（原版按 AbstractPiglin 类匹配的脑间等价集合） */
    private static final Set<String> PIGLIN_FAKE_MODES = Set.of("piglin", "piglinbrute");

    public WitherSkeletonBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "witherskeleton";
    }

    @Override
    protected void assemble() {
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // pri 2 玩家
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 20.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // pri 3 猪灵类：真实猪灵实体
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractPiglin.class, 16.0, 10, true,
                a -> a.isAlive()));
        // pri 3 猪灵类：猪灵/蛮兵脑假人（原版按 AbstractPiglin 类匹配的脑间等价）。
        // getModeKey 对无脑假人返回 null，须先判空再查集合（Set.of 不接受 null 查询）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> {
                    if (!(p instanceof ServerPlayer sp) || sp == this.player || !sp.isAlive()
                            || sp.isSpectator() || sp.isCreative()) {
                        return false;
                    }
                    String mode = BrainManager.getModeKey(sp);
                    return mode != null && PIGLIN_FAKE_MODES.contains(mode);
                }));
        // pri 3 铁傀儡（骷髅基座目标）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 20.0, 10, true,
                g -> g.isAlive()));
        // pri 3 幼年海龟（骷髅基座目标）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 20.0, 10, true,
                t -> t.isBaby()));
        // 行为：远程（pri 4）与近战兜底（pri 5）——原版 reassessWeaponGoal 双武器
        // 同位 pri 4 且同一时刻只存在一个；移植版冷却间隔层下同位会永久互斥
        // （近战 4 tick 早于远程 20 tick 抢到候选），故近战降一档实现等价分流：
        // 持弓远程抢占、无弓近战兜底
        this.goalSelector.addGoal(4, new PlayerRangedAttackGoal(this.prowler, 1.0, 20, 8.0F));
        this.goalSelector.addGoal(5, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // pri 3 规避狼（骷髅基座：AvoidEntityGoal<Wolf> 6 格）
        this.goalSelector.addGoal(3, new PlayerAvoidEntityGoal<>(
                this.prowler, Wolf.class, 6.0F, 1.0, 1.2));
        // 漫游（原版 WaterAvoidingRandomStrollGoal pri 5）
        this.goalSelector.addGoal(5, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
