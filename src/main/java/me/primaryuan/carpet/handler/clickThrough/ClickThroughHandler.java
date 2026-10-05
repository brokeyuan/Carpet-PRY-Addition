package me.primaryuan.carpet.handler.clickThrough;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 穿透点击：右键墙告示牌/挂墙木牌/墙横幅/展示框/发光展示框/画，直接打开背后贴挂的容器。
 * 纯服务端实现（Fabric API UseBlock/UseEntity 事件，零 mixin），原版客户端即用。
 *
 * <p>潜行时放行原版交互（编辑告示牌/给告示牌染色/旋转展示框/往展示框放物品）；背后无
 * 容器同样放行。打开走 {@code BlockState.getMenuProvider}——与原版右键同一入口，猫占、
 * 上方被堵等"箱子打不开"的原版校验天然一致。检测到独立服务端模组 clickthrough_server
 * 时整体让路（软依赖红线：suggests + isModLoaded，同 /tpp 对 TIS 的先例）；客户端装的
 * 原版 ClickThrough（gbl）改的是准星目标，发来的是普通右键包，双方天然兼容无需让路。</p>
 */
public final class ClickThroughHandler {

    /** 装了 clickthrough_server 模组时本规则整体不生效，避免同一交互双开容器 */
    private static final boolean SERVER_MOD_PRESENT =
            FabricLoader.getInstance().isModLoaded("clickthrough_server");

    private ClickThroughHandler() {}

    /** UseBlockCallback：墙告示牌/挂墙木牌/墙横幅（立式告示牌/站立横幅无水平朝向不处理） */
    public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (!CarpetPrimaryuanSettings.clickThrough
                || SERVER_MOD_PRESENT
                || level.isClientSide()
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WallSignBlock)
                && !(state.getBlock() instanceof WallHangingSignBlock)
                && !(state.getBlock() instanceof WallBannerBlock)) {
            return InteractionResult.PASS;
        }
        return passUnlessOpen(serverPlayer, player, level,
                pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    /** UseEntityCallback：展示框/发光展示框/画。裸 INTERACT 包 hitResult 为 null 直接放行（单次触发门控，同骑乘拦截口径） */
    public static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        if (!CarpetPrimaryuanSettings.clickThrough
                || SERVER_MOD_PRESENT
                || level.isClientSide()
                || !(player instanceof ServerPlayer serverPlayer)
                || hitResult == null) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof HangingEntity hanging)) {
            return InteractionResult.PASS;
        }
        return passUnlessOpen(serverPlayer, player, level,
                hanging.blockPosition(), hanging.getDirection());
    }

    /** 旁观者/潜行放行；背后无容器放行；有则打开并消费交互 */
    private static InteractionResult passUnlessOpen(ServerPlayer serverPlayer, Player player,
                                                    Level level, BlockPos frontPos, Direction facing) {
        if (player.isSpectator() || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        BlockPos behindPos = frontPos.relative(facing.getOpposite());
        MenuProvider menuProvider = level.getBlockState(behindPos).getMenuProvider(level, behindPos);
        if (menuProvider == null) {
            return InteractionResult.PASS;
        }
        serverPlayer.openMenu(menuProvider);
        return InteractionResult.SUCCESS;
    }
}
