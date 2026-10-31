package me.primaryuan.carpet.clones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 共享背包权威状态（M3）：主实例（组内 cloneId 最小）的 Inventory 是唯一
 * 权威副本；非主实例的背包写方法被 InventoryMixin 重定向到主实例执行，
 * 执行后把权威 items 快照回写全组——同一 Tick 内多分身依次修改时，
 * 每次修改后全组立即可见最新值（64 石头连续放置 63→62 不回滚，spec 6.2）。
 *
 * <p>装备槽（26.3 起独立于 Inventory 的 EntityEquipment）与盔甲走
 * {@link #syncAll} 全组对齐（EquipEvent 后调用）。</p>
 */
public final class SharedInventoryManager {

    private SharedInventoryManager() {}

    /** 该玩家是否应被重定向（非主实例分身） */
    public static boolean shouldRedirect(ServerPlayer player) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return false;
        }
        if (!(player instanceof carpet.patches.EntityPlayerMPFake)) {
            return false;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        return group != null && group.isNonPrimary(player);
    }

    /** 重定向目标：主实例（可能就是调用者自己或 null——组内无人在线） */
    public static ServerPlayer primaryOf(ServerPlayer player) {
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        return group == null ? null : group.primary();
    }

    /** 组内任一实例的背包写操作后调用：权威 → 全组对齐（同 Tick 立即） */
    public static void syncGroup(ServerPlayer actor) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(actor.getUUID());
        if (group == null) {
            return;
        }
        ServerPlayer primary = group.primary();
        if (primary == null) {
            return;
        }
        List<ServerPlayer> members = group.onlineMembers();
        if (members.size() <= 1) {
            return;
        }
        Inventory src = primary.getInventory();
        ItemStack[] snapshot = snapshot(src);
        for (ServerPlayer m : members) {
            if (m == primary) {
                continue;
            }
            applySnapshot(m.getInventory(), snapshot);
        }
    }

    private static ItemStack[] snapshot(Inventory inv) {
        var items = inv.getNonEquipmentItems();
        ItemStack[] out = new ItemStack[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            out[i] = s.isEmpty() ? ItemStack.EMPTY : s.copy();
        }
        return out;
    }

    private static void applySnapshot(Inventory inv, ItemStack[] snapshot) {
        var items = inv.getNonEquipmentItems();
        for (int i = 0; i < items.size() && i < snapshot.length; i++) {
            ItemStack cur = items.get(i);
            ItemStack want = snapshot[i];
            // 按内容判断避免无差别 set 打爆客户端同步（count/damage/组件全比对）
            if (!ItemStack.matches(cur, want)) {
                items.set(i, want.isEmpty() ? ItemStack.EMPTY : want.copy());
            }
        }
    }

    /** 装备槽 + 主手/选中槽全组对齐（盔甲、副手、手持更新走这里） */
    public static void syncEquipment(ServerPlayer actor) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        CloneGroup group = CloneGroupRegistry.byUuid(actor.getUUID());
        if (group == null) {
            return;
        }
        ServerPlayer primary = group.primary();
        if (primary == null || group.onlineMembers().size() <= 1) {
            return;
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack want = primary.getItemBySlot(slot);
            for (ServerPlayer m : group.onlineMembers()) {
                if (m == primary) {
                    continue;
                }
                if (!ItemStack.matches(m.getItemBySlot(slot), want)) {
                    m.setItemSlot(slot, want.isEmpty() ? ItemStack.EMPTY : want.copy());
                }
            }
        }
    }
}
