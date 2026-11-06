package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 狐狸采莓目标（← 原版 {@code Fox.EatBerriesGoal}，26.3 类结构核实）。
 * 玩家身体映射：对成熟甜浆果丛 {@code useItemOn} 玩家右键采摘——原版方块逻辑
 * 掉落浆果并回退生长阶段（丛不破坏），掉落物经玩家碰撞拾取。
 *
 * <p><b>原版行为依据</b>：{@code SweetBerryBush.use}：age &gt; 1 时掉落
 * {@code getCount=1+random(2)}（age==MAX 额外 +1）并置 age=1； {@code
 * Fox.EatBerriesGoal} 采莓条件 age &gt; 1（同款阈值），搜索盒水平 12 / 垂直 1
 * （原版构造参数 1.2F, 12, 1）。重采冷却 40 tick（原版类内 40t 节奏）。</p>
 */
public class PlayerFoxEatBerriesGoal extends PlayerGoal {

    private static final int SEARCH_HORIZONTAL = 12;
    private static final int SEARCH_VERTICAL = 1;
    private static final int RESEARCH_COOLDOWN_TICKS = 40;
    private static final double ARRIVE_DIST_SQ = 2.0;

    protected final PryMob mob;

    private BlockPos bushTarget;
    private long nextScanTime;

    public PlayerFoxEatBerriesGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    protected BlockState readStateAt(Level level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    private static final IntegerProperty AGE = SweetBerryBushBlock.AGE;

    @Override
    public boolean canUse() {
        long now = this.mob.asPlayer().level().getGameTime();
        if (now < this.nextScanTime) {
            return false;
        }
        this.nextScanTime = now + RESEARCH_COOLDOWN_TICKS;
        return this.scanBush() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.bushTarget != null;
    }

    @Override
    public void tick() {
        Level level = this.mob.asPlayer().level();
        long now = level.getGameTime();
        if (this.bushTarget == null) {
            this.bushTarget = this.scanBush();
            if (this.bushTarget == null) {
                return;
            }
            this.nextScanTime = now + RESEARCH_COOLDOWN_TICKS;
        }
        BlockState state = this.readStateAt(level, this.bushTarget);
        if (!(state.getBlock() instanceof SweetBerryBushBlock)
                || state.getValue(AGE) <= 1) {
            this.bushTarget = null; // 被摘过/破坏：重扫
            return;
        }
        double distSq = this.mob.distanceToSqr(
                this.bushTarget.getX() + 0.5, this.bushTarget.getY(), this.bushTarget.getZ() + 0.5);
        if (distSq > ARRIVE_DIST_SQ) {
            if (this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(
                        this.bushTarget.getX() + 0.5, this.bushTarget.getY(), this.bushTarget.getZ() + 0.5, 1.2);
            }
            return;
        }
        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(
                this.bushTarget.getX() + 0.5, this.bushTarget.getY() + 0.5, this.bushTarget.getZ() + 0.5, 30.0F, 30.0F);
        // 玩家右键采摘：原版 use 掉落浆果 + age 回退 1（丛保留）
        this.mob.pryUseItemOnBlock(this.bushTarget, InteractionHand.MAIN_HAND);
        this.bushTarget = null;
        this.nextScanTime = now + RESEARCH_COOLDOWN_TICKS;
    }

    @Override
    public void stop() {
        this.bushTarget = null;
        this.mob.getNavigation().stop();
    }

    /** 成熟浆果丛扫描（水平 12 / 垂直 1，原版 EatBerriesGoal 盒） */
    private BlockPos scanBush() {
        BlockPos origin = this.mob.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_HORIZONTAL, -SEARCH_VERTICAL, -SEARCH_HORIZONTAL),
                origin.offset(SEARCH_HORIZONTAL, SEARCH_VERTICAL, SEARCH_HORIZONTAL))) {
            BlockState state = this.readStateAt(this.mob.asPlayer().level(), pos.immutable());
            if (state.getBlock() instanceof SweetBerryBushBlock && state.getValue(AGE) > 1) {
                double d = this.mob.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
