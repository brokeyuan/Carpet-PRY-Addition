package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import me.primaryuan.carpet.brain.MobFields;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.PlayerLookControl;
import me.primaryuan.carpet.brain.PlayerMoveControl;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PlayerSensing;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 假人"生物化"注入 Mixin（核心架构第 1 步：注入导航器/移动控制/目标选择器）。
 *
 * <p>把 {@link PryMob} 假面接口以 {@code @Implements(prefix = "pry$")} 嫁接到
 * 所有 {@code ServerPlayer}（含 Carpet 假人）上：下面每个 {@code pry$xxx}
 * 方法即接口抽象方法（{@code getNavigation}/{@code getMoveControl}/…）的实装，
 * 全部转发到 {@link MobFields} 惰性状态容器。</p>
 *
 * <p>关键点：</p>
 * <ul>
 *   <li><b>真人零开销</b>：{@link MobFields} 在首次被访问时才创建；真人既无
 *       Brain 也不运行 Goal，几乎永远不会触达——不注入任何每 tick 逻辑；</li>
 *   <li><b>只换脑子、不换身体</b>：注入的都是"逻辑状态"（寻路/移动/视线/目标），
 *       不碰模型/碰撞箱/属性，实体的类型始终是 {@code ServerPlayer}；</li>
 *   <li><b>零额外实体</b>：任何控制器都没有对应的 Entity 对象。</li>
 * </ul>
 */
@Mixin(ServerPlayer.class)
@org.spongepowered.asm.mixin.Implements(
        @org.spongepowered.asm.mixin.Interface(iface = PryMob.class, prefix = "pry$", unique = true)
)
public abstract class ServerPlayerMobMixin {

    /** 惰性状态容器（真人无脑时保持 null，零分配） */
    @Unique
    private MobFields pry$mobFields;

    /**
     * PryMob.fields() 的实装（方法名 = 前缀 + 接口方法名）：
     * 首次访问才创建容器。
     */
    public MobFields pry$fields() {
        if (this.pry$mobFields == null) {
            this.pry$mobFields = new MobFields((ServerPlayer) (Object) this);
        }
        return this.pry$mobFields;
    }

    // ----- 以下均为 PryMob 抽象方法的实装（转发到 MobFields）-----

    public PlayerPathNavigation pry$getNavigation() {
        return this.pry$fields().getNavigation();
    }

    public PlayerMoveControl pry$getMoveControl() {
        return this.pry$fields().getMoveControl();
    }

    public PlayerLookControl pry$getLookControl() {
        return this.pry$fields().getLookControl();
    }

    public PlayerSensing pry$getSensing() {
        return this.pry$fields().getSensing();
    }

    public PlayerGoalSelector pry$getGoalSelector() {
        return this.pry$fields().goalSelector();
    }

    public PlayerGoalSelector pry$getTargetSelector() {
        return this.pry$fields().targetSelector();
    }

    public LivingEntity pry$getTarget() {
        return this.pry$fields().getTarget();
    }

    public void pry$setTarget(LivingEntity target) {
        this.pry$fields().setTarget(target);
    }
}