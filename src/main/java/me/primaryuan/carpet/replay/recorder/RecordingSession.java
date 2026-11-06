package me.primaryuan.carpet.replay.recorder;

import me.primaryuan.carpet.replay.common.InputEvent;
import me.primaryuan.carpet.replay.common.InputEventType;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import me.primaryuan.carpet.replay.common.VisualFlags;
import me.primaryuan.carpet.replay.common.VisualFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个参与者在一次连续在线片段内的录制会话：从玩家状态采样并喂入纯逻辑时间轴。
 * 服务器主线程独占；断线/死亡/重连时由任务层结束旧片段并创建新会话。
 */
public final class RecordingSession implements CaptureSink
{
    private final ServerPlayer player;
    private final ReplayParticipant.Segment segment;
    private final RecordingTimeline timeline;
    private final Map<Byte, ItemStack> lastEquipment = new HashMap<>();
    private boolean digging;

    public RecordingSession(ServerPlayer player, ReplayParticipant participant, ReplayParticipant.Segment segment)
    {
        this.player = player;
        this.segment = segment;
        this.timeline = new RecordingTimeline(segment);
        participant.skinName = pry$profileName();
        participant.skinUuid = pry$profileId();
        participant.skinTextures = pry$skinTextures();
        participant.skinSignature = pry$skinSignature();
        participant.model = pry$modelType(participant.skinTextures);
        timeline.setInitialSlot(selectedSlot());
        timeline.onMovementInput(initialMovementMask());
    }

    public ServerPlayer player()
    {
        return player;
    }

    public ReplayParticipant.Segment segment()
    {
        return segment;
    }

    public RecordingTimeline timeline()
    {
        return timeline;
    }

    public boolean finished()
    {
        return timeline.finished();
    }

    public boolean paused()
    {
        return timeline.paused();
    }

    public boolean pause()
    {
        return timeline.pause();
    }

    public boolean resume()
    {
        return timeline.resume(selectedSlot());
    }

    /** 暂停期间新建立、尚未 pause 的会话在恢复时补当前输入/槽位基线 */
    public void resyncBaseline()
    {
        timeline.syncSlot(selectedSlot());
        timeline.onMovementInput(initialMovementMask());
    }

    public int tick()
    {
        return timeline.tick();
    }

    public void onServerTick()
    {
        commitTick();
    }

    public void commitTick()
    {
        if (timeline.finished() || timeline.paused())
        {
            return;
        }
        int t = timeline.tick();
        String dim = dim();
        VisualFrame f = sampleFrame();
        if (!timeline.commitTick(dim, f, player.getYRot(), player.getXRot()))
        {
            return;
        }
        captureFrameExtras(t);
    }

    private String dim()
    {
        //#if MC >= 12111
        return player.level().dimension().identifier().toString();
        //#else
        //$$ return player.level().dimension().location().toString();
        //#endif
    }

    private VisualFrame sampleFrame()
    {
        VisualFrame f = new VisualFrame();
        f.x = player.getX();
        f.y = player.getY();
        f.z = player.getZ();
        f.vx = (float) player.getDeltaMovement().x;
        f.vy = (float) player.getDeltaMovement().y;
        f.vz = (float) player.getDeltaMovement().z;
        f.bodyYaw = player.yBodyRot;
        f.headYaw = player.yHeadRot;
        f.pitch = player.getXRot();
        //#if MC >= 12105
        f.slot = player.getInventory().getSelectedSlot();
        //#else
        //$$ f.slot = player.getInventory().selected;
        //#endif
        byte flags = 0;
        if (player.isShiftKeyDown())
        {
            flags |= VisualFlags.SNEAKING;
        }
        if (player.isSprinting())
        {
            flags |= VisualFlags.SPRINTING;
        }
        if (player.isSwimming())
        {
            flags |= VisualFlags.SWIMMING;
        }
        if (player.isFallFlying())
        {
            flags |= VisualFlags.FALL_FLYING;
        }
        if (player.isPassenger())
        {
            flags |= VisualFlags.RIDING;
        }
        if (player.onGround())
        {
            flags |= VisualFlags.ON_GROUND;
        }
        if (player.isUsingItem())
        {
            flags |= VisualFlags.USING_ITEM;
        }
        f.flags = flags;
        return f;
    }

    // ===== CaptureSink：包事件入口（主线程） =====

    @Override
    public void onMovementInput(int mask)
    {
        timeline.onMovementInput(mask);
    }

    @Override
    public void onSwing(InteractionHand hand)
    {
        timeline.addEvent(InputEventType.SWING, hand == InteractionHand.MAIN_HAND ? 0 : 1);
    }

    @Override
    public void onAttackEntity(int entityId)
    {
        InputEvent e = timeline.addEvent(InputEventType.ATTACK_ENTITY, -1);
        if (e != null)
        {
            e.d = entityId;
        }
    }

    @Override
    public void onDigStart(BlockPos pos)
    {
        digging = true;
        InputEvent e = timeline.addEvent(InputEventType.DIG_START, -1);
        if (e != null)
        {
            e.d = pos.asLong();
        }
    }

    @Override
    public void onDigAbort(BlockPos pos)
    {
        digging = false;
        InputEvent e = timeline.addEvent(InputEventType.DIG_ABORT, -1);
        if (e != null)
        {
            e.d = pos.asLong();
        }
    }

    @Override
    public void onDigFinish(BlockPos pos)
    {
        digging = false;
        InputEvent e = timeline.addEvent(InputEventType.DIG_FINISH, -1);
        if (e != null)
        {
            e.d = pos.asLong();
        }
    }

    @Override
    public void onUseItemOn(InteractionHand hand, HitResult hit)
    {
        int handByte = hand == InteractionHand.MAIN_HAND ? 0 : 1;
        if (hit instanceof EntityHitResult ehr)
        {
            timeline.addUseDown(handByte, InputEventType.USE_TARGET_ENTITY, ehr.getEntity().getId());
        }
        else
        {
            timeline.addUseDown(handByte, InputEventType.USE_TARGET_BLOCK, -1);
        }
    }

    @Override
    public void onUseEntity(InteractionHand hand, int entityId)
    {
        timeline.addEntityUse(hand == InteractionHand.MAIN_HAND ? 0 : 1, entityId);
    }

    @Override
    public void onUseItem(InteractionHand hand)
    {
        timeline.addUseDown(hand == InteractionHand.MAIN_HAND ? 0 : 1,
                InputEventType.USE_TARGET_AIR, -1);
    }

    @Override
    public void onReleaseUse()
    {
        timeline.addUseUp(-1);
    }

    @Override
    public void onSelectSlot(int slot)
    {
        InputEvent e = timeline.addEvent(InputEventType.SELECT_SLOT, -1);
        if (e != null)
        {
            e.a = slot;
        }
    }

    @Override
    public void onDrop(boolean all)
    {
        InputEvent e = timeline.addEvent(InputEventType.DROP_ITEM, -1);
        if (e != null)
        {
            e.a = all ? 1 : 0;
        }
    }

    @Override
    public void onSwapHands()
    {
        timeline.addEvent(InputEventType.SWAP_HANDS, -1);
    }

    /**
     * 逐 Tick 装备快照：先比较 ItemStack（含组件），只有变化才序列化，
     * 避免每个 Tick 对每名参与者重复 NBT 编码。
     */
    private void captureFrameExtras(int t)
    {
        ItemStack[] slots = {
                player.getMainHandItem(), player.getOffhandItem(),
                player.getItemBySlot(EquipmentSlot.FEET),
                player.getItemBySlot(EquipmentSlot.LEGS),
                player.getItemBySlot(EquipmentSlot.CHEST),
                player.getItemBySlot(EquipmentSlot.HEAD)
        };
        for (byte i = 0; i < slots.length; i++)
        {
            ItemStack current = slots[i] == null ? ItemStack.EMPTY : slots[i];
            ItemStack previous = lastEquipment.get(i);
            if (previous != null && ItemStack.matches(previous, current))
            {
                continue;
            }
            lastEquipment.put(i, current.copy());
            byte[] bytes = me.primaryuan.carpet.replay.phantom.ItemStackBytes.serialize(
                    player.level().getServer(), current);
            segment.visual.equipmentEvents.add(
                    new me.primaryuan.carpet.replay.common.VisualTrack.EquipmentEvent(t, i, bytes));
        }
    }

    public void finish()
    {
        timeline.finish();
    }

    public void abort()
    {
        timeline.abort();
    }

    public boolean digging()
    {
        return digging;
    }

    private int selectedSlot()
    {
        //#if MC >= 12105
        return player.getInventory().getSelectedSlot();
        //#else
        //$$ return player.getInventory().selected;
        //#endif
    }

    private int initialMovementMask()
    {
        //#if MC >= 12103
        net.minecraft.world.entity.player.Input in = player.getLastClientInput();
        if (in == null)
        {
            return 0;
        }
        int mask = 0;
        if (in.forward()) mask |= me.primaryuan.carpet.replay.common.InputFlags.FORWARD;
        if (in.backward()) mask |= me.primaryuan.carpet.replay.common.InputFlags.BACKWARD;
        if (in.left()) mask |= me.primaryuan.carpet.replay.common.InputFlags.LEFT;
        if (in.right()) mask |= me.primaryuan.carpet.replay.common.InputFlags.RIGHT;
        if (in.jump()) mask |= me.primaryuan.carpet.replay.common.InputFlags.JUMP;
        if (in.shift()) mask |= me.primaryuan.carpet.replay.common.InputFlags.SNEAK;
        if (in.sprint()) mask |= me.primaryuan.carpet.replay.common.InputFlags.SPRINT;
        return mask;
        //#else
        //$$ return 0;
        //#endif
    }

    private String pry$skinTextures()
    {
        for (com.mojang.authlib.properties.Property p : pry$textureProps())
        {
            return p.value();
        }
        return "";
    }

    private String pry$skinSignature()
    {
        for (com.mojang.authlib.properties.Property p : pry$textureProps())
        {
            return p.signature() == null ? "" : p.signature();
        }
        return "";
    }

    private java.util.Collection<com.mojang.authlib.properties.Property> pry$textureProps()
    {
        //#if MC >= 12110
        return player.getGameProfile().properties().get("textures");
        //#else
        //$$ return player.getGameProfile().getProperties().get("textures");
        //#endif
    }

    private static String pry$modelType(String tex)
    {
        return tex != null && tex.contains("\"slim\"") ? "slim" : "wide";
    }

    private String pry$profileName()
    {
        //#if MC >= 12110
        return player.getGameProfile().name();
        //#else
        //$$ return player.getGameProfile().getName();
        //#endif
    }

    private String pry$profileId()
    {
        //#if MC >= 12110
        return player.getGameProfile().id().toString();
        //#else
        //$$ return player.getGameProfile().getId().toString();
        //#endif
    }
}
