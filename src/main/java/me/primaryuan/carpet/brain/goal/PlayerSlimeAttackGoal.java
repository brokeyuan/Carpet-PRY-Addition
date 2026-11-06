package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.world.entity.LivingEntity;

/**
 * 移植版史莱姆/岩浆怪跳行追击（← 原版 {@code CubeMobAttackGoal} +
 * {@code CubeMobMoveControl} 的节奏，26.3 字节码逐条对齐）。
 *
 * <p>与标准近战的差别：不做 A* 寻路——原版史莱姆本就无寻路、直线跳行。
 * 原版职责切分：CubeMobAttackGoal（rUE=true，LOOK 旗标）只做"看向目标 +
 * 设置移动方向/激怒态"，攻击伤害在 push/playerTouch <b>碰撞</b>时由实体侧
 * {@code canDealDamage()}（!isTiny && isEffectiveAi）结算；跳跃节奏在
 * MoveControl——落地后 {@code jumpDelay=getJumpDelay()}（{@code
 * nextInt(20)+10}，rUE 每 tick 递减），激怒（追击目标存在）时 ÷3，归零起跳。</p>
 *
 * <p>移植版职责：本 Goal（rUE）每游戏刻看目标并驱动"落地-等待-起跳"节奏
 * （等待计数与原版 jumpDelay 同款：nextInt(20)+10、有目标时 ÷3——固定 12 的
 * 旧实现与字节码不符已废）；贴近时的伤害走玩家原生攻击管线（{@code
 * doHurtTarget}=玩家 attack），节奏对齐原版"碰撞结算"：落地蓄力结束即起跳、
 * 起跳瞬间若在攻击距离内出手（原版为接触结算，本移植以起跳/贴身两检近似，
 * 披露差异：假人无原版体积挤压的 push 流程）。</p>
 */
public class PlayerSlimeAttackGoal extends PlayerGoal {

    /** 跳跃等待基准（游戏 tick）：原版 getJumpDelay = nextInt(20)+10 */
    private static final int JUMP_DELAY_BASE = 10;
    private static final int JUMP_DELAY_SPAN = 20;

    private final PryMob mob;
    /** 落地后的跳跃等待（游戏 tick，原版 jumpDelay 语义，rUE 每 tick 递减） */
    private int jumpDelay;

    public PlayerSlimeAttackGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(1);
    }

    /** 原版 CubeMobAttackGoal 覆写 requiresUpdateEveryTick=true（26.3 字节码） */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        this.mob.getMoveControl().setStop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        // 原版 Goal.tick：看向目标（10/10 限速）+ setDirection（含激怒态）
        this.mob.getLookControl().setLookAt(target, 10.0F, 10.0F);
        if (this.mob.asPlayer().onGround()) {
            if (this.jumpDelay > 0) {
                this.jumpDelay--; // 原版 MoveControl.tick：落地后 jumpDelay 每 tick 递减
                this.mob.getMoveControl().setStop(); // 落地小歇：清输入防滑行
                return;
            }
            // 起跳瞬间贴身即出手（原版伤害在碰撞侧结算，见类注披露）；
            // 出手与起跳同一节拍，无额外冷却层
            double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            if (distSq <= this.mob.attackReachSq()) {
                this.mob.doHurtTarget(target);
            }
            // 原版 MoveControl：jumpDelay = getJumpDelay()；有目标（激怒态）时 ÷3
            int delay = JUMP_DELAY_BASE + this.mob.getRandom().nextInt(JUMP_DELAY_SPAN);
            this.jumpDelay = Math.max(1, delay / 3);
            // 起跳 + 朝目标的 MOVE_TO 短脉冲（滞空期间持续朝目标前进）
            this.mob.getMoveControl().setWantedPosition(
                    target.getX(), target.getY(), target.getZ(), 1.0);
            this.mob.asPlayer().jumpFromGround();
        }
    }
}
