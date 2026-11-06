package me.primaryuan.carpet.brain;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 假人战斗队伍（运行期，跨重启不持久化）。
 *
 * <p><b>语义（独立于模式名与名牌后缀计分板队）</b>：同一非空队名才互为友军；
 * 两个都没入队不算同队。同队保护跨脑模式生效；对敌对模式，异队/无队假人
 * 仍是合法敌方；被动模式（猪/鱼/村民）没有目标 Goal，队伍不改变其行为；
 * 真人不参与判定（{@link #isFakeCombatant} 门禁——影子假人与真人共享 UUID，
 * 队伍只对假人实例生效）。难度/创造/旁观/主人豁免等原版门禁在队伍过滤之外
 * 独立生效，队伍不能绕过。</p>
 *
 * <p><b>生命周期</b>：按 UUID 记录——换脑、brain off、keep 开关都不清除；
 * 假人下线重上自动恢复；服务器停止清空（不写存档）。</p>
 */
public final class BrainTeams {

    /** 保留关键字：退出队伍（不可作队名，大小写不敏感） */
    public static final String OFF_KEYWORD = "off";

    /** 战斗队伍（假人 UUID → 队名）；停服清空，跨重启不持久化 */
    private static final Map<UUID, String> TEAMS = new HashMap<>();

    private static boolean registered = false;

    private BrainTeams() {}

    /** 在 CarpetPrimaryuanServer.onGameStarted 中调用：注册停服清理 */
    public static void init() {
        if (registered) return;
        registered = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TEAMS.clear());
    }

    // ==================== 成员管理 ====================

    /** 加入或更换队伍；调用方保证队名合法（非 off、非空） */
    public static void join(UUID fakeId, String team) {
        TEAMS.put(fakeId, team);
        BrainManager.revokeTeamInvalidTargets();
    }

    /** 退出队伍；未入队返回 false */
    public static boolean leave(UUID fakeId) {
        boolean removed = TEAMS.remove(fakeId) != null;
        if (removed) {
            BrainManager.revokeTeamInvalidTargets();
        }
        return removed;
    }

    /** 该假人当前队名（未入队返回 null） */
    public static String teamOf(UUID fakeId) {
        return TEAMS.get(fakeId);
    }

    /** 已有队名集合（Tab 补全用；排序去重） */
    public static Set<String> knownTeams() {
        return new TreeSet<>(TEAMS.values());
    }

    /** 队名合法性：非空、不与保留关键字冲突（大小写不敏感） */
    public static boolean isValidName(String team) {
        return team != null && !team.isEmpty() && !OFF_KEYWORD.equalsIgnoreCase(team.trim());
    }

    // ==================== 敌我判定 ====================

    /**
     * 队伍判定的"参战身份"门：只有 Carpet 假人携带战斗队伍身份。
     * 真人按原有生物行为处理，不自动获得队伍身份；影子假人与真人共享 UUID，
     * 该门保证队伍状态不串到真人身上。
     */
    public static boolean isFakeCombatant(LivingEntity entity) {
        return entity instanceof EntityPlayerMPFake;
    }

    /**
     * 参战身份判定缝（package-private）：生产恒为 {@link #isFakeCombatant}；
     * 纯 JVM 测试无 Mixin、无法构造 EntityPlayerMPFake 的可用子类（构造器私有），
     * 测试临时替换本谓词让桩通过。生产代码不得改写。
     */
    static java.util.function.Predicate<LivingEntity> combatantGate = BrainTeams::isFakeCombatant;

    /**
     * 双方是否互为友军（同队假人）：供索敌、反击、群体仇恨、凝视激怒与
     * 出手前检查共用——同队候选一律不可选。
     *
     * <p>参战双方都必须是假人且入同一非空队才成立；任一方无队/异队/非假人
     * → false（交战合法，交由各模式原有规则裁决）。</p>
     */
    public static boolean isTeamFriendly(LivingEntity self, LivingEntity candidate) {
        if (self == null || candidate == null || self == candidate) {
            return false;
        }
        if (!combatantGate.test(self) || !combatantGate.test(candidate)) {
            return false;
        }
        String mine = TEAMS.get(((ServerPlayer) self).getUUID());
        if (mine == null || mine.isEmpty()) {
            return false;
        }
        return mine.equals(TEAMS.get(((ServerPlayer) candidate).getUUID()));
    }
}
