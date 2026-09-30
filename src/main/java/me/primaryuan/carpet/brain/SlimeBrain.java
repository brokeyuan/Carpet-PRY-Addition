package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerSlimeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerSlimeWanderGoal;
//#if MC >= 12111
import net.minecraft.world.entity.animal.golem.IronGolem;
//#else
//$$ import net.minecraft.world.entity.animal.IronGolem;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 史莱姆/岩浆怪模式脑（装配器）：26.3 字节码核实的原版装配
 * （Slime.addTargetingGoals：玩家 pri 1 / 铁傀儡 pri 3；攻击 Goal pri 2）。
 *
 * <p>两种方块怪行为同源（岩浆怪仅属性差异），共用本脑。特征 = 跳行移动：
 * idle 永不停跳（{@code PlayerSlimeWanderGoal}），锁定目标后直线跳行追击
 * （{@code PlayerSlimeAttackGoal}，原版史莱姆无寻路），贴近按玩家原生攻击
 * 管线挥砍。目标链对齐原版：只有玩家与铁傀儡，无 HurtBy 反击 Goal（原版
 * 史莱姆不挂）——被打不记仇，只因"你是玩家"而持续敌对。</p>
 *
 * <p>不做：死亡分裂（分裂出子史莱姆 = 凭空造物实体，红线）、
 * 按体积定伤害（伤害走玩家攻击管线取主手）。</p>
 */
public class SlimeBrain extends PlayerBrainController {

    /** 模式标识（slime / magmacube，由子类或工厂指定） */
    private final String key;

    public SlimeBrain(ServerPlayer player, String key) {
        super(player);
        this.key = key;
    }

    @Override
    public String modeKey() {
        return this.key;
    }

    @Override
    protected void assemble() {
        // pri 1 玩家（mustSee）
        this.targetSelector.addGoal(1, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()));
        // pri 3 铁傀儡
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 16.0, 10, true,
                g -> g.isAlive()));
        // 行为：跳行追击（pri 2）+ idle 连续起跳（pri 3，无目标时）
        this.goalSelector.addGoal(2, new PlayerSlimeAttackGoal(this.prowler));
        this.goalSelector.addGoal(3, new PlayerSlimeWanderGoal(this.prowler));
    }
}
