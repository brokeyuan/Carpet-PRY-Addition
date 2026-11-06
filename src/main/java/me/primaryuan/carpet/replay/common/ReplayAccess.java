package me.primaryuan.carpet.replay.common;

/** 录像访问范围：公开=所有具备回放功能权限的玩家；私有=创作者与管理员 */
public enum ReplayAccess
{
    PUBLIC,
    PRIVATE;

    public static ReplayAccess byOrdinal(int ordinal)
    {
        ReplayAccess[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PRIVATE;
    }
}
