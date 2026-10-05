package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
//#if MC < 260102
import net.minecraft.world.inventory.ClickType;
//#else
//$$ // 26.1.2 起点击类型枚举改名 ContainerInput
//$$ import net.minecraft.world.inventory.ContainerInput;
//#endif
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
 * 口令铁砧。全部使用原版菜单类型（客户端用原版箱子界面预测），原版客户端即用。
 *
 * <p>保护在 Slot 层与菜单层双重实现：保护格 {@code mayPlace/mayPickup=false}、
 * {@code removeItem} 返回空、{@code set} 空操作；{@link RedPacketMenu#clicked} 对保护格
 * 只放行 PICKUP 转回调、其余点击类型（shift-click/数字键交换/拖拽/扔出/克隆）整体屏蔽——
 * Container 层拦截不足以覆盖菜单交互（quickMoveStack/moveItemStackTo/doClick SWAP
 * 在 Slot 层直接读写，绕过 Container 拦截造成图标复制与玩家物品蒸发，生产实证）。
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
    public static Item redShulkerBox() {
        //#if MC >= 260200
        //$$ return Items.DYED_SHULKER_BOX.pick(net.minecraft.world.item.DyeColor.RED);
        //#else
        return Items.RED_SHULKER_BOX;
        //#endif
    }

    public static Item limeDye() {
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

    /** 纯悬浮样式（无点击无颜色）：领取明细单行 + 悬浮展开用，版本分叉同 claimStyle */
    public static Style hoverStyle(Component hover) {
        //#if MC < 12105
        //$$ return Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        //#else
        return Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover));
        //#endif
    }

    // ==================== 菜单 ====================

    /** 红包自简容器：纯数据（图标注入 forceSet），全部保护在菜单 Slot 层 */
    public static final class RedPacketContainer extends SimpleContainer {

        RedPacketContainer(int size) {
            super(size);
        }

        /** 直接写槽（图标注入/选中高亮/收集清空用） */
        public void forceSet(int slot, ItemStack stack) {
            super.setItem(slot, stack);
        }
    }

    /**
     * 红包自定义菜单：保护格经 ProtectedSlot + clicked 全拦实现零复制零蒸发；
     * onRemoved 在菜单关闭时回调（物品投放的退回语义用）。
     */
    public static final class RedPacketMenu extends AbstractContainerMenu {
        private final IntPredicate protectedSlots;
        private final IntConsumer onButton;
        private final Runnable onRemoved;

        RedPacketMenu(MenuType<?> type, int id, Inventory playerInventory,
                      net.minecraft.world.Container container, int rows,
                      IntPredicate protectedSlots, IntConsumer onButton, Runnable onRemoved) {
            super(type, id);
            this.protectedSlots = protectedSlots;
            this.onButton = onButton;
            this.onRemoved = onRemoved;
            int containerSlots = rows * 9;
            for (int index = 0; index < containerSlots; index++) {
                int row = index / 9;
                int col = index % 9;
                if (protectedSlots.test(index)) {
                    this.addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return false;
                        }

                        @Override
                        public ItemStack remove(int amount) {
                            // 点击识别统一由 clicked 触发回调；此处返回空使一切拿取无效
                            return ItemStack.EMPTY;
                        }

                        @Override
                        public void set(ItemStack stack) {
                            // 数字键交换等直接写槽的路径防线：保护格永不改变
                        }

                        @Override
                        public void setByPlayer(ItemStack oldStack, ItemStack newStack) {
                            // 同上
                        }
                    });
                } else {
                    this.addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18));
                }
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new Slot(playerInventory, 9 + row * 9 + col,
                            8 + col * 18, 18 + containerSlots + 14 + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col,
                        8 + col * 18, 76 + containerSlots + 14));
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = this.slots.get(index);
            if (!slot.hasItem() || protectedSlots.test(index)) {
                return ItemStack.EMPTY;
            }
            // 玩家背包格 shift-click：仅可进入非保护容器格（mayPlace=false 的格会被
            // moveItemStackTo 自动跳过，物品不会消失）；容器格 shift-click：收进玩家背包
            ItemStack current = slot.getItem();
            ItemStack copy = current.copy();
            int containerSlots = this.slots.size() - 36;
            boolean moved;
            if (index < containerSlots) {
                moved = this.moveItemStackTo(current, containerSlots, this.slots.size(), true);
            } else {
                moved = this.moveItemStackTo(current, 0, containerSlots, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
            if (current.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return copy;
        }

        //#if MC < 260102
        @Override
        public void clicked(int index, int button, ClickType type, Player player) {
            // 保护格：只放行左键点击转回调，shift-click/数字键交换/拖拽/扔出/克隆全部屏蔽
            if (index >= 0 && index < this.slots.size() && protectedSlots.test(index)) {
                if (type == ClickType.PICKUP) {
                    onButton.accept(index);
                }
                return;
            }
            super.clicked(index, button, type, player);
        }
        //#else
        //$$ @Override
        //$$ public void clicked(int index, int button, ContainerInput type, Player player) {
        //$$     // 保护格：只放行左键点击转回调，shift-click/数字键交换/拖拽/扔出/克隆全部屏蔽
        //$$     if (index >= 0 && index < this.slots.size() && protectedSlots.test(index)) {
        //$$         if (type == ContainerInput.PICKUP) {
        //$$             onButton.accept(index);
        //$$         }
        //$$         return;
        //$$     }
        //$$     super.clicked(index, button, type, player);
        //$$ }
        //#endif

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            if (onRemoved != null) {
                onRemoved.run();
            }
            super.removed(player);
        }
    }

    /**
     * 口令铁砧：改名免费（不耗经验），点成品即提交口令文本，全程纯虚拟（不依赖世界方块）。
     *
     * <p>虚拟菜单三防线：{@link #stillValid} 恒真（原版判断要求脚下是铁砧，普通地点会
     * 下一 tick 自动关闭）；输入槽点击全阻断（{@link #clicked}——原版 Slot.mayPickup 恒真
     * 可把 UI 纸拿进背包或 Q 键丢进世界）；shift-click 一律不迁移（{@link #quickMoveStack}
     * ——super 会把玩家物品塞进输入槽、把成品纸塞进背包）。</p>
     *
     * <p>输入槽白名单：只有标记口令纸可留在输入槽（{@link #slotsChanged} 兜底拦截非点击
     * 路径的写入）。标记在 {@code CustomData} 组件而非名字——改名后的成品纸带同标记，
     * 真实玩家的同名纸不带，两者不混淆。</p>
     *
     * <p>{@link #onTake} 不调 {@code super}：javap 26.3 实证 super 首行即
     * {@code giveExperienceLevels(-cost)}（免费提交被扣经验），随后消耗输入槽、重置费用、
     * 文本过滤、access.execute（铁砧损耗与音效，虚拟菜单全不适用）。这里复刻消耗输入槽
     * 语义并额外把成品栈清零——漏消耗即纸复制机。</p>
     */
    public static final class PasswordAnvilMenu extends AnvilMenu {
        /** 口令纸 CustomData 标记键：识别 UI 用纸（改名后仍在，玩家自带同名纸无此标记） */
        private static final String PASSWORD_PAPER_MARKER = "pry_redpacket_paper";
        private final Consumer<String> onPassword;
        private final Runnable onClosed;
        private final Inventory playerInventory;

        PasswordAnvilMenu(int id, Inventory playerInventory, ContainerLevelAccess access,
                          Consumer<String> onPassword, Runnable onClosed) {
            super(id, playerInventory, access);
            this.onPassword = onPassword;
            this.onClosed = onClosed;
            this.playerInventory = playerInventory;
            // UI 用纸：关闭时按标记回收（见 reclaimPapers），避免凭空造物流入玩家背包
            this.slots.get(0).set(passwordPaper());
        }

        public static ItemStack passwordPaper() {
            ItemStack stack = icon(Items.PAPER, PASSWORD_PAPER_NAME);
            CompoundTag marker = new CompoundTag();
            marker.putBoolean(PASSWORD_PAPER_MARKER, true);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
            return stack;
        }

        @Override
        public boolean stillValid(Player player) {
            // 虚拟铁砧：不绑定世界方块位置，恒有效（原版判断要求 access 处是铁砧）
            return true;
        }

        @Override
        protected boolean mayPickup(Player player, boolean hasStacks) {
            return true;
        }

        @Override
        protected void onTake(Player player, ItemStack stack) {
            // 不调 super：免费提交（super 首行扣经验）+ 虚拟菜单（super 会损耗世界铁砧）
            String password = stack.isEmpty() ? "" : stack.getHoverName().getString();
            // 原版语义：消耗输入槽（javap 26.3：inputSlots.setItem(0/1, EMPTY)），漏它即复制机；
            // setChanged 链会顺带走 slotsChanged → createResult 重置费用
            this.inputSlots.setItem(0, ItemStack.EMPTY);
            this.inputSlots.setItem(1, ItemStack.EMPTY);
            // 成品纸随提交一并消耗：原版取出流程把它交给玩家（safeTake 副本进 carried），
            // 清零同一实例即可同时覆盖直调与点击取走两条路径，纸不进背包
            stack.setCount(0);
            onPassword.accept(password);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            // 成品格 shift-click = 提交口令（同点击取走语义）；其余格一律不迁移：
            // super 会把玩家物品塞进输入槽/成品纸塞进背包
            if (index == RESULT_SLOT && this.slots.get(index).hasItem()) {
                this.onTake(player, this.slots.get(index).getItem());
            }
            return ItemStack.EMPTY;
        }

        //#if MC < 260102
        @Override
        public void clicked(int index, int button, ClickType type, Player player) {
            // 输入槽服务端托管：阻断拿取/交换/丢弃等一切点击，UI 纸只进不出
            if (index == 0 || index == 1) {
                return;
            }
            super.clicked(index, button, type, player);
        }
        //#else
        //$$ @Override
        //$$ public void clicked(int index, int button, ContainerInput type, Player player) {
        //$$     // 输入槽服务端托管：阻断拿取/交换/丢弃等一切点击，UI 纸只进不出
        //$$     if (index == 0 || index == 1) {
        //$$         return;
        //$$     }
        //$$     super.clicked(index, button, type, player);
        //$$ }
        //#endif

        @Override
        public void slotsChanged(net.minecraft.world.Container container) {
            super.slotsChanged(container);
            if (container == this.inputSlots) {
                this.evictForeignInput();
            }
        }

        /** 输入槽白名单：槽 0 只留标记口令纸，槽 1 一律清空；外来物品弹回背包（放不下掉脚下） */
        private void evictForeignInput() {
            for (int slot = 0; slot < this.inputSlots.getContainerSize(); slot++) {
                ItemStack stack = this.inputSlots.getItem(slot);
                if (stack.isEmpty() || (slot == 0 && isPasswordPaper(stack))) {
                    continue;
                }
                this.inputSlots.setItem(slot, ItemStack.EMPTY);
                //#if MC >= 260300
                //$$ this.playerInventory.placeItemBackInInventory(stack, net.minecraft.util.Prediction.PREDICTED);
                //#else
                this.playerInventory.placeItemBackInInventory(stack);
                //#endif
            }
        }

        @Override
        public void removed(Player player) {
            reclaimPapers();
            onClosed.run();
            super.removed(player);
        }

        /** 按标记回收菜单内 UI 用纸（输入槽 + 手持）。玩家背包不扫——玩家物品不受触碰 */
        private void reclaimPapers() {
            if (isPasswordPaper(this.slots.get(0).getItem())) {
                this.slots.get(0).set(ItemStack.EMPTY);
            }
            if (isPasswordPaper(this.getCarried())) {
                this.setCarried(ItemStack.EMPTY);
            }
        }

        private static boolean isPasswordPaper(ItemStack stack) {
            if (stack.getItem() != Items.PAPER) {
                return false;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return data != null && data.copyTag().contains(PASSWORD_PAPER_MARKER);
        }
    }

    // ==================== 打开 ====================

    // containerId 铁律：菜单必须在 createMenu(id,...) 回调内用 openMenu 分配的真实 id 构建。
    // 预构建实例（id 硬编码 0）会让服务端把 GUI 全量同步打上 containerId=0——那是玩家
    // 背包菜单的保留号，原版客户端把 id=0 的内容同步无条件路由进 46 格背包菜单：类型菜单
    // 9x3 共 63 格（27 容器 + 36 背包）灌 46 格 → IndexOutOfBoundsException → 客户端
    // "网络协议错误"踢出（d65e6c3 回归，26.2 生产实证）。

    /** 类型选择：3 行，图标按 TYPE_SLOTS 槽位摆放；回调传图标下标 0-3 */
    public static void openTypeMenu(ServerPlayer player, Component title,
                                    ItemStack[] icons, IntConsumer onPick) {
        RedPacketContainer container = new RedPacketContainer(27);
        for (int i = 0; i < TYPE_SLOTS.length; i++) {
            container.forceSet(TYPE_SLOTS[i], icons[i]);
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RedPacketMenu(
                MenuType.GENERIC_9x3, id, inv, container, 3, slot -> true,
                slot -> {
                    for (int i = 0; i < TYPE_SLOTS.length; i++) {
                        if (TYPE_SLOTS[i] == slot) {
                            onPick.accept(i);
                            return;
                        }
                    }
                }, null), title));
    }

    /** 物品投放：6 行，槽 0-44 可编辑，末行（按钮+两侧空格）全保护——投放区以外禁止存放，防物品丢失 */
    public static RedPacketContainer openItemInput(ServerPlayer player, Component title,
                                                   ItemStack cancelIcon, ItemStack confirmIcon, ItemStack clearIcon,
                                                   IntConsumer onButton, Runnable onRemoved) {
        RedPacketContainer container = new RedPacketContainer(54);
        container.forceSet(SLOT_CANCEL, cancelIcon);
        container.forceSet(SLOT_CONFIRM, confirmIcon);
        container.forceSet(SLOT_CLEAR, clearIcon);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RedPacketMenu(
                MenuType.GENERIC_9x6, id, inv, container, 6,
                slot -> slot >= SLOT_CANCEL,
                onButton, onRemoved), title));
        return container;
    }

    /** 专属对象选择：6 行放在线玩家头（不含发送者），全槽保护 */
    public static RedPacketContainer openTargetSelect(ServerPlayer player, Component title,
                                                      List<ItemStack> heads, IntConsumer onPick) {
        RedPacketContainer container = new RedPacketContainer(54);
        for (int i = 0; i < heads.size() && i < 54; i++) {
            container.forceSet(i, heads.get(i));
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RedPacketMenu(
                MenuType.GENERIC_9x6, id, inv, container, 6, slot -> true, onPick, null), title));
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
