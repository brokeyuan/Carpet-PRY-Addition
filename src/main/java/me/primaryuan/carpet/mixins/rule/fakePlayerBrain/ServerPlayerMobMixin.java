package me.primaryuan.carpet.mixins.rule.fakePlayerBrain;

import me.primaryuan.carpet.brain.BrainTeams;
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
     *
     * <p>此处是近战与史莱姆跳击全部 {@code doHurtTarget} 调用方的中央友伤门：
     * 目标与本人互为同队假人 → 不出手。长矛刺击伤害由原版动能武器在使用期间
     * 按碰撞结算、不经过此门（与已离手弹道/扫击同类的边界，docs 披露）。</p>
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity living
                && BrainTeams.isTeamFriendly((ServerPlayer) (Object) this, living)) {
            return false;
        }
        ((ServerPlayer) (Object) this).attack(target);
        this.pry$swingMainHand();
        return true;
    }

    /** 主手挥手广播（26.3 起 swing 三参：仅广播观察者，等价旧语义） */
    @Override
    public void prySwingMainHand() {
        this.pry$swingMainHand();
    }

    @Unique
    private void pry$swingMainHand() {
        //#if MC >= 260300
        //$$ ((ServerPlayer) (Object) this).swing(InteractionHand.MAIN_HAND,
        //$$         net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        //#else
        ((ServerPlayer) (Object) this).swing(InteractionHand.MAIN_HAND);
        //#endif
    }

    /**
     * 玩家原生右键方块（PryMob.pryUseItemOnBlock）：走 gameMode.useItemOn 全管线
     * （冷却/旁观门禁/方块自身 use 逻辑/物品消耗/音效/事件）。命中点取方块中心、
     * 面朝上——use 管线不消费命中细节的方块（堆肥桶/浆果丛/作物）与玩家平视
     * 点按等价。距离由调用方按 blockReachSq 把门（此处再兜底一次）。
     */
    @Override
    public boolean pryUseItemOnBlock(net.minecraft.core.BlockPos pos, InteractionHand hand) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (this.pry$blockTooFar(pos)) {
            return false;
        }
        net.minecraft.world.item.ItemStack stack = self.getItemInHand(hand);
        // 空手右键开容器是玩家合法操作（木箱/铜箱等），不放空手门
        net.minecraft.world.InteractionResult result = self.gameMode.useItemOn(
                self, self.level(), stack, hand,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
        return result.consumesAction();
    }

    /**
     * 玩家原生"空手开容器"（PryMob.pryOpenContainer）：同 {@link #pryUseItemOnBlock}
     * 的管线与距离门，但以空手栈交互。容器 use 在非潜行时先于物品 use 执行，
     * 手持方块不影响开箱；传空手栈是为了让"容器不可用"的失败路径落在空物品上，
     * 不把手中方块放到箱顶，也不改动玩家物品栏（无换槽 = 无货物槽失配）。
     */
    @Override
    public boolean pryOpenContainer(net.minecraft.core.BlockPos pos, InteractionHand hand) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (this.pry$blockTooFar(pos)) {
            return false;
        }
        net.minecraft.world.InteractionResult result = self.gameMode.useItemOn(
                self, self.level(), net.minecraft.world.item.ItemStack.EMPTY, hand,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
        return result.consumesAction();
    }

    /** 玩家原生右键使用手中物品（PryMob.pryUseItemInHand）：投掷喷溅药水等即刻型 */
    @Override
    public boolean pryUseItemInHand(InteractionHand hand) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        net.minecraft.world.item.ItemStack stack = self.getItemInHand(hand);
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.world.InteractionResult result = self.gameMode.useItem(
                self, self.level(), stack, hand);
        if (result.consumesAction()) {
            this.pry$swingMainHand();
        }
        return result.consumesAction();
    }

    /** 玩家原生即时破坏（PryMob.pryBreakBlock）：作物等零硬度方块的原版破坏管线 */
    @Override
    public boolean pryBreakBlock(net.minecraft.core.BlockPos pos) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (this.pry$blockTooFar(pos)) {
            return false;
        }
        boolean destroyed = self.gameMode.destroyBlock(pos);
        if (destroyed) {
            this.pry$swingMainHand();
        }
        return destroyed;
    }

    /** 方块交互距离兜底门（网络层校验不覆盖直调 gameMode 的路径） */
    @Unique
    private boolean pry$blockTooFar(net.minecraft.core.BlockPos pos) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        double reachSq = ((PryMob) (Object) self).blockReachSq();
        if (self.getEyePosition().distanceToSqr(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reachSq) {
            return true;
        }
        // 直调 gameMode 绕过网络层射线：补一次玩家视线射线，隔墙不可交互。
        // 目标方块自身命中/MISS/命中点在目标中心之后（低矮作物的射线穿过后
        // 打到下方耕地）都放行；只有中途被其它方块挡住才算不可达。
        net.minecraft.world.phys.Vec3 eye = self.getEyePosition();
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        net.minecraft.world.phys.BlockHitResult hit = self.level().clip(
                new net.minecraft.world.level.ClipContext(
                        eye, center,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        self));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getBlockPos().equals(pos)) {
            return false;
        }
        return hit.getLocation().distanceToSqr(eye) + 1.0E-6 < eye.distanceToSqr(center);
    }
}
