package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;

/**
 * 移植版史莱姆/岩浆怪空闲跳行（← 原版 {@code CubeMobRandomDirectionGoal} +
 * {@code CubeMobMoveControl} 跳跃节奏，26.3 字节码逐条对齐）。
 *
 * <p><b>生命周期（字节码对照）</b>：原版 RandomDirection 无 canContinueToUse
 * 覆写（默认复用 canUse），Mob 在地面时常驻运行；它的 tick 只做"方向重掷"，
 * 跳跃节奏在 MoveControl——每 tick 处理 MOVE_TO（setZza 后置 WAIT），WAIT 分支
 * 里在地面才递减 {@code jumpDelay}（getJumpDelay()=nextInt(20)+10，无目标不
 * ÷3），归零起跳。<b>jumpDelay 与 chosenDegrees 存活于控制层，跨落地周期</b>：
 * 起跳后离地，MoveControl 的 WAIT 分支停跳等待；落地后从剩余等待继续消耗，
 * 不立即重跳。</p>
 *
 * <p><b>移植版映射（合并实现、双更新频率）</b>：本 Goal 覆写
 * requiresUpdateEveryTick=true（对齐 MoveControl 每游戏 tick 的跳跃控制），
 * tick 内两套时钟各自按原版节奏推进：</p>
 * <ul>
 *   <li>canContinueToUse 复用 canUse（原版同款）——离地即暂停推进，落地后
 *       同一 Goal 实例继续（选择器不重启，状态天然跨周期）；</li>
 *   <li>start 只做"挂脑首拍"初始化（方向立即重掷一次），<b>不清空运行期已有
 *       的等待/方向</b>——stop 后重启接续剩余等待（原版字段生命周期等价）；</li>
 *   <li>方向重掷：RandomDecision 节奏——tick 内用 gameTickParity 奇偶自减
 *       （每 2 游戏 tick 推进 1 次，等效原版非 rUE Goal 的 tick 频率），到期重掷；</li>
 *   <li>跳跃等待：MoveControl 节奏——每游戏 tick 递减 1（rUE），归零起跳并
 *       重置等待；</li>
 *   <li>有目标切换：canUse/canContinueToUse 见空目标即 false → Attack Goal
 *       接管；回到空闲后本 Goal 从剩余状态继续（原版 RandomDirection 的
 *       nextRandomizeTime 同样不因目标出现而清零）。</li>
 * </ul>
 *
 * <p>液体/漂浮门：玩家身体涉水即离地（isInWater 语义由 onGround 天然涵盖），
 * 漂浮效果无对应身体状态——披露差异。</p>
 */
public class PlayerSlimeWanderGoal extends PlayerGoal {

    /** 方向重掷基准（tick）：原版 adjustedTickDelay(40 + nextInt(60))，非 rUE 折半 */
    private static final int RANDOMIZE_BASE = 40;
    private static final int RANDOMIZE_SPAN = 60;
    /** 起跳等待基准（游戏 tick）：原版 getJumpDelay = nextInt(20)+10 */
    private static final int JUMP_DELAY_BASE = 10;
    private static final int JUMP_DELAY_SPAN = 20;

    private final PryMob mob;
    /** 方向重掷倒计时（评估次数推进，非 rUE——原版同字段同节奏） */
    private int nextRandomizeTime;
    /** 起跳等待（游戏 tick 语义，rUE 节奏递减；跨落地周期保留） */
    private int jumpDelay;
    /** 是否已做过首拍初始化（挂脑后第一拍立即掷方向，之后 stop/start 不再清状态） */
    private boolean initialized;
    private float chosenDegrees;

    public PlayerSlimeWanderGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    @Override
    public boolean canUse() {
        // 原版门：无目标（有目标时走 Attack Goal）、在地面
        return this.mob.getTarget() == null && this.mob.asPlayer().onGround();
    }

    @Override
    public boolean canContinueToUse() {
        // 原版 RandomDirection 无覆写（默认复用 canUse）：空目标/离地暂停推进，
        // 同一实例存活，状态跨周期（本修法的核心——不再恒 false 重启清零）
        return this.canUse();
    }

    @Override
    public void start() {
        // 仅挂脑首拍初始化（对齐原版"字段随实体创建存在"）；运行期 stop→start
        // 不清零：剩余 jumpDelay/方向重掷时钟继续消耗
        if (!this.initialized) {
            this.initialized = true;
            this.nextRandomizeTime = 0; // 首拍立即掷方向
            this.jumpDelay = 0;
        }
    }

    /**
     * 方向重掷时钟的奇偶基准（rUE Goal 内模拟原版非 rUE 的"每 2 tick 推进 1 次"）。
     * 初始值任意：首拍 -- 后 ≤0 即触发重掷，之后按推进次数奇偶交替。
     */
    private boolean randomizeParity;

    @Override
    public boolean requiresUpdateEveryTick() {
        // 原版跳跃控制在 MoveControl（每游戏 tick）；方向重掷在非 rUE Goal。
        // 合并实现取 rUE（保跳变等待的正确频率），方向时钟在 tick 内自行分频
        return true;
    }

    @Override
    public void tick() {
        // 0) 原版 MoveControl 语义：onGround 为跳整个跳跃段的前置门（字节码
        //    offset 106-109: ifeq 291）——腾空期间等待挂起不递减，落地续扣。
        //    rUE 下完整 tick 起跳后紧随的轻量 tick 仍会进入本方法，必须在此
        //    门控，否则腾空 tick 继续扣等待。
        //    目标出现：canContinueToUse（=canUse）在下一完整 tick 清理时即 false，
        //    选择器停本 Goal——但同拍/轻量 tick 可能仍推进一次，输出前再查。
        if (!this.mob.asPlayer().onGround() || this.mob.getTarget() != null) {
            return;
        }
        // 1) 方向重掷时钟（原版 RandomDirection.tick，非 rUE 节奏：每 2 游戏 tick
        //    推进 1 次——rUE Goal 内用奇偶门模拟，重掷节奏不因 rUE 加倍）
        this.randomizeParity = !this.randomizeParity;
        if (this.randomizeParity) {
            // 每 2 游戏 tick 执行一次的"评估"（对齐原版非 rUE tick）
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = reducedTickDelay(
                        RANDOMIZE_BASE + this.mob.getRandom().nextInt(RANDOMIZE_SPAN));
                this.chosenDegrees = this.mob.getRandom().nextFloat() * (float) Math.PI * 2.0F;
            }
        }
        // 2) 跳跃等待（原版 MoveControl WAIT 分支：onGround 时每游戏 tick 递减）
        if (this.jumpDelay > 0) {
            this.jumpDelay--;
            return;
        }
        // 3) 起跳 + 朝随机方向的 MOVE_TO 短脉冲（原版 setDirection + jumpControl）
        double dx = Math.cos(this.chosenDegrees);
        double dz = Math.sin(this.chosenDegrees);
        this.mob.getMoveControl().setWantedPosition(
                this.mob.getX() + dx * 2.0, this.mob.getY(), this.mob.getZ() + dz * 2.0, 1.0);
        this.mob.asPlayer().jumpFromGround();
        this.jumpDelay = JUMP_DELAY_BASE + this.mob.getRandom().nextInt(JUMP_DELAY_SPAN);
    }

    /** 剩余跳跃等待（测试观测缝） */
    int jumpDelayForTest() {
        return this.jumpDelay;
    }

    /** 当前朝向角（测试观测缝） */
    float chosenDegreesForTest() {
        return this.chosenDegrees;
    }
}
