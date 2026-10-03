package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * 红包全部服务端 GUI：类型选择（3 行）、物品投放（6 行）、专属对象选择（6 行玩家头）、
 * 口令铁砧。全部使用原版菜单类型，原版客户端即用。
 *
 * <p>按钮/头颅槽经 {@link RedPacketContainer} 保护（不可取出、不可覆盖、不可放入），
 * 点击经 removeItem 拦截转为回调（客户端预测产生的 ghost 由下一次 broadcastChanges 纠正）。
 * 物品投放菜单关闭时把 0-44 槽剩余物品交还回调——未点确认即关闭的原样退回语义。</p>
 */
public final class RedPacketGui {

    /** 亮红（§c） */
    public static final int BRIGHT_RED = 0xFF5555;
    /** 口令纸标记名：关闭铁砧时按此回收，避免 UI 用纸凭空流入世界 */
    public static final String PASSWORD_PAPER_NAME = "请输入口令后点击成品";
    /** 物品投放槽位（规范固定） */
    public static final int SLOT_CANCEL = 45;
    public static final int SLOT_CONFIRM = 49;
    public static final int SLOT_CLEAR = 53;
    /** 类型选择图标槽位：拼手气/普通/专属/口令 */
    public static final int[] TYPE_SLOTS = {10, 12, 14, 16};

    private RedPacketGui() {}

    // ==================== 物品构造 ====================

    /** 26.2 起染色物品常量并入 ColorCollection，按颜色取值 */
    static Item redShulkerBox() {
        //#if MC >= 260200
        //$$ return Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.RED);
        //#else
        return Items.RED_SHULKER_BOX;
        //#endif
    }

    static Item limeDye() {
        //#if MC >= 260200
        //$$ return Items.DYE.pick(net.minecraft.world.item.DyeColor.LIME);
        //#else
        return Items.LIME_DYE;
        //#endif
    }

    /** 带名+悬浮说明的图标 */
    public static ItemStack icon(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>(lore.length);
            for (String line : lore) {
                lines.add(Component.literal(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    /** 玩家头（按名字解析皮肤；26.1.2 起工厂方法，1.21.x 三参构造，javap 核实） */
    public static ItemStack head(String name) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        //#if MC < 12110
        //$$ // 1.21~1.21.8 为 record 构造（1.21.10 起改工厂方法，javap 核实）
        //$$ stack.set(DataComponents.PROFILE, new ResolvableProfile(new com.mojang.authlib.GameProfile(null, name)));
        //#else
        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
        //#endif
        stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        return stack;
    }

    /** 选中态高亮：附魔光效 + 说明行 */
    public static void markSelected(ItemStack stack, String hint) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(hint))));
    }

    /**
     * 聊天可点击样式（亮红 + 运行命令 + 悬浮说明）。
     * 1.21.5 起原版把 ClickEvent/HoverEvent 改为接口+record 实现（1.21~1.21.4 为类构造，javap 核实）。
     */
    public static Style claimStyle(String command, Component hover) {
        //#if MC < 12105
        //$$ return Style.EMPTY.withColor(TextColor.fromRgb(BRIGHT_RED))
        //$$         .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        //$$         .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        //#else
        return Style.EMPTY.withColor(TextColor.fromRgb(BRIGHT_RED))
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover));
        //#endif
    }

    // ==================== 受保护容器 ====================

    /**
     * 红包容器：受保护槽不可取/不可放/不可覆盖，点击经 removeItem 拦截转为回调。
     */
    public static final class RedPacketContainer extends SimpleContainer {
        private final IntPredicate protectedSlots;
        private final IntConsumer clickAction;

        RedPacketContainer(int size, IntPredicate protectedSlots, IntConsumer clickAction) {
            super(size);
            this.protectedSlots = protectedSlots;
            this.clickAction = clickAction;
        }

        /** 绕过保护直接写槽（选中高亮更新用） */
        public void forceSet(int slot, ItemStack stack) {
            super.setItem(slot, stack);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (amount > 0 && protectedSlots.test(slot)) {
                clickAction.accept(slot);
                return ItemStack.EMPTY;
            }
            return super.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return protectedSlots.test(slot) ? ItemStack.EMPTY : super.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (protectedSlots.test(slot)) {
                return;
            }
            super.setItem(slot, stack);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !protectedSlots.test(slot) && super.canPlaceItem(slot, stack);
        }
    }

    // ==================== 菜单 ====================

    /** 物品投放菜单：关闭时把 0-44 槽剩余物品交还回调（未确认即关闭的原样退回语义） */
    public static final class ItemInputMenu extends ChestMenu {
        private final RedPacketContainer container;
        private final Consumer<List<ItemStack>> onClosed;

        ItemInputMenu(int id, Inventory playerInventory, RedPacketContainer container,
                      Consumer<List<ItemStack>> onClosed) {
            super(MenuType.GENERIC_9x6, id, playerInventory, container, 6);
            this.container = container;
            this.onClosed = onClosed;
        }

        @Override
        public void removed(Player player) {
            List<ItemStack> remaining = new ArrayList<>();
            for (int slot = 0; slot < 45; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    remaining.add(stack);
                    container.forceSet(slot, ItemStack.EMPTY);
                }
            }
            onClosed.accept(remaining);
            super.removed(player);
        }
    }

    /** 口令铁砧：改名免费（mayPickup 恒真，不耗经验），点成品即提交口令文本 */
    public static final class PasswordAnvilMenu extends AnvilMenu {
        private final Consumer<String> onPassword;
        private final Runnable onClosed;

        PasswordAnvilMenu(int id, Inventory playerInventory, ContainerLevelAccess access,
                          Consumer<String> onPassword, Runnable onClosed) {
            super(id, playerInventory, access);
            this.onPassword = onPassword;
            this.onClosed = onClosed;
            // UI 用纸：关闭时统一回收（见 reclaimPapers），避免凭空造物流入玩家背包
            this.slots.get(0).set(passwordPaper());
        }

        public static ItemStack passwordPaper() {
            return icon(Items.PAPER, PASSWORD_PAPER_NAME);
        }

        @Override
        protected boolean mayPickup(Player player, boolean hasStacks) {
            return true;
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {
            onPassword.accept(stack.getHoverName().getString());
        }

        @Override
        public void removed(Player player) {
            reclaimPapers(player);
            onClosed.run();
            super.removed(player);
        }

        private void reclaimPapers(Player player) {
            if (isPasswordPaper(this.slots.get(0).getItem())) {
                this.slots.get(0).set(ItemStack.EMPTY);
            }
            if (isPasswordPaper(this.getCarried())) {
                this.setCarried(ItemStack.EMPTY);
            }
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                if (isPasswordPaper(inv.getItem(slot))) {
                    inv.setItem(slot, ItemStack.EMPTY);
                }
            }
        }

        private static boolean isPasswordPaper(ItemStack stack) {
            return stack.getItem() == Items.PAPER
                    && PASSWORD_PAPER_NAME.equals(stack.getHoverName().getString());
        }
    }

    // ==================== 打开 ====================

    /** 类型选择：3 行，图标按 TYPE_SLOTS 槽位摆放，全槽保护；回调传图标下标 0-3 */
    public static RedPacketContainer openTypeMenu(ServerPlayer player, Component title,
                                                  ItemStack[] icons, IntConsumer onPick) {
        RedPacketContainer container = new RedPacketContainer(27, slot -> true, slot -> {
            for (int i = 0; i < TYPE_SLOTS.length; i++) {
                if (TYPE_SLOTS[i] == slot) {
                    onPick.accept(i);
                    return;
                }
            }
        });
        for (int i = 0; i < TYPE_SLOTS.length; i++) {
            container.forceSet(TYPE_SLOTS[i], icons[i]);
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, container, 3),
                title));
        return container;
    }

    /** 物品投放：6 行，槽 0-44 可编辑，按钮槽保护；关闭回调负责未确认退回语义 */
    public static RedPacketContainer openItemInput(ServerPlayer player, Component title,
                                                   ItemStack cancelIcon, ItemStack confirmIcon, ItemStack clearIcon,
                                                   IntConsumer onButton, Consumer<List<ItemStack>> onClosed) {
        RedPacketContainer container = new RedPacketContainer(54,
                slot -> slot == SLOT_CANCEL || slot == SLOT_CONFIRM || slot == SLOT_CLEAR,
                onButton);
        container.forceSet(SLOT_CANCEL, cancelIcon);
        container.forceSet(SLOT_CONFIRM, confirmIcon);
        container.forceSet(SLOT_CLEAR, clearIcon);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ItemInputMenu(id, inv, container, onClosed),
                title));
        return container;
    }

    /** 专属对象选择：6 行放在线玩家头（不含发送者），全槽保护 */
    public static RedPacketContainer openTargetSelect(ServerPlayer player, Component title,
                                                      List<ItemStack> heads, IntConsumer onPick) {
        RedPacketContainer container = new RedPacketContainer(54, slot -> true, onPick);
        for (int i = 0; i < heads.size() && i < 54; i++) {
            container.forceSet(i, heads.get(i));
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, container, 6),
                title));
        return container;
    }

    /** 口令铁砧：打开即放入标记用纸，点成品提交口令 */
    public static void openPasswordAnvil(ServerPlayer player, Component title,
                                         Consumer<String> onPassword, Runnable onClosed) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PasswordAnvilMenu(id, inv,
                        ContainerLevelAccess.create(p.level(), p.blockPosition()),
                        onPassword, onClosed),
                title));
    }
}
