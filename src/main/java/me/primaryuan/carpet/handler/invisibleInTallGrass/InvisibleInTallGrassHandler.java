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
     * 本功能添加的隐身效果标记：无限时长 + 无粒子 + <b>ambient</b>。有限时长会被
     * 原版每 tick 递减（MobEffectInstance.tickDownDuration），MAX_VALUE 标记
     * 在添加后的第一 tick 即失效，导致离开草地时无法识别归属、隐身效果永久残留；
     * 无限时长不参与递减，标记稳定。ambient 位是关键归属标记：原版 /effect give
     * 无法设置 ambient（只能给有限时长 + 可选无粒子），数据包也造不出
     * "无限 + 无粒子 + ambient"的玩家效果——外部授予的同类隐身不会被误判为
     * 本功能的（旧标记只看"无限 + 无粒子"，规则关闭时会误删管理员授予的隐身）。
     * 视觉不变：ambient + visible=false 同样无粒子、无 HUD 图标。
     */
    private static final int MARKER_DURATION = MobEffectInstance.INFINITE_DURATION;

    public static void checkAndApplyInvisibility(Player player) {
        // 仅服务端处理：本 mixin 注入 Player.tick 双端执行，而规则值不同步到客户端
        // （clientSync=false）。联机时装了本 mod 的客户端若不加守卫，会把服务端
        // 施加的隐身药水误判为"规则关闭清理残留"而本地移除，与服务端视觉状态冲突。
        if (player.level().isClientSide()) {
            return;
        }

        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        boolean hasOwnEffect = current != null
                && current.getDuration() == MARKER_DURATION
                && !current.isVisible()
                && current.isAmbient();

        // 规则关闭：仅清理本功能残留的效果，不触碰药水等外部来源的隐身
        if (!CarpetPrimaryuanSettings.invisibleInTallGrass) {
            if (hasOwnEffect) {
                player.removeEffect(MobEffects.INVISIBILITY);
            }
            return;
        }

        boolean inGrass = isInTallGrass(player);

        if (inGrass && current == null) {
            // 仅在无任何隐身时添加（药水隐身在场时不覆盖；药水失效后下一 tick 自动补上）；
            // ambient=true 是归属标记（见类注释），命令/数据包不可伪造
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    MARKER_DURATION,
                    0,
                    true,
                    false
            ));
        } else if (!inGrass && hasOwnEffect) {
            // 只移除本功能添加的隐身，保留药水/指令效果
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