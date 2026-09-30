package me.primaryuan.carpet.handler.patPatPlayers;

import carpet.patches.EntityPlayerMPFake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
//#if MC >= 260300
//$$ import net.minecraft.world.item.component.SwingAnimation;
//#endif
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 摸摸头（patPatPlayers）：右键另一名**真人**玩家的头部，像撸猫一样抚摸——
 * 每次右键一次抚摸脉冲（挥手+头顶爱心+眼前爱心+蹲下脉冲+定向轻响），
 * 连续点击即连续抚摸（节奏随手）。
 *
 * <p>纯服务端实现，零 Mixin：复用 ridingPlayers 同款 Fabric UseEntityCallback。
 * 只认带命中坐标的交互包（客户端 use 流程先发 INTERACT_AT/26.x 合并后的带坐标
 * 单包，fabric 事件在该路径上携带实体加回后的世界坐标 hitResult；其后的裸
 * INTERACT 包 hitResult 为 null，直接放行原版，天然不重复触发）。</p>
 *
 * <p>火后不管：本方法恒不消费交互（调用方无需检查返回值），效果纯叠加，本次
 * 右键的原版处理与其它模组的 UseEntityCallback 监听不受任何影响（fabric 事件链
 * 是非 PASS 即短路，消费交互会压掉后续监听器）。</p>
 *
 * <p>挥手必须服务端自己做：原版 dispatch 的成功挥手分支对本场景不生效（客户端
 * 侧恒 PASS 不预测挥手），{@code swing(hand, true)} 尾参 = sendToSelf，广播含
 * 发起者本人（1.21.x 为 broadcastAndSend，26.3 为 sendToTrackingPlayersAndSelf，
 * 字节码核实）。</p>
 *
 * <p>蹲下脉冲：每次脉冲若目标因自己的输入而潜行则跳过（保持蹲、不弹起），
 * 否则服务端强制蹲 {@link #CROUCH_HOLD_TICKS} tick 后解除——站立目标随抚摸节奏
 * 往复蹲起，目标自行松开 shift 即恢复往复，抚摸停止即交还自主。蹲起为服务端
 * 实体标志同步（周围玩家可见其蹲起，本人画面无变化，其客户端在自身 shift
 * 变化时会覆盖标志）。目标卷入玩家骑乘（作为载具或乘客）时跳过蹲下脉冲：
 * 强制蹲会触发骑乘系统的蹲下卸客/原版潜行下车，把骑乘塔拆掉（与
 * ridingPlayers/pickupPlayers 的冲突修复点），其余抚摸效果不受影响。
 * 仅真人可被摸：carpet 假人（EntityPlayerMPFake）在入口
 * 即被排除；玩家可用 /patnod on|off 开关接不接受被摸（拒绝时摸头对其完全不
 * 生效，状态持久化于 config/carpet-pry-patnod.json）。</p>
 */
public class PatPatPlayersHandler {

    /** 慢速点击的抚摸间隔（冷却上限 tick 数） */
    private static final int COOLDOWN_MAX_TICKS = 10;

    /** 极速连点的抚摸间隔下限（冷却下限 tick 数，≈6.6 次脉冲/秒） */
    private static final int COOLDOWN_MIN_TICKS = 3;

    /** 连点每命中一次，冷却递减的 tick 数 */
    private static final int COOLDOWN_STEP_TICKS = 2;

    /** 超过该 tick 数未抚摸，连击加速重置回上限 */
    private static final int COMBO_RESET_TICKS = 20;

    /** key=发起者名，value={上次抚摸 tick, 当前冷却}；仅服务器主线程访问 */
    private static final Map<String, long[]> actorStrokes = new HashMap<>();

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 被摸接受偏好持久化文件（模式同 carpet-pry-pvp.json：UTF-8 + 临时文件原子替换） */
    private static final Path PAT_CONFIG_FILE = Path.of("config/carpet-pry-patnod.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 已拒绝被摸的玩家名（缺省 = 接受）；仅服务器主线程访问 */
    private static final Set<String> patDeclined = new HashSet<>();

    /** 正处于强制蹲脉冲中的目标名（脉冲飞完自清；骑乘/捡起挂塔前据此提前释放） */
    private static final Set<String> forcedCrouch = new HashSet<>();

    /**
     * 头部区域：命中点（实体碰撞箱表面 raycast 交点）距脚底的高度占碰撞箱高度
     * 的比例超过此值视为摸到头。站立 1.8 高碰撞箱 ≈ 顶部 0.63 格（头颅+下巴），
     * 潜行 1.5 高碰撞箱头部位置同样按比例上移，玩家缩放（比例缩放模型+碰撞箱）亦成立
     */
    private static final double HEAD_ZONE_MIN_FRACTION = 0.65;

    /** 蹲下脉冲的强制蹲保持 tick 数 */
    private static final int CROUCH_HOLD_TICKS = 3;

    private static boolean sweepRegistered = false;

    /** CarpetPrimaryuanServer.onGameStarted 调用：加载被摸偏好 + 注册过期条目的周期清理 */
    public static void init() {
        if (sweepRegistered) return;
        sweepRegistered = true;
        loadPatConfig();
        ServerTickScheduler.register(server -> {
            if (actorStrokes.isEmpty()) {
                return true;
            }
            long gameTime = server.overworld().getGameTime();
            actorStrokes.values().removeIf(st -> gameTime - st[0] > 200);
            return true;
        });
    }

    /**
     * 抚摸入口（UseEntityCallback 最后一个监听位，火后不管）。
     * 触发条件：服务端 + 规则开启（sneak 模式需按下潜行）+ 带命中坐标的交互包 +
     * 目标为其他真人玩家（carpet 假人排除）+ 目标未拒绝被摸 + 双方非旁观者 +
     * 不在冷却中 + 命中点位于头部区域 + 主手未持图腾（骑乘/捡起开启时图腾是
     * 骑乘手势，主手点击已由骑乘系统消费，此处对副手跟随包整体让路）。
     */
    public static void patPlayer(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        if (level.isClientSide()
                || hitResult == null
                || !(entity instanceof Player)
                || entity instanceof EntityPlayerMPFake
                || entity == player
                || !patEnabledFor(player)) {
            return;
        }
        if (player.isSpectator() || entity.isSpectator()) {
            return;
        }
        // 图腾是骑乘/捡起的手势物品：主手点击已由骑乘系统消费，但客户端预测为
        // PASS 仍会补发副手跟随包（带命中坐标）——在此整体让路，否则骑着别人的
        // 同时摸头效果叠加（副手包命中头部区，蹲下脉冲落到刚成为载具的目标上，
        // 修复前表现为"上了一下头立马被下"）。图腾换到副手即可正常摸头
        if ((CarpetPrimaryuanSettings.ridingPlayers || CarpetPrimaryuanSettings.pickupPlayers)
                && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        Player target = (Player) entity;
        if (!acceptsPat(target)) {
            return;
        }
        String actorName = player.getName().getString();
        long now = level.getGameTime();
        long[] st = actorStrokes.get(actorName);
        boolean combo = st != null && now - st[0] <= COMBO_RESET_TICKS;
        int cooldown = combo ? (int) st[1] : COOLDOWN_MAX_TICKS;
        if (st != null && now < st[0] + cooldown) {
            return;
        }
        // 头部区域判定：命中点位于目标碰撞箱顶部比例内
        double headY = hitResult.getLocation().y - target.getY();
        if (headY < target.getBbHeight() * HEAD_ZONE_MIN_FRACTION) {
            return;
        }

        actorStrokes.put(actorName, new long[]{now,
                combo ? Math.max(COOLDOWN_MIN_TICKS, cooldown - COOLDOWN_STEP_TICKS) : COOLDOWN_MAX_TICKS});

        applyPatEffects(player, target, level, hand);
    }

    /**
     * 抚摸脉冲效果层（挥手 + 头顶/眼前爱心 + 蹲下脉冲 + 定向轻响）。
     * 恒不消费交互。眼前爱心生成在视线水平前方 0.75 格、眼位上方 0.05——
     * 距离更远体积更小、位置偏离视野中心，不遮挡视线。
     */
    private static void applyPatEffects(Player actor, Player target, Level level, InteractionHand hand) {
        // 挥手（尾参 = 发给自己，发起者本人和周围玩家都能看到抚摸动作）
        //#if MC >= 260300
        //$$ actor.swing(hand, SwingAnimation.DEFAULT, true);
        //#else
        actor.swing(hand, true);
        //#endif

        // 头顶爱心（给发起者与旁观者的"画面"）
        ((ServerLevel) level).sendParticles(ParticleTypes.HEART,
                target.getX(), target.getEyeY() + 0.2, target.getZ(),
                1, 0.15, 0.15, 0.15, 0.0);

        // 蹲下脉冲：目标自己按着 shift 则跳过（保持蹲、不弹起），否则强制蹲后按时解除。
        // 目标卷入玩家骑乘（作为载具或乘客）时同样跳过——强制蹲会触发骑乘系统的
        // 两处蹲下语义，把骑乘塔拆了：载具侧 onPlayerTick 的"蹲下卸客"会踹掉头上
        // 的乘客，乘客侧触发原版"潜行下车"；其余抚摸效果不受影响
        if (!target.isShiftKeyDown() && !target.isVehicle() && !target.isPassenger()) {
            target.setShiftKeyDown(true);
            forcedCrouch.add(target.getName().getString());
            ServerTickScheduler.registerDelayed(CROUCH_HOLD_TICKS,
                    server -> {
                        target.setShiftKeyDown(false);
                        forcedCrouch.remove(target.getName().getString());
                        return false;
                    });
        }

        if (target instanceof ServerPlayer serverTarget) {
            // 眼前爱心：被摸者视线水平前方 0.75 格、眼位上方 0.05——距离更远体积更小、
            // 位置偏离视野中心，不遮挡视线；爱心自下而上飘出视野
            Vec3 look = serverTarget.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            Vec3 fwd = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
            ((ServerLevel) level).sendParticles(ParticleTypes.HEART,
                    serverTarget.getX() + fwd.x * 0.75, serverTarget.getEyeY() + 0.05, serverTarget.getZ() + fwd.z * 0.75,
                    1, 0.05, 0.05, 0.05, 0.0);

            // 定向轻响：其他人走广播（排除被摸者，发声点在被摸者头顶）；
            // 被摸者单独收一个更高更近的包，发声点在发起者位置——3D 定位音效，
            // 转头即可知道谁在摸你
            level.playSound((Entity) serverTarget, serverTarget.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.25F, 1.4F);
            serverTarget.connection.send(new ClientboundSoundPacket(
                    Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), SoundSource.PLAYERS,
                    actor.getX(), actor.getY(), actor.getZ(),
                    0.35F, 1.9F, serverTarget.getRandom().nextLong()));
        }
    }

    /** 规则模式：true=随手摸；sneak=按下潜行才摸（标志来自交互包，dispatch 前已同步到玩家身上） */
    private static boolean patEnabledFor(Player player) {
        String mode = CarpetPrimaryuanSettings.patPatPlayers;
        if ("true".equals(mode)) {
            return true;
        }
        if ("sneak".equals(mode)) {
            return player.isShiftKeyDown();
        }
        return false;
    }

    /** 目标是否接受被摸（默认接受；/patnod off 后拒绝） */
    public static boolean acceptsPat(Player target) {
        return !patDeclined.contains(target.getName().getString());
    }

    /**
     * 提前释放目标身上未结束的强制蹲脉冲（骑乘/捡起挂塔前调用）。
     * 蹲着的玩家被挂进骑乘塔会立即触发原版"乘客潜行下车"——连点抚摸把目标
     * 长期压在蹲姿时，pickup 会一直失败；挂塔前清一次蹲标志即可（脉冲的
     * 延迟回调随后再清一次是无害的重复）。
     */
    public static void releaseCrouchPulse(Player target) {
        if (forcedCrouch.remove(target.getName().getString())) {
            target.setShiftKeyDown(false);
        }
    }

    /** /patnod on|off：设置目标玩家的被摸接受状态并持久化 */
    public static void setPatAccept(ServerPlayer player, boolean accept) {
        String name = player.getName().getString();
        if (accept) {
            patDeclined.remove(name);
        } else {
            patDeclined.add(name);
        }
        savePatConfig();
    }

    /** 查询某玩家名是否拒绝被摸（含离线玩家，供命令展示用） */
    public static boolean isPatDeclined(String name) {
        return patDeclined.contains(name);
    }

    // ===== 被摸接受偏好持久化（模式同 PvpManager：UTF-8 + 临时文件原子替换） =====

    private static void loadPatConfig() {
        if (!Files.exists(PAT_CONFIG_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(PAT_CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return;
            }
            patDeclined.clear();
            JsonElement declinedElem = json.get("declined");
            if (declinedElem != null && declinedElem.isJsonArray()) {
                declinedElem.getAsJsonArray().forEach(e -> {
                    if (e.isJsonPrimitive()) {
                        patDeclined.add(e.getAsString());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("[Pat] Failed to load config file", e);
        }
    }

    private static void savePatConfig() {
        JsonObject config = new JsonObject();
        com.google.gson.JsonArray declinedArr = new com.google.gson.JsonArray();
        for (String name : patDeclined) {
            declinedArr.add(new com.google.gson.JsonPrimitive(name));
        }
        config.add("declined", declinedArr);

        try {
            // 原子写：先写临时文件再原子替换，避免写一半崩溃/断电留下损坏的 JSON
            Path tmp = PAT_CONFIG_FILE.resolveSibling(PAT_CONFIG_FILE.getFileName() + ".tmp");
            Files.createDirectories(PAT_CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tmp, PAT_CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("[Pat] Failed to save config file", e);
        }
    }
}
