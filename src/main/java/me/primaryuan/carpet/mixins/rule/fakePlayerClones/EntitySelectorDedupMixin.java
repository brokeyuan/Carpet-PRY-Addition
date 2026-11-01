package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneGroup;
import me.primaryuan.carpet.clones.CloneGroupRegistry;
import net.minecraft.commands.arguments.selector.EntitySelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.UUID;

/**
 * 选择器层分身去重：EntitySelector.findPlayers/findEntities 是全部玩家命令
 * （give/clear/kill/experience…单数复数与 @a）的统一出口。分身组每个成员
 * 都满足名字匹配，"同一账号"会被重复选中（give 5 变 10，M3 实测）。
 * 这里在出口按 UUID 去重、每组保留主实例（cloneId 最小）。
 *
 * <p>普通玩家无重复 UUID，集合原样返回，零改动。execute as @e 等实体场景
 * 走 findEntities——分身是玩家实体，同样去重。</p>
 */
@Mixin(EntitySelector.class)
public abstract class EntitySelectorDedupMixin {

    /** maxResults 读取（private 字段经 accessor） */
    private static int pry$maxResults(EntitySelector selector) {
        return ((EntitySelectorAccessor) selector).pry$maxResults();
    }

    @Inject(
            method = "findPlayers",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void pry$dedupFindPlayers(
            net.minecraft.commands.CommandSourceStack source,
            CallbackInfoReturnable<List<net.minecraft.server.level.ServerPlayer>> cir
    ) {
        List<net.minecraft.server.level.ServerPlayer> in = cir.getReturnValue();
        // 复数选择器不去重：分身脚本/命令需要按实体定位每个分身（R1 F5）。
        // 只对"单实体语义"（maxResults==1，如 give/clear 的 player() 参数）去重，
        // 防止组命令对同一账号重复生效。
        if (!CarpetPrimaryuanSettings.fakePlayerClones || in.size() <= 1 || pry$maxResults((EntitySelector) (Object) this) > 1) {
            return;
        }
        List<net.minecraft.server.level.ServerPlayer> out = new ArrayList<>(in.size());
        Set<UUID> seen = new HashSet<>();
        for (net.minecraft.server.level.ServerPlayer p : in) {
            CloneGroup group = CloneGroupRegistry.byUuid(p.getUUID());
            if (group == null) {
                out.add(p);
            } else if (p == group.primary() && seen.add(p.getUUID())) {
                out.add(p);
            }
        }
        cir.setReturnValue(out);
    }

    @Inject(
            method = "findEntities",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void pry$dedupFindEntities(
            net.minecraft.commands.CommandSourceStack source,
            CallbackInfoReturnable<List<? extends net.minecraft.world.entity.Entity>> cir
    ) {
        List<? extends net.minecraft.world.entity.Entity> in = cir.getReturnValue();
        if (!CarpetPrimaryuanSettings.fakePlayerClones || in.size() <= 1
                || pry$maxResults((EntitySelector) (Object) this) > 1) {
            return;
        }
        boolean hasClone = false;
        for (net.minecraft.world.entity.Entity e : in) {
            if (CloneGroupRegistry.byUuid(e.getUUID()) != null) {
                hasClone = true;
                break;
            }
        }
        if (!hasClone) {
            return;
        }
        List<net.minecraft.world.entity.Entity> out = new ArrayList<>(in.size());
        Set<UUID> seen = new HashSet<>();
        for (net.minecraft.world.entity.Entity e : in) {
            CloneGroup group = CloneGroupRegistry.byUuid(e.getUUID());
            if (group == null) {
                out.add(e);
            } else if (e == group.primary() && seen.add(e.getUUID())) {
                out.add(e);
            }
        }
        cir.setReturnValue(out);
    }

}
