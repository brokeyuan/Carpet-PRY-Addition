package me.primaryuan.carpet.handler.patPatPlayers;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.i18n.ServerI18n;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
//#if MC >= 260300
//$$ import net.minecraft.world.item.component.SwingAnimation;
//#endif
//#if MC >= 12103
//$$ import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
//#else
//$$ import net.minecraft.world.entity.RelativeMovement;
//#endif

import java.util.HashMap;
import java.util.Map;

/**
 * 摸摸头（patPatPlayers）：右键另一名玩家的头部，头顶冒爱心并播放轻响。
 *
 * <p>纯服务端实现，零 Mixin：复用 ridingPlayers 同款 Fabric UseEntityCallback。
 * 只认带命中坐标的交互包（客户端 use 流程先发 INTERACT_AT/26.x 合并后的带坐标
 * 单包，fabric 事件在该路径上携带实体加回后的世界坐标 hitResult；其后的裸
 * INTERACT 包 hitResult 为 null，直接放行原版，天然不重复触发）。</p>
 *
 * <p>火后不管：本方法恒不消费交互（调用方无需检查返回值），爱心/音效/提示/
 * 挥手纯叠加，本次右键的原版处理与其它模组的 UseEntityCallback 监听不受任何
 * 影响（fabric 事件链是非 PASS 即短路，消费交互会压掉后续监听器）。</p>
 *
 * <p>挥手必须服务端自己做：原版 dispatch 的成功挥手分支对本场景不生效（客户端
 * 侧恒 PASS 不预测挥手），{@code swing(hand, true)} 尾参 = sendToSelf，广播含
 * 发起者本人（1.21.x 为 broadcastAndSend，26.3 为 sendToTrackingPlayersAndSelf，
 * 字节码核实）。</p>
 */
public class PatPatPlayersHandler {

    /** 两次摸头之间的冷却 tick 数（防连点刷粒子/消息） */
    private static final int PAT_COOLDOWN_TICKS = 10;

    /**
     * 头部区域：命中点（实体碰撞箱表面 raycast 交点）距脚底的高度占碰撞箱高度
     * 的比例超过此值视为摸到头。站立 1.8 高碰撞箱 ≈ 顶部 0.63 格（头颅+下巴），
     * 潜行 1.5 高碰撞箱头部位置同样按比例上移，玩家缩放（比例缩放模型+碰撞箱）亦成立
     */
    private static final double HEAD_ZONE_MIN_FRACTION = 0.65;

    /** 镜头轻点幅度（度，正值=低头） */
    private static final float NOD_DELTA_DEGREES = 12.0F;

    /** key=玩家名，value=可再次摸头的 game time；仅服务器主线程访问 */
    private static final Map<String, Long> patCooldowns = new HashMap<>();

    /** 1.21/1.21.1 相对旋转包的 teleport id（避开原版 awaitingTeleport=0 的"无等待"哨兵） */
    private static int nodTeleportId = 1000;

    private static boolean sweepRegistered = false;

    /** CarpetPrimaryuanServer.onGameStarted 调用：注册过期冷却条目的周期清理 */
    public static void init() {
        if (sweepRegistered) return;
        sweepRegistered = true;
        ServerTickScheduler.register(server -> {
            if (patCooldowns.isEmpty()) {
                return true;
            }
            long gameTime = server.overworld().getGameTime();
            patCooldowns.values().removeIf(until -> gameTime >= until);
            return true;
        });
    }

    /**
     * 摸头入口（UseEntityCallback 最后一个监听位，火后不管）。
     * 触发条件：服务端 + 规则开启（sneak 模式需按下潜行）+ 带命中坐标的交互包 +
     * 目标为其他玩家 + 双方非旁观者 + 不在冷却中 + 命中点位于头部区域。
     */
    public static void patPlayer(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
        if (level.isClientSide()
                || hitResult == null
                || !(entity instanceof Player)
                || entity == player
                || !patEnabledFor(player)) {
            return;
        }
        if (player.isSpectator() || entity.isSpectator()) {
            return;
        }
        Player target = (Player) entity;
        if (isOnCooldown(player)) {
            return;
        }
        // 头部区域判定：命中点位于目标碰撞箱顶部比例内
        double headY = hitResult.getLocation().y - target.getY();
        if (headY < target.getBbHeight() * HEAD_ZONE_MIN_FRACTION) {
            return;
        }

        patCooldowns.put(player.getName().getString(), level.getGameTime() + PAT_COOLDOWN_TICKS);

        // 挥手（尾参 = 发给自己，发起者本人和周围玩家都能看到摸头动作）
        //#if MC >= 260300
        //$$ player.swing(hand, SwingAnimation.DEFAULT, true);
        //#else
        player.swing(hand, true);
        //#endif

        // 头顶爱心（给发起者与旁观者的"画面"）
        ((ServerLevel) level).sendParticles(ParticleTypes.HEART,
                target.getX(), target.getEyeY() + 0.2, target.getZ(),
                3, 0.25, 0.25, 0.25, 0.0);

        if (target instanceof ServerPlayer serverTarget) {
            // 眼前爱心：沿被摸者视线前方半格生成，第一人称必然入画（原版粒子会向上飘）
            Vec3 look = serverTarget.getLookAngle();
            Vec3 eye = serverTarget.getEyePosition();
            ((ServerLevel) level).sendParticles(ParticleTypes.HEART,
                    eye.x + look.x * 0.5, eye.y + look.y * 0.5, eye.z + look.z * 0.5,
                    1, 0.05, 0.05, 0.05, 0.0);

            // 贴耳音效：其他人走广播（排除被摸者），被摸者单独发更高更近的包
            level.playSound((Entity) serverTarget, serverTarget.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
            serverTarget.connection.send(new ClientboundSoundPacket(
                    Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP), SoundSource.PLAYERS,
                    serverTarget.getX(), serverTarget.getY(), serverTarget.getZ(),
                    0.7F, 1.9F, serverTarget.getRandom().nextLong()));

            // 被摸玩家快捷栏上方提示（26.x 移除 displayClientMessage，改名 sendOverlayMessage）
            Component message = ServerI18n.tr("carpetprimaryuan.patPat.received", player.getName().getString());
            //#if MC >= 260102
            //$$ serverTarget.sendOverlayMessage(message);
            //#else
            //$$ serverTarget.displayClientMessage(message, true);
            //#endif

            // 镜头轻点（独立规则，默认关）：低头一下再复位
            if (CarpetPrimaryuanSettings.patPatPlayersHeadBob) {
                nodHead(serverTarget);
            }
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

    /**
     * 被摸者镜头轻点：低头 {@link #NOD_DELTA_DEGREES} 度，2 tick 后复位。
     * 复位发反向增量而非回到快照值，玩家在间隔内动鼠标不会被拽回。
     */
    private static void nodHead(ServerPlayer target) {
        sendNod(target, NOD_DELTA_DEGREES);
        final int[] ticksLeft = {2};
        ServerTickScheduler.register(server -> {
            if (--ticksLeft[0] > 0) {
                return true;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(target.getUUID());
            if (p != null) {
                sendNod(p, -NOD_DELTA_DEGREES);
            }
            return false;
        });
    }

    private static void sendNod(ServerPlayer target, float delta) {
        //#if MC >= 12110
        //$$ // 1.21.10+ 旋转包为 record 且支持相对模式：yaw 不变、pitch 叠加 delta
        //$$ target.connection.send(new ClientboundPlayerRotationPacket(0.0F, true, delta, true));
        //#else
        //#if MC >= 12103
        //$$ // 1.21.3~1.21.8 旋转包为绝对值：读当前视角叠加 delta（复位时重读当前值，等效相对可逆）
        //$$ target.connection.send(new ClientboundPlayerRotationPacket(target.getYRot(), target.getXRot() + delta));
        //#else
        //$$ // 1.21/1.21.1 无旋转包：相对位移包只带旋转分量（原版 /tp 相对旋转同路径；
        //$$ // ROTATION 常量本身即 Set<RelativeMovement>，无需再包 Set.of）
        //$$ target.connection.send(new ClientboundPlayerPositionPacket(
        //$$         0.0D, 0.0D, 0.0D, 0.0F, delta, RelativeMovement.ROTATION, ++nodTeleportId));
        //#endif
        //#endif
    }

    private static boolean isOnCooldown(Player player) {
        Long until = patCooldowns.get(player.getName().getString());
        return until != null && player.level().getGameTime() < until;
    }
}
