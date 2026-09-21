package me.primaryuan.carpet.brain.goal;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 追随主人目标（模式：狼）。自写实现，不复用原版 {@code FollowOwnerGoal}——
 * 后者构造依赖 {@code TamableAnimal} 大接口，跨版本（1.21/26.x）漂移极不稳定。
 *
 * <p>语义（按距离分档）：</p>
 * <ul>
 *   <li>距主人 &gt; 8 格：全速（1.0）走过去；</li>
 *   <li>距主人 &gt; 2.5 格：慢速靠拢（0.6）——"黏人"但不抢路；</li>
 *   <li>距主人 ≤ 2.5 格：原地停住并面向主人（2~6 格待机/面向主人的分档语义）。</li>
 * </ul>
 */
public class PlayerFollowOwnerGoal extends PlayerGoal {

    private static final double FAST_FOLLOW_SQ = 64.0;   // 8 格以上 → 全速
    private static final double SLOW_FOLLOW_SQ = 6.25;   // 2.5 格以上 → 慢速靠拢

    private final PryMob mob;
    private final UUID ownerUuid;
    private final double speedModifier;

    public PlayerFollowOwnerGoal(PryMob mob, UUID ownerUuid, double speedModifier) {
        this.mob = mob;
        this.ownerUuid = ownerUuid;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
        this.setInterval(10);
    }

    @Override
    public boolean canUse() {
        ServerPlayer owner = this.resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        // 同维度才跟随；跨维度不追（传送由其它机制处理）
        return owner.level() == this.mob.level()
                && this.mob.distanceToSqr(owner) > SLOW_FOLLOW_SQ;
    }

    @Override
    public boolean canContinueToUse() {
        ServerPlayer owner = this.resolveOwner();
        return owner != null && owner.isAlive()
                && owner.level() == this.mob.level()
                && this.mob.distanceToSqr(owner) > SLOW_FOLLOW_SQ * 0.8; // 带 20% 滞回，防边界抖动
    }

    @Override
    public void start() {
        ServerPlayer owner = this.resolveOwner();
        if (owner != null) {
            this.mob.getNavigation().moveTo(owner, this.speedModifier);
        }
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerPlayer owner = this.resolveOwner();
        if (owner == null) {
            return;
        }
        double d = this.mob.distanceToSqr(owner);
        if (d > FAST_FOLLOW_SQ) {
            this.mob.getNavigation().moveTo(owner, this.speedModifier);          // 远：全速追
        } else if (d > SLOW_FOLLOW_SQ) {
            this.mob.getNavigation().moveTo(owner, this.speedModifier * 0.6);    // 近：慢速贴
        } else {
            this.mob.getNavigation().stop();                                     // 贴住：停 + 面向主人
            this.mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
    }

    /** 按 UUID 解析主人（主人下线/换世界则视为不在） */
    private ServerPlayer resolveOwner() {
        ServerPlayer player = this.mob.level().getServer() != null
                ? this.mob.level().getServer().getPlayerList().getPlayer(this.ownerUuid) : null;
        return player;
    }
}