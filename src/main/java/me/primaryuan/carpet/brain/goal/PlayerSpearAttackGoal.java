package me.primaryuan.carpet.brain.goal;

//#if MC >= 260102
//$$ // 26.1.2 起原版加入长矛（动能武器），本 Goal 仅在该版本段编入
//$$ import me.primaryuan.carpet.brain.PlayerGoal;
//$$ import me.primaryuan.carpet.brain.PlayerPathNavigation;
//$$ import me.primaryuan.carpet.brain.PryMob;
//$$ import net.minecraft.core.BlockPos;
//$$ import net.minecraft.core.component.DataComponents;
//$$ import net.minecraft.util.Mth;
//$$ import net.minecraft.world.InteractionHand;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$ import net.minecraft.world.item.ItemStack;
//$$ import net.minecraft.world.phys.Vec3;
//$$
//$$ import java.util.EnumSet;
//$$
//$$ /**
//$$  * 移植版长矛刺击目标（← 原版 26.x {@code SpearUseGoal}，原版僵尸持矛时装配
//$$  * 在 goalSelector 优先级 2，本移植版保持同一优先级与算法骨架）。
//$$  *
//$$  * <p><b>长矛 = 动能武器（KINETIC_WEAPON 组件）</b>：与弓不同，刺击伤害不在
//$$  * "松手"（releaseUsing）时结算，而是由 {@code LivingEntity} 在<b>使用期间</b>
//$$  * 按持有者的冲刺状态自动判定（字节码核实：{@code KineticWeapon.damageEntities}
//$$  * 由实体 tick 驱动）。因此 AI 侧只需：</p>
//$$  * <ol>
//$$  *   <li>接近目标到交战距离 → {@code startUsingItem} 进入使用态（客户端自动
//$$  *       播放举矛蓄力动画，无需发包欺骗）；</li>
//$$  *   <li>蓄力期间<b>全速冲向目标</b>——动能判定命中后伤害/击退/音效全原生，
//$$  *       零凭空造物；</li>
//$$  *   <li>蓄满（{@code KineticWeapon#computeDamageUseDuration}）后
//$$  *       {@code stopUsingItem}（原版同款用 stop 而非 release）→ 反向后撤
//$$  *       拉开距离，再进入下一轮接近——对齐原版"刺了就撤"的长矛节奏。</li>
//$$  * </ol>
//$$  */
//$$ public class PlayerSpearAttackGoal extends PlayerGoal {
//$$
//$$     /** 开始交战（举矛蓄力）的距离（格，对齐原版僵尸装配的 10.0F） */
//$$     private static final double ENGAGE_RANGE_SQ = 10.0 * 10.0;
//$$     /** 蓄满后后撤的目标距离（格） */
//$$     private static final double FLEE_DIST = 6.0;
//$$     /** 后撤超时（tick）：awayPos 走不到也要回到接近段，防卡在后撤段 */
//$$     private static final int FLEE_TIMEOUT = 60;
//$$
//$$     private final PryMob mob;
//$$     private final double speedModifier;
//$$
//$$     private Phase phase = Phase.APPROACH;
//$$     private int engageTime;      // 蓄力剩余 tick
//$$     private int fleeTime;        // 后撤剩余 tick（超时保护）
//$$     private Vec3 awayPos;        // 后撤目标点（null = 未选定）
//$$     /** 接近段的重算路径倒计时（tick）：A* 每次最多 4096 次迭代，每 tick 全量
//$$      *  重算会把多假人服务器的 tick 拖垮（与 {@link PlayerRangedAttackGoal} 同纪律；
//$$      *  寻路器对"目标未换格"的重发会自动沿用现路径，此处约束的是移动目标的刷新节奏） */
//$$     private int pathRecalcCooldown;
//$$
//$$     private enum Phase { APPROACH, ENGAGE, FLEE }
//$$
//$$     public PlayerSpearAttackGoal(PryMob mob, double speedModifier) {
//$$         this.mob = mob;
//$$         this.speedModifier = speedModifier;
//$$         this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE, PlayerGoal.Flag.LOOK));
//$$         this.setInterval(2);
//$$     }
//$$
//$$     /** 原版 ableToAttack：有目标 && 主手是动能武器（长矛系列）&& 未在使用中 */
//$$     private boolean ableToAttack() {
//$$         return this.mob.getTarget() != null
//$$                 && this.mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON)
//$$                 && !this.mob.isUsingItem();
//$$     }
//$$
//$$     /** 主手长矛的蓄力时长（对齐原版 getKineticWeaponUseDuration） */
//$$     private int kineticUseDuration() {
//$$         ItemStack main = this.mob.getMainHandItem();
//$$         var weapon = main.get(DataComponents.KINETIC_WEAPON);
//$$         return weapon != null ? weapon.computeDamageUseDuration() : 0;
//$$     }
//$$
//$$     @Override
//$$     public boolean canUse() {
//$$         return this.ableToAttack();
//$$     }
//$$
//$$     @Override
//$$     public boolean canContinueToUse() {
//$$         if (this.phase != Phase.APPROACH) {
//$$             // 蓄力/后撤段进行中：只要武器还在手、目标活着就打完本轮
//$$             LivingEntity target = this.mob.getTarget();
//$$             return target != null && target.isAlive()
//$$                     && this.mob.isHolding(s -> s.has(DataComponents.KINETIC_WEAPON));
//$$         }
//$$         return this.canUse();
//$$     }
//$$
//$$     @Override
//$$     public void stop() {
//$$         // 与弓同理：goal 停止必须清移动控制器（防 STRAFE/MOVE_TO 粘滞冻结）
//$$         this.mob.getNavigation().stop();
//$$         this.mob.stopUsingItem();
//$$         this.phase = Phase.APPROACH;
//$$         this.engageTime = 0;
//$$         this.fleeTime = 0;
//$$         this.awayPos = null;
//$$     }
//$$
//$$     @Override
//$$     public void tick() {
//$$         LivingEntity target = this.mob.getTarget();
//$$         if (target == null) {
//$$             return;
//$$         }
//$$         this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
//$$         switch (this.phase) {
//$$             case APPROACH -> {
//$$                 if (--this.pathRecalcCooldown <= 0) {
//$$                     this.mob.getNavigation().moveTo(target, this.speedModifier);
//$$                     this.pathRecalcCooldown = 5;
//$$                 }
//$$                 if (this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ())
//$$                         <= ENGAGE_RANGE_SQ) {
//$$                     // 进入交战：举矛蓄力（刺击伤害由 LivingEntity 在使用期间自动结算）
//$$                     this.engageTime = this.kineticUseDuration();
//$$                     this.mob.startUsingItem(InteractionHand.MAIN_HAND);
//$$                     this.phase = Phase.ENGAGE;
//$$                 }
//$$             }
//$$             case ENGAGE -> {
//$$                 // 蓄力期间持续全速冲向目标：动能武器的命中判定看冲刺状态。
//$$                 // 保持每 tick 重发（蓄力窗口短、命中精度优先）：
//$$                 // 目标未换格时由寻路器沿用现路径，不会触发全量 A*
//$$                 this.mob.getNavigation().moveTo(target, this.speedModifier);
//$$                 this.engageTime--;
//$$                 if (this.engageTime <= 0) {
//$$                     // 蓄满收矛（原版同款 stopUsingItem；伤害已在期间判定）
//$$                     this.mob.stopUsingItem();
//$$                     this.awayPos = this.pickAwayPos(target);
//$$                     this.fleeTime = FLEE_TIMEOUT;
//$$                     if (this.awayPos != null) {
//$$                         this.mob.getNavigation().moveTo(
//$$                                 this.awayPos.x, this.awayPos.y, this.awayPos.z, this.speedModifier);
//$$                     }
//$$                     this.phase = Phase.FLEE;
//$$                 }
//$$             }
//$$             case FLEE -> {
//$$                 this.fleeTime--;
//$$                 if (this.fleeTime <= 0 || this.mob.getNavigation().isDone()) {
//$$                     this.phase = Phase.APPROACH;
//$$                 }
//$$             }
//$$         }
//$$     }
//$$
//$$     /**
//$$      * 沿"目标 → 假人"反方向选一个可站立的后撤点（原版用
//$$      * {@code LandRandomPos.getPosAway}，其签名强绑定 PathfinderMob，
//$$      * 这里自算等价点并复用寻路器的可站立口径）。
//$$      */
//$$     private Vec3 pickAwayPos(LivingEntity target) {
//$$         double dx = this.mob.getX() - target.getX();
//$$         double dz = this.mob.getZ() - target.getZ();
//$$         double len = Math.sqrt(dx * dx + dz * dz);
//$$         if (len < 1.0E-4) {
//$$             dx = 1.0;
//$$             dz = 0.0;
//$$             len = 1.0;
//$$         }
//$$         BlockPos origin = this.mob.blockPosition();
//$$         for (int i = 0; i < 20; i++) {
//$$             double dist = 3.0 + this.mob.getRandom().nextDouble() * (FLEE_DIST - 3.0);
//$$             double angle = (this.mob.getRandom().nextDouble() - 0.5) * 1.2; // 反向 ±0.6 rad 扇形
//$$             double cos = Math.cos(angle);
//$$             double sin = Math.sin(angle);
//$$             int ox = Mth.floor((dx / len * cos - dz / len * sin) * dist);
//$$             int oz = Mth.floor((dx / len * sin + dz / len * cos) * dist);
//$$             BlockPos candidate = origin.offset(ox, 0, oz);
//$$             if (PlayerPathNavigation.isWalkableCell(this.mob.level(), candidate)) {
//$$                 return new Vec3(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
//$$             }
//$$         }
//$$         return null; // 四周都不可站：留在原地，直接进入下一轮接近
//$$     }
//$$ }
//#endif
