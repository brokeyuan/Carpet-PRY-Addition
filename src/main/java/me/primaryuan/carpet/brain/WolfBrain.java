package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerFollowOwnerGoal;
import me.primaryuan.carpet.brain.goal.PlayerHurtByTargetGoal;
import me.primaryuan.carpet.brain.goal.PlayerMeleeAttackGoal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 狼模式脑（装配器）：跟随主人 + 仇恨同步。
 *
 * <p>装配（全部 MOVE+LOOK 旗标，优先级互斥）：</p>
 * <ul>
 *   <li>优先级 0 —— 近战：主人下达目标（仇恨同步）时扑咬（{@code PlayerMeleeAttackGoal}，
 *       速度 1.2 ≈ 狼的疾扑）；</li>
 *   <li>优先级 1 —— 跟随：无目标时跟进主人（{@code PlayerFollowOwnerGoal}，保持
 *       经典">8 格跑、>2.5 格走、近了蹲点"的跟随节奏）。</li>
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
        // 自卫反击：谁打狼锁谁（豁免主人——主人误伤不还手，与原版驯服生物一致）
        this.targetSelector.addGoal(1, new PlayerHurtByTargetGoal(this.prowler, this.ownerUuid));
        // 扑咬：有仇恨目标时立刻冲到目标脸上（近战，速度 1.2 → 疾跑扑杀）
        this.goalSelector.addGoal(0, new PlayerMeleeAttackGoal(this.prowler, 1.2, true));
        // 跟随：无目标时跟着主人（原版狼的跟随节奏）
        this.goalSelector.addGoal(1, new PlayerFollowOwnerGoal(this.prowler, this.ownerUuid, 1.0));
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

    /** 扑咬候选校验：存活、同维度、不是狼自己也不是主人；合格返回原引用，否则 null */
    private LivingEntity biteable(ServerPlayer owner, LivingEntity target) {
        if (target == null || !target.isAlive()
                || target.level() != this.player.level()
                || target == this.player
                || target == owner) {
            return null;
        }
        return target;
    }
}