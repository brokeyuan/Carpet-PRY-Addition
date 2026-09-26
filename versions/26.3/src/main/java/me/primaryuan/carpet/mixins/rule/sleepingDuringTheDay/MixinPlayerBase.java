package me.primaryuan.carpet.mixins.rule.sleepingDuringTheDay;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinPlayerBase - 醒来时修正时间（生产版本，无日志）- 26.3 版本
 *
 * 与 26.2 版本一致：使用 getOverworldClockTime()（26.x API 变更）。
 * 26.3 变更：startSleepInBed 增加 AbstractBedBlock/BlockState/BedRule 三个前导参数，
 * HEAD 注入 handler 签名同步扩展（注入点语义不变）。
 * 判据与根版本一致：以"入睡时刻是否为白天"为准（startSleepInBed HEAD 记录），
 * 夜间开始的睡眠完全放行原版，仅白天入睡的唤醒由本 mixin 接管。
 */
@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class MixinPlayerBase {

    /** 本次入睡开始时是否为白天；下一次 startSleepInBed 时覆盖，睡眠结束时复位 */
    @Unique
    private boolean pry$startedDuringDay;

    @Inject(method = "startSleepInBed", at = @At("HEAD"))
    private void pry$recordSleepStartDaytime(AbstractBedBlock bed, BlockState state, BedRule rule, BlockPos pos,
            CallbackInfoReturnable<Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, ?>> cir) {
        this.pry$startedDuringDay = pry$isDaytime();
    }

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"
            )
    )
    private void onStopSleepInBed(net.minecraft.world.entity.player.Player player, boolean wakeImmediately, boolean updateLevel, Operation<Void> original) {
        // 规则关闭：完全放行
        if (!CarpetPrimaryuanSettings.sleepingDuringTheDay) {
            original.call(player, wakeImmediately, updateLevel);
            return;
        }

        // 夜间开始的睡眠：完全走原版（黎明唤醒 / 全员睡眠跳夜均由原版处理）
        if (!this.pry$startedDuringDay) {
            original.call(player, wakeImmediately, updateLevel);
            return;
        }

        if (!pry$isDaytime()) {
            // 白天入睡，醒来时已是夜晚：无需跳变，放行并结束本标记
            this.pry$startedDuringDay = false;
            original.call(player, wakeImmediately, updateLevel);
        } else if (player.getSleepTimer() >= 100) {
            // 白天入睡且睡满 100 tick：跳到夜晚并唤醒
            this.pry$startedDuringDay = false;
            setTimeToNight(player);
            original.call(player, wakeImmediately, updateLevel);
        }
        // 白天入睡未满 100 tick：阻止唤醒（标记保留，下一 tick 原版会再次尝试）
    }

    /** 当前世界时间是否处于白天（与根版本判据一致：dayTime mod 24000 < 13000） */
    @Unique
    private boolean pry$isDaytime() {
        return ((net.minecraft.world.entity.player.Player) (Object) this).level() instanceof Level level
                && level.getOverworldClockTime() % 24000L < 13000L;
    }

    /**
     * 设置时间为夜晚（使用 /time set 命令）
     */
    private void setTimeToNight(net.minecraft.world.entity.player.Player player) {
        try {
            if (player.level() instanceof ServerLevel serverLevel) {
                long currentTime = serverLevel.getOverworldClockTime();
                long currentDayTime = currentTime % 24000L;
                long targetTime = currentTime + (13000L - currentDayTime);

                String timeCmd = "/time set " + targetTime;
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    serverLevel.getServer().createCommandSourceStack(),
                    timeCmd
                );
            }
        } catch (Exception e) {
            // 静默失败，不影响游戏体验
        }
    }
}
