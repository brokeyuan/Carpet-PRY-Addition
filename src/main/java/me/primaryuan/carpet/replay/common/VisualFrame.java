package me.primaryuan.carpet.replay.common;

/**
 * 单 Tick 视觉快照：全部来自录制者实际实体状态。
 */
public final class VisualFrame
{
    public double x;
    public double y;
    public double z;
    public float vx;
    public float vy;
    public float vz;
    /** 身体朝向（绝对 yaw） */
    public float bodyYaw;
    /** 头部朝向（绝对 yaw） */
    public float headYaw;
    /** 视角 pitch */
    public float pitch;
    /** 状态位：见 VisualFlags */
    public byte flags;
    /** 当前主手快捷栏槽位 0~8，-1 未知 */
    public int slot = -1;

    public VisualFrame copy()
    {
        VisualFrame f = new VisualFrame();
        f.x = x;
        f.y = y;
        f.z = z;
        f.vx = vx;
        f.vy = vy;
        f.vz = vz;
        f.bodyYaw = bodyYaw;
        f.headYaw = headYaw;
        f.pitch = pitch;
        f.flags = flags;
        f.slot = slot;
        return f;
    }
}
