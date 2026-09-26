package me.primaryuan.carpet.brain;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanServer;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

    /** 该假人是否处于 AI 接管状态（供 ActionPack 取消 mixin 查询） */
    public static boolean hasBrain(ServerPlayer player) {
        return BRAINS.containsKey(player.getUUID());
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
     * 敌对假人在铁傀儡眼里等同怪物）。敌对 = 会主动攻击玩家的模式：
     * zombie / babyzombie / skeleton / pillager / spider / piglin / enderman。
     */
    public static boolean isHostileFake(ServerPlayer player) {
        String mode = getModeKey(player);
        return "zombie".equals(mode) || "babyzombie".equals(mode) || "skeleton".equals(mode)
                || "pillager".equals(mode) || "spider".equals(mode)
                || "piglin".equals(mode) || "enderman".equals(mode);
    }

    /**
     * 挂载（或切换）脑子。仅对 Carpet 原生假人有效。
     * 狼模式要求传入主人 UUID（执行命令的玩家）；传 null 时狼模式挂载失败。
     *
     * @return 挂载成功后的脑子的模式标识；非假人/未知模式/狼模式缺主人返回 null
     */
    public static String attach(ServerPlayer player, String mode, UUID ownerUuid) {
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
            default -> null;
        };
        if (brain == null) {
            return null;
        }
        detach(player);
        // 屏蔽 Carpet 手动指令：清空已排队的计划任务（移动/攻击/使用等）
        ((ServerPlayerInterface) player).getActionPack().stopAll();
        BRAINS.put(player.getUUID(), brain);
        brain.onAttach();
        return brain.modeKey();
    }

    /** 卸载脑子；恢复身体静止输入。返回是否确有脑子被卸载 */
    public static boolean detach(ServerPlayer player) {
        PlayerBrainController brain = BRAINS.remove(player.getUUID());
        if (brain == null) {
            return false;
        }
        brain.onDetach();
        if (!player.isRemoved() && !player.hasDisconnected()) {
            // 归还控制权时把身体停在原地（避免残留最后一次的移动输入导致"滑行"）
            player.zza = 0.0F;
            player.xxa = 0.0F;
            player.setSprinting(false);
        }
        return true;
    }

    /** 停服清理：卸载全部脑子 */
    public static void detachAll() {
        BRAINS.values().forEach(PlayerBrainController::onDetach);
        BRAINS.clear();
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
        if (brain == null) {
            return;
        }
        // 规则被关掉：立即自动卸载，Carpet 手动指令随之恢复
        if (!CarpetPrimaryuanSettings.fakePlayerBrain) {
            detach(player);
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

    /** 周期扫描：移除已下线/失效假人的会话（tick 驱动无法触达的残留） */
    private static void sweep(MinecraftServer server) {
        if (BRAINS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PlayerBrainController>> it = BRAINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PlayerBrainController> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (!(player instanceof EntityPlayerMPFake)
                    || player.isRemoved()
                    || player.hasDisconnected()) {
                entry.getValue().onDetach();
                it.remove();
            }
        }
    }
}