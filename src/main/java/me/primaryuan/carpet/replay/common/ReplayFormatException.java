package me.primaryuan.carpet.replay.common;

/** 录像格式或内容非法 */
public class ReplayFormatException extends Exception
{
    public ReplayFormatException(String message)
    {
        super(message);
    }

    public ReplayFormatException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
