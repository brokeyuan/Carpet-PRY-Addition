package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * 移植版乞食目标（← 原版 {@code BegGoal}，26.3/1.21.11 字节码对齐，狼模式 pri 9）。
 *
 * <p>语义（字节码逐条）：8 格内最近的手持"骨头或狼食物"的玩家（
 * {@code playerHoldingInteresting = stack.is(Items.BONE) || wolf.isFood(stack)}，
 * 1.21.11/26.3 均无驯服门，主手或副手皆算）；狼停下歪头注视（原版
 * setIsInterested 姿态，假人用注视表达）；玩家距离超出 8 格、死亡或注视时长
 * 耗尽即结束。{@code lookTime = adjustedTickDelay(40 + nextInt(40))}（非 rUE，
 * 每 2 tick 递减 1 次 → 时长 40~80 游戏刻，原版同款换算），无冷却，纯 LOOK
 * 旗标——与移动类 Goal 并行，被战斗/跟随抢占。</p>
 */
public class PlayerBegGoal extends PlayerGoal {

    /** 乞食范围（格，原版 BegGoal(wolf, 8.0F)） */
    private static final float LOOK_DISTANCE = 8.0F;
    /** 注视时长基准（tick）：原版 start() = adjustedTickDelay(40 + nextInt(40)) */
    private static final int LOOK_TIME = 40;

    private final PryMob mob;
    private Player player;
    private int lookTime;

    public PlayerBegGoal(PryMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版无冷却层，每 2 tick 评估
    }

    /** 原版 playerHoldingInteresting：主手或副手持骨头或狼食物（无驯服门） */
    private boolean holdingInteresting(Player player) {
        return isInteresting(player.getMainHandItem()) || isInteresting(player.getOffhandItem());
    }

    private boolean isInteresting(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(net.minecraft.world.item.Items.BONE) || stack.is(ItemTags.WOLF_FOOD);
    }

    @Override
    public boolean canUse() {
        this.player = this.findBegTarget();
        return this.player != null;
    }

    /**
     * 8 格内最近的持骨头/狼食物玩家（原版经 TargetingConditions 距离选择后复核
     * 持物）。抽取为独立方法供离线测试注入候选（选择/过滤逻辑本体保持真实执行）。
     */
    protected Player findBegTarget() {
        Player best = null;
        double bestSq = LOOK_DISTANCE * LOOK_DISTANCE;
        for (Player candidate : this.nearbyBegCandidates()) {
            if (candidate == this.mob.asLiving() || !candidate.isAlive()
                    || candidate.isSpectator() || !this.holdingInteresting(candidate)) {
                continue;
            }
            double sq = this.mob.distanceToSqr(candidate);
            if (sq < bestSq) {
                bestSq = sq;
                best = candidate;
            }
        }
        return best;
    }

    /** 乞食范围候选（原版 getNearestPlayer(lookDistance)）。独立方法供离线测试覆写。 */
    protected java.util.List<Player> nearbyBegCandidates() {
        return this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(LOOK_DISTANCE, 3.0F, LOOK_DISTANCE));
    }

    @Override
    public boolean canContinueToUse() {
        if (this.player == null || !this.player.isAlive()) {
            return false;
        }
        if (this.mob.distanceToSqr(this.player) > (double) (LOOK_DISTANCE * LOOK_DISTANCE)) {
            return false;
        }
        // lookTime > 0 且玩家仍持骨头/狼食物才继续（lookTime 在 tick() 递减）
        return this.lookTime > 0 && this.holdingInteresting(this.player);
    }

    @Override
    public void start() {
        this.lookTime = this.adjustedTickDelay(LOOK_TIME + this.mob.getRandom().nextInt(LOOK_TIME));
    }

    @Override
    public void stop() {
        this.player = null;
    }

    @Override
    public void tick() {
        if (this.player == null) {
            return;
        }
        // 原版 tick：注视玩家眼睛（水平角 10、垂直角 maxHeadXRot），lookTime 每
        // tick() 调用递减（非 rUE → 每 2 游戏刻 1 次）
        this.mob.getLookControl().setLookAt(this.player, 10.0F, 30.0F);
        this.lookTime--;
    }
}
