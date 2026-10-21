package me.primaryuan.carpet.handler.clickThrough;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;

/**
 * 穿透点击：右键墙告示牌/挂墙木牌/墙横幅/展示框/发光展示框/画，直接打开背后贴挂的容器。
 * 纯服务端实现（Fabric API UseBlock/UseEntity 事件，零 mixin），原版客户端即用。
 *
 * <p>潜行时放行原版交互（编辑告示牌/给告示牌染色/旋转展示框/往展示框放物品）；背后无
 * 容器同样放行。打开走 {@code BlockState.getMenuProvider}——与原版右键同一入口，猫占、
 * 上方被堵等"箱子打不开"的原版校验天然一致。潜影盒额外复刻原版 {@code canOpen} 开盖
 * 空间判定（private，javap 26.3/1.21.4 实证：动画中放行，否则盖子伸出 1 格体积
 * noCollision 才可开）。打开成功后补齐原版 {@code useWithoutItem} 的后续步骤——打开
 * 统计（chest/barrel/shulker）与 {@code PiglinAi.angerNearbyPiglins} 猪灵仇恨，
 * 与原版右键开箱行为对齐。检测到独立服务端模组 clickthrough_server
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
        BlockState behindState = level.getBlockState(behindPos);
        // 潜影盒：开盖空间被堵时原版打不开，穿透点击同样拒绝（对齐 canOpen 语义）
        if (behindState.getBlock() instanceof ShulkerBoxBlock && !canOpenShulker(level, behindPos, behindState)) {
            return InteractionResult.PASS;
        }
        MenuProvider menuProvider = behindState.getMenuProvider(level, behindPos);
        if (menuProvider == null) {
            return InteractionResult.PASS;
        }
        OptionalInt opened = serverPlayer.openMenu(menuProvider);
        if (opened.isPresent()) {
            // 对齐原版 useWithoutItem 后续步骤：打开统计 + 猪灵仇恨（javap 实证）
            if (behindState.getBlock() instanceof ShulkerBoxBlock) {
                serverPlayer.awardStat(Stats.OPEN_SHULKER_BOX);
                angerNearbyPiglins(level, serverPlayer);
            } else if (behindState.getBlock() instanceof BarrelBlock) {
                serverPlayer.awardStat(Stats.OPEN_BARREL);
                angerNearbyPiglins(level, serverPlayer);
            } else if (behindState.getBlock() instanceof ChestBlock) {
                serverPlayer.awardStat(Stats.OPEN_CHEST);
                angerNearbyPiglins(level, serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 猪灵仇恨（对齐原版开箱流程）：1.21~1.21.1 两参，1.21.3 起三参带 level（javap 实证） */
    private static void angerNearbyPiglins(Level level, ServerPlayer player) {
        //#if MC < 12103
        //$$ PiglinAi.angerNearbyPiglins(player, true);
        //#else
        PiglinAi.angerNearbyPiglins((ServerLevel) level, player, true);
        //#endif
    }

    /**
     * 潜影盒开盖空间判定（复刻原版 {@code ShulkerBoxBlock.canOpen}，private 不可调用）：
     * 盖子动画中直接放行；否则按完全伸出（progress=1）计算盖子体积（deflate 1e-6），
     * 与世界无碰撞才可开——盖子被方块顶住的潜影盒原版打不开，穿透点击同样打不开。
     * javap 实证 1.21~26.3 同构，仅盖子体积定位方式分叉：1.21~1.21.3 为四参
     * getProgressDeltaAabb + move(BlockPos)，1.21.4 起为五参直接带底部中心 origin。
     */
    private static boolean canOpenShulker(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity blockEntity
                && blockEntity.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            return true;
        }
        Direction shulkerFacing = state.getValue(ShulkerBoxBlock.FACING);
        //#if MC < 12104
        //$$ AABB lidVolume = Shulker.getProgressDeltaAabb(1.0F, shulkerFacing, 0.0F, 0.5F)
        //$$         .move(pos).deflate(1.0E-6);
        //#else
        Vec3 origin = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        AABB lidVolume = Shulker.getProgressDeltaAabb(1.0F, shulkerFacing, 0.0F, 0.5F, origin)
                .deflate(1.0E-6);
        //#endif
        return level.noCollision(lidVolume);
    }
}
