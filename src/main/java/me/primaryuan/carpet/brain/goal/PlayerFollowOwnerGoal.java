package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 追随主人目标（← 原版 {@code FollowOwnerGoal(1.0, 10F, 2F)}，26.3/1.21.11
 * 字节码逐条对齐，狼 pri 6）。
 *
 * <p>启停阈值（原版字段语义）：<b>startDistance=10</b> 是启动阈值——距主人
 * ≥10 格才开始跟随（{@code canUse: distanceToSqr >= startDistance²}）；
 * <b>stopDistance=2</b> 是结束阈值——跟随途中距主人 ≤2 格即结束
 * （{@code canContinueToUse: !navigation.isDone() && distanceToSqr > stopDistance²}）。
 * 不是"十格外全速、两格外慢速"的分档：运行中始终以 speedModifier=1.0 直奔主人
 * （原版 tick 每 adjustedTickDelay(10)=10 游戏刻重算路径），无 0.6 慢速档。</p>
 *
 * <p>start/stop 临时把水路 malus 置 0/还原（原版 setPathfindingMalus(WATER, 0)
 * 同款，跟主人下水）。原版的传送回主人（tryToTeleportToOwner）属坐标瞬移，
 * 假人不实现（只换脑不换身体红线），照常寻路跟随。</p>
 */
public class PlayerFollowOwnerGoal extends PlayerGoal {

    /** 启动阈值（格）：原版 FollowOwnerGoal 构造第三参 startDistance=10 */
    private static final float START_DISTANCE = 10.0F;
    /** 结束阈值（格）：原版构造第四参 stopDistance=2 */
    private static final float STOP_DISTANCE = 2.0F;
    /** 路径重算周期（tick）：原版 tick 内 adjustedTickDelay(10)（非 rUE → 每 2 tick 推 1 次） */
    private static final int RECALC_DELAY_TICKS = 10;

    private final PryMob mob;
    private final UUID ownerUuid;
    private final double speedModifier;
    private int timeToRecalcPath;
    private double oldWaterMalus;

    public PlayerFollowOwnerGoal(PryMob mob, UUID ownerUuid, double speedModifier) {
        this.mob = mob;
        this.ownerUuid = ownerUuid;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(1); // 原版无内部节流：每 2 tick 评估一次
    }

    @Override
    public boolean canUse() {
        ServerPlayer owner = this.resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        // 同维度才跟随；跨维度不追（原版传送回主人不实现，寻路只在本世界）
        return owner.level() == this.mob.level()
                && this.mob.distanceToSqr(owner) >= (double) (START_DISTANCE * START_DISTANCE);
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer owner = this.resolveOwner();
        return owner != null && owner.isAlive()
                && owner.level() == this.mob.level()
                && !this.mob.getNavigation().isDone()
                && this.mob.distanceToSqr(owner) > (double) (STOP_DISTANCE * STOP_DISTANCE);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        // 原版 setPathfindingMalus(WATER, 0)：跟随期间允许下水，结束还原
        this.oldWaterMalus = this.mob.fields().getWaterMalus();
        this.mob.fields().setWaterMalus(0.0);
    }

    @Override
    public void stop() {
        this.mob.fields().setWaterMalus(this.oldWaterMalus);
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.resolveOwner();
        if (owner == null) {
            return;
        }
        // 原版同款：不该传送到主人时面向主人（移速 10、水平角限制 maxHeadXRot）
        this.mob.getLookControl().setLookAt(owner, 10.0F, 30.0F);
        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(RECALC_DELAY_TICKS); // 非 rUE → 5 次推进 = 10 tick
        this.mob.getNavigation().moveTo(owner, this.speedModifier);
    }

    /** 按 UUID 解析主人（主人下线/换世界则视为不在）。protected 供离线测试覆写。 */
    protected ServerPlayer resolveOwner() {
        ServerPlayer player = this.mob.level().getServer() != null
                ? this.mob.level().getServer().getPlayerList().getPlayer(this.ownerUuid) : null;
        return player;
    }
}
