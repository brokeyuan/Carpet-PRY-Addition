package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import net.minecraft.commands.arguments.EntityArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命令层分身去重：give/clear/kill 等按名字（或 @a）匹配玩家的命令会把
 * 分身组每个成员各算一个目标，对"同一账号"重复生效（give 5 变 10，M3 实测
 * 的复制路径）。这里在 EntityArgument 的两个玩家集合出口按 UUID 去重，
 * 每个分身组只保留主实例（cloneId 最小）——组身份命令语义与单账号一致。
 *
 * <p>普通玩家无重复 UUID，集合原样返回，零改动。</p>
 */
@Mixin(EntityArgument.class)
public abstract class EntityArgumentDedupMixin {

    @Inject(
            method = "getPlayers",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void pry$dedupPlayers(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String name,
            CallbackInfoReturnable<java.util.Collection<net.minecraft.server.level.ServerPlayer>> cir
    ) {
        cir.setReturnValue(dedup(cir.getReturnValue()));
    }

    @Inject(
            method = "getOptionalPlayers",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void pry$dedupOptionalPlayers(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String name,
            CallbackInfoReturnable<java.util.Collection<net.minecraft.server.level.ServerPlayer>> cir
    ) {
        cir.setReturnValue(dedup(cir.getReturnValue()));
    }

    private static java.util.Collection<net.minecraft.server.level.ServerPlayer> dedup(
            java.util.Collection<net.minecraft.server.level.ServerPlayer> in
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones || in.size() <= 1) {
            return in;
        }
        List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>(in.size());
        for (net.minecraft.server.level.ServerPlayer p : in) {
            CloneGroup group = CloneGroupRegistry.byUuid(p.getUUID());
            if (group == null || p == group.primary()) {
                out.add(p);
            }
        }
        return out;
    }
}
