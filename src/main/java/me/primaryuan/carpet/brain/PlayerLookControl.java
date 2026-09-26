package me.primaryuan.carpet.brain;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 玩家身上的视线控制器（零实体 token：对齐原版 {@code LookControl} 的被调用面）。
 *
 * <p>原版目标类调用 {@code this.mob.getLookControl().setLookAt(target, yRotSpeed,
 * xRotSpeed)} 请求"看着某个点"。本类把这些 AI 视线请求翻译成玩家的原生旋转字段
 * （{@code yRot}/{@code xRot}，并同步 {@code yHeadRot}/{@code yBodyRot} 保持
 * 追踪数据一致），带有限角速度（限速转头，避免"瞬移视角"的机械感）。</p>
 *
 * <p>只写旋转角度，绝不改位置/速度——旋转由行走物理天然消费，与 Carpet
 * {@code /player look} 同一条同步路径，客户端自动渲染转头。</p>
 */
public class PlayerLookControl {

    /** 每 tick 最大水平转向角（度）：原版生物 LookControl 默认约 10°，
     *  目标类通常请求 30°/tick（近战跟踪 / 远程风筝），这里取上限 30 保证跟手 */
    private static final float MAX_HORIZONTAL_TURN = 30.0F;
    private static final float MAX_VERTICAL_TURN = 30.0F;

    private final Player player;

    private Vec3 wantPos = null;        // 想要注视的世界坐标（null = 无观看请求）
    private float yRotSpeed = MAX_HORIZONTAL_TURN;
    private float xRotSpeed = MAX_VERTICAL_TURN;

    public PlayerLookControl(Player player) {
        this.player = player;
    }

    /** 注视实体（目标类最常用的入口） */
    public void setLookAt(Entity entity, float yRotSpeed, float xRotSpeed) {
        this.wantPos = entity.getEyePosition();
        this.yRotSpeed = yRotSpeed;
        this.xRotSpeed = xRotSpeed;
    }

    /** 注视坐标点 */
    public void setLookAt(double x, double y, double z, float yRotSpeed, float xRotSpeed) {
        this.wantPos = new Vec3(x, y, z);
        this.yRotSpeed = yRotSpeed;
        this.xRotSpeed = xRotSpeed;
    }

    /** 清除注视请求：goal 停止后不清会继续朝旧坐标转头（如盯住敌人死亡的位置） */
    public void clearLook() {
        this.wantPos = null;
    }

    /** 每 tick 推进：把当前位置按有限角速度转向目标点 */
    public void tick() {
        if (this.wantPos == null) {
            return;
        }
        double dx = this.wantPos.x - this.player.getX();
        double dy = this.wantPos.y - this.player.getEyeY();
        double dz = this.wantPos.z - this.player.getZ();
        double horizontalSq = dx * dx + dz * dz;
        if (horizontalSq < 1.0E-12) {
            return; // 目标点与自己重叠，无转向意义
        }
        // MC yaw=0 朝 +Z、随逆时针增大：atan2(dz, dx)·180/π - 90 为目标 yaw
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        // pitch 为正朝下，与视线向量 y 分量符号相反
        float targetPitch = (float) (-(Mth.atan2(dy, Math.sqrt(horizontalSq)) * (180.0 / Math.PI)));
        float deltaYaw = Mth.wrapDegrees(targetYaw - this.player.getYRot());
        deltaYaw = Mth.clamp(deltaYaw, -this.yRotSpeed, this.yRotSpeed);
        float newYaw = this.player.getYRot() + deltaYaw;
        float newPitch = this.player.getXRot()
                + Mth.clamp(Mth.wrapDegrees(targetPitch - this.player.getXRot()), -this.xRotSpeed, this.xRotSpeed);
        this.player.setYRot(newYaw);
        this.player.setXRot(Mth.clamp(newPitch, -90.0F, 90.0F));
        // 玩家无独立身体/头部轴，同步头部/身体旋转保证追踪包内四项旋转一致
        this.player.setYHeadRot(newYaw);
        this.player.setYBodyRot(newYaw);
    }
}