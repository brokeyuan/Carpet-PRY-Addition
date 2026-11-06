package me.primaryuan.carpet.replay.script;

/** 脚本提取失败（无参与者/无有效输入/无片段），消息可直接显示 */
public final class ReplayScriptException extends Exception
{
    public ReplayScriptException(String message)
    {
        super(message);
    }
}
