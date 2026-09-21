package me.primaryuan.carpet.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * "玩家-生物"假面接口（Pry = Make them Player-ly）。
 *
 * <p><b>为什么需要它（Java 类型墙）</b>：原版 {@code Goal} 的构造器参数与内部字段
 * 类型是 {@code PathfinderMob}/{@code Mob}，而假人 100% 保持 {@code ServerPlayer}
 * （红线：只换脑子、不换身体）。{@code ServerPlayer} 不是其子类——直接把原版 Goal
 * 挂上来会 {@code ClassCastException}/{@code NoSuchMethodError}（方法在编译期被
 * 定位在 {@code Mob} 上）。</p>
 *
 * <p><b>解法</b>：逐行移植原版 Goal 源码（算法 1:1），把内部对 {@code mob} 的引用
 * 全部换成这个接口；接口由 {@code ServerPlayerMobMixin} 以
 * {@code @Implements(prefix = "pry$")} 嫁接到 {@code ServerPlayer} 上。
 * 于是移植 Goal 在编译期只依赖 {@code PryMob}，运行期 {@code (PryMob) player}
 * 就是玩家本人——零额外实体、零凭空造物。</p>
 *
 * <p><b>方法分两类</b>：</p>
 * <ul>
 *   <li><b>抽象方法</b>：玩家身上【不存在】的状态（寻路器/移动控制/视线/感知/
 *       双目标选择器/攻击目标字段），由 Mixin 转发到 {@link MobFields} 惰性容器；</li>
 *   <li><b>default 方法</b>：{@code LivingEntity}/{@code Player} 本身就有、或可以
 *       用玩家原生方法转写的，直接用 {@code (Player)(Object)this} 强转转发——
 *       实现类必然是 {@code ServerPlayer}（Mixin 保证），不存在转写成本。</li>
 * </ul>
 */
public interface PryMob {

    // ==================== 抽象方法：由 ServerPlayerMobMixin 提供 ====================

    /** 该玩家所属的 AI 状态容器（惰性创建） */
    MobFields fields();

    /** 玩家身上的自研 A* 寻路器 */
    PlayerPathNavigation getNavigation();

    /** 玩家身上的移动控制器（AI 移动指令 → 原生 movementInput 的唯一翻译器） */
    PlayerMoveControl getMoveControl();

    /** 玩家身上的视线控制器 */
    PlayerLookControl getLookControl();

    /** 玩家身上的感知器（视线检测） */
    PlayerSensing getSensing();

    /** 行为目标选择器（MOVE/LOOK 等） */
    PlayerGoalSelector getGoalSelector();

    /** 攻击目标选择器（TARGET 旗标目标） */
    PlayerGoalSelector getTargetSelector();

    /** 当前攻击目标（玩家没有 setTarget/getTarget 字段，借 MobFields 存储） */
    LivingEntity getTarget();

    void setTarget(LivingEntity target);

    // ==================== default 转发：玩家原生能力 ====================

    /** 把假面还原成它本来的 LivingEntity 形态（强转必然安全） */
    default LivingEntity asLiving() {
        return (LivingEntity) (Object) this;
    }

    default Player asPlayer() {
        return (Player) (Object) this;
    }

    default Level level() {
        return asLiving().level();
    }

    default RandomSource getRandom() {
        return asLiving().getRandom();
    }

    default double distanceToSqr(Entity entity) {
        return asLiving().distanceToSqr(entity);
    }

    default double distanceToSqr(double x, double y, double z) {
        return asLiving().distanceToSqr(x, y, z);
    }

    default float getBbWidth() {
        return asLiving().getBbWidth();
    }

    default AABB getBoundingBox() {
        return asLiving().getBoundingBox();
    }

    default BlockPos blockPosition() {
        return asLiving().blockPosition();
    }

    default Vec3 position() {
        return asLiving().position();
    }

    default Vec3 getEyePosition() {
        return asLiving().getEyePosition();
    }

    default double getEyeY() {
        return asLiving().getEyeY();
    }

    default double getX() {
        return asLiving().getX();
    }

    default double getY() {
        return asLiving().getY();
    }

    default double getZ() {
        return asLiving().getZ();
    }

    /**
     * 近战攻击转写点（对齐原版 {@code Mob#doHurtTarget}）：
     * 直接调用<b>玩家原生的 {@code Player#attack}</b>——伤害/暴击/击退/扫击/附魔/
     * 武器耐久/音效全套走原版玩家战斗逻辑，绝不手搓伤害。
     */
    default boolean doHurtTarget(Entity target) {
        asPlayer().attack(target);
        return true;
    }

    /** 玩家没有"敌对姿态"概念；对齐 Goal 接口留空实现 */
    default void setAggressive(boolean aggressive) {
    }

    /** 玩家没有"领地"概念：任何位置都合法（对齐 Mob#isWithinRestriction） */
    default boolean isWithinRestriction(BlockPos pos) {
        return true;
    }

    /** 最近伤害来源（供铁傀儡反打 / 村民惊惧 / 狼仇恨同步使用） */
    default LivingEntity getLastHurtByMob() {
        return asLiving().getLastHurtByMob();
    }

    /**
     * 视线转向（对齐原版 {@code LivingEntity#lookAt(Entity, float, float)} 语义：
     * 以限定的角速度把 yaw/pitch 拧向目标）。26.x 起原版仅保留
     * {@code lookAt(Anchor, Vec3)} 重载且行为为瞬时转向，这里手写限速版
     * 保证全版本一致的"生物式转头"观感（供远程风筝/近战锁定用）。
     */
    default void lookAt(Entity target, float yRotSpeed, float xRotSpeed) {
        LivingEntity self = asLiving();
        double dx = target.getX() - self.getX();
        double dy = target.getEyeY() - self.getEyeY();
        double dz = target.getZ() - self.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0 / Math.PI)));
        float deltaYaw = Mth.clamp(Mth.wrapDegrees(targetYaw - self.getYRot()), -yRotSpeed, yRotSpeed);
        float deltaPitch = Mth.clamp(targetPitch - self.getXRot(), -xRotSpeed, xRotSpeed);
        self.setYRot(self.getYRot() + deltaYaw);
        self.setXRot(Mth.clamp(self.getXRot() + deltaPitch, -90.0F, 90.0F));
    }

    // ===== 使用物品（骷髅/掠夺者拉弓蓄力全部走原生流程，零凭空造物）=====

    default void startUsingItem(InteractionHand hand) {
        asLiving().startUsingItem(hand);
    }

    default void stopUsingItem() {
        asLiving().stopUsingItem();
    }

    /**
     * 原生"放箭"入口：{@code releaseUsingItem} 会调用
     * {@code ItemStack.releaseUsing → BowItem.releaseUsing}——由原版逻辑扣除
     * 背包箭矢、按蓄力强度生成箭矢实体并广播。注意它与 {@link #stopUsingItem()}
     * 的区别：{@code stopUsingItem} 只是<b>静默收弓不发射</b>（字节码已核实），
     * 真正"满弦松手"必须走本方法。
     */
    default void releaseUsingItem() {
        asLiving().releaseUsingItem();
    }

    default boolean isUsingItem() {
        return asLiving().isUsingItem();
    }

    default int getTicksUsingItem() {
        return asLiving().getTicksUsingItem();
    }

    default boolean isHolding(Item item) {
        return asLiving().isHolding(item);
    }

    default boolean isHolding(Predicate<ItemStack> predicate) {
        return asLiving().isHolding(predicate);
    }

    default ItemStack getMainHandItem() {
        return asLiving().getMainHandItem();
    }

    default void setSprinting(boolean sprinting) {
        asLiving().setSprinting(sprinting);
    }

    default boolean isSprinting() {
        return asLiving().isSprinting();
    }

    /**
     * 远程攻击转写点（对齐原版 {@code RangedAttackMob#performRangedAttack}）。
     *
     * <p><b>本方法为空实现</b>：因为箭矢由假人
     * {@code startUsingItem → 本次蓄力满 → stopUsingItem} 触发的<b>原生
     * {@code BowItem.releaseUsing}</b>流程射出——包内真实箭矢被扣除、力学/
     * 暴击/音效/客户端拉弓动画全部免费且以服务端为准。
     * 若此处再手动生成箭矢就属于"凭空造物"红线，故留空。</p>
     */
    default void performRangedAttack(LivingEntity target, float power) {
    }

    /** 玩家原生近战攻击距离的平方（entity_interaction_range，默认 3.0 → 9.0） */
    default double attackReachSq() {
        double reach = asPlayer().getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return reach * reach;
    }

    /**
     * 当前是否为"外界明亮"（蜘蛛模式昼中立/夜敌对的判定基准）。
     * 1.21.4 及以前叫 {@code Level.isDay}，1.21.5 起更名为
     * {@code Level.isBrightOutside}（同一语义：天空光照亮的外界）。
     */
    default boolean levelIsBrightOutside() {
        //#if MC >= 12105
        return asLiving().level().isBrightOutside();
        //#else
        //$$ return asLiving().level().isDay();
        //#endif
    }
}