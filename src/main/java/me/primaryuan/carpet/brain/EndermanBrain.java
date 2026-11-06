package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerStareAggroGoal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 末影人模式脑（装配器）：被凝视激怒 + 疾跑扑击。
 *
 * <p>装配（26.3 字节码：{@code Enderman.registerGoals}）：</p>
 * <ul>
 *   <li>targetSelector —— 凝视激怒（pri 1，原版 {@code EndermanLookForPlayerGoal}：
 *       谁盯着看就锁定谁）+ 反击（pri 2，HurtBy）+ 末影螨（pri 3，mustSee）；</li>
 *   <li>goalSelector —— 近战（pri 2，原版 {@code MeleeAttackGoal(1.0, false)}）+
 *       看人/张望（pri 8，8 格）。</li>
 * </ul>
 *
 * <p>与原版末影人的差异（红线/引擎，docs 披露）：不做传送与搬方块、白天闪烁与随机消失（customServerAiStep 的光照条件随机 teleport）
 * （EndermanLeaveBlock/TakeBlock = 方块交互 + 坐标瞬移），
 * 激怒后表现为"盯着你冲脸"；漫游不装配（原版漫游概率 0，从不随机走动）。</p>
 */
public class EndermanBrain extends PlayerBrainController {

    public EndermanBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "enderman";
    }

    @Override
    protected void assemble() {
        // 被谁盯上就打谁（凝视算法 = 原版 isStaredAt 的逐行移植；pri 1 原版同位）
        this.targetSelector.addGoal(1, new PlayerStareAggroGoal(this.prowler));
        // 被打反击（原版 HurtByTargetGoal pri 2）
        this.targetSelector.addGoal(2, new PlayerHurtByTargetGoal(this.prowler, null));
        // pri 3 末影螨（原版 NearestAttackableTargetGoal<Endermite>(mustSee)；
        // 范围 64 = getFollowDistance（26.3 字节码：Enderman FOLLOW_RANGE 显式
        // 覆写 64，目标 Goal 用 getFollowDistance 作半径；凝视 Goal 的
        // startAggroTargetConditions 亦为 range(getFollowDistance)=64））
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, net.minecraft.world.entity.monster.Endermite.class, 64.0, 10, true,
                e -> e.isAlive()));
        // 近战扑击（原版 MeleeAttackGoal(1.0, false) pri 2——失视线收手）
        this.goalSelector.addGoal(2, new PlayerMeleeAttackGoal(this.prowler, 1.0, false));
        // 空闲看人/随机张望（原版 pri 8，8 格）；漫游不装配（原版概率 0）
        this.goalSelector.addGoal(8, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new PlayerRandomLookAroundGoal(this.prowler));
    }
}
