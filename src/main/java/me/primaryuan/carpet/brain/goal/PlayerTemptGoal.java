package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * 移植版食物引诱目标（← 原版 {@code TemptGoal}，26.3/1.21.11 字节码逐条对齐）。
 *
 * <p>调度对齐原版：选择器每 2 tick 评估一次 canUse；{@code calmDown} 在 canUse
 * 内自减（每评估一次减 1），{@code stop()} 置 {@code reducedTickDelay(100)}=50
 * 次评估 ≈ 100 游戏刻防抖（原版同款换算）。</p>
 *
 * <p>目标选择：范围内最近的手持引诱食物（主手或副手）的玩家（原版经
 * TargetingConditions 选择器过滤持物，即"最近的<b>持食物</b>玩家"，TEMPT_RANGE
 * 属性 Animal 基座 10，猪未覆写）。{@code canContinueToUse} 末尾直接复跑
 * canUse 重扫——旧引诱者走远/收起食物/死亡/跨维即被重新选择或结束，无需
 * 单独失效分支；多人同持食物时每次评估取最近者（原版同款，可随站位切换）。
 * 停走距离 {@code stopDistance}=2.5（原版构造默认值），贴脸后站定注视。</p>
 *
 * <p>canScare 语义（构造参数）：为 true 时玩家在 6 格内移动或转头即中止
 * （猫/豹猫同款）；猪构造传 false（原版同款，不受玩家动作吓跑）。</p>
 */
public class PlayerTemptGoal extends PlayerGoal {

    /** 引诱范围（格）：原版 TEMPT_RANGE 属性（Animal 基座 10.0，Pig 未覆写） */
    private static final double RANGE_SQ = 10.0 * 10.0;
    /** 受惊距离平方：原版 canContinueToUse 内 {@code distanceToSqr <= 36.0} 且玩家在动 */
    private static final double SCARE_DIST_SQ = 6.0 * 6.0;
    /** 停走距离：原版 stopDistance 构造默认 2.5（tick() 内 distanceToSqr < stopDistance²） */
    private static final double STOP_DIST_SQ = 2.5 * 2.5;
    /** 原版 stop() 的防抖冷却：reducedTickDelay(100) = 50 次评估 ≈ 100 游戏刻 */
    private static final int CALM_DOWN_EVALUATIONS = 50;

    private final PryMob mob;
    private final TagKey<net.minecraft.world.item.Item> foodTag;
    private final net.minecraft.world.item.Item singleItem;
    private final double speed;
    private final boolean canScare;
    private Player tempter;
    /** 原版 calmDown：canUse 每评估自减，stop() 置 50 */
    private int calmDown;
    /** 受惊检测锚点（原版 px/py/pz + pRotX/pRotY：玩家动了就怕） */
    private double lastX;
    private double lastY;
    private double lastZ;
    private double lastXRot;
    private double lastYRot;

    /** 引诱物品：标签（如 {@code #pig_food}，ItemTags.PIG_FOOD） */
    public PlayerTemptGoal(PryMob mob, TagKey<net.minecraft.world.item.Item> foodTag, double speed) {
        this(mob, foodTag, null, speed, false);
    }

    /** 引诱物品：单件（如 {@code Items.CARROT_ON_A_STICK}，猪的第二条引诱） */
    public PlayerTemptGoal(PryMob mob, net.minecraft.world.item.Item item, double speed) {
        this(mob, null, item, speed, false);
    }

    /** 完整构造：canScare=true 时启用原版受惊语义（猫/豹猫），canScare=false 常规 */
    public PlayerTemptGoal(PryMob mob, TagKey<net.minecraft.world.item.Item> foodTag,
                           net.minecraft.world.item.Item singleItem, double speed, boolean canScare) {
        this.mob = mob;
        this.foodTag = foodTag;
        this.singleItem = singleItem;
        this.speed = speed;
        this.canScare = canScare;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版每 2 tick 评估（calmDown 自减语义按评估次数计）
    }

    /** 该玩家是否手持引诱物（主手或副手，原版 shouldFollow 同款双查） */
    private boolean isTempting(Player player) {
        return isFood(player.getMainHandItem()) || isFood(player.getOffhandItem());
    }

    private boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (this.foodTag != null && stack.is(this.foodTag)) {
            return true;
        }
        return this.singleItem != null && stack.is(this.singleItem);
    }

    @Override
    public boolean canUse() {
        // 原版 canUse 首分支：calmDown > 0 时自减并直接拒绝（按评估次数）
        if (this.calmDown > 0) {
            this.calmDown--;
            return false;
        }
        this.tempter = this.findTempter();
        return this.tempter != null;
    }

    /**
     * 范围内最近的持引诱物玩家（原版经 TargetingConditions 选择器在
     * getNearestPlayer 内过滤持物，等价于"最近的持食物玩家"）。
     * 抽取为独立方法供离线测试注入候选（选择/过滤逻辑本体保持真实执行）。
     */
    protected Player findTempter() {
        Player best = null;
        double bestSq = RANGE_SQ;
        for (Player candidate : this.nearbyTemptCandidates()) {
            if (candidate == this.mob.asLiving() || !candidate.isAlive()
                    || candidate.isSpectator() || !this.isTempting(candidate)) {
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

    /**
     * 引诱范围候选（原版 getNearestPlayer(TargetingConditions.range(TEMPT_RANGE),
     * ignoreLineOfSight)）。独立方法供离线测试覆写。
     */
    protected java.util.List<Player> nearbyTemptCandidates() {
        return this.mob.level().getEntitiesOfClass(Player.class,
                this.mob.getBoundingBox().inflate(10.0, 3.0, 10.0));
    }

    @Override
    public boolean canContinueToUse() {
        // 原版结构：canScare 分支（玩家在 6 格内动了/转头 → 受惊中止），
        // 之后直接复跑 canUse() 重扫最近持食物玩家——失效/切换全在其中
        if (this.canScare && !this.scareChecksPass()) {
            return false;
        }
        return this.canUse();
    }

    /** 原版 canScare 分支体：距离 ≤6 格且玩家位移/视角变化 → false；锚点随评估滚动 */
    private boolean scareChecksPass() {
        if (this.tempter == null || this.mob.distanceToSqr(this.tempter) >= SCARE_DIST_SQ) {
            this.updateScareAnchor();
            return true;
        }
        double dx = this.tempter.getX() - this.lastX;
        double dy = this.tempter.getY() - this.lastY;
        double dz = this.tempter.getZ() - this.lastZ;
        if (dx * dx + dy * dy + dz * dz > 0.010000000000000002D) {
            return false;
        }
        if (Math.abs(this.tempter.getXRot() - this.lastXRot) > 5.0D
                || Math.abs(this.tempter.getYRot() - this.lastYRot) > 5.0D) {
            return false;
        }
        this.updateScareAnchor();
        return true;
    }

    private void updateScareAnchor() {
        if (this.tempter == null) {
            return;
        }
        this.lastX = this.tempter.getX();
        this.lastY = this.tempter.getY();
        this.lastZ = this.tempter.getZ();
        this.lastXRot = this.tempter.getXRot();
        this.lastYRot = this.tempter.getYRot();
    }

    @Override
    public void start() {
        this.calmDown = 0;
        this.updateScareAnchor();
    }

    @Override
    public void stop() {
        this.tempter = null;
        this.mob.getNavigation().stop();
        this.calmDown = CALM_DOWN_EVALUATIONS; // 原版 stop(): calmDown = reducedTickDelay(100)
    }

    @Override
    public void tick() {
        if (this.tempter == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(this.tempter, 30.0F, 30.0F);
        if (this.mob.distanceToSqr(this.tempter) < STOP_DIST_SQ) {
            this.mob.getNavigation().stop();
        } else {
            this.mob.getNavigation().moveTo(this.tempter, this.speed);
        }
    }
}
