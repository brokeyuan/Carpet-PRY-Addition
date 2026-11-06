package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayParticipant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 准备/录制任务的纯逻辑名单与邀请状态机（无 MC 依赖，可单测）。
 * 身份键是参与者 UUID；名称只用于显示。每条邀请持有独立令牌，
 * 重新邀请会替换旧令牌，旧按钮不能批准新邀请。
 *
 * 邀请语义（本轮确认）：准备阶段未回复的邀请在点击“开始”时全部失效；
 * 已同意（ACCEPTED）的参与者在开始时加入。录制中可继续邀请，接受后从当时加入。
 */
public final class RecordingRoster
{
    public enum State { INVITED, ACCEPTED, JOINED, DECLINED, LEFT }

    public enum InviteResult
    {
        OK,
        ALREADY_JOINED,
        ALREADY_INVITED,
        NOT_FOUND,
        NOT_INVITED,
        OLD_TOKEN,
        NO_SLOT,
        NOT_ALLOWED
    }

    public static final class Entry
    {
        public final String uuid;
        public final String name;
        public ReplayParticipant.JoinMethod joinMethod;
        public State state;
        /** 仅普通邀请；管理员直接添加为空 */
        public UUID token;
        public long invitedAtMillis;

        Entry(String uuid, String name, ReplayParticipant.JoinMethod joinMethod, State state)
        {
            this.uuid = uuid;
            this.name = name;
            this.joinMethod = joinMethod;
            this.state = state;
        }

        public boolean invited()
        {
            return state == State.INVITED;
        }

        public boolean accepted()
        {
            return state == State.ACCEPTED;
        }

        public boolean joined()
        {
            return state == State.JOINED;
        }
    }

    public final UUID taskId;
    public final String ownerUuid;
    public final String ownerName;
    public final String name;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean settingsLocked;
    private boolean invitesSent;
    private ReplayAccess access = ReplayAccess.PUBLIC;
    private int durationTicks = ReplayLimits.DEFAULT_DURATION_TICKS;

    public RecordingRoster(UUID taskId, String ownerUuid, String ownerName, String name)
    {
        this.taskId = taskId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.name = name;
        addSelf();
    }

    public void addSelf()
    {
        entries.put(ownerUuid, new Entry(ownerUuid, ownerName,
                ReplayParticipant.JoinMethod.OWNER, State.JOINED));
    }

    public void removeSelf()
    {
        Entry self = entries.get(ownerUuid);
        if (self != null && self.joinMethod == ReplayParticipant.JoinMethod.OWNER)
        {
            entries.remove(ownerUuid);
        }
    }

    public boolean selfSelected()
    {
        Entry self = entries.get(ownerUuid);
        return self != null && self.joined();
    }

    public ReplayAccess access()
    {
        return access;
    }

    public int durationTicks()
    {
        return durationTicks;
    }

    public boolean settingsLocked()
    {
        return settingsLocked;
    }

    /** 修改时长/访问范围；邀请发出或录制开始后锁定，保存后方可改访问范围 */
    public void setDurationTicks(int ticks)
    {
        if (!settingsLocked)
        {
            durationTicks = ReplayLimits.clampDurationTicks(ticks);
        }
    }

    public void setAccess(ReplayAccess access)
    {
        if (!settingsLocked && access != null)
        {
            this.access = access;
        }
    }

    public void lockSettings()
    {
        settingsLocked = true;
    }

    public boolean invitesSent()
    {
        return invitesSent;
    }

    public Entry get(String uuid)
    {
        return entries.get(uuid);
    }

    public List<Entry> entries()
    {
        return new ArrayList<>(entries.values());
    }

    public List<Entry> joinedEntries()
    {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values())
        {
            if (e.joined())
            {
                out.add(e);
            }
        }
        return out;
    }

    public List<Entry> pendingInvites()
    {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values())
        {
            if (e.invited())
            {
                out.add(e);
            }
        }
        return out;
    }

    public boolean hasJoinedParticipant()
    {
        return !joinedEntries().isEmpty();
    }

    public int joinedCount()
    {
        return joinedEntries().size();
    }

    /**
     * 邀请/直接添加。
     * @param admin true = 管理员直接添加，立即加入，不需要同意
     */
    public InviteResult invite(String targetUuid, String targetName, boolean admin)
    {
        Entry existing = entries.get(targetUuid);
        if (existing != null)
        {
            if (existing.joined() || existing.accepted())
            {
                return InviteResult.ALREADY_JOINED;
            }
            if (existing.invited())
            {
                if (!admin)
                {
                    // 重新邀请：换发新令牌，旧按钮立即失效
                    existing.token = UUID.randomUUID();
                    existing.invitedAtMillis = System.currentTimeMillis();
                    lockSettings();
                    return InviteResult.OK;
                }
                // 管理员升级为直接添加：旧邀请令牌立即失效
                existing.state = State.JOINED;
                existing.token = null;
                existing.joinMethod = ReplayParticipant.JoinMethod.ADMIN;
                return InviteResult.OK;
            }
            if (existing.state == State.LEFT)
            {
                if (admin)
                {
                    existing.state = State.JOINED;
                    existing.joinMethod = ReplayParticipant.JoinMethod.ADMIN;
                    return InviteResult.OK;
                }
                // 退出后再次参与必须重新邀请并同意
                existing.state = State.INVITED;
                existing.joinMethod = ReplayParticipant.JoinMethod.INVITE;
                existing.token = UUID.randomUUID();
                existing.invitedAtMillis = System.currentTimeMillis();
                invitesSent = true;
                lockSettings();
                return InviteResult.OK;
            }
        }
        if (entries.size() >= ReplayLimits.MAX_PARTICIPANTS)
        {
            return InviteResult.NO_SLOT;
        }
        if (admin)
        {
            entries.put(targetUuid, new Entry(targetUuid, targetName,
                    ReplayParticipant.JoinMethod.ADMIN, State.JOINED));
        }
        else
        {
            Entry e = new Entry(targetUuid, targetName,
                    ReplayParticipant.JoinMethod.INVITE, State.INVITED);
            e.token = UUID.randomUUID();
            e.invitedAtMillis = System.currentTimeMillis();
            entries.put(targetUuid, e);
        }
        invitesSent = true;
        lockSettings();
        return InviteResult.OK;
    }

    public Entry inviteEntry(String targetUuid)
    {
        return entries.get(targetUuid);
    }

    /** 开始录制：清理未回复邀请，已同意者转为已加入；至少一名已加入者 */
    public InviteResult start()
    {
        List<Entry> toJoin = new ArrayList<>(joinedEntries());
        java.util.Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext())
        {
            Entry e = it.next();
            if (e.state == State.ACCEPTED)
            {
                e.state = State.JOINED;
                toJoin.add(e);
            }
            else if (e.state == State.INVITED || e.state == State.DECLINED)
            {
                it.remove();
            }
        }
        if (toJoin.isEmpty())
        {
            return InviteResult.NO_SLOT;
        }
        if (invitesSent)
        {
            lockSettings();
        }
        return InviteResult.OK;
    }

    /** 录制中同意邀请：校验任务、受邀者、独立令牌 */
    public InviteResult accept(String inviteeUuid, UUID token, boolean recording)
    {
        Entry e = entries.get(inviteeUuid);
        if (e == null)
        {
            return InviteResult.NOT_FOUND;
        }
        if (e.state == State.JOINED || e.state == State.ACCEPTED)
        {
            return InviteResult.ALREADY_JOINED;
        }
        if (e.state != State.INVITED)
        {
            return InviteResult.NOT_INVITED;
        }
        if (!e.token.equals(token))
        {
            return InviteResult.OLD_TOKEN;
        }
        if (recording)
        {
            e.state = State.JOINED;
            e.token = null;
        }
        else
        {
            e.state = State.ACCEPTED;
            e.token = null;
        }
        return InviteResult.OK;
    }

    public InviteResult deny(String inviteeUuid, UUID token)
    {
        Entry e = entries.get(inviteeUuid);
        if (e == null)
        {
            return InviteResult.NOT_FOUND;
        }
        if (e.state != State.INVITED)
        {
            return InviteResult.NOT_INVITED;
        }
        if (!e.token.equals(token))
        {
            return InviteResult.OLD_TOKEN;
        }
        e.state = State.DECLINED;
        e.token = null;
        return InviteResult.OK;
    }

    public InviteResult cancelInvite(String targetUuid)
    {
        Entry e = entries.get(targetUuid);
        if (e == null || e.state != State.INVITED)
        {
            return InviteResult.NOT_INVITED;
        }
        entries.remove(targetUuid);
        return InviteResult.OK;
    }

    /** 录制中管理员直接加入（不接受普通玩家自行“直接加入”） */
    public InviteResult joinDirectNow(String targetUuid, String targetName, boolean admin)
    {
        if (!admin)
        {
            return InviteResult.NOT_ALLOWED;
        }
        return invite(targetUuid, targetName, true);
    }

    /**
     * 普通邀请加入者录制中主动退出；管理员直接添加者和创作者不能走此入口。
     */
    public InviteResult leave(String targetUuid)
    {
        Entry e = entries.get(targetUuid);
        if (e == null || !e.joined())
        {
            return InviteResult.NOT_FOUND;
        }
        if (e.joinMethod != ReplayParticipant.JoinMethod.INVITE)
        {
            return InviteResult.NOT_ALLOWED;
        }
        e.state = State.LEFT;
        return InviteResult.OK;
    }

    public void clear()
    {
        entries.clear();
    }
}
