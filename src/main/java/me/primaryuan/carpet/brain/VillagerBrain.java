package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAvoidEntityGoal;
import me.primaryuan.carpet.brain.goal.PlayerFarmerWorkGoal;
import me.primaryuan.carpet.brain.goal.PlayerGotoPoiGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerPanicGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import me.primaryuan.carpet.brain.goal.PlayerSleepInBedGoal;
//#if MC >= 12111
// 1.21.11 起僵尸类迁入 monster.zombie 子包
import net.minecraft.world.entity.monster.zombie.Zombie;
//#else
//$$ import net.minecraft.world.entity.monster.Zombie;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 村民模式脑（装配器）：昼夜日程驱动的"过日子"行为栈，无仇恨。
 *
 * <p>原版村民为 Brain 系统驱动（{@code Villager.registerBrainGoals}），GoalSelector
 * 优先级无法 1:1 对照，按行为骨架等价装配（披露口径）。日程时间线取自原版
 * {@code villager_schedule} 数据（26.3 json：2000 work / 9000 meet / 11000 idle /
 * 12000 rest，周期 24000）。</p>
 *
 * <p>行为栈（优先级升序，MOVE 互斥保证同刻只走一个）：</p>
 * <ul>
 *   <li>0 恐慌（默认 PANIC_CAUSES：被打/环境伤害即逃——遇险打断日程）；</li>
 *   <li>1 规避僵尸（8 格，原版 Brain 传感器距离）；</li>
 *   <li>2 回床入睡（REST 段：登记床位 → 原生 startSleepInBed 真睡 → 天亮起）；</li>
 *   <li>3 工作（WORK 段：职业工作站占用到位）；</li>
 *   <li>4 集合（MEET 段：钟下集合点到位）；</li>
 *   <li>5 社交注视（并行 LOOK：看其他村民/玩家）；</li>
 *   <li>6 漫游（IDLE 段：原版 idle 活动同构的自主游荡）。</li>
 * </ul>
 *
 * <p>遇险打断与恢复：恐慌/规避（MOVE 高优先级）抢占日程 Goal，险情结束由
 * 选择器按日程重新激活对应 Goal（日程恢复）；占用登记随 Goal stop 释放、
 * 随失效复核重取。</p>
 *
 * <p>未完成项（如实披露，不视为"还原完成"）：职业系统（职业绑定/升级/交易）、
 * 繁殖、幼年 PLAY 互动（以漫游表达）、原版 PoiManager 区块级引擎（以方块扫描 +
 * 占用登记表等价）、音效与社交互动动画。{@link #onDetach} 释放全部占用并唤醒，
 * 换脑/下线/死亡不留幽灵床位。</p>
 */
public class VillagerBrain extends PlayerBrainController {

    /** 工作目标（农民版：只接受堆肥桶求职，到位后干农活/堆肥） */
    private final PlayerFarmerWorkGoal workGoal;

    /** 当前职业后缀 lang 键（[村民] / [村民-农民]） */
    private String professionKey;

    /** 控制器持有的有效农民岗位（职业归属，与工作 Goal 的临时 claim 解耦） */
    private BlockPos farmerStation;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> farmerStationDim;

    public VillagerBrain(ServerPlayer player) {
        super(player);
        // 工作 Goal 成功认领堆肥桶 → 通知控制器登记岗位归属；长期岗位由
        // 控制器持有，工作/集合/睡眠/恐慌暂停不释放。
        this.workGoal = new PlayerFarmerWorkGoal(this.prowler,
                this::onComposterClaimed, this::isLongTermStation);
    }

    /** 有效长期岗位：同维、方块仍是堆肥桶、登记持有人仍是本实例玩家 */
    boolean isLongTermStation(net.minecraft.world.level.Level level, BlockPos pos) {
        return this.farmerStation != null
                && this.farmerStationDim != null
                && pos.equals(this.farmerStation)
                && level.dimension().equals(this.farmerStationDim)
                && this.player.level().dimension().equals(this.farmerStationDim)
                && this.isStationValid(level, pos)
                && VillagerPoiRegistry.isClaimedBy(
                        this.player, this.farmerStationDim, pos);
    }

    /** 测试缝：模拟工作 Goal 认领成功（走真实登记链） */
    void onComposterClaimedForTest(BlockPos pos) {
        this.onComposterClaimed(pos);
    }

    /** 工作 Goal 认领堆肥桶成功的回调（岗位归属登记；路过不转职由认领机制保证） */
    private void onComposterClaimed(BlockPos pos) {
        var dim = this.player.level().dimension();
        // 只接受真实登记在本实例名下的岗位，防止 Goal 资格失败后仍写归属
        if (!VillagerPoiRegistry.isClaimedBy(this.player, dim, pos)) {
            return;
        }
        if (this.farmerStation != null && this.farmerStationDim != null
                && (!this.farmerStation.equals(pos) || !this.farmerStationDim.equals(dim))) {
            // 换岗：先释放旧岗位，避免一个村民双持两个工作站
            VillagerPoiRegistry.release(this.player, this.farmerStationDim, this.farmerStation);
        }
        this.farmerStation = pos.immutable();
        this.farmerStationDim = dim;
    }

    @Override
    public String modeKey() {
        return "villager";
    }

    /**
     * 名牌后缀键：刚挂脑显示 [村民]；成功取得农民职业后 [村民-农民]。
     * 只改后缀，不改登录名/UUID（计分板队伍机制，见 BrainManager）。
     */
    @Override
    public String suffixLangKey() {
        return this.professionKey != null ? this.professionKey : "mode_villager";
    }

    @Override
    protected void assemble() {
        // 没有被攻击 → 恐慌不激活；有僵尸逼近 → 规避接管；日程段 → 对应目标
        this.goalSelector.addGoal(0, new PlayerPanicGoal(this.prowler, 0.5));
        // 规避僵尸：8 格生效（原版 Brain 传感器距离）、先走 0.9 后疾跑 1.35；
        // 原版规避集还含猪灵蛮兵/卫道士/掠夺者/劫掠兽/监守者等（跨版本包路径
        // 漂移，移植版只覆盖僵尸类并披露）
        this.goalSelector.addGoal(1, new PlayerAvoidEntityGoal<>(
                this.prowler, Zombie.class, 8.0F, 0.9, 1.35));
        // 日程目标：REST 回床入睡（跨夜）/ WORK 农民工作（岗位认领+农活+堆肥）/ MEET 钟下集合
        this.goalSelector.addGoal(2, new PlayerSleepInBedGoal(this.prowler));
        this.goalSelector.addGoal(3, this.workGoal);
        this.goalSelector.addGoal(4, new PlayerGotoPoiGoal(this.prowler,
                VillagerSchedule.Activity.MEET, 0.5));
        // 社交：注视其他村民/玩家（并行 LOOK，与日程移动并存）
        this.goalSelector.addGoal(5, new PlayerLookAtPlayerGoal(this.prowler, Player.class, 6.0F));
        // IDLE 漫游兜底（原版 idle 活动同构）
        this.goalSelector.addGoal(6, new PlayerRandomStrollGoal(this.prowler, 0.6));
    }

    /**
     * 职业生命周期（归控制器，不依赖工作 Goal 的临时 claim）：
     * <ul>
     *   <li>持有岗位且仍有效（同维、方块仍是堆肥桶）→ 农民——集合/睡觉/
     *       恐慌/工作 Goal 被抢占均不清除；岗位被其他假人抢占时本 Goal 求职
     *       会失败并另寻堆肥桶，回调以新岗位更新归属；</li>
     *   <li>岗位方块被拆/跨维/被其他假人抢占 → 岗位失效：结束归属并回到
     *       [村民]，由工作 Goal 在下一个工作时段重新求职；</li>
     *   <li>卸脑/换脑/死亡：onDetach 释放全部占用并清空归属。</li>
     * </ul>
     * 有效性方块判定经 {@link #isStationValid} 缝（离线测试覆写）。
     */
    void refreshProfession() {
        if (this.farmerStation != null) {
            boolean sameDim = this.player.level().dimension().equals(this.farmerStationDim);
            boolean held = sameDim && this.farmerStationDim != null
                    && VillagerPoiRegistry.isClaimedBy(
                            this.player, this.farmerStationDim, this.farmerStation);
            boolean valid = sameDim && held
                    && this.isStationValid(this.player.level(), this.farmerStation);
            if (!valid) {
                // 岗位失效（被拆/跨维）：释放自己名下残留；被其他假人持有时只清归属
                if (held && this.farmerStationDim != null) {
                    VillagerPoiRegistry.release(
                            this.player, this.farmerStationDim, this.farmerStation);
                }
                this.farmerStation = null;
                this.farmerStationDim = null;
            }
        }
        String key = this.farmerStation != null
                ? "mode_villager_farmer"
                : "mode_villager";
        if (!key.equals(this.professionKey)) {
            this.professionKey = key;
            BrainManager.refreshSuffix(this.player, this.modeKey(), key);
        }
    }

    /** 岗位方块有效性缝（默认读真实方块；离线测试覆写） */
    protected boolean isStationValid(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.COMPOSTER);
    }

    @Override
    public void onTickHealthy() {
        this.refreshProfession();
        super.onTickHealthy();
    }

    @Override
    public void onDetach() {
        // 换脑/下线/死亡：只释放本实例持有的长期岗位（旧实例不得清新实例岗位）；
        // 床位/集合点由各自 Goal 的 stop 按持有维度释放。
        // 实例级所有权：只释放本 Player 实例名下的占用（旧 UUID 实例不能清新实例岗位）
        VillagerPoiRegistry.releaseAll(this.player);
        this.farmerStation = null;
        this.farmerStationDim = null;
        if (this.player.isSleeping()) {
            this.player.stopSleepInBed(true, true);
        }
        super.onDetach();
    }
}
