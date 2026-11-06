package me.primaryuan.carpet.replay.common;

/** 录像元数据（格式 v2：多人参与者，无旧版适配器） */
public final class ReplayMetadata
{
    /** 文件格式版本；功能未发布，v1 单人格式不再兼容 */
    public static final int FORMAT_VERSION = 2;

    public int formatVersion = FORMAT_VERSION;
    /** 稳定录像标识（任务 UUID），文件名与按钮身份均使用它 */
    public String recordingId;
    public String ownerUuid;
    public String ownerName;
    public String name;
    public long createdAtEpochMillis;
    /** 半开区间 [0, durationTicks) */
    public int durationTicks;
    public ReplayAccess access = ReplayAccess.PUBLIC;
    public String initialDimension;
    public double initialX;
    public double initialY;
    public double initialZ;
    public float initialYaw;
    public float initialPitch;
    /** 是否由崩溃检查点恢复：内容只到最近可靠保存点，不是完整录制 */
    public boolean recoveredFromCrash;

    public void copyFrom(ReplayMetadata other)
    {
        formatVersion = other.formatVersion;
        recordingId = other.recordingId;
        ownerUuid = other.ownerUuid;
        ownerName = other.ownerName;
        name = other.name;
        createdAtEpochMillis = other.createdAtEpochMillis;
        durationTicks = other.durationTicks;
        access = other.access;
        initialDimension = other.initialDimension;
        initialX = other.initialX;
        initialY = other.initialY;
        initialZ = other.initialZ;
        initialYaw = other.initialYaw;
        initialPitch = other.initialPitch;
        recoveredFromCrash = other.recoveredFromCrash;
    }
}
