package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/** 诊断信息：录制精度限制与降级说明，随录像持久化 */
public final class ReplayDiagnostics
{
    public final List<String> captureLimitations = new ArrayList<>();

    public void add(String limitation)
    {
        if (!captureLimitations.contains(limitation))
        {
            captureLimitations.add(limitation);
        }
    }
}
