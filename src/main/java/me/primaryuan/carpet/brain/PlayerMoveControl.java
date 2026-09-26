package me.primaryuan.carpet.brain;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家身上的移动控制器 —— <b>防鬼畜核心</b>。
 *
 * <p>对齐原版 {@code MoveControl} 的被调用面（{@code setWantedPosition} /
 * {@code strafe}）：生物 AI（寻路器、风筝目标）把"想去的坐标"喂给它，
 * 由它负责把生物式移动指令变成玩家身体的真实运动。</p>
 *
 * <p><b>极其关键的适配逻辑（为什么不会鬼畜）</b>：</p>
 * <ul>
 *   <li>原版 {@code MoveControl} 拿到移动指令后直接把 {@code setDeltaMovement} /
 *       修改 {@code mob} 坐标来推进——那是生物的运动模型；</li>
 *   <li>玩家实体没有这套模型：玩家的运动 100% 由
 *       {@code LivingEntity.tickMovement() → travel()} 消费
 *       <b>{@code zza}（前进输入）、{@code xxa}（横向输入）与 yaw</b> 计算。
 *       若 AI 绕过 travel 直接改速度/坐标，就会与玩家物理（摩擦/疾跑/转向）打架，
 *       表现就是"鬼畜抽搐"——来来回回瞬间位移；</li>
 *   <li>所以这里 <b>绝不 setDeltaMovement、绝不改坐标</b>：MOVE_TO 时把目标方向
 *       折算成 {@code zza = 速度系数、xxa = 0} 并向目标 yaw 限速转向，
 *       然后交给 travel 走原生行走物理（Carpet 假人无客户端输入包，
 *       服务端写 zza/xxa 对假人就是唯一的移动来源，与 {@code /player move} 同机制）；</li>
 *   <li>WAIT 状态清零 zza/xxa 防惯性滑行；跳跃需求转成原生
 *       {@code jumpFromGround()}（含台阶/跳上）。</li>
 * </ul>
 */
public class PlayerMoveControl {

    /** 每 tick 最大水平转向角（度）：限速转头，模拟生物 MoveControl 的转向节奏 */
    private static final float MAX_TURN_PER_TICK = 30.0F;

    /** 操作状态机（对齐原版 MoveControl.Operation） */
    public enum Operation { WAIT, MOVE_TO, STRAFE }

    private Operation operation = Operation.WAIT;
    private double wantedX;
    private double wantedY;
    private double wantedZ;
    private double speedModifier = 0.0;     // 移动速度系数（0.5 慢走 / 1.0 正常 / 1.2 疾驰）
    private float strafeForward = 0.0F;     // 风筝：前后分量
    private float strafeRight = 0.0F;       // 风筝：左右分量

    private final Player player;

    public PlayerMoveControl(Player player) {
        this.player = player;
    }

    /** 生物式"前往坐标"指令（寻路器每推进一个节点调用一次） */
    public void setWantedPosition(double x, double y, double z, double speed) {
        this.wantedX = x;
        this.wantedY = y;
        this.wantedZ = z;
        this.speedModifier = speed;
        this.operation = Operation.MOVE_TO;
    }

    /** 风筝走位（骷髅近身/村民逃跑）：forward ±1 前后，right ±1 左右 */
    public void strafe(float forward, float right) {
        this.strafeForward = forward;
        this.strafeRight = right;
        this.operation = Operation.STRAFE;
    }

    /** 强制停机（寻路结束/目标丢失时调用） */
    public void setStop() {
        this.operation = Operation.WAIT;
    }

    /** 是否仍持有未完成的移动指令 */
    public boolean hasWanted() {
        return this.operation != Operation.WAIT;
    }

    /** 每 tick 推进：把当前操作翻译成玩家原生移动输入 */
    public void tick() {
        switch (this.operation) {
            case MOVE_TO -> this.tickMoveTo();
            case STRAFE -> this.tickStrafe();
            case WAIT -> {
                // 原地待命：清空全部移动输入，防止上次指令残留下的惯性"滑行"
                this.player.zza = 0.0F;
                this.player.xxa = 0.0F;
                this.player.setSprinting(false);
            }
        }
    }

    /** MOVE_TO：转向目标 + 写 zza 前进（核心的"移动转输入"翻译） */
    private void tickMoveTo() {
        double dx = this.wantedX - this.player.getX();
        double dz = this.wantedZ - this.player.getZ();
        double horizontalSq = dx * dx + dz * dz;

        // 到达判定：横向贴近（0.6 格）且高度差 < 1 → 视为到达，转 WAIT 停住
        if (horizontalSq < 0.36 && Math.abs(this.wantedY - this.player.getY()) < 1.0) {
            this.operation = Operation.WAIT;
            return;
        }

        // 1) 转向：以有限角速度把朝向拧到"玩家位置 → 目标位置"的方向
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float deltaYaw = Mth.wrapDegrees(targetYaw - this.player.getYRot());
        deltaYaw = Mth.clamp(deltaYaw, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        float newYaw = this.player.getYRot() + deltaYaw;
        this.player.setYRot(newYaw);
        this.player.setYHeadRot(newYaw);   // 同步头部/身体，追踪包内旋转数据一致
        this.player.setYBodyRot(newYaw);

        // 2) ★★★ 防鬼畜核心：不 setDeltaMovement / 不改坐标，
        //    只写玩家原生 zza/xxa（模拟按 W 键），位移交给本 tick 稍后的
        //    travel() 走原版行走物理（摩擦/疾跑/碰撞全原生）★★★
        this.player.zza = (float) Mth.clamp(this.speedModifier, 0.1, 1.0);
        this.player.xxa = 0.0F;
        // 高移速系数 → 原生疾跑；降速档（回避 1.35→0.9、跟随→0.6）必须显式复位，
        // 否则假人保持疾跑状态/动画直到本条移动指令结束（原实现只置不复位）
        this.player.setSprinting(this.speedModifier >= 1.2);

        // 3) 跳跃：目标点明显高于脚下（台阶/田埂）且横向贴近 → 原生跳跃；
        //    撞墙兜底：水平碰撞且在地面 → 也跳一下（原版 MoveControl 的撞墙跳）
        if (this.wantedY - this.player.getY() > 0.5
                && horizontalSq < 1.0 && this.player.onGround()) {
            this.player.jumpFromGround();
        } else if (this.player.horizontalCollision && this.player.onGround()) {
            this.player.jumpFromGround();
        }
    }

    /** STRAFE：风筝走位直接折算 zza/xxa（倒退/侧移交给原版行走物理计算净位移） */
    private void tickStrafe() {
        this.player.zza = this.strafeForward;
        this.player.xxa = this.strafeRight;
        this.player.setSprinting(false);
    }
}