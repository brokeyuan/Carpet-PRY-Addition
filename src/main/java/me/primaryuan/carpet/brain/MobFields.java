package me.primaryuan.carpet.brain;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 玩家身上的"生物式"状态容器（所有 AI 注入状态的唯一持有者）。
 *
 * <p>玩家实体本身没有寻路器/移动控制/攻击目标字段，Carpet 假人也一样——
 * 硬塞进 {@code ServerPlayer} 反而是破坏。这里用一个按玩家惰性挂载的
 * 普通对象存放：{@code ServerPlayerMobMixin} 持有 {MobFields} 引用，
 * {@code PryMob} 的抽象方法全部转发到这里。真人（无脑子）几乎不会触达——
 * 只有移植的 Goal 运行时才会经 {@code PryMob} 访问，零每 tick 开销。</p>
 *
 * <p>所有子部件均<b>惰性创建</b>；寻路器额外做跨维度检测：{@code /tp}
 * 换世界后原实例绑定的旧 {@code Level} 已失效，按需重建（不残留旧世界网格）。</p>
 */
public final class MobFields {

    private final ServerPlayer player;
    private final PryMob prowler;

    /** 玩家借用的攻击目标字段（玩家没有 getTarget/setTarget，移到容器里） */
    private LivingEntity target;

    /** 行为目标选择器（MOVE/LOOK 等）与攻击目标选择器（TARGET） */
    private final PlayerGoalSelector goalSelector = new PlayerGoalSelector();
    private final PlayerGoalSelector targetSelector = new PlayerGoalSelector();

    // 惰性控制器
    private PlayerPathNavigation navigation;
    private PlayerMoveControl moveControl;
    private PlayerLookControl lookControl;
    private PlayerSensing sensing;

    public MobFields(ServerPlayer player) {
        this.player = player;
        this.prowler = (PryMob) (Object) player;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public PryMob prowler() {
        return this.prowler;
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public PlayerGoalSelector goalSelector() {
        return this.goalSelector;
    }

    public PlayerGoalSelector targetSelector() {
        return this.targetSelector;
    }

    public PlayerPathNavigation getNavigation() {
        if (this.navigation == null || this.navigation.level() != this.player.level()) {
            // 惰性创建 + 跨维度重建（/tp 换世界后旧 Level 失效）
            this.navigation = new PlayerPathNavigation(this.player);
        }
        return this.navigation;
    }

    public PlayerMoveControl getMoveControl() {
        if (this.moveControl == null) {
            this.moveControl = new PlayerMoveControl(this.player);
        }
        return this.moveControl;
    }

    public PlayerLookControl getLookControl() {
        if (this.lookControl == null) {
            this.lookControl = new PlayerLookControl(this.player);
        }
        return this.lookControl;
    }

    public PlayerSensing getSensing() {
        if (this.sensing == null) {
            this.sensing = new PlayerSensing(this.player);
        }
        return this.sensing;
    }
}