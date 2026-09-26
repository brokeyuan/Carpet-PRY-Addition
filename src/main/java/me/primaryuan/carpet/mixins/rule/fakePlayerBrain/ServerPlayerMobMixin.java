package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import me.primaryuan.carpet.brain.MobFields;
import me.primaryuan.carpet.brain.PlayerGoalSelector;
import me.primaryuan.carpet.brain.PlayerLookControl;
import me.primaryuan.carpet.brain.PlayerMoveControl;
import me.primaryuan.carpet.brain.PlayerPathNavigation;
import me.primaryuan.carpet.brain.PlayerSensing;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 假人"生物化"注入 Mixin（核心架构第 1 步：注入导航器/移动控制/目标选择器）。
 *
 * <p>把 {@link PryMob} 假面接口嫁接到所有 {@code ServerPlayer}（含 Carpet 假人）上：
 * mixin 类<b>直接 {@code implements PryMob}</b>——Mixin 会把 mixin 类声明的接口
 * 一并合并进目标类（Carpet 自己的 {@code ServerPlayerInterface} 就是这个写法，
 * 全版本可靠）。最初版本用的 {@code @Implements(prefix=...)} 软嫁接在部分
 * Mixin 版本上不生效（接口没进目标类，运行期 ClassCastException），故废弃。</p>
 *
 * <p>下面每个接口方法的实装全部转发到 {@link MobFields} 惰性状态容器：</p>
 * <ul>
 *   <li><b>真人零开销</b>：{@link MobFields} 在首次被访问时才创建；真人既无
 *       Brain 也不运行 Goal，几乎永远不会触达；</li>
 *   <li><b>只换脑子、不换身体</b>：注入的都是"逻辑状态"（寻路/移动/视线/目标），
 *       不碰模型/碰撞箱/属性，实体的类型始终是 {@code ServerPlayer}；</li>
 *   <li><b>零额外实体</b>：任何控制器都没有对应的 Entity 对象。</li>
 * </ul>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMobMixin implements PryMob {

    /** 惰性状态容器（真人无脑时保持 null，零分配） */
    @Unique
    private MobFields pry$mobFields;

    /** PryMob.fields()：首次访问才创建容器 */
    @Override
    public MobFields fields() {
        if (this.pry$mobFields == null) {
            this.pry$mobFields = new MobFields((ServerPlayer) (Object) this);
        }
        return this.pry$mobFields;
    }

    @Override
    public PlayerPathNavigation getNavigation() {
        return this.fields().getNavigation();
    }

    @Override
    public PlayerMoveControl getMoveControl() {
        return this.fields().getMoveControl();
    }

    @Override
    public PlayerLookControl getLookControl() {
        return this.fields().getLookControl();
    }

    @Override
    public PlayerSensing getSensing() {
        return this.fields().getSensing();
    }

    @Override
    public PlayerGoalSelector getGoalSelector() {
        return this.fields().goalSelector();
    }

    @Override
    public PlayerGoalSelector getTargetSelector() {
        return this.fields().targetSelector();
    }

    @Override
    public LivingEntity getTarget() {
        return this.fields().getTarget();
    }

    @Override
    public void setTarget(LivingEntity target) {
        this.fields().setTarget(target);
    }

    /**
     * 近战攻击转写（PryMob.doHurtTarget）：玩家原生 attack + 挥手广播。
     * 实现在 mixin 上（而非接口 default）以保证 Mixin 合并后分派可达——
     * 接口 default 在合并环境实测不可达（见 PryMob 注释）。
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        ((ServerPlayer) (Object) this).attack(target);
        //#if MC >= 260300
        //$$ // 26.3: swing(hand) 移除，三参 swing(hand, animation, sendToSelf)，false=仅广播观察者（等价旧语义）
        //$$ ((ServerPlayer) (Object) this).swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        //#else
        ((ServerPlayer) (Object) this).swing(InteractionHand.MAIN_HAND);
        //#endif
        return true;
    }
}
