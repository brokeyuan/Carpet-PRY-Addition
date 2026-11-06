package me.primaryuan.carpet.replay.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段录像：统一的服务器 Tick 时间轴（[0, durationTicks)）。
 * 每条参与者轨道分别服务 playerReplay 幻影回放与 fakePlayerScript 假人脚本；
 * 轨道按参与者 UUID 与在线片段组织，身份不依赖录像名称。
 */
public final class ReplayData
{
    public final ReplayMetadata metadata = new ReplayMetadata();
    public final List<ReplayParticipant> participants = new ArrayList<>();
    public final ReplayDiagnostics diagnostics = new ReplayDiagnostics();

    public int durationTicks()
    {
        return metadata.durationTicks;
    }

    public ReplayParticipant participant(String uuid)
    {
        if (uuid == null)
        {
            return null;
        }
        for (ReplayParticipant p : participants)
        {
            if (uuid.equals(p.uuid))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * 兼容单人/旧脚本语义的主参与者：优先创作者本人，否则第一位参与者。
     */
    public ReplayParticipant primaryParticipant()
    {
        ReplayParticipant owner = participant(metadata.ownerUuid);
        if (owner != null)
        {
            return owner;
        }
        return participants.isEmpty() ? null : participants.get(0);
    }

    /** 深拷贝（检查点用）：所有可变轨道均复制，不与录制会话共享 */
    public ReplayData copy()
    {
        ReplayData out = new ReplayData();
        out.metadata.copyFrom(metadata);
        for (ReplayParticipant p : participants)
        {
            ReplayParticipant cp = new ReplayParticipant(p.uuid, p.name, p.joinMethod);
            cp.skinName = p.skinName;
            cp.skinUuid = p.skinUuid;
            cp.skinTextures = p.skinTextures;
            cp.skinSignature = p.skinSignature;
            cp.model = p.model;
            for (ReplayParticipant.Segment seg : p.segments)
            {
                ReplayParticipant.Segment cs = new ReplayParticipant.Segment(seg.startTick);
                cs.endTick = seg.endTick;
                cs.visual.copyFrom(seg.visual);
                cs.input.copyFrom(seg.input);
                cp.segments.add(cs);
            }
            out.participants.add(cp);
        }
        out.diagnostics.captureLimitations.addAll(diagnostics.captureLimitations);
        return out;
    }
}
