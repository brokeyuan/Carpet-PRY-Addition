package me.primaryuan.carpet.replay.common;

/** 视觉帧状态位 */
public final class VisualFlags
{
    public static final byte SNEAKING = 1;
    public static final byte SPRINTING = 1 << 1;
    public static final byte SWIMMING = 1 << 2;
    public static final byte FALL_FLYING = 1 << 3;
    public static final byte RIDING = 1 << 4;
    public static final byte ON_GROUND = 1 << 5;
    public static final byte USING_ITEM = 1 << 6;

    private VisualFlags() {}
}
