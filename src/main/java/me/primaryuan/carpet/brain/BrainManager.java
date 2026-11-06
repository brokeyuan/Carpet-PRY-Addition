package me.primaryuan.carpet.brain;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanServer;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 假人脑子会话管理器：Brain 的挂载/卸载/每 tick 驱动与生命周期兜底。
 *
 * <h2>与 Carpet 手动指令的协调（"AI 接管"语义）</h2>
 * <ul>
 *   <li>挂载脑子时调用 {@code actionPack.stopAll()}：清空 Carpet 已排队的
 *       手动指令（move/attack/use/jump 等计划任务）；</li>
 *   <li>接管期间由 {@code EntityPlayerActionPackBrainMixin} 直接取消
 *       {@code actionPack.onUpdate()}——Carpet 每 tick 会用 actionPack 的
 *       移动输入覆写 {@code zza/xxa}（对假人无条件写入，即使全部为 0），
 *       取消后移动输入唯一写入者变为 Brain；后续新下发的手动指令也不再执行；</li>
 *   <li>卸载脑子后 Carpet 手动指令立即恢复可用（onUpdate 不再被取消）。</li>
 * </ul>
 *
 * <h2>每 tick 驱动链</h2>
 * 由 {@code PlayerBrainController.tick()} 串联：
 * {@code targetSelector → goalSelector → navigation → moveControl → lookControl}，
 * 本类只在 {@code ServerPlayer.tick()} 头部（{@code ServerPlayerBrainMixin}）
 * 调用 {@link #onPlayerTick} 产生一次入口。
 *
 * <h2>生命周期兜底</h2>
 * 假人下线后 tick 不再触发，故中央调度器每 20 tick 扫描一次会话表清理残留
 * （与 SendtoLinkManager 懒清理同思路）；服务器停止时全部卸载。因为整个体系
 * <b>不生成任何额外实体</b>（仅 Mixin 注入字段 + 普通对象引用），卸载即纯净回收。
 */
public final class BrainManager {

    /** 假人 UUID → 挂载中的脑子（策略对象；字段注入在玩家实体上的 MobFields 中） */
    private static final Map<UUID, PlayerBrainController> BRAINS = new HashMap<>();

    /** keep 记录：模式 + 默认主人（狼的仇恨同步 / 悦灵的交付对象；owner 可为 null）。
     *  假人下线重上后由 sweep 自动恢复 */
    private record KeepRecord(String mode, UUID owner) {}

    /**
     * 带 keep 选项的脑子（UUID → 记录）。语义 = <b>本次服务器运行内</b>有效：
     * 假人下线重上自动恢复，服务器重启随会话表一起作废（重生的假人需重新挂载）。
     * brain off 立即清除。
     */
    private static final Map<UUID, KeepRecord> KEEP = new HashMap<>();

    /**
     * 本会话实际创建的计分板临时队及挂载前原队伍。保存对象引用而非仅队名：
     * 同名队伍被删除后重建会产生新对象，旧会话不得按名字误认领/删除新队。
     * createdBySession 标记该对象是否由本会话创建，便于回滚时区分外部队伍。
     */
    private record TeamState(ServerPlayer sessionPlayer, PlayerTeam temporaryTeam,
                             PlayerTeam originalTeam, boolean createdBySession) {}

    /** UUID → 本会话队伍状态（只在当前队伍仍是本会话对象时恢复/刷新） */
    private static final Map<UUID, TeamState> TEAM_STATES = new HashMap<>();

    /** 本模组脑队伍名前缀（每模式一队，后缀 = [模式名]） */
    private static final String TEAM_PREFIX = "pry_brain_";

    /**
     * 挂载目标身份门（package-private 测试缝）：生产恒为 Carpet 原生假人。
     * 纯 JVM 测试无法构造可用的 EntityPlayerMPFake 子类，测试临时放行桩。
     */
    static java.util.function.Predicate<ServerPlayer> attachGate =
            p -> p instanceof EntityPlayerMPFake;

    private static boolean registered = false;

    private BrainManager() {}

    /** 在 CarpetPrimaryuanServer.onGameStarted 中调用：注册周期扫描与停服清理 */
    public static void init() {
        if (registered) return;
        registered = true;
        // 每 20 tick（1 秒）扫描：清理 tick 驱动已触达不了的死会话（计数器实现，
        // 与类注释契约一致；空表时近乎零成本）
        ServerTickScheduler.register(server -> {
            if (++sweepCounter % 20 != 0) {
                return true;
            }
            sweep(server);
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            detachAll();
            VillagerPoiRegistry.clear(); // 村民 POI 占用登记随停服清空
        });
    }

    /** sweep 的 20 tick 分频计数器 */
    private static int sweepCounter = 0;

    /** 该假人是否处于 AI 接管状态（供 ActionPack 取消 mixin 查询）。
     *  须校验会话归属：keep 假人死后立即重生命名相同 UUID 相同，sweep 补挂前的
     *  一个周期内 BRAINS 里挂的是死对象的旧会话，不算数 */
    public static boolean hasBrain(ServerPlayer player) {
        PlayerBrainController brain = BRAINS.get(player.getUUID());
        return brain != null && brain.player == player;
    }

    /** 当前挂载的脑子模式标识（无脑子返回 null，供命令状态查询）。
     *  校验脑子记录的实例就是查询实例：Carpet 影子假人与真人共享同一
     *  GameProfile/UUID，仅按 UUID 查表会互相串状态 */
    public static String getModeKey(ServerPlayer player) {
        PlayerBrainController brain = BRAINS.get(player.getUUID());
        return brain != null && brain.player == player ? brain.modeKey() : null;
    }

    /**
     * 会话诊断快照（状态查询用）：无有效会话返回 null（= 脑子被卸载态）。
     * 实例校验口径同 {@link #getModeKey}。
     */
    public static PlayerBrainController.Diagnostics diagnosticsOf(ServerPlayer player) {
        PlayerBrainController brain = BRAINS.get(player.getUUID());
        return brain != null && brain.player == player ? brain.diagnostics() : null;
    }

    /**
     * 战斗队伍变更后的全局撤销：遍历会话，凡当前目标与本人互为同队的立即
     * 撤销目标并停追击（改同队立即生效，不需重新挂脑）。
     */
    static void revokeTeamInvalidTargets() {
        BRAINS.values().forEach(PlayerBrainController::revokeTeamTarget);
    }

    /**
     * 该假人是否处于"敌对"AI 模式（铁傀儡脑的目标选择用：
     * 敌对假人在铁傀儡眼里等同怪物）。口径 = 原版 {@code Enemy} 标记接口
     * （26.3 字节码：{@code Monster implements Enemy}，铁傀儡目标谓词
     * {@code instanceof Enemy}）：敌对与中立敌对模式的生物均实现 Enemy——
     * zombie / babyzombie / drowned / skeleton / witherskeleton / zombiepiglin /
     * pillager / vindicator / spider / slime / magmacube / piglin / piglinbrute /
     * enderman。
     */
    public static boolean isHostileFake(ServerPlayer player) {
        String mode = getModeKey(player);
        return "zombie".equals(mode) || "babyzombie".equals(mode) || "skeleton".equals(mode)
                || "pillager".equals(mode) || "spider".equals(mode)
                || "piglin".equals(mode) || "enderman".equals(mode)
                || "drowned".equals(mode) || "zombiepiglin".equals(mode)
                || "witherskeleton".equals(mode) || "vindicator".equals(mode)
                || "piglinbrute".equals(mode)
                || "slime".equals(mode) || "magmacube".equals(mode);
    }

    /**
     * 挂载（或切换）脑子。仅对 Carpet 原生假人有效。
     * 狼模式要求传入主人 UUID（执行命令的玩家）；传 null 时狼模式挂载失败。
     *
     * @return 挂载成功后的脑子的模式标识；非假人/未知模式/狼模式缺主人返回 null
     */
    public static String attach(ServerPlayer player, String mode, UUID ownerUuid) {
        return attach(player, mode, ownerUuid, false);
    }

    /**
     * 挂载（或切换）脑子，可选 keep 保持。
     *
     * @param keep true 时记入 KEEP 表：假人下线重上后由 sweep 自动恢复同一脑子；
     *             false 时清除该假人的 keep 记录（显式重挂不带 keep 即撤回保持）
     */
    public static String attach(ServerPlayer player, String mode, UUID ownerUuid, boolean keep) {
        if (!attachGate.test(player)) {
            return null;
        }
        PlayerBrainController brain = switch (mode) {
            case "zombie" -> new ZombieBrain(player);
            case "skeleton" -> new SkeletonBrain(player);
            case "irongolem" -> new IronGolemBrain(player);
            case "spider" -> new SpiderBrain(player);
            case "wolf" -> ownerUuid != null ? new WolfBrain(player, ownerUuid) : null;
            case "villager" -> new VillagerBrain(player);
            case "allay" -> new AllayBrain(player, ownerUuid);
            case "fox" -> new FoxBrain(player);
            case "witch" -> new WitchBrain(player);
            case "pillager" -> new PillagerBrain(player);
            case "enderman" -> new EndermanBrain(player);
            case "babyzombie" -> new BabyZombieBrain(player);
            case "pig" -> new PigBrain(player);
            case "piglin" -> new PiglinBrain(player);
            case "drowned" -> new DrownedBrain(player);
            case "zombiepiglin" -> new ZombiePiglinBrain(player);
            case "witherskeleton" -> new WitherSkeletonBrain(player);
            case "vindicator" -> new VindicatorBrain(player);
            case "piglinbrute" -> new PiglinBruteBrain(player);
            case "slime" -> new SlimeBrain(player, "slime");
            //#if MC >= 12110
            case "coppergolem" -> new CopperGolemBrain(player);
            //#endif
            case "magmacube" -> new MagmaCubeBrain(player);
            case "fish" -> new FishBrain(player);
            default -> null;
        };
        if (brain == null) {
            return null;
        }
        return installSession(player, brain, mode, ownerUuid, keep);
    }

    /**
     * 安装已构造的会话。生产入口 {@link #attach} 已完成假人门禁；包内测试
     * 可注入装配失败的控制器，验证失败回滚（会话、keep、监听器、临时队与
     * 手动控制均释放）。挂载异常不得留下半接管状态。
     */
    static String installSession(ServerPlayer player, PlayerBrainController brain,
                                 String mode, UUID ownerUuid, boolean keep) {
        // 同 UUID 的旧实例会话（Carpet 影子假人/重生竞态）：只释放旧实例自己
        // 绑定的会话与队伍，绝不能按 UUID 直接拆掉新实例状态。同实例切脑走普通 detach。
        PlayerBrainController previous = BRAINS.get(player.getUUID());
        if (previous != null && previous.player != player) {
            BRAINS.remove(player.getUUID(), previous);
            releaseSession(previous, false);
        } else {
            detach(player, false);
        }
        boolean keepWritten = false;
        try {
            // keep 记录在 detach 之后写：模式切换/撤回保持都由本次 attach 语义决定
            if (keep) {
                KEEP.put(player.getUUID(), new KeepRecord(mode, ownerUuid));
            } else {
                KEEP.remove(player.getUUID());
            }
            keepWritten = true;
            // 屏蔽 Carpet 手动指令：清空已排队的计划任务（移动/攻击/使用等）
            stopActionPack(player);
            BRAINS.put(player.getUUID(), brain);
            brain.onAttach();
            applyTeam(brain, player, mode);
            return brain.modeKey();
        } catch (Throwable t) {
            rollbackAttach(player, brain, keepWritten);
            throw t;
        }
    }

    /** 停掉 Carpet 已排队的手动指令；测试桩不实现 Carpet 接口时跳过。 */
    private static void stopActionPack(ServerPlayer player) {
        if (player instanceof ServerPlayerInterface actionPackPlayer) {
            actionPackPlayer.getActionPack().stopAll();
        }
    }

    /**
     * 挂载失败后的局部回滚：先摘除会话（恢复手动控制），再释放本会话的
     * 监听器/目标/临时队与 keep。各步独立兜底，单步失败不阻止其余清理。
     */
    private static void rollbackAttach(ServerPlayer player, PlayerBrainController brain, boolean keepWritten) {
        BRAINS.remove(player.getUUID(), brain);
        try {
            brain.onDetach();
        } catch (Throwable t) {
            CarpetPrimaryuanServer.LOGGER.error(
                    "brain attach rollback detach failed for {}", player.getName().getString(), t);
        }
        try {
            restoreTeam(player);
        } catch (Throwable t) {
            CarpetPrimaryuanServer.LOGGER.error(
                    "brain attach rollback team restore failed for {}", player.getName().getString(), t);
        }
        if (keepWritten) {
            KEEP.remove(player.getUUID());
        }
        if (player instanceof EntityPlayerMPFake
                && !player.isRemoved() && !player.hasDisconnected()) {
            player.zza = 0.0F;
            player.xxa = 0.0F;
            player.setSprinting(false);
        }
    }

    /** 卸载脑子并清除 keep（brain off 语义）。返回是否确有脑子被卸载 */
    public static boolean detach(ServerPlayer player) {
        return detach(player, true);
    }

    /**
     * 卸载脑子；恢复身体静止输入与原计分板队伍。
     *
     * <p>当前无脑子也须清除 keep：规则自动卸载（detach(player,false)）后，重新开启
     * 且 sweep 补挂前执行 off，若因"无脑子"提前返回，keep 记录残留会让扫描把脑子
     * 再挂回来——off 语义是"不再保持"，与当前是否挂载无关。</p>
     *
     * @param clearKeep true 时同时清除 keep 记录（brain off）；下线/规则切换等
     *                  自动卸载传 false——keep 语义要求下线重上后自动恢复
     */
    /**
     * 假人下线入口（PlayerList.remove HEAD 注入调用）：必须在 save 与原版
     * RootVehicle 卸载链之前执行——假人（唯一玩家乘客）骑鸡下线时，先驱散
     * 本脑建立的骑乘，鸡恢复自由实体，随后的 hasExactlyOnePlayerPassenger
     * 判断 isPassenger=false 直接跳过 UNLOADED_WITH_PLAYER 卸载分支，鸡留在世界。
     * 下线传 clearKeep=false：keep 假人重上后由 sweep 自动恢复。
     */
    public static void onFakePlayerRemoved(ServerPlayer player) {
        detach(player, false);
    }

    public static boolean detach(ServerPlayer player, boolean clearKeep) {
        PlayerBrainController brain = BRAINS.get(player.getUUID());
        // 先核对会话绑定实例：同 UUID 旧实例的自动下线/卸载事件不得移除新实例的
        // 会话、keep、队伍与资源；只有确实无会话时才执行显式 off 的 keep 清理。
        if (brain == null) {
            if (clearKeep) {
                KEEP.remove(player.getUUID());
            }
            return false;
        }
        if (brain.player != player) {
            // 旧实例事件：新实例的会话/keep/队伍/资源一概不碰
            return false;
        }
        BRAINS.remove(player.getUUID(), brain);
        releaseSession(brain, clearKeep);
        return true;
    }

    /** 释放一个已确认归属的会话：卸载、恢复该会话自己的队伍、按需清 keep、复位身体输入 */
    private static void releaseSession(PlayerBrainController brain, boolean clearKeep) {
        ServerPlayer bound = brain.player;
        brain.onDetach();
        if (bound != null) {
            restoreTeam(bound);
            if (clearKeep) {
                KEEP.remove(bound.getUUID());
            }
            if (!bound.isRemoved() && !bound.hasDisconnected()) {
                // 归还控制权时把身体停在原地（避免残留最后一次的移动输入导致"滑行"）
                bound.zza = 0.0F;
                bound.xxa = 0.0F;
                bound.setSprinting(false);
            }
        }
    }

    /** 停服清理：卸载全部脑子并恢复原计分板队伍——世界停服时仍会保存，漏恢复会把
     *  临时脑队伍（后缀/成员）写进存档，重启后残留且原队伍记录丢失 */
    public static void detachAll() {
        BRAINS.values().forEach(brain -> {
            brain.onDetach();
            if (brain.player != null) {
                restoreTeam(brain.player);
            }
        });
        BRAINS.clear();
        KEEP.clear();
        TEAM_STATES.clear();
    }

    /**
     * 群体仇恨广播（{@code PlayerGroupHurtByTargetGoal} 的后端）：把攻击者
     * 告知范围内指定脑模式的同伴假人。范围 20 格水平 / 10 格垂直（原版
     * {@code HurtByTargetGoal.alertOthers} 为跟随距离×10，移植版统一取常规
     * 索敌半径）；只唤醒当前无攻击目标的同伴（原版跳过已锁定个体）；玩家类
     * 攻击者沿用全局门禁（创造/旁观/和平难度不传播）。零额外实体：仅写
     * 同伴脑子的目标字段。
     */
    public static void alertOthers(PryMob source, LivingEntity attacker, Set<String> modes) {
        LivingEntity self = source.asLiving();
        if (!attacker.isAlive() || attacker.level() != self.level() || attacker == self) {
            return;
        }
        if (attacker instanceof Player p && (p.isSpectator() || p.isCreative()
                || self.level().getDifficulty() == Difficulty.PEACEFUL)) {
            return;
        }
        List<ServerPlayer> nearby = self.level().getEntitiesOfClass(ServerPlayer.class,
                self.getBoundingBox().inflate(20.0, 10.0, 20.0));
        for (ServerPlayer p : nearby) {
            if (p == self || p == attacker) {
                continue;
            }
            // 战斗队伍：攻击者是同伴的同队假人 → 不向该同伴广播（同队误伤不围攻）
            if (BrainTeams.isTeamFriendly(p, attacker)) {
                continue;
            }
            PlayerBrainController brain = BRAINS.get(p.getUUID());
            // brain.player != p：Carpet 影子假人与真人同 UUID，须确认为挂脑本体
            if (brain == null || brain.player != p || !modes.contains(brain.modeKey())) {
                continue;
            }
            if (brain.prowler.getTarget() != null) {
                continue; // 原版语义：已有目标的同伴不覆盖
            }
            brain.alertAnger(attacker);
        }
    }

    /**
     * 每 tick 驱动入口：由 ServerPlayerBrainMixin 在 ServerPlayer.tick() 头部调用。
     * 决策与移动输入写入发生在本 tick 的 travel() 之前，时序与原版生物
     * tickMovement → travel 一致（移动输入在本 tick 稍后被消费）。
     */
    public static void onPlayerTick(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake)) {
            return; // 真人：无脑子、零开销早退
        }
        PlayerBrainController brain = BRAINS.get(player.getUUID());
        if (brain == null || brain.player != player) {
            // null = 无脑子；brain.player != player = keep 同 UUID 重生竞态留下的
            // 死对象旧会话（tick 驱动不得打在新假人身上，由 sweep 统一换血）
            return;
        }
        // 规则被关掉：立即自动卸载，Carpet 手动指令随之恢复（keep 记录保留，
        // 规则重新开启且假人在线时由 sweep 自动恢复脑子）
        if (!CarpetPrimaryuanSettings.fakePlayerBrain) {
            detach(player, false);
            return;
        }
        // 假人死亡/断开：只停动作，会话由周期扫描回收（死亡到移除仍会 tick 若干次）
        if (player.isRemoved() || player.hasDisconnected() || player.isDeadOrDying()) {
            player.zza = 0.0F;
            player.xxa = 0.0F;
            return;
        }
        try {
            brain.tick();
            brain.onTickHealthy();
        } catch (Throwable t) {
            if (!handleTickCrash(brain, t)) {
                detach(player);
            }
        }
    }

    /**
     * tick 崩溃处置：瞬时异常保活会话（单次崩溃可能来自世界/实体状态的瞬时竞态，
     * 直接卸脑并清 keep 会让使用者毫无感知地失去脑子——"挂了脑子没反应"的
     * 实锤成因之一）；日志限频（首次 + 每第 20 次连续崩溃记完整堆栈）。
     *
     * @return false = 连续崩溃达 {@link PlayerBrainController#MAX_CONSECUTIVE_CRASHES}，
     *         属确定性故障，调用方应卸脑并清 keep（重挂必然复现，保活只会崩溃循环）
     */
    static boolean handleTickCrash(PlayerBrainController brain, Throwable t) {
        brain.onTickCrashed();
        int crashes = brain.consecutiveCrashes();
        if (crashes == 1 || (crashes % 20 == 0 && crashes < PlayerBrainController.MAX_CONSECUTIVE_CRASHES)) {
            CarpetPrimaryuanServer.LOGGER.error(
                    "Fake player brain '{}' tick crashed (consecutive {})", brain.modeKey(), crashes, t);
        }
        if (crashes >= PlayerBrainController.MAX_CONSECUTIVE_CRASHES) {
            CarpetPrimaryuanServer.LOGGER.error(
                    "Fake player brain '{}' crashed {} ticks in a row, detaching (keep cleared)",
                    brain.modeKey(), crashes);
            return false;
        }
        return true;
    }

    /**
     * 周期扫描：清理死会话与重生竞态的旧会话 + keep 补挂（下线重上的假人自动
     * 恢复脑子与名字后缀）。
     *
     * <p><b>会话有效性按实例身份判定</b>：会话绑定的实例仍在在线列表（身份相等）
     * 即健康——不再按 UUID 反查比对实例。影子假人与真人共享同一 UUID，UUID 反查
     * 可能命中真人实例，把健康的假人脑子误判为"归属不符"而每轮 sweep 强拆
     * （"挂脑后一会儿就停"的实锤成因之一）。</p>
     *
     * <p><b>同 UUID 重生竞态</b>：keep 假人死亡后立即重生命名/UUID 相同（皮肤
     * 缓存时 join 快于一个 sweep 周期），此时 BRAINS 里挂着死对象的旧会话——
     * 旧实例已不在在线列表，按"会话失效"换血（旧会话按其自带玩家的名字清队伍、
     * 移出 BRAINS），随后 keep 补挂给新假人重建。无 keep 的脑子随换血失效并记
     * 一条 INFO（使用者可据此区分"脑子被卸载"与"有脑无目标"）。</p>
     */
    private static void sweep(MinecraftServer server) {
        if (BRAINS.isEmpty() && KEEP.isEmpty()) {
            return;
        }
        // 身份集合：ServerPlayer 不覆写 equals，HashSet 即身份相等
        Set<ServerPlayer> onlineIdentities = new HashSet<>(server.getPlayerList().getPlayers());
        Iterator<Map.Entry<UUID, PlayerBrainController>> it = BRAINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerBrainController> entry = it.next();
            PlayerBrainController brain = entry.getValue();
            ServerPlayer bound = brain.player;
            boolean gone = bound == null || bound.isRemoved() || bound.hasDisconnected()
                    || !onlineIdentities.contains(bound);
            if (gone) {
                // 统一走绑定实例释放：队伍按对象移除后仍可读的名字清理——否则
                // 同名真人后来加入会继承假人的后缀
                releaseSession(brain, false);
                it.remove();
                String mode = brain.modeKey();
                if (KEEP.containsKey(entry.getKey())) {
                    CarpetPrimaryuanServer.LOGGER.info(
                            "brain session of {} ({}) replaced, keep will re-attach", mode, bound.getName().getString());
                } else {
                    CarpetPrimaryuanServer.LOGGER.info(
                            "brain session of {} ({}) discarded: entity left or replaced (no keep)",
                            mode, bound.getName().getString());
                }
            }
        }
        // keep 补挂：在线、无有效会话、有记录、规则开启——下线重上（重新 spawn 的
        // 同名假人 UUID 相同）1 秒内自动恢复；模式失效时记录保留、下轮重试。
        // 逐假人 try/catch：单个假人补挂异常不允许打断本轮，连累其余 keep 假人
        if (!KEEP.isEmpty() && CarpetPrimaryuanSettings.fakePlayerBrain) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player instanceof EntityPlayerMPFake)) {
                    continue;
                }
                PlayerBrainController brain = BRAINS.get(player.getUUID());
                if (brain != null && brain.player == player) {
                    continue;
                }
                KeepRecord record = KEEP.get(player.getUUID());
                if (record == null) {
                    continue;
                }
                try {
                    attach(player, record.mode(), record.owner(), true);
                } catch (Throwable t) {
                    CarpetPrimaryuanServer.LOGGER.error(
                            "keep re-attach failed for {} ({}), will retry next sweep",
                            player.getName().getString(), record.mode(), t);
                }
            }
        }
    }


    // ==================== 名字后缀（计分板队伍） ====================

    /**
     * 模式 → 名字后缀颜色：敌对红 / 中立蓝 / 不敌对绿。
     * 红 = 见面即打玩家；蓝 = 被打/激怒/条件触发才敌对（蜘蛛昼伏夜击、末影人
     * 凝视激怒、猪灵看装行事、僵尸猪灵被打反击、狼护主）；绿 = 不主动打玩家
     * （铁傀儡守卫、村民/猪/鱼被动）。口径独立于 {@link #isHostileFake}
     * （那是铁傀儡索敌用的 Enemy 标记口径，含中立敌对）。
     */
    private static ChatFormatting categoryColor(String mode) {
        return switch (mode) {
            case "zombie", "babyzombie", "skeleton", "witherskeleton", "drowned",
                    "pillager", "vindicator", "piglinbrute", "slime", "magmacube" -> ChatFormatting.RED;
            case "zombiepiglin", "piglin", "enderman", "spider", "wolf" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GREEN;
        };
    }

    /** 为本次会话选择一个尚未被占用的临时队名；已有同名队伍一律不认领。 */
    private static String uniqueTeamName(Scoreboard scoreboard, String baseName) {
        if (scoreboard.getPlayerTeam(baseName) == null) {
            return baseName;
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = baseName + "_" + i;
            if (scoreboard.getPlayerTeam(candidate) == null) {
                return candidate;
            }
        }
        return baseName + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 挂载后把假人加入本模式的计分板队伍（头顶名牌/Tab 显示 [模式名] 后缀） */
    private static void applyTeam(PlayerBrainController brain, ServerPlayer player, String mode) {
        Scoreboard scoreboard = player.level().getServer().getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam previous = scoreboard.getPlayersTeam(name);
        String baseName = TEAM_PREFIX + mode + "_" + player.getUUID();
        // 同名既有队伍可能是外部队伍（或旧会话残留）：不认领、不改写、不删除，
        // 改用唯一队名。对象身份是后续刷新/清理的唯一所有权凭据。
        String temporaryTeam = uniqueTeamName(scoreboard, baseName);
        PlayerTeam team = scoreboard.addPlayerTeam(temporaryTeam);
        TEAM_STATES.put(player.getUUID(),
                new TeamState(player, team, previous, true));
        // 原队伍行为面整体继承（挂脑不改变原队伍归属带来的行为：颜色、友伤保护、
        // 隐身可见、名字/死亡消息可见性、碰撞规则）——只复制前缀会让假人进入
        // 临时队后丢失原队伍的全部队务语义
        if (previous != null) {
            team.setAllowFriendlyFire(previous.isAllowFriendlyFire());
            team.setSeeFriendlyInvisibles(previous.canSeeFriendlyInvisibles());
            team.setNameTagVisibility(previous.getNameTagVisibility());
            team.setDeathMessageVisibility(previous.getDeathMessageVisibility());
            team.setCollisionRule(previous.getCollisionRule());
            //#if MC >= 260200
            //$$ team.setColor(previous.getColor()); // 26.2 起队色为 Optional<TeamColor>
            //#else
            team.setColor(previous.getColor()); // 1.21~26.1.2 为 ChatFormatting
            //#endif
        }
        // 队后缀每次挂载都刷新：颜色挂在后缀组件自己身上；三语显示名随
        // carpet 语言切换即时生效（键由脑子给出——村民按职业给未就业/农民键）
        team.setPlayerSuffix(Component.literal(
                "[" + ServerI18n.tr("carpetprimaryuan.command.brain." + brain.suffixLangKey()).getString() + "]")
                .withStyle(categoryColor(mode)));
        // 原队伍前缀随挂载保留（引用只读共享；卸载整队删除，原队属性不受影响）
        team.setPlayerPrefix(previous != null ? previous.getPlayerPrefix() : Component.empty());
        scoreboard.addPlayerToTeam(name, team);
    }

    /**
     * 名牌后缀刷新（脑内状态变化用，如村民职业变更）：只改本模组临时队
     * 的后缀组件，不动登录名/UUID/Tab 列表。非本模组队伍（已被真人改队）
     * 时不刷——那是使用者的队伍， brain 无权覆写。
     */
    static void refreshSuffix(ServerPlayer player, String mode, String suffixKey) {
        TeamState state = TEAM_STATES.get(player.getUUID());
        if (state == null || state.sessionPlayer() != player) {
            return;
        }
        Scoreboard scoreboard = player.level().getServer().getScoreboard();
        PlayerTeam team = state.temporaryTeam();
        // 必须同时满足：当前所属队 == 本会话对象，且该名字下仍是同一对象。
        // 管理员删除后同名重建的是新对象，旧会话不得改写其后缀。
        if (scoreboard.getPlayersTeam(player.getScoreboardName()) != team
                || scoreboard.getPlayerTeam(team.getName()) != team) {
            return;
        }
        team.setPlayerSuffix(Component.literal(
                "[" + ServerI18n.tr("carpetprimaryuan.command.brain." + suffixKey).getString() + "]")
                .withStyle(categoryColor(mode)));
    }

    /**
     * 卸载后清理本会话的临时队与恢复记录。只有假人仍属于本会话临时队时
     * 才恢复挂载前的原队伍；管理员已改到其他队或移出队伍时保留外部结果。
     * 临时队按记录的具体队名删除，不按前缀猜所有权。
     */
    private static void restoreTeam(ServerPlayer player) {
        TeamState state = TEAM_STATES.get(player.getUUID());
        if (state == null || state.sessionPlayer() != player) {
            return; // 无本实例会话记录（含同 UUID 旧实例事件）：不触碰当前队伍
        }
        Scoreboard scoreboard = player.level().getServer().getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam owned = state.temporaryTeam();
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        boolean stillInOwned = current == owned
                && scoreboard.getPlayerTeam(owned.getName()) == owned;
        if (stillInOwned) {
            scoreboard.removePlayerFromTeam(name, owned);
            PlayerTeam previous = state.originalTeam();
            // 原队伍也按对象校验：被删除后同名重建的外部队伍不自动恢复。
            if (previous != null && scoreboard.getPlayerTeam(previous.getName()) == previous) {
                scoreboard.addPlayerToTeam(name, previous);
            }
        }
        // 只清理本会话创建且仍由本会话持有的对象；同名重建的新对象不碰。
        // 有其他成员时保留队伍，避免把外部成员的归属一并删掉。
        if (state.createdBySession()
                && scoreboard.getPlayerTeam(owned.getName()) == owned
                && owned.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(owned);
        }
        TEAM_STATES.remove(player.getUUID(), state);
    }
}