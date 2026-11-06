package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.Path;
import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * 移植版规避目标（← 原版 {@code AvoidEntityGoal}，26.3 字节码对齐）。
 *
 * <p>语义：检测到视距内出现某类实体（如村民遇僵尸、骷髅遇狼）时，朝远离它的
 * 方向逃跑；距离近（7 格内）加速（sprint 疾跑）。{@code canContinueToUse} =
 * {@code !navigation.isDone() && 威胁存活 && 距威胁 < maxDist²}（原版同款：
 * 逃出 maxDist 即安全停止）。注视与逃跑同向（玩家实体身体朝向 = 前进输入
 * 方向，不能像原版生物那样身体随移动、头部盯威胁）。原版依赖 {@code RandomPos}
 * 选点，这里自包含复刻（反方向 + 随机扇形偏移）。仅规避存活实体。</p>
 *
 * <p>子类条件钩子（对齐原版 Wolf$WolfAvoidEntityGoal 的覆写结构）：
 * {@code extraCondition} 在找到威胁后调用（未通过则本轮不启动）；
 * {@code clearTarget} 为 true 时 start/tick 期间清空攻击目标（原版狼逃羊驼时
 * {@code setTarget(null)} 同款）。</p>
 */
public class PlayerAvoidEntityGoal<T extends LivingEntity> extends PlayerGoal {

    private final PryMob mob;
    private final Class<T> avoidClass;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    private final float maxDist;   // 视野距离：该距离内出现目标就开始规避
    private final float tooClose;  // 该距离内加速（本实现取 7 格，原版 49 平方）
    /** 子类条件钩子（null = 无）：原版 WolfAvoidEntityGoal.canUse 的附加判定 */
    private final Predicate<T> extraCondition;
    /** 逃跑期间是否持续清空攻击目标（原版狼避羊驼同款） */
    private final boolean clearTarget;

    private LivingEntity toAvoid;
    private Path path;
    /** 逃跑路径锚点（start 时从 path 取）：注视方向跟随逃跑路径而非威胁 */
    private BlockPos fleePos;

    public PlayerAvoidEntityGoal(PryMob mob, Class<T> avoidClass, float maxDist,
                                 double walkSpeedModifier, double sprintSpeedModifier) {
        this(mob, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier, null, false);
    }

    /** 完整构造：带子类条件钩子与清目标语义（狼避羊驼用） */
    public PlayerAvoidEntityGoal(PryMob mob, Class<T> avoidClass, float maxDist,
                                 double walkSpeedModifier, double sprintSpeedModifier,
                                 Predicate<T> extraCondition, boolean clearTarget) {
        this.mob = mob;
        this.avoidClass = avoidClass;
        this.maxDist = maxDist;
        this.tooClose = 7.0F;
        this.walkSpeedModifier = walkSpeedModifier;
        this.sprintSpeedModifier = sprintSpeedModifier;
        this.extraCondition = extraCondition;
        this.clearTarget = clearTarget;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版无内部节流：每 2 tick 评估一次
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean canUse() {
        if (this.findToAvoid() == null) {
            return false;
        }
        return this.planFleePath();
    }

    /**
     * 在范围内找规避对象并过子类条件钩子；找到时写入 {@link #toAvoid} 并返回它，
     * 否则返回 null（原版 getNearestEntity + WolfAvoidEntityGoal 钩子的分解）。
     */
    @SuppressWarnings("unchecked")
    protected T findToAvoid() {
        double sx = this.mob.getX();
        double sy = this.mob.getEyePosition().y;
        double sz = this.mob.getZ();
        this.toAvoid = null;
        double bestSq = this.maxDist * this.maxDist;
        // 候选查询独立成方法供离线测试注入（选择/过滤/钩子逻辑本体保持真实执行）
        for (T candidate : this.nearbyAvoidCandidates()) {
            double sq = candidate.distanceToSqr(sx, sy, sz);
            if (sq < bestSq) {
                bestSq = sq;
                this.toAvoid = candidate;
            }
        }
        if (this.toAvoid == null) {
            return null;
        }
        // 原版 WolfAvoidEntityGoal 钩子：类型确认后的附加判定（驯服态/羊驼强度掷骰）
        if (this.extraCondition != null && !this.extraCondition.test((T) this.toAvoid)) {
            this.toAvoid = null;
            return null;
        }
        return (T) this.toAvoid;
    }

    /** 朝远离威胁方向选点并建路；选不出点或建不出路返回 false（原版 canUse 尾段分解） */
    protected boolean planFleePath() {
        BlockPos flee = this.fleePoint(this.toAvoid);
        if (flee == null) {
            return false;
        }
        // 逃跑点必须比"当前位置→威胁"更远才算有效（否则拦在脸上没意义）
        if (this.toAvoid.distanceToSqr(flee.getX() + 0.5, flee.getY(), flee.getZ() + 0.5)
                < this.toAvoid.distanceToSqr(this.mob.getX(), this.mob.getY(), this.mob.getZ())) {
            return false;
        }
        this.path = this.createFleePath(flee);
        return this.path != null;
    }

    /** 建逃跑路径（独立方法供离线测试注入固定路径） */
    protected Path createFleePath(BlockPos flee) {
        return this.mob.getNavigation().createPath(flee.getX() + 0.5, flee.getY(), flee.getZ() + 0.5, 0);
    }

    /**
     * 范围内规避候选（原版 getEntitiesOfClass(Class, inflate(maxDist,3,maxDist))
     * + getNearestEntity(TargetingConditions.forCombat().range(maxDist))）。
     */
    protected List<T> nearbyAvoidCandidates() {
        return this.mob.level().getEntitiesOfClass(this.avoidClass,
                this.mob.getBoundingBox().inflate(this.maxDist, 3.0, this.maxDist),
                // 排除自身：鱼类规避玩家时 avoidClass=Player 会扫到假人本体
                // （把自己当威胁追着逃），任何规避都不应包含自身
                e -> e != this.mob.asLiving() && e.isAlive() && !e.isRemoved());
    }

    @Override
    public void start() {
        // 注视与逃跑同向：玩家实体的身体朝向同时是移动输入的"前方"——若沿用原版
        // "边逃边盯威胁"（生物身体随移动、头部独立），视线控制会把身体转回威胁方向，
        // 与移动控制的逃离转向互相抵消，表现为朝危险前进或原地打转。注视跟随逃跑
        // 路径方向，前进输入才指向远离威胁
        this.fleePos = this.path != null && !this.path.isEmpty() ? this.path.first() : null;
        if (this.clearTarget) {
            this.mob.setTarget(null); // 原版 WolfAvoidEntityGoal.start 同款
        }
        this.mob.getNavigation().moveTo(this.path, this.walkSpeedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        // 原版同款：路径走完 / 威胁消失 / 逃出 maxDist 即结束
        return !this.mob.getNavigation().isDone() && this.toAvoid != null && this.toAvoid.isAlive()
                && this.mob.distanceToSqr(this.toAvoid) < (double) (this.maxDist * this.maxDist);
    }

    @Override
    public void stop() {
        this.toAvoid = null;
        this.fleePos = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.toAvoid != null) {
            if (this.clearTarget) {
                this.mob.setTarget(null); // 原版 WolfAvoidEntityGoal.tick 同款
            }
            if (this.fleePos != null) {
                this.mob.getLookControl().setLookAt(
                        this.fleePos.getX() + 0.5, this.fleePos.getY(), this.fleePos.getZ() + 0.5,
                        30.0F, 30.0F); // 盯逃跑方向，身体朝向与前进输入一致
            }
            // 距离越近跑得越快（原版：小于 7 格切换疾跑速度）
            this.mob.getNavigation().setSpeedModifier(
                    this.mob.distanceToSqr(this.toAvoid) < this.tooClose * this.tooClose
                            ? this.sprintSpeedModifier : this.walkSpeedModifier);
        }
    }

    /** 朝远离威胁方向选逃跑点（最远 8 格），失败重试 40 次。protected 供离线测试覆写。 */
    protected BlockPos fleePoint(LivingEntity threat) {
        double awayX = this.mob.getX() - threat.getX();
        double awayZ = this.mob.getZ() - threat.getZ();
        double len = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (len < 1.0E-4) {
            awayX = 1.0;
            awayZ = 0.0;
            len = 1.0;
        }
        double ux = awayX / len;
        double uz = awayZ / len;
        BlockPos origin = this.mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            double dist = 4.0 + this.mob.getRandom().nextDouble() * 4.0;
            double angle = (this.mob.getRandom().nextDouble() - 0.5) * 1.2;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            BlockPos candidate = origin.offset(
                    Mth.floor((ux * cos - uz * sin) * dist), 0, Mth.floor((ux * sin + uz * cos) * dist));
            if (this.isWalkableStand(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isWalkableStand(BlockPos pos) {
        return PlayerPathNavigation.isWalkableCell(this.mob.level(), pos);
    }
}