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
     * 本功能添加的隐身效果时长标记（无限时长）。
     * 药水等外部来源的隐身时长为有限值，借此区分效果归属，
     * 避免: 玩家喝隐身药水后走出草地时误删药水效果 / 药水生效期间重复添加。
     */
    private static final int MARKER_DURATION = Integer.MAX_VALUE;

    public static void checkAndApplyInvisibility(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        boolean hasOwnEffect = current != null && current.getDuration() == MARKER_DURATION;

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