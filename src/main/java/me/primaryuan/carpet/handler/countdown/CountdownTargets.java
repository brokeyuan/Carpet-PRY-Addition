package me.primaryuan.carpet.handler.countdown;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 倒计时目标解析：选择器输入 → 动态全服或 UUID 快照。
 *
 * <p>参数缺省或原始文本为独立 {@code @a} → 动态全服（观众随加入/退出实时增减，
 * 选择器不再计算）；其余选择器在命令执行时解析一次、落为 UUID 快照（距离/队伍等按
 * 执行当时的位置与状态判定），运行期不重复计算。Carpet 假人一律排除——假人
 * 不应成为 UI 显示对象，即使其名字或档案命中选择器。</p>
 */
public final class CountdownTargets {

    /** 解析结果：dynamicAll=true 时 targets 为空且忽略 */
    public record Resolved(boolean dynamicAll, Set<UUID> targets, int realCount) {

        public static Resolved dynamic() {
            return new Resolved(true, Set.of(), 0);
        }
    }

    private CountdownTargets() {}

    /**
     * UP 正计时默认目标：玩家来源=本人快照（减少无意打扰）；控制台/命令方块等
     * 非玩家来源=动态全服。
     */
    public static Resolved resolveUpDefault(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof EntityPlayerMPFake)) {
            return new Resolved(false, Set.of(player.getUUID()), 1);
        }
        return Resolved.dynamic();
    }

    /**
     * 解析目标参数；raw 为 null 表示目标参数缺省（动态全服），selector 为对应解析结果。
     *
     * @return Resolved；非动态且无匹配真人时 realCount == 0（调用方报"无匹配"）
     */
    public static Resolved resolve(String rawSelector, EntitySelector selector, CommandSourceStack source) {
        if (rawSelector == null || rawSelector.equals("@a")) {
            return Resolved.dynamic();
        }
        List<ServerPlayer> matched;
        try {
            matched = new ArrayList<>(selector.findPlayers(source));
        } catch (Exception e) {
            // findPlayers 的权限类异常在本命令权限门控下不会发生，兜底按无匹配处理
            return new Resolved(false, Set.of(), 0);
        }
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer player : matched) {
            if (player instanceof EntityPlayerMPFake) {
                continue;
            }
            ids.add(player.getUUID());
        }
        return new Resolved(false, Set.copyOf(ids), ids.size());
    }
}
