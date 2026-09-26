package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerPickupItemsGoal;
import me.primaryuan.carpet.brain.goal.PlayerRangedAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 260102
//$$ import me.primaryuan.carpet.brain.goal.PlayerSpearAttackGoal;
//#endif
//#if MC >= 12111
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
//#else
//$$ import net.minecraft.world.entity.animal.Chicken;
//$$ import net.minecraft.world.entity.monster.WitherSkeleton;
//$$ import net.minecraft.world.entity.monster.ZombifiedPiglin;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 猪灵模式脑（装配器）：敌视不穿金装的玩家，对金装玩家保持中立。
 *
 * <p>目标选择直接复用原版判定 {@code PiglinAi#isWearingSafeArmor(LivingEntity)}
 * （1.21~1.21.1 叫 {@code isWearingGold}，1.21.3 起更名，语义同为"全身盔甲
 * 均为金质/猪灵可接受装备"）——与原版猪灵的"看装行事"完全一致：</p>
 * <ul>
 *   <li>不穿金装的玩家 → 敌对，近战追击（原版猪灵徒手/持金剑均近战）；</li>
 *   <li>穿金装的玩家 → 中立，不主动索敌；</li>
 *   <li>被谁打都还手（{@code HurtByTargetGoal}，优先级 1，无论对方穿什么）；</li>
 *   <li>无事随机漫步。</li>
 * </ul>
 * <p><b>武器决定战斗方式（对齐 Wiki：金剑近战/弩远程/金矛冲锋）</b>：三个
 * 行为 Goal 并存、靠 canUse 的持械判定自然互斥——持矛（26.1.2+，{@code
 * PlayerSpearAttackGoal}）优先，其次持弩（{@code PlayerRangedAttackGoal}，
 * 射击节奏≈2 秒/发、射程 8 格、射击时不左右移动），徒手/金剑走近战兜底；
 * 更换手中武器后下一评估周期自动切换战斗方式。</p>
 *
 * <p><b>其余 Wiki 对齐</b>：和平难度下不与玩家敌对（目标谓词门控）；立刻
 * 敌对 16 格内的凋灵骷髅；非敌对态主动远离僵尸猪灵（{@code PlayerAvoidEntityGoal}）；
 * 被谁打都还手（HurtBy，即"被攻击后群体反击"的单体版）。不做（红线/范围）：
 * 僵尸化（身体转化）、以物易物与拾取装备（物品系统状态机）、灵魂火方块规避
 * （无方块感知规避引擎）、疣猪兽 30 秒群攻冷却（群体协调）。</p>
 */
public class PiglinBrain extends PlayerBrainController {

    public PiglinBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "piglin";
    }

    /** Wiki：和平难度中成年猪灵不会与玩家敌对 */
    private boolean isPeaceful() {
        return this.player.level().getDifficulty() == Difficulty.PEACEFUL;
    }

    @Override
    public void tick() {
        super.tick();
        this.tickEquipment();
    }

    /**
     * 捡到物品后的装备逻辑（Wiki：捡起后端详片刻，然后拿在手中/穿戴上/
     * 收进物品栏）。每 64 tick 处理一件——"端详片刻"的节奏简化实现：
     * <ol>
     *   <li>副手空 + 背包有盾牌 → 装副手；</li>
     *   <li>空盔甲槽（头/胸/腿/脚）← 背包中对应 {@code *_ARMOR} 标签物品
     *       （金质优先；已装备的槽不换，品质对比不做）；</li>
     *   <li>主手空 ← 背包中金剑/弩（猪灵偏好金质武器；26.x 金矛可经
     *       KINETIC_WEAPON 扩展）。</li>
     * </ol>
     * <b>物品守恒（零凭空造物）</b>：全部通过 Inventory 槽位移出 +
     * {@code setItemSlot} 装入完成——等价玩家在背包里拖动穿戴，
     * 不生成/不销毁任何 ItemStack。
     */
    private void tickEquipment() {
        if ((this.player.tickCount & 63) != 0) {
            return;
        }
        // Wiki：被攻击后 20 秒（400 tick）内不再尝试捡起/装备。
        // lastHurtByMobTimestamp 新实体默认 0（仅 setLastHurtByMob/读档两处写点），
        // 必须先判 getLastHurtByMob() 非空——否则出生后前 400 tick 恒判"刚被打"，
        // 表现为猪灵开局 20 秒不捡物、不装备
        if (this.player.getLastHurtByMob() != null
                && this.player.tickCount - this.player.getLastHurtByMobTimestamp() < 400) {
            return;
        }
        Inventory inv = this.player.getInventory();

        // 1) 副手盾牌
        if (this.player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            int shield = this.findInInventory(st -> st.is(Items.SHIELD));
            if (shield >= 0) {
                this.equipFromInventory(shield, EquipmentSlot.OFFHAND);
                return;
            }
        }
        // 2) 空盔甲槽（金质优先）
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!this.player.getItemBySlot(slot).isEmpty()) {
                continue;
            }
            var tag = switch (slot) {
                case HEAD -> ItemTags.HEAD_ARMOR;
                case CHEST -> ItemTags.CHEST_ARMOR;
                case LEGS -> ItemTags.LEG_ARMOR;
                case FEET -> ItemTags.FOOT_ARMOR;
                default -> null;
            };
            if (tag == null) {
                continue;
            }
            int found = this.findInInventory(
                    st -> st.is(tag) || (st.is(ItemTags.PIGLIN_LOVED) && st.is(tag)));
            if (found >= 0) {
                this.equipFromInventory(found, slot);
                return;
            }
        }
        // 3) 主手空 ← 金剑/弩（猪灵偏好金质武器）
        if (this.player.getMainHandItem().isEmpty()) {
            int weapon = this.findInInventory(st ->
                    st.is(Items.GOLDEN_SWORD) || st.is(Items.CROSSBOW)
                            //#if MC >= 260102
                            || st.is(Items.GOLDEN_SPEAR)
                            //#endif
            );
            if (weapon >= 0) {
                this.equipFromInventory(weapon, EquipmentSlot.MAINHAND);
            }
        }
    }

    /**
     * 在主背包（36 格）中找满足条件的物品；金质（#piglin_loved）优先。
     *
     * @return 槽位下标；未找到返回 -1
     */
    private int findInInventory(java.util.function.Predicate<ItemStack> filter) {
        Inventory inv = this.player.getInventory();
        int golden = -1;
        // 主背包 0~35（Container 通用索引：36~39 盔甲、40 副手）
        for (int i = 0; i < 36; i++) { // 主背包固定 36 格（Container 索引 0~35）
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !filter.test(st)) {
                continue;
            }
            if (st.is(ItemTags.PIGLIN_LOVED)) {
                return i; // 金质最高优先
            }
            if (golden < 0) {
                golden = i;
            }
        }
        return golden;
    }

    /** 从背包槽位取出物品并装备到指定槽（守恒转移；原持物退回背包，装不下的剩余走原生掉落） */
    private void equipFromInventory(int index, EquipmentSlot slot) {
        Inventory inv = this.player.getInventory();
        ItemStack picked = inv.removeItem(index, 1);
        if (picked.isEmpty()) {
            return;
        }
        // 原槽位上的旧装备退回背包。Inventory.add 装不下的剩余栈留在传入对象里、
        // 不会自动掉落——直接丢引用即物品消失，这里把剩余部分走玩家原生 drop
        // （脚下 ItemEntity，含拾取延迟），与"背包满则掉在脚下"的注释契约一致
        ItemStack previous = this.player.getItemBySlot(slot);
        this.player.setItemSlot(slot, picked);
        if (!previous.isEmpty()) {
            inv.add(previous);
            if (!previous.isEmpty()) {
                //#if MC >= 260300
                //$$ // 26.3：drop 收敛为三参（Prediction 控制客户端预测路径），
                //$$ // 与原版调用点一致取 PREDICTED
                //$$ this.player.drop(previous, false, net.minecraft.util.Prediction.PREDICTED);
                //#else
                this.player.drop(previous, false);
                //#endif
            }
        }
    }

    @Override
    protected void assemble() {
        // 被打反击：无论攻击者穿什么（与原版猪灵被激怒一致，大范围敌对的落地方案）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 敌视不穿金装的玩家（原版判定：全身均为猪灵可接受盔甲才算中立；
        // 1.21~1.21.1 叫 isWearingGold，1.21.3+ 更名 isWearingSafeArmor）
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !this.isPeaceful()
                        //#if MC >= 12103
                        && !PiglinAi.isWearingSafeArmor(p)));
                        //#else
                        //$$ && !PiglinAi.isWearingGold(p)));
                        //#endif
        // 立刻敌对 16 格内的凋灵骷髅（Wiki：成年猪灵会立刻主动攻击）
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, WitherSkeleton.class, 16.0, 10, true,
                w -> w.isAlive()));
        // 行为：
        // pri 0 远离僵尸猪灵（非敌对态的天敌规避；战斗激活时被更高优先级抢占）
        this.goalSelector.addGoal(0, new PlayerAvoidEntityGoal<>(
                this.prowler, ZombifiedPiglin.class, 12.0F, 0.9, 1.35));
        // pri 1 金矛冲锋（26.1.2+；Wiki：金矛的冲锋攻击，拉开距离后再冲）
        //#if MC >= 260102
//$$         this.goalSelector.addGoal(1, new PlayerSpearAttackGoal(this.prowler, 1.0));
        //#endif
        // pri 1 弩远程：射击不左右移动（Wiki：猪灵射箭时不左右移动，与弓类不同）；
        // canUse 要求主手是弩（含持弩上弦状态机），与矛/近战按武器自然互斥
        this.goalSelector.addGoal(1, new PlayerRangedAttackGoal(
                this.prowler, 1.0, 20, 8.0F, Items.CROSSBOW, 25, false));
        // pri 2 金剑/徒手近战兜底（持矛/弩时被上两者抢占；换武器自动切换）
        this.goalSelector.addGoal(2, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // pri 3 拾取掉落物（金质优先；被攻击 20 秒内不捡）——高于漫步
        this.goalSelector.addGoal(3, new PlayerPickupItemsGoal(this.prowler));
        // 空闲漫游
        this.goalSelector.addGoal(4, new PlayerRandomStrollGoal(this.prowler, 1.0));
    }
}
