package me.primaryuan.carpet.handler.invisibleInTallGrass;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class InvisibleInTallGrassHandler {

    /**
     * 本功能添加的隐身效果标记：无限时长（-1，与 /effect ... infinite 同款表示）。
     * 有限时长会被原版每 tick 递减（MobEffectInstance.tickDownDuration），MAX_VALUE 标记
     * 在添加后的第一 tick 即失效，导致离开草地时无法识别归属、隐身效果永久残留；
     * 无限时长不参与递减，标记稳定。叠加"无粒子"位（添加时 visible=false，药水默认
     * 显示粒子）进一步区分药水来源。残留边界：外部授予的"无限 + 无粒子"隐身
     * （/effect give ... infinite 0 true）会被误判为本功能的，离开草地时一并移除。
     */
    private static final int MARKER_DURATION = MobEffectInstance.INFINITE_DURATION;

    public static void checkAndApplyInvisibility(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        boolean hasOwnEffect = current != null
                && current.getDuration() == MARKER_DURATION
                && !current.isVisible();

        // 规则关闭：仅清理本功能残留的效果，不触碰药水等外部来源的隐身
        if (!CarpetPrimaryuanSettings.invisibleInTallGrass) {
            if (hasOwnEffect) {
                player.removeEffect(MobEffects.INVISIBILITY);
            }
            return;
        }

        boolean inGrass = isInTallGrass(player);

        if (inGrass && current == null) {
            // 仅在无任何隐身时添加（药水隐身在场时不覆盖；药水失效后下一 tick 自动补上）
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    MARKER_DURATION,
                    0,
                    false,
                    false
            ));
        } else if (!inGrass && hasOwnEffect) {
            // 只移除本功能添加的隐身，保留药水效果
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    private static boolean isInTallGrass(Player player) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        BlockPos headPos = BlockPos.containing(eyePos);

        Block block = level.getBlockState(headPos).getBlock();
        return block == Blocks.TALL_GRASS;
    }
}