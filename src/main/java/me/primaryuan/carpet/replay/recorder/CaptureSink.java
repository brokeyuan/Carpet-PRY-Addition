package me.primaryuan.carpet.replay.recorder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

/**
 * 捕获 mixin → 录制会话的只回调接口（避免 mixin 直接依赖 RecordingSession 细节）。
 * 全部回调发生在服务器主线程。
 */
public interface CaptureSink
{
    /** 七类移动输入位掩码（InputFlags）变化；1.21 线从 xxa/zza 阈值化，1.21.2+ 从 Input record */
    void onMovementInput(int mask);

    void onSwing(InteractionHand hand);

    void onAttackEntity(int entityId);

    void onDigStart(BlockPos pos);

    void onDigAbort(BlockPos pos);

    void onDigFinish(BlockPos pos);

    /** 右键：对方块或对实体（hit 为 BlockHitResult 或 EntityHitResult） */
    void onUseItemOn(InteractionHand hand, HitResult hit);

    /** 右键：对实体（INTERACT/INTERACT_AT 合并后的一次点击） */
    void onUseEntity(InteractionHand hand, int entityId);

    /** 右键：对空气（开始持续使用） */
    void onUseItem(InteractionHand hand);

    /** 释放使用（RELEASE_USE_ITEM） */
    void onReleaseUse();

    void onSelectSlot(int slot);

    void onDrop(boolean all);

    void onSwapHands();
}
