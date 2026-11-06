package me.primaryuan.carpet.replay.common;

/**
 * 输入轨道的移动输入位标志。
 * 七类可观察移动输入：前进/后退/左移/右移/跳跃/潜行/疾跑。
 * 1.21/1.21.1 无法观察 WASD 与跳跃，对应位恒为 0，限制写入 Diagnostics。
 */
public final class InputFlags
{
    public static final int FORWARD = 1;
    public static final int BACKWARD = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int JUMP = 1 << 4;
    public static final int SNEAK = 1 << 5;
    public static final int SPRINT = 1 << 6;

    private InputFlags() {}

    public static String describe(int mask)
    {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        first = append(sb, first, mask, FORWARD, "forward");
        first = append(sb, first, mask, BACKWARD, "backward");
        first = append(sb, first, mask, LEFT, "left");
        first = append(sb, first, mask, RIGHT, "right");
        first = append(sb, first, mask, JUMP, "jump");
        first = append(sb, first, mask, SNEAK, "sneak");
        first = append(sb, first, mask, SPRINT, "sprint");
        return sb.append(']').toString();
    }

    private static boolean append(StringBuilder sb, boolean first, int mask, int bit, String name)
    {
        if ((mask & bit) == 0)
        {
            return first;
        }
        if (!first)
        {
            sb.append(',');
        }
        sb.append(name);
        return false;
    }
}
