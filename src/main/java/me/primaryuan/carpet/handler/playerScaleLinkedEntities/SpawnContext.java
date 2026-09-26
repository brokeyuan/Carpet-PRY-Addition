package me.primaryuan.carpet.handler.playerScaleLinkedEntities;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * playerScaleLinkedEntities 规则的生成上下文：
 * ServerPlayerGameModeMixin 在服务端物品使用漏斗（useItem / useItemOn）前后
 * 写入/清除"当前操作玩家"，ServerLevelMixin 在 addFreshEntity 时读取，
 * 从而把"该实体是否由玩家使用物品直接生成"传递到实体添加点。
 *
 * 注意：本类是被运行时直接引用的普通工具类，不能放在 mixins 包
 * （me.primaryuan.carpet.mixins.*）下——Mixin 对已声明的 mixin 包做类加载
 * 监控，从包内直接加载非 mixin 类会抛 IllegalClassLoadError。
 *
 * 服务端物品使用与实体添加均在主线程执行；ThreadLocal 仅作线程隔离防御。
 * 写入带 game time 戳：useItem 若中途抛异常，RETURN 清理不会执行、残留的
 * 旧玩家上下文会让后续与物品使用无关的 addFreshEntity（怪物生成等）被错误
 * 缩放——读取时校验戳与目标世界当前 tick 一致，跨 tick 的残留一律判无效。
 */
public final class SpawnContext {

    private static final ThreadLocal<ServerPlayer> ACTING_PLAYER = new ThreadLocal<>();
    /** 上下文写入时刻的 game time（见类注释：异常残留的跨 tick 校验） */
    private static final ThreadLocal<Long> ACTING_TICK = new ThreadLocal<>();

    private SpawnContext() {}

    /** 漏斗入口（useItem / useItemOn 的 HEAD）写入操作玩家与时间戳 */
    public static void begin(ServerPlayer player, Level level) {
        ACTING_PLAYER.set(player);
        ACTING_TICK.set(level.getGameTime());
    }

    /**
     * 漏斗出口（addFreshEntity）读取操作玩家。
     * 时间戳与目标世界当前 tick 不符 = 上一次漏斗异常中断的残留，返回 null。
     */
    public static ServerPlayer current(Level level) {
        Long stamp = ACTING_TICK.get();
        if (stamp == null || stamp != level.getGameTime()) {
            return null;
        }
        return ACTING_PLAYER.get();
    }

    /** 漏斗出口（useItem / useItemOn 的 RETURN）清除上下文 */
    public static void clear() {
        ACTING_PLAYER.remove();
        ACTING_TICK.remove();
    }
}
