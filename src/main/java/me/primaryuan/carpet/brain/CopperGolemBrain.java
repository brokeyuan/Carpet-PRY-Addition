package me.primaryuan.carpet.brain;

//#if MC >= 12110
import me.primaryuan.carpet.brain.goal.PlayerCopperGolemSortGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.server.level.ServerPlayer;
/**
 * 铜傀儡模式脑（装配器）：铜箱→普通箱分类搬运 + 闲逛，无仇恨。
 *
 * <p>搬运契约与玩家身体映射见 {@link PlayerCopperGolemSortGoal}（26.3
 * {@code TransportItemsBetweenContainers} 字节码对齐）。仅 1.21.10+（铜傀儡
 * 与铜箱所在版本）编译启用；旧版本无 {@code COPPER_CHESTS} 标签，本文件
 * 整体为空、命令树不暴露该模式。</p>
 *
 * <p>卸脑/换脑/下线：释放本脑的容器占用登记（{@link PlayerCopperGolemSortGoal
 * #releaseBrain}）；背包中的货物与主手物品原样保留，不回写源箱不销毁。</p>
 */
public class CopperGolemBrain extends PlayerBrainController {

    private final PlayerCopperGolemSortGoal sortGoal;

    public CopperGolemBrain(ServerPlayer player) {
        super(player);
        this.sortGoal = new PlayerCopperGolemSortGoal(this.prowler);
    }

    @Override
    public String modeKey() {
        return "coppergolem";
    }

    @Override
    protected void assemble() {
        // 分类搬运（MOVE，最高日常优先级）；铁傀儡/僵尸类不主动招惹铜傀儡
        // 身（玩家身体原版门口径，与村民/猪等被动模式一致）
        this.goalSelector.addGoal(2, this.sortGoal);
        // 闲逛兜底（原版 IDLE 活动同构：RandomStroll + 随机注视）
        this.goalSelector.addGoal(6, new PlayerRandomStrollGoal(this.prowler, 1.0));
        this.goalSelector.addGoal(7, new PlayerLookAtPlayerGoal(this.prowler, net.minecraft.world.entity.player.Player.class, 6.0F));
    }

    @Override
    public void onDetach() {
        PlayerCopperGolemSortGoal.releaseBrain(this.player);
        super.onDetach();
    }
}
//#else
//$$ /**
//$$  * 铜傀儡模式脑：仅 1.21.10+（铜傀儡/铜箱版本）存在。旧版本编译为空文件，
//$$  * 命令树不暴露 coppergolem 模式（不伪造铜箱、不用普通箱替代）。
//$$  */
//#endif
