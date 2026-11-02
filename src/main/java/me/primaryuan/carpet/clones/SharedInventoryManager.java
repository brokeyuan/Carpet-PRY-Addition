package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 共享背包生命周期：只在入组时把身体接到组的权威存储，不再按 Tick/写方法
 * 复制 ItemStack。物品读取与原地修改（consume/shrink/hurtAndBreak/组件写入）
 * 直接作用于同一批 ItemStack，因此无需覆盖每个写入口。
 */
public final class SharedInventoryManager {

    private SharedInventoryManager() {}

    /**
     * 把成员接到组存储；组无存储时以该成员当前容器建立。
     *
     * @return 失败返回 false（调用方必须撤销本次创建，不能留下未接管的身体）
     */
    public static boolean attach(CloneGroup group, ServerPlayer player) {
        try {
            SharedInventory shared = group.sharedInventory();
            if (shared == null) {
                group.setSharedInventory(SharedInventory.adopt(player));
            } else {
                shared.bind(player);
            }
            return true;
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan")
                    .error("[pry:clones] attach shared inventory failed for {}",
                            CloneVersionAdapter.profileName(player.getGameProfile()), t);
            return false;
        }
    }

    /** 组存储是否已完成首次 playerdata 读取（后续成员不得再读盘覆盖） */
    public static boolean needsInitialLoad(CloneGroup group) {
        SharedInventory shared = group.sharedInventory();
        return shared != null && !shared.isLoaded();
    }

    public static void markLoaded(CloneGroup group) {
        SharedInventory shared = group.sharedInventory();
        if (shared != null) {
            shared.markLoaded();
        }
    }

    /**
     * 物品 tick 只由组主实例执行一次。共享存储若被每个身体各 tick 一次，
     * 指南针/地图等 inventoryTick 会重复执行；动作消耗不经过这条路径。
     */
    public static boolean isTickOwner(ServerPlayer player) {
        CloneGroup group = CloneGroupRegistry.byUuid(player.getUUID());
        if (group == null || group.sharedInventory() == null) {
            return true;
        }
        return group.primary() == player;
    }

    /** 权威存储直接可见时的物品快照（诊断/测试用，不参与同步） */
    public static ItemStack[] snapshot(CloneGroup group) {
        SharedInventory shared = group.sharedInventory();
        if (shared == null) {
            return new ItemStack[0];
        }
        var items = shared.items();
        ItemStack[] out = new ItemStack[items.size()];
        for (int i = 0; i < items.size(); i++) {
            out[i] = items.get(i);
        }
        return out;
    }
}
