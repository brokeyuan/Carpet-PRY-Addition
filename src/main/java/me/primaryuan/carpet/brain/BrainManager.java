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

    /** keep 记录：模式 + 狼模式主人（owner 可为 null）。假人下线重上后由 sweep 自动恢复 */
    private record KeepRecord(String mode, UUID owner) {}

    /** 带 keep 选项的脑子（UUID → 记录）；brain off 清除，服务器关闭随 JVM 消亡 */
    private static final Map<UUID, KeepRecord> KEEP = new HashMap<>();

    /** 挂载前假人原属的计分板队伍（卸载时恢复，不破坏原有队伍归属） */
    private static final Map<UUID, String> ORIGINAL_TEAM = new HashMap<>();

    /** 本模组脑队伍名前缀（每模式一队，后缀 = [模式名]） */
    private static final String TEAM_PREFIX = "pry_brain_";

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
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> detachAll());
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
        if (!(player instanceof EntityPlayerMPFake)) {
            return null;
        }
        PlayerBrainController brain = switch (mode) {
            case "zombie" -> new ZombieBrain(player);
            case "skeleton" -> new SkeletonBrain(player);
            case "irongolem" -> new IronGolemBrain(player);
            case "spider" -> new SpiderBrain(player);
            case "wolf" -> ownerUuid != null ? new WolfBrain(player, ownerUuid) : null;
            case "villager" -> new VillagerBrain(player);
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
            case "magmacube" -> new MagmaCubeBrain(player);
            case "fish" -> new FishBrain(player);
            default -> null;
        };
        if (brain == null) {
            return null;
        }
        detach(player, false);
        // keep 记录在 detach 之后写：模式切换/撤回保持都由本次 attach 语义决定
        if (keep) {
            KEEP.put(player.getUUID(), new KeepRecord(mode, ownerUuid));
        } else {
            KEEP.remove(player.getUUID());
        }
        // 屏蔽 Carpet 手动指令：清空已排队的计划任务（移动/攻击/使用等）
        ((ServerPlayerInterface) player).getActionPack().stopAll();
        BRAINS.put(player.getUUID(), brain);
        brain.onAttach();
        applyTeam(player, mode);
        return brain.modeKey();
    }

    /** 卸载脑子并清除 keep（brain off 语义）。返回是否确有脑子被卸载 */
    public static boolean detach(ServerPlayer player) {
        return detach(player, true);
    }

    /**
     * 卸载脑子；恢复身体静止输入与原计分板队伍。
     *
     * @param clearKeep true 时同时清除 keep 记录（brain off）；下线/规则切换等
     *                  自动卸载传 false——keep 语义要求下线重上后自动恢复
     */
    public static boolean detach(ServerPlayer player, boolean clearKeep) {
        PlayerBrainController brain = BRAINS.remove(player.getUUID());
        if (brain == null) {
            return false;
        }
        brain.onDetach();
        restoreTeam(player);
        if (clearKeep) {
            KEEP.remove(player.getUUID());
        }
        if (!player.isRemoved() && !player.hasDisconnected()) {
            // 归还控制权时把身体停在原地（避免残留最后一次的移动输入导致"滑行"）
            player.zza = 0.0F;
            player.xxa = 0.0F;
            player.setSprinting(false);
        }
        return true;
    }

    /** 停服清理：卸载全部脑子（keep 表随服务器实例一起作废——重启后假人需重新 spawn，届时自动恢复） */
    public static void detachAll() {
        BRAINS.values().forEach(PlayerBrainController::onDetach);
        BRAINS.clear();
        KEEP.clear();
        ORIGINAL_TEAM.clear();
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
        } catch (Throwable t) {
            // 单个脑子崩溃不允许拖垮服务器 tick：摘除该脑子并记录完整堆栈
            CarpetPrimaryuanServer.LOGGER.error(
                    "Fake player brain '{}' crashed, detaching", brain.modeKey(), t);
            detach(player);
        }
    }

    /**
     * 周期扫描：清理死会话与重生竞态的旧会话 + keep 补挂（下线重上的假人自动
     * 恢复脑子与名字后缀）。
     *
     * <p><b>同 UUID 重生竞态</b>：keep 假人死亡后立即重生命名/UUID 相同（皮肤
     * 缓存时 join 快于一个 sweep 周期），此时 BRAINS 里挂着死对象的旧会话——
     * 按 UUID 查到的"在线玩家"已是新假人，旧会话既不该继续 tick 也不能算作
     * 已挂载：统一按"会话归属不符"换血（旧会话按其自带玩家的名字清队伍、
     * 移出 BRAINS），随后 keep 补挂给新假人重建。</p>
     */
    private static void sweep(MinecraftServer server) {
        if (BRAINS.isEmpty() && KEEP.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PlayerBrainController>> it = BRAINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerBrainController> entry = it.next();
            PlayerBrainController brain = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean gone = !(player instanceof EntityPlayerMPFake)
                    || player.isRemoved()
                    || player.hasDisconnected();
            boolean stale = !gone && brain.player != player;
            if (gone || stale) {
                brain.onDetach();
                // 队伍按玩家名记录：优先按在线新假人清（重生竞态），对象已不在
                // 世界则用会话自带玩家的名字清（对象移除后名字仍可读）——
                // 否则同名真人后来加入会继承假人的后缀
                restoreTeam(player != null ? player : brain.player);
                ORIGINAL_TEAM.remove(entry.getKey());
                it.remove();
            }
        }
        // keep 补挂：在线、无有效会话、有记录、规则开启——下线重上（重新 spawn 的
        // 同名假人 UUID 相同）1 秒内自动恢复；模式失效时记录保留、下轮重试
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
                if (record != null) {
                    attach(player, record.mode(), record.owner(), true);
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

    /** 挂载后把假人加入本模式的计分板队伍（头顶名牌/Tab 显示 [模式名] 后缀） */
    private static void applyTeam(ServerPlayer player, String mode) {
        Scoreboard scoreboard = player.level().getServer().getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam previous = scoreboard.getPlayersTeam(name);
        ORIGINAL_TEAM.put(player.getUUID(), previous != null ? previous.getName() : null);
        // 每假人一队：原队伍前缀要"接"到头顶，共享队会让同模式假人互相泄漏前缀
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_PREFIX + mode + "_" + player.getUUID());
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_PREFIX + mode + "_" + player.getUUID());
        }
        // 队后缀每次挂载都刷新：颜色挂在后缀组件自己身上（不设队色——队色会把
        // 假人名字一起染色，需求是"名字颜色不变、只有后缀变色"）；三语显示名随
        // carpet 语言切换即时生效
        team.setPlayerSuffix(Component.literal(
                "[" + ServerI18n.tr("carpetprimaryuan.command.brain.mode_" + mode).getString() + "]")
                .withStyle(categoryColor(mode)));
        // 原队伍前缀随挂载保留（引用只读共享；卸载整队删除，原队属性不受影响）
        team.setPlayerPrefix(previous != null ? previous.getPlayerPrefix() : Component.empty());
        scoreboard.addPlayerToTeam(name, team);
    }

    /** 卸载后把假人移出本模组队伍（整队删除）并恢复挂载前的原队伍 */
    private static void restoreTeam(ServerPlayer player) {
        Scoreboard scoreboard = player.level().getServer().getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            scoreboard.removePlayerFromTeam(name, current);
            scoreboard.removePlayerTeam(current); // 每假人一队：随卸载删除，不残留
        }
        String previous = ORIGINAL_TEAM.remove(player.getUUID());
        if (previous != null) {
            // getPlayerTeam=按队伍名查（getPlayersTeam 是按玩家名查所属队伍，勿混用）
            PlayerTeam team = scoreboard.getPlayerTeam(previous);
            if (team != null) {
                scoreboard.addPlayerToTeam(name, team);
            }
        }
    }
}