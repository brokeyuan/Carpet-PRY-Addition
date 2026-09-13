package me.primaryuan.carpet.mixins.rule.sleepingDuringTheDay;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinPlayerBase - 醒来时修正时间（生产版本，无日志）。
 *
 * 判据（关键）：以"入睡时刻是否为白天"为准，而非"醒来时刻是否为白天"。
 * {@code startSleepInBed} 的 HEAD 注入在入睡真正发生的时刻记录当前是否白天
 * （ServerPlayer.startSleepInBed 在全部原版校验通过后于末尾调用 super，本注入
 * 全版本命中；校验失败不会调用 super，不会留下脏标记）。夜间开始的睡眠完全
 * 放行原版——旧版按"醒来时是白天 + sleepTimer≥100"判定，会把夜间睡过夜、
 * 黎明被原版唤醒的玩家也拨回夜晚，昼夜循环被破坏。
 *
 * 白天入睡的唤醒分支（原版 Player.tick 检测到不可入睡时段会逐 tick 尝试唤醒）：
 *   - 醒来时是白天 + sleepTimer&lt;100  → 阻止唤醒（保持睡觉状态，睡满为止）
 *   - 醒来时是白天 + sleepTimer≥100 → 设置时间为夜晚并允许唤醒
 *   - 醒来时已是夜晚 → 放行原版（无需跳变）
 */
@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class MixinPlayerBase {

    /** 本次入睡开始时是否为白天；下一次 startSleepInBed 时覆盖，睡眠结束时复位 */
    @Unique
    private boolean pry$startedDuringDay;

    @Inject(method = "startSleepInBed", at = @At("HEAD"))
    private void pry$recordSleepStartDaytime(BlockPos pos,
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

    /** 当前世界时间是否处于白天（与旧版判据一致：dayTime mod 24000 < 13000） */
    @Unique
    private boolean pry$isDaytime() {
        return ((net.minecraft.world.entity.player.Player) (Object) this).level() instanceof Level level
                && level.getDayTime() % 24000L < 13000L;
    }

    /**
     * 设置时间为夜晚（使用 /time set 命令）
     */
    private void setTimeToNight(net.minecraft.world.entity.player.Player player) {
        try {
            if (player.level() instanceof ServerLevel serverLevel) {
                long currentTime = serverLevel.getServer().overworld().getDayTime();
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
