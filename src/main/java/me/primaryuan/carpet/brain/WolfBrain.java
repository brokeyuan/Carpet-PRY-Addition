package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerBegGoal;
import me.primaryuan.carpet.brain.goal.PlayerFollowOwnerGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLeapAtTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomLookAroundGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 狼模式脑（装配器）：跟随主人 + 仇恨同步。
 *
 * <p><b>状态映射（单一确定语义）</b>：本模式代表<b>跟随命令执行者的驯服狼</b>
 * （OwnerHurtBy/OwnerHurtTarget 仇恨同步、FollowOwner、Beg 都是驯服态行为）——
 * 因此按原版 {@code Wolf$WolfAvoidEntityGoal} 的 {@code !wolf.isTame} 门，
 * <b>不装配羊驼规避</b>（那是野生狼的规避，与驯服态互斥；26.3 字节码核实）。
 * 恐慌同理是 {@code TamableAnimalPanicGoal}（环境伤害标签），普通挨打反击。</p>
 *
 * <p>装配（26.3 字节码 {@code Wolf.registerGoals} 的驯服态等价）：</p>
 * <ul>
 *   <li>优先级 0 —— 近战：主人下达目标（仇恨同步）时扑咬（原版
 *       {@code MeleeAttackGoal(1.0, true)} pri 5，移植版提升到 0 以压过跟随）；</li>
 *   <li>优先级 1 —— 跟随：无目标时跟进主人（原版 {@code FollowOwnerGoal(1.0,
 *       10F, 2F)} pri 6：>10 格跟、2 格内停）；</li>
 *   <li>优先级 1 —— 恐慌（原版 {@code TamableAnimalPanicGoal(1.5)} pri 1：
 *       受伤/着火逃离）。</li>
 * </ul>
 * <p>因为旗标互斥且近战优先级更高：目标被同步后近战立刻接管（冲向被咬目标），
 * 目标消失后近战 canUse 回落到 false，跟随自动恢复——"有仗打就打，没仗打就跟"。 </p>
 *
 * <p><b>仇恨同步（复刻原版狼"主人打谁咬谁 + 主人被谁打咬谁"）</b>：每 tick 先读取
 * 主人 {@code getLastHurtByMob()}（近期伤害来源，优先）与 {@code getLastHurtMob()}
 * （主人的最近攻击目标），来源存活且同维度且不是本假人/主人时写入
 * {@link PryMob#setTarget}，近战 Goal 据此行动；主人离线/换维度或当前目标
 * 已死/已卸载时清退目标，回到跟随状态。狼自己被打则由
 * {@code PlayerHurtByTargetGoal} 自卫反击（豁免主人）。</p>
 */
public class WolfBrain extends PlayerBrainController {

    /** 主人（执行 /player brain wolf 命令的玩家）的 UUID */
    private final UUID ownerUuid;

    public WolfBrain(ServerPlayer player, UUID ownerUuid) {
        super(player);
        this.ownerUuid = ownerUuid;
    }

    @Override
    public String modeKey() {
        return "wolf";
    }

    @Override
    protected void assemble() {
        // 优先级对齐原版 Wolf.registerGoals（26.3/1.21.11 字节码，驯服态口径）：
        // Panic 1 > Leap 4 > Melee 5 > FollowOwner 6 > Stroll 8 > Beg 9 > LookAt/RandomLook 10。
        // AvoidLlama 3 是野生狼行为（!isTame 门），驯服态不装配。
        //
        // 目标链：
        // 自卫反击（原版 HurtByTargetGoal pri 3；OwnerHurtBy/OwnerHurtTarget
        // 由 syncHate 的仇恨同步表达）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, this.ownerUuid));
        // 行为链：
        // 恐慌（原版 TamableAnimalPanicGoal(1.5, PANIC_ENVIRONMENTAL_CAUSES) pri 1）：
        // 只认环境伤害（火/岩浆/冰冻/仙人掌/闪电）——普通挨打不恐慌，走 HurtBy 反击
        this.goalSelector.addGoal(1, new PlayerPanicGoal(this.prowler, 1.5,
                DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        // 扑击（原版 LeapAtTargetGoal(0.4F) pri 4：距目标 2~4 格 1/3 概率扑跳，
        // MOVE+JUMP 旗标、腾空至落地）
        this.goalSelector.addGoal(4, new PlayerLeapAtTargetGoal(this.prowler));
        // 扑咬（原版 MeleeAttackGoal(1.0, true) pri 5）
        this.goalSelector.addGoal(5, new PlayerMeleeAttackGoal(this.prowler, 1.0, true));
        // 跟随（原版 FollowOwnerGoal(1.0, 10F, 2F) pri 6：≥10 启动、≤2 结束）
        this.goalSelector.addGoal(6, new PlayerFollowOwnerGoal(this.prowler, this.ownerUuid, 1.0));
        // 闲逛（原版 WaterAvoidingRandomStrollGoal pri 8）
        this.goalSelector.addGoal(8, new PlayerRandomStrollGoal(this.prowler, 1.0));
        // 乞食（原版 BegGoal(8F) pri 9：8 格内持骨头或狼食物的玩家歪头注视）
        this.goalSelector.addGoal(9, new PlayerBegGoal(this.prowler));
        // 看人/随机张望（原版 pri 10 同位）
        this.goalSelector.addGoal(10, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new PlayerRandomLookAroundGoal(this.prowler));
    }

    @Override
    public void tick() {
        syncHate(); // 先同步仇恨，再由父类驱动目标/导航/移动链
        super.tick();
    }

    /**
     * 仇恨同步：主人最近被谁打，狼就咬谁（原版狼的护主逻辑）。
     *
     * <p>目标字段有两个写方（本方法与 targetSelector 里的
     * {@code PlayerHurtByTargetGoal}），仲裁顺序对齐原版狼：主人攻击者
     * （OwnerHurtByTargetGoal 优先级更高）&gt; 自身被打反击——</p>
     * <ul>
     *   <li>want 有效：无条件覆写（原版优先级语义，HurtBy 目标让位）；</li>
     *   <li>want 无效：不动运行中目标的锁定；清退仅在"无 TARGET 目标运行 +
     *       当前目标已死/卸载"时执行，消除双写方对同一字段的拉锯。</li>
     * </ul>
     */
    private void syncHate() {
        // 不走 ServerPlayer#getServer（1.21.10+ 映射已移除该访问器），
        // Level#getServer 全支持版本签名稳定
        MinecraftServer server = this.player.level().getServer();
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(this.ownerUuid);
        LivingEntity current = this.prowler.getTarget();
        if (owner == null || !owner.isAlive() || owner.level() != this.player.level()) {
            // 主人不在线/死亡/异维：无仇恨来源，只清理已失效的目标
            // （运行中的 TARGET 目标由其自身 canContinueToUse 管理，不代清）
            if (!this.targetSelector.hasRunningFlag(PlayerGoal.Flag.TARGET)
                    && current != null && (current.isRemoved() || !current.isAlive())) {
                this.prowler.setTarget(null);
            }
            return;
        }
        LivingEntity attacker = owner.getLastHurtByMob();   // 主人被谁打（原版 OwnerHurtByTargetGoal）
        LivingEntity ownerTarget = owner.getLastHurtMob();  // 主人打过谁（原版 OwnerHurtTargetGoal）
        LivingEntity want = this.biteable(owner, attacker);
        if (want == null) {
            want = this.biteable(owner, ownerTarget);
        }
        if (want != null) {
            // 主人的仇恨/攻击目标有效：锁定为扑咬目标
            this.prowler.setTarget(want);
        } else if (!this.targetSelector.hasRunningFlag(PlayerGoal.Flag.TARGET)
                && current != null && (current.isRemoved() || !current.isAlive())) {
            // 目标已死/卸载且无 TARGET 目标运行：清退，回跟随状态
            this.prowler.setTarget(null);
        }
    }

    /**
     * 扑咬候选校验：存活、同维度、不是狼自己也不是主人；同队假人不可选
     * （队伍过滤覆盖仇恨同步路径）；创造/旁观/和平难度玩家不可选——与
     * {@link PlayerHurtByTargetGoal} 同口径，对齐原版
     * {@code TargetingConditions} 的 canBeSeenAsEnemy 语义（invulnerable 恒排除，
     * 原版狼不会把创造玩家锁成目标）。合格返回原引用，否则 null。
     */
    private LivingEntity biteable(ServerPlayer owner, LivingEntity target) {
        if (target == null || !target.isAlive()
                || target.level() != this.player.level()
                || target == this.player
                || target == owner
                || BrainTeams.isTeamFriendly(this.player, target)) {
            return null;
        }
        if (target instanceof Player p && (p.isSpectator() || p.isCreative()
                || p.level().getDifficulty() == Difficulty.PEACEFUL)) {
            return null;
        }
        return target;
    }
}