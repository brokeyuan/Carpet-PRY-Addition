package me.primaryuan.carpet.replay.script;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.replay.common.InputFlags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;


/**
 * Carpet ActionPack 驱动实现（javap 核实：m1-playback-api.md §4）。
 * 轮内禁止传送/寻路/位置纠偏——位移完全由当前物理模拟产生；
 * 唯一例外是脚本明确配置"每轮回到起点"时的轮间位置重置（onNewRound）。
 * Carpet 语义：start(type, null) 停止单动作；USE/ATTACK 无 hand 参数（副手降级为主手）。
 */
public final class CarpetFakeDriver implements FakeDriver
{
    private final EntityPlayerMPFake fake;
    /** 会话 UUID：脚本会话标识（生命周期由 ScriptScheduler 管理） */
    final String sessionUuid;
    /** 轮间重置目标（null = 接续当前位置） */
    private final String startDimKey;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final float startYaw;

    CarpetFakeDriver(EntityPlayerMPFake fake, String sessionUuid,
                     String startDimKey, double startX, double startY, double startZ, float startYaw)
    {
        this.fake = fake;
        this.sessionUuid = sessionUuid;
        this.startDimKey = startDimKey;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.startYaw = startYaw;
    }

    private EntityPlayerActionPack pack()
    {
        return ((ServerPlayerInterface) fake).getActionPack();
    }

    @Override
    public void setMovementMask(int mask)
    {
        float forward = 0;
        if ((mask & InputFlags.FORWARD) != 0)
        {
            forward = 1;
        }
        else if ((mask & InputFlags.BACKWARD) != 0)
        {
            forward = -1;
        }
        float strafe = 0;
        if ((mask & InputFlags.LEFT) != 0)
        {
            strafe = 1;
        }
        else if ((mask & InputFlags.RIGHT) != 0)
        {
            strafe = -1;
        }
        pack().setForward(forward).setStrafing(strafe);
    }

    @Override
    public void setJump(boolean jump)
    {
        if (jump)
        {
            pack().start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.continuous());
        }
        else
        {
            pack().start(EntityPlayerActionPack.ActionType.JUMP, null);
        }
    }

    @Override
    public void setSneaking(boolean sneak)
    {
        pack().setSneaking(sneak);
    }

    @Override
    public void setSprinting(boolean sprint)
    {
        pack().setSprinting(sprint);
    }

    @Override
    public void look(float yaw, float pitch)
    {
        pack().look(yaw, pitch);
    }

    @Override
    public void setSlot(int slot)
    {
        int carpetSlot = carpetSlot(slot);
        if (carpetSlot > 0)
        {
            pack().setSlot(carpetSlot);
        }
    }

    /** 录像槽位 0~8 → Carpet 槽位 1~9；非法返回 -1（包内可见以便单测边界） */
    static int carpetSlot(int slot)
    {
        return (slot >= 0 && slot <= 8) ? slot + 1 : -1;
    }

    @Override
    public boolean startAttackHold()
    {
        pack().start(EntityPlayerActionPack.ActionType.ATTACK, EntityPlayerActionPack.Action.continuous());
        return true;
    }

    @Override
    public void stopAttackHold()
    {
        pack().start(EntityPlayerActionPack.ActionType.ATTACK, null);
    }

    @Override
    public boolean attackOnce()
    {
        pack().start(EntityPlayerActionPack.ActionType.ATTACK, EntityPlayerActionPack.Action.once());
        return true;
    }

    @Override
    public boolean startUseHold(int hand)
    {
        pack().start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.continuous());
        return true;
    }

    @Override
    public void stopUseHold(int hand)
    {
        pack().start(EntityPlayerActionPack.ActionType.USE, null);
    }

    @Override
    public boolean useOnce(int hand)
    {
        pack().start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.once());
        return true;
    }

    @Override
    public void dropItem(int hand, boolean all)
    {
        // Carpet drop 首参是库存槽号：-1 = 当前选中槽。hand 不参与槽位选择。
        pack().drop(carpetDropSlot(), all);
    }

    /** Carpet drop 的当前选中槽参数；0/1 是库存槽号，不能把 hand 传进去 */
    static int carpetDropSlot()
    {
        return -1;
    }

    @Override
    public void swapHands()
    {
        pack().start(EntityPlayerActionPack.ActionType.SWAP_HANDS, EntityPlayerActionPack.Action.once());
    }

    @Override
    public boolean isValid()
    {
        return fake.isAlive()
                && !fake.hasDisconnected()
                && !fake.isRemoved()
                && fake.getId() == fakeEntityIdSnapshot();
    }

    private int fakeEntityIdSnapshot()
    {
        return fake.getId();
    }

    @Override
    public void releaseAll()
    {
        pack().stopAll();
    }

    @Override
    public boolean onNewRound()
    {
        if (startDimKey == null)
        {
            return true;
        }
        return teleportTo(startDimKey, startX, startY, startZ, startYaw, 0f);
    }

    /** 本脚本召唤的假人结束时下线（Carpet kill 全版本签名 javap 核实） */
    @Override
    public void takeOffline()
    {
        fake.kill(net.minecraft.network.chat.Component.literal("假人脚本结束"));
    }

    @Override
    public boolean teleportTo(String dimKey, double x, double y, double z, float yaw, float pitch)
    {
        ServerLevel level;
        try
        {
            level = fake.level().getServer().getLevel(dimKey == null ? null : parseDim(dimKey));
        }
        catch (RuntimeException e)
        {
            // 维度标识损坏/不存在：返回失败由会话停止并反馈
            return false;
        }
        if (level == null)
        {
            return false;
        }
        //#if MC >= 12103
        return fake.teleportTo(level, x, y, z,
                java.util.Collections.<net.minecraft.world.entity.Relative>emptySet(), yaw, pitch, false);
        //#else
        //$$ return fake.teleportTo(level, x, y, z,
        //$$         java.util.Collections.<net.minecraft.world.entity.RelativeMovement>emptySet(), yaw, pitch);
        //#endif
    }

    private static ResourceKey<Level> parseDim(String dimKey)
    {
        //#if MC >= 12111
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.parse(dimKey));
        //#else
        //$$ return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
        //$$         net.minecraft.resources.ResourceLocation.parse(dimKey));
        //#endif
    }

    public static CarpetFakeDriver of(ServerPlayer player, String sessionUuid)
    {
        return of(player, sessionUuid, null, 0, 0, 0, 0);
    }

    public static CarpetFakeDriver of(ServerPlayer player, String sessionUuid,
                                      String startDimKey, double x, double y, double z, float yaw)
    {
        if (!(player instanceof EntityPlayerMPFake fake))
        {
            return null;
        }
        return new CarpetFakeDriver(fake, sessionUuid, startDimKey, x, y, z, yaw);
    }

    EntityPlayerMPFake fake()
    {
        return fake;
    }

    /** 供 ScriptSession 校验实体身份 */
    static int entityIdOf(ServerPlayer player)
    {
        return player.getId();
    }

}
