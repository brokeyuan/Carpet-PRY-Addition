package me.primaryuan.carpet.mixins.rule.sleepingDuringTheDay;

//#if MC >= 12111
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * MixinPlayer - 允许白天开始睡觉 (生产版本 - 无日志)。
 *
 * 仅 1.21.11+ 注册：原版将白天入睡检查收口为 BedRule.canSleep(Level)，
 * 本 redirect 在规则开启时放行该检查，使玩家可白天入睡；入睡后的唤醒控制
 * 与"醒来切换至夜晚"由 MixinPlayerBase 处理（所有版本注册）。
 * 白天点床仍会先记录重生点（原版 setRespawnPosition 在该检查之前），怪物
 * 检测（NOT_SAFE）等其他入睡条件不受影响，与 1.21~1.21.10 的行为一致。
 *
 * 1.21~1.21.10 不注册本 mixin：该版本段的检查点（Level.isDay /
 * (Server)Level.isBrightOutside，已逐一核实字节码）由 fabric-entity-events-v1
 * 的 ALLOW_SLEEP_TIME redirect 占用，本类若直接 redirect 同一调用会因
 * @Redirect 冲突被跳过（require=1 下启动失败）。旧版本的白天入睡放行改由
 * CarpetPrimaryuanServer 注册 ALLOW_SLEEP_TIME 监听实现。
 */
@Mixin(net.minecraft.server.level.ServerPlayer.class)
public abstract class MixinPlayer {

    @Redirect(
            method = "startSleepInBed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"
            )
    )
    private boolean redirectCanSleep(BedRule bedRule, Level level) {
        boolean originalResult = bedRule.canSleep(level);

        if (!originalResult && CarpetPrimaryuanSettings.sleepingDuringTheDay) {
            return true;
        }

        return originalResult;
    }
}
//#endif
