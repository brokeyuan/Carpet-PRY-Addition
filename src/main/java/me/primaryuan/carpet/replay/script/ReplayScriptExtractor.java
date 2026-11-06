package me.primaryuan.carpet.replay.script;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFrame;
import me.primaryuan.carpet.replay.common.VisualSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 假人脚本提取：取指定参与者真正在线的片段，去掉离线空档后顺接成连续脚本。
 * 面板必须说明该行为；片段边界写入 segmentStarts，由 SessionCore 在末帧消费后
 * 释放旧持续动作并重建基线，绝不在片段间按录像位置传送。
 */
public final class ReplayScriptExtractor
{
    private ReplayScriptExtractor() {}

    public static ReplayData extract(ReplayData replay, String participantUuid) throws ReplayScriptException
    {
        ReplayParticipant participant = replay.participant(participantUuid);
        if (participant == null)
        {
            throw new ReplayScriptException("录像中没有该参与者");
        }
        boolean hasInput = false;
        for (ReplayParticipant.Segment seg : participant.segments)
        {
            if (seg.input.hasInputContent())
            {
                hasInput = true;
                break;
            }
        }
        if (!hasInput)
        {
            throw new ReplayScriptException("该参与者没有有效输入内容，不能执行假人脚本");
        }
        int total = 0;
        for (ReplayParticipant.Segment seg : participant.segments)
        {
            total += Math.max(0, seg.duration());
        }
        if (total <= 0)
        {
            throw new ReplayScriptException("该参与者没有可执行的在线片段");
        }

        ReplayData out = new ReplayData();
        out.metadata.copyFrom(replay.metadata);
        out.metadata.durationTicks = total;
        out.diagnostics.captureLimitations.addAll(replay.diagnostics.captureLimitations);
        out.diagnostics.captureLimitations.add("多人录像脚本按参与者在线片段顺接，已去掉离线空档");
        ReplayParticipant scripted = new ReplayParticipant(participant.uuid, participant.name, participant.joinMethod);
        scripted.skinName = participant.skinName;
        scripted.skinUuid = participant.skinUuid;
        scripted.skinTextures = participant.skinTextures;
        scripted.skinSignature = participant.skinSignature;
        scripted.model = participant.model;
        ReplayParticipant.Segment outSeg = new ReplayParticipant.Segment(0);
        outSeg.endTick = total;
        int offset = 0;
        VisualFrame first = participant.firstFrame();
        if (first != null)
        {
            out.metadata.initialX = first.x;
            out.metadata.initialY = first.y;
            out.metadata.initialZ = first.z;
            out.metadata.initialYaw = first.bodyYaw;
            out.metadata.initialPitch = first.pitch;
            ReplayParticipant.Segment firstSeg = participant.firstSegment();
            if (firstSeg != null && !firstSeg.visual.segments.isEmpty())
            {
                out.metadata.initialDimension = firstSeg.visual.segments.get(0).dimension;
            }
        }
        for (ReplayParticipant.Segment seg : participant.segments)
        {
            int length = Math.max(0, seg.duration());
            if (length == 0)
            {
                continue;
            }
            if (offset > 0)
            {
                outSeg.input.segmentStarts.add(offset);
            }
            for (VisualSegment v : seg.visual.segments)
            {
                VisualSegment copy = new VisualSegment(v.dimension, offset + v.startTick);
                for (VisualFrame f : v.frames)
                {
                    copy.frames.add(f.copy());
                }
                outSeg.visual.segments.add(copy);
            }
            outSeg.visual.equipmentEvents.addAll(seg.visual.equipmentEvents);
            outSeg.visual.animEvents.addAll(seg.visual.animEvents);
            outSeg.input.ensureDuration(offset + length);
            for (int i = 0; i < length; i++)
            {
                outSeg.input.movementMasks[offset + i] = seg.input.movementMasks[i];
                outSeg.input.yaw[offset + i] = seg.input.yaw[i];
                outSeg.input.pitch[offset + i] = seg.input.pitch[i];
            }
            for (InputEvent e : seg.input.events)
            {
                InputEvent copy = new InputEvent(offset + e.tick, e.seq, e.type);
                copy.hand = e.hand;
                copy.a = e.a;
                copy.b = e.b;
                copy.c = e.c;
                copy.d = e.d;
                outSeg.input.events.add(copy);
            }
            // 装备/动画事件 tick 是相对片段，顺接后同步平移
            for (int i = outSeg.visual.equipmentEvents.size() - seg.visual.equipmentEvents.size();
                 i < outSeg.visual.equipmentEvents.size(); i++)
            {
                var e = outSeg.visual.equipmentEvents.get(i);
                outSeg.visual.equipmentEvents.set(i,
                        new me.primaryuan.carpet.replay.common.VisualTrack.EquipmentEvent(
                                offset + e.tick(), e.slot(), e.itemBytes()));
            }
            for (int i = outSeg.visual.animEvents.size() - seg.visual.animEvents.size();
                 i < outSeg.visual.animEvents.size(); i++)
            {
                var e = outSeg.visual.animEvents.get(i);
                outSeg.visual.animEvents.set(i,
                        new me.primaryuan.carpet.replay.common.VisualTrack.AnimEvent(
                                offset + e.tick(), e.seq(), e.type(), e.hand()));
            }
            offset += length;
        }
        outSeg.input.trimToDuration();
        outSeg.input.ensureOrdered();
        scripted.segments.add(outSeg);
        out.participants.add(scripted);
        return out;
    }

    /** 可选参与者列表（有多名时 UI 先选择） */
    public static List<ReplayParticipant> selectable(ReplayData replay)
    {
        List<ReplayParticipant> out = new ArrayList<>();
        for (ReplayParticipant p : replay.participants)
        {
            if (p.hasContent())
            {
                out.add(p);
            }
        }
        return out;
    }
}
