package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerNearestAttackableTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
//#if MC >= 12111
// 1.21.11 起鸡迁入 animal.chicken 子包
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
//#else
//$$ import net.minecraft.world.entity.animal.Chicken;
//$$ import net.minecraft.world.entity.animal.IronGolem;
//$$ import net.minecraft.world.entity.animal.Turtle;
//$$ import net.minecraft.world.entity.npc.AbstractVillager;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * 小僵尸模式脑（装配器）：比僵尸更快更急躁的近战追击。
 *
 * <p><b>鸡骑士（Chicken Jockey，Wiki 对齐）</b>：幼年僵尸能控制鸡。挂载后
 * 每 20 tick 检索 5 格内最近的鸡（若存在）并骑上去——骑乘走实体原生
 * {@code startRiding}（Carpet {@code /player mount} 同路径，不生成实体、
 * 不改坐标，零凭空造物）；鸡作为载具的"缓慢下落/免摔伤"由鸡的原生
 * 物理免费获得。骑乘态的移动把追击意图喂给<b>鸡自己的原生导航器</b>
 * （调用鸡实体自身的 AI，等价原版"幼年僵尸控制鸡"）；近战照常在攻击
 * 距离内用玩家原生 {@code attack()} 挥砍。落水立即下车（Wiki：视线碰到
 * 水会被赶下骑乘位置），随后地面行为照常。附近没有鸡时就是普通
 * 小僵尸——与原版"5% 概率在附近有鸡才成为鸡骑士"的语义呼应。</p>
 *
 * <p>其余对齐原版幼年僵尸：移动速度加成（Wiki：比成年僵尸快 50%，取
 * 1.5 匹配玩家体感的"窜得飞快"）、搜索范围略小。受身体锁定红线约束，
 * 假人不真的变小/缩小碰撞箱——"小"体现在行为节奏。</p>
 * <ul>
 *   <li>被击反击（HurtBy，优先级 1）；</li>
 *   <li>锁定最近玩家（优先级 2）；</li>
 *   <li>近战挥砍（原生 attack，优先级 1 行为）；</li>
 *   <li>空闲漫步（优先级 2 行为）。</li>
 * </ul>
 */
public class BabyZombieBrain extends PlayerBrainController {

    /** 寻找鸡并尝试骑乘的半径（格） */
    private static final double CHICKEN_SEARCH_RANGE_SQ = 5.0 * 5.0;
    /** 骑乘尝试间隔（tick）：骑乘失败/无鸡时不要每 tick 扫 */
    private static final int MOUNT_RETRY_INTERVAL = 20;

    private int mountCooldown;

    public BabyZombieBrain(ServerPlayer player) {
        super(player);
    }

    @Override
    public String modeKey() {
        return "babyzombie";
    }

    @Override
    public void tick() {
        super.tick();
        this.tickChickenJockey();
    }

    /**
     * 鸡骑士骑乘状态机（在行为链之后每 tick 推进）：
     * <ol>
     *   <li>骑乘中：落水立即 {@code stopRiding}（原生实体方法；Wiki"视线碰到
     *       水会被赶下骑乘位置"）；有目标时把追击意图喂给<b>鸡自己的原生
     *       导航器</b>——鸡按自己的 AI 驱动跑路/跳跃，等价原版"幼年僵尸控制鸡"；
     *   <li>未骑乘：每 {@value #MOUNT_RETRY_INTERVAL} tick 找 5 格内最近的鸡，
     *       原生 {@code startRiding}（force 跳过"鸡不可被玩家骑"的通用限制——
     *       原版鸡骑士生成即如此）。</li>
     * </ol>
     */
    private void tickChickenJockey() {
        if (this.mountCooldown > 0) {
            this.mountCooldown--;
        }
        var vehicle = this.player.getVehicle();
        if (vehicle != null) {
            if (!(vehicle instanceof Chicken chicken) || !chicken.isAlive()) {
                this.player.stopRiding(); // 载具消失：兜底清乘
                return;
            }
            // 落水下车（视线/身体进水即视为"碰到水"，比纯眼部判定更保守可靠）
            if (this.player.isInWater() || chicken.isInWater()) {
                this.player.stopRiding();
                this.mountCooldown = MOUNT_RETRY_INTERVAL;
                return;
            }
            // 把追击意图喂给鸡的原生导航器（鸡自己的 AI 负责执行）。
            // 必须每 tick 强制喂：鸡自己的漫步/恐慌 goal 也在抢占导航器，
            // 仅在空闲时喂会被漫步带偏（实测鸡骑士被漫步目标拐去角落）
            var target = this.prowler.getTarget();
            if (target != null && target.isAlive()) {
                chicken.getNavigation().moveTo(target, 1.2);
            }
            return;
        }
        // 未骑乘：周期性找鸡骑乘（附近没有鸡就是普通小僵尸，与原版语义呼应）
        if (this.mountCooldown > 0) {
            return;
        }
        this.mountCooldown = MOUNT_RETRY_INTERVAL;
        if (this.player.isInWater()) {
            return; // 水里不上鸡
        }
        Chicken best = null;
        double bestSq = CHICKEN_SEARCH_RANGE_SQ;
        for (Chicken chicken : this.player.level().getEntitiesOfClass(Chicken.class,
                this.player.getBoundingBox().inflate(5.0, 2.0, 5.0))) {
            if (!chicken.isAlive() || chicken.isPassenger() || chicken.isBaby()) {
                continue;
            }
            double sq = this.player.distanceToSqr(chicken);
            if (sq < bestSq) {
                bestSq = sq;
                best = chicken;
            }
        }
        if (best != null) {
            // 实体原生骑乘：force 跳过通用"鸡不可被骑"限制（原版鸡骑士同款）
            //#if MC >= 12110
            this.player.startRiding(best, true, true);
            //#else
            //$$ this.player.startRiding(best, true);
            //#endif
        }
    }

    @Override
    protected void assemble() {
        // 被打反击：小僵尸被打同样火速还手
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, null));
        // 追击目标（与成年僵尸同构，范围 16/戴僵尸头 8 = 减半）：
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 16.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && !p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(2, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Player.class, 8.0, 10, true,
                p -> p != this.player && p.isAlive() && !p.isSpectator() && !p.isCreative()
                        && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.ZOMBIE_HEAD)));
        this.targetSelector.addGoal(3, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, AbstractVillager.class, 16.0, 10, false,
                v -> v.isAlive() && !v.isBaby()));
        this.targetSelector.addGoal(4, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, IronGolem.class, 16.0, 10, true,
                g -> g.isAlive()));
        this.targetSelector.addGoal(5, new PlayerNearestAttackableTargetGoal<>(
                this.prowler, Turtle.class, 16.0, 10, true,
                t -> t.isBaby()));
        // 行为：疾速近战（1.5：Wiki"移动速度比成年僵尸快 50%" → 原生疾跑）
        this.goalSelector.addGoal(1, new PlayerMeleeAttackGoal(this.prowler, 1.5, true));
        // 空闲漫游
        this.goalSelector.addGoal(2, new PlayerRandomStrollGoal(this.prowler, 1.5));
    }
}
