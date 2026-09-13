package me.primaryuan.carpet.handler.playerScaleLinkedEntities;

import net.minecraft.server.level.ServerPlayer;

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
 * 服务端物品使用与实体添加均在主线程执行；ThreadLocal 仅作线程隔离防御，
 * 读写严格成对（HEAD 设置 / RETURN 清除）。
 */
public final class SpawnContext {

    public static final ThreadLocal<ServerPlayer> ACTING_PLAYER = new ThreadLocal<>();

    private SpawnContext() {}
}
