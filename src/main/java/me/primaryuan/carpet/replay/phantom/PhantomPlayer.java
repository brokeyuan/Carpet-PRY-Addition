package me.primaryuan.carpet.replay.phantom;

/**
 * 幻影玩家实例：纯数据包视觉实体，不进 PlayerList、不创建真实 ServerPlayer。
 * 状态（实体 id/UUID/皮肤 profile/播放光标）由 PhantomEntityTracker 持有；
 * 本类只负责把 VisualFrame 变换成包序列经 PacketSink 发出。
 * 版本差异集中在 PacketSync。
 */
public final class PhantomPlayer
{
    public final int entityId;
    public final String uuid;
    public final String profileName;
    public final String profileTextures;
    /** Mojang 签名（非空时以三参 Property 发送，客户端按 secure 皮肤接受）；可空 */
    public final String profileSignature;
    /** wide / slim */
    public final String model;

    public PhantomPlayer(int entityId, String uuid, String profileName, String profileTextures,
                         String profileSignature, String model)
    {
        this.entityId = entityId;
        this.uuid = uuid;
        this.profileName = profileName;
        this.profileTextures = profileTextures;
        this.profileSignature = profileSignature;
        this.model = model == null || model.isEmpty() ? "wide" : model;
    }
}
