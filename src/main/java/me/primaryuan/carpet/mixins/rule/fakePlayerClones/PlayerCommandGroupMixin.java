package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import carpet.commands.PlayerCommand;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneVersionAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * /player &lt;name&gt; 组语义（fakePlayerClones 规则开启 + 名字已建分身组时生效）：
 *
 * <ul>
 *   <li>manipulate / kill（ movement、attack、use、drop、kill、sneak、mount 等
 *       全部动作子命令的统一执行体）：对分身组<b>全部在线成员按 cloneId 顺序</b>
 *       执行同一动作——"player xxx kill 同时下线"、"player xxx jump 同时跳"。
 *       同 Tick 顺序结算，共享库存随之顺序更新。返回值为成员数。</li>
 *   <li>spawn：增量语义已由 PlayerCommandSpawnMixin 处理（每个 spawn +1 分身）。</li>
 *   <li>rejoin（TIS 语义）：由 /fakeclone rejoin 提供（/player 无此子命令，
 *       不额外注入 Carpet 命令树——用户可用 /fakeclone rejoin name）。</li>
 * </ul>
 *
 * <p>未建组名字（普通假人/真人）与规则关闭时零改动。单体操作走
 * /fakeclone action &lt;action&gt; &lt;name&gt; &lt;cloneId&gt;。</p>
 */
@Mixin(PlayerCommand.class)
public abstract class PlayerCommandGroupMixin {

    @Inject(
            method = "manipulate",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void pry$groupManipulate(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            java.util.function.Consumer<carpet.helpers.EntityPlayerActionPack> action,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        String name;
        try {
            name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
        } catch (IllegalArgumentException e) {
            return;
        }
        var group = me.primaryuan.carpet.clones.CloneGroupRegistry.byName(name);
        if (group == null || group.onlineCount() == 0) {
            return;
        }
        int n = 0;
        for (ServerPlayer p : group.onlineMembers()) {
            action.accept(((carpet.fakes.ServerPlayerInterface) p).getActionPack());
            n++;
        }
        cir.setReturnValue(n);
    }

    @Inject(
            method = "kill",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void pry$groupKill(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        String name;
        try {
            name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
        } catch (IllegalArgumentException e) {
            return;
        }
        var group = me.primaryuan.carpet.clones.CloneGroupRegistry.byName(name);
        if (group == null || group.onlineCount() == 0) {
            return;
        }
        int n = 0;
        for (ServerPlayer p : group.onlineMembers()) {
            CloneVersionAdapter.killEntity(p);
            n++;
        }
        cir.setReturnValue(n);
    }
}
