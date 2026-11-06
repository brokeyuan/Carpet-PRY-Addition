package me.primaryuan.carpet.replay.common;

import java.util.regex.Pattern;

/** 录像名称/稳定标识合法性：防目录穿越 */
public final class ReplayNames
{
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9\\-_\\u4e00-\\u9fff]{1," + ReplayLimits.MAX_NAME_LENGTH + "}");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9\\-_]{1,64}");

    private ReplayNames() {}

    /** 合法返回 true：仅中英文、数字、下划线、连字符，1~32 字符 */
    public static boolean isValid(String name)
    {
        return name != null && SAFE.matcher(name).matches() && !name.startsWith(".");
    }

    /** 稳定 ID（UUID 字符串）：只允许 ASCII 字母、数字、连字符、下划线 */
    public static boolean isValidId(String id)
    {
        return id != null && SAFE_ID.matcher(id).matches() && !id.startsWith(".");
    }
}
