package me.primaryuan.carpet.mixins.rule.playerReplay;

import me.primaryuan.carpet.replay.recorder.ReplayRecorder;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * playerReplay/fakePlayerScript 共享输入捕获：只记录操作意图，不改变任何处理逻辑。
 * 截点 TAIL：包处理完成后的最终状态（ensureRunningOnSameThread 切线程后本方法在主线程执行；
 * 早退分支（未加载完成等）不会走到 TAIL，天然排除无效输入）。
 * 注入点与 accessor 名已经 javap 核实（m1-injection-points.md）：
 * 1.21 线 class + getHand()/getHitResult()/getXxa()；26.x record + hand()/hitResult()。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ReplayCaptureMixin
{
    private net.minecraft.server.level.ServerLevel pry$serverLevel()
    {
        net.minecraft.server.level.ServerPlayer p = ((ServerGamePacketListenerImpl) (Object) this).player;
        return (net.minecraft.server.level.ServerLevel) p.level();
    }

    private static boolean pry$probed;

    private void pry$record(java.util.function.Consumer<me.primaryuan.carpet.replay.recorder.CaptureSink> fn)
    {
        if (me.primaryuan.carpet.CarpetPrimaryuanSettings.playerReplay
                || me.primaryuan.carpet.CarpetPrimaryuanSettings.fakePlayerScript)
        {
            net.minecraft.server.level.ServerPlayer p = ((ServerGamePacketListenerImpl) (Object) this).player;
            ReplayRecorder.dispatch(p, fn);
        }
    }

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void pry$onInput(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet, CallbackInfo ci)
    {
        int mask = 0;
        //#if MC >= 12102
        net.minecraft.world.entity.player.Input in = packet.input();
        if (in.forward()) mask |= me.primaryuan.carpet.replay.common.InputFlags.FORWARD;
        if (in.backward()) mask |= me.primaryuan.carpet.replay.common.InputFlags.BACKWARD;
        if (in.left()) mask |= me.primaryuan.carpet.replay.common.InputFlags.LEFT;
        if (in.right()) mask |= me.primaryuan.carpet.replay.common.InputFlags.RIGHT;
        if (in.jump()) mask |= me.primaryuan.carpet.replay.common.InputFlags.JUMP;
        if (in.shift()) mask |= me.primaryuan.carpet.replay.common.InputFlags.SNEAK;
        if (in.sprint()) mask |= me.primaryuan.carpet.replay.common.InputFlags.SPRINT;
        //#else
        //$$ // 1.21/1.21.1：xxa 是左右轴（正值=左），zza 是前后轴（正值=前），此前两轴映射反了
        //$$ if (packet.getXxa() > 0.01f) mask |= me.primaryuan.carpet.replay.common.InputFlags.LEFT;
        //$$ if (packet.getXxa() < -0.01f) mask |= me.primaryuan.carpet.replay.common.InputFlags.RIGHT;
        //$$ if (packet.getZza() > 0.01f) mask |= me.primaryuan.carpet.replay.common.InputFlags.FORWARD;
        //$$ if (packet.getZza() < -0.01f) mask |= me.primaryuan.carpet.replay.common.InputFlags.BACKWARD;
        //$$ if (packet.isJumping()) mask |= me.primaryuan.carpet.replay.common.InputFlags.JUMP;
        //$$ if (packet.isShiftKeyDown()) mask |= me.primaryuan.carpet.replay.common.InputFlags.SNEAK;
        //#endif
        final int m = mask;
        pry$record(s -> s.onMovementInput(m));
    }

    @Inject(method = "handlePlayerAction", at = @At("TAIL"))
    private void pry$onAction(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet, CallbackInfo ci)
    {
        String action = packet.getAction().name();
        switch (action)
        {
            case "START_DESTROY_BLOCK" -> pry$record(s -> s.onDigStart(packet.getPos()));
            case "ABORT_DESTROY_BLOCK" -> pry$record(s -> s.onDigAbort(packet.getPos()));
            case "STOP_DESTROY_BLOCK" -> pry$record(s -> s.onDigFinish(packet.getPos()));
            case "DROP_ITEM" -> pry$record(s -> s.onDrop(false));
            case "DROP_ALL_ITEMS" -> pry$record(s -> s.onDrop(true));
            case "RELEASE_USE_ITEM" -> pry$record(s -> s.onReleaseUse());
            case "SWAP_ITEM_WITH_OFFHAND" -> pry$record(s -> s.onSwapHands());
            default -> {
                // STAB（1.21.11+）/ CHANGE_DESTROY_DIRECTION（26.3+）不映射，见 m1 报告
            }
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("TAIL"))
    private void pry$onUseOn(net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet, CallbackInfo ci)
    {
        //#if MC >= 260300
        //$$ HitResult hit = packet.hitResult();
        //$$ pry$record(s -> s.onUseItemOn(packet.hand(), hit));
        //#else
        HitResult hit = packet.getHitResult();
        pry$record(s -> s.onUseItemOn(packet.getHand(), hit));
        //#endif
    }

    @Inject(method = "handleUseItem", at = @At("TAIL"))
    private void pry$onUse(net.minecraft.network.protocol.game.ServerboundUseItemPacket packet, CallbackInfo ci)
    {
        //#if MC >= 260300
        //$$ pry$record(s -> s.onUseItem(packet.hand()));
        //#else
        pry$record(s -> s.onUseItem(packet.getHand()));
        //#endif
    }

    @Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
    private void pry$onSlot(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket packet, CallbackInfo ci)
    {
        pry$record(s -> s.onSelectSlot(packet.getSlot()));
    }

    //#if MC < 260300
    //$$ @Inject(method = "handleAnimate", at = @At("TAIL"))
    //$$ private void pry$onSwing(net.minecraft.network.protocol.game.ServerboundSwingPacket packet, CallbackInfo ci)
    //$$ {
    //$$     pry$record(s -> s.onSwing(packet.getHand()));
    //$$ }
    //#endif

    //#if MC >= 260300
    //$$ @Inject(method = "handlePunch", at = @At("TAIL"))
    //$$ private void pry$onPunch(net.minecraft.network.protocol.game.ServerboundPunchPacket packet, CallbackInfo ci)
    //$$ {
    //$$     // 26.3 挥动包为空载荷：按主手挥动记录（攻击实体由 handleAttack 单独记录）
    //$$     pry$record(s -> s.onSwing(net.minecraft.world.InteractionHand.MAIN_HAND));
    //$$ }
    //#endif

    //#if MC < 260102
    @Inject(method = "handleInteract", at = @At("TAIL"))
    private void pry$onInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, CallbackInfo ci)
    {
        // 1.21~26.1：同一包承载 ATTACK / INTERACT / INTERACT_AT，dispatch 回调给出动作类型与手
        // （javap 实证：Handler.onAttack/onInteraction(hand)/onInteraction(hand,vec)）
        net.minecraft.world.entity.Entity target = packet.getTarget(pry$serverLevel());
        final int id = target == null ? -1 : target.getId();
        packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler()
        {
            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand)
            {
                if (id >= 0)
                {
                    pry$record(s -> s.onUseEntity(hand, id));
                }
            }

            @Override
            public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 location)
            {
                // INTERACT_AT 与紧随的 INTERACT 由会话层按实体+相邻 Tick 去重
                if (id >= 0)
                {
                    pry$record(s -> s.onUseEntity(hand, id));
                }
            }

            @Override
            public void onAttack()
            {
                if (id >= 0)
                {
                    pry$record(s -> s.onAttackEntity(id));
                }
            }
        });
    }
    //#endif

    //#if MC >= 260102
    //$$ @Inject(method = "handleInteract", at = @At("TAIL"))
    //$$ private void pry$onInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, CallbackInfo ci)
    //$$ {
    //$$     // 26.1+ 攻击已拆到 ServerboundAttackPacket，本包只剩实体右键
    //$$     net.minecraft.world.InteractionHand hand = packet.hand();
    //$$     int id = packet.entityId();
    //$$     pry$record(s -> s.onUseEntity(
    //$$             hand == null ? net.minecraft.world.InteractionHand.MAIN_HAND : hand, id));
    //$$ }
    //$$
    //$$ @Inject(method = "handleAttack", at = @At("TAIL"))
    //$$ private void pry$onAttack(net.minecraft.network.protocol.game.ServerboundAttackPacket packet, CallbackInfo ci)
    //$$ {
    //$$     pry$record(s -> s.onAttackEntity(packet.entityId()));
    //$$ }
    //#endif
}
