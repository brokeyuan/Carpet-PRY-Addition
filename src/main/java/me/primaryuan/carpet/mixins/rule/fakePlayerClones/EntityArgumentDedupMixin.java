package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import com.mojang.brigadier.tree.LiteralCommandNode;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 共享资源命令按账号去重：give/clear/item/enchant/loot 这类操作作用于
 * 组共享库存的命令，同一 UUID 只保留一个被选中的成员（主实例不在选择
 * 结果里时保留第一个选中的非主成员），避免对共享库存重复执行。
 *
 * <p>tp/execute/effect 等身体命令不再经过此层去重，选择器照常返回每个
 * 身体。去重只发生在 EntityArgument 输出，不修改 EntitySelector 本身。</p>
 */
@Mixin(EntityArgument.class)
public abstract class EntityArgumentDedupMixin {

    private static final Set<String> SHARED_RESOURCE_COMMANDS = Set.of(
            "give", "clear", "item", "enchant", "loot");

    @Inject(
            method = "getPlayers",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void pry$dedupPlayers(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
            String name,
            CallbackInfoReturnable<Collection<ServerPlayer>> cir
    ) {
        if (pry$applies(context)) {
            cir.setReturnValue(dedup(cir.getReturnValue()));
        }
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
            CallbackInfoReturnable<Collection<ServerPlayer>> cir
    ) {
        if (pry$applies(context)) {
            cir.setReturnValue(dedup(cir.getReturnValue()));
        }
    }

    private static boolean pry$applies(
            com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context
    ) {
        for (var parsed : context.getNodes()) {
            if (parsed.getNode() instanceof LiteralCommandNode<?> literal
                    && SHARED_RESOURCE_COMMANDS.contains(literal.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Collection<ServerPlayer> dedup(Collection<ServerPlayer> in) {
        if (in.size() <= 1) {
            return in;
        }
        List<ServerPlayer> out = new ArrayList<>(in.size());
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : in) {
            CloneGroup group = CloneGroupRegistry.byUuid(p.getUUID());
            if (group == null) {
                out.add(p);
                continue;
            }
            if (!seen.add(p.getUUID())) {
                continue;
            }
            ServerPlayer keep = group.primary();
            if (keep == null || !in.contains(keep)) {
                keep = p;
            }
            out.add(keep);
        }
        return out;
    }
}
