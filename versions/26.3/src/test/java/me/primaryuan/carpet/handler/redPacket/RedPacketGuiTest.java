package me.primaryuan.carpet.handler.redPacket;

import com.mojang.authlib.GameProfile;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实菜单和背包，只隔离网络、世界及玩家构造。 */
class RedPacketGuiTest {
    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @BeforeEach
    void clearSessions() throws Exception {
        sessions().clear();
    }

    @Test
    void replacingItemSessionRefundsItsContents() throws Exception {
        MenuPlayer player = menuPlayer();
        RedPacketManager.openTypeMenuTargeted(player, 1, "祝福", player);
        player.menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 7));
        RedPacketManager.openTypeMenuTargeted(player, 1, "再次发送", player);
        assertEquals(7, count(player.getInventory(), Items.DIAMOND));
        player.closeContainer();
        assertEquals(7, count(player.getInventory(), Items.DIAMOND), "关闭新会话不能重复退款");
    }

    @Test
    void replacingPasswordSessionRefundsLockedPayload() throws Exception {
        MenuPlayer player = menuPlayer();
        RedPacketManager.openTypeMenuTargeted(player, 1, "祝福", player);
        player.menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 11));
        Object session = sessions().get(player.getUUID());
        Field type = session.getClass().getDeclaredField("type");
        type.setAccessible(true);
        type.set(session, RedPacket.Type.PASSWORD);
        Method confirm = RedPacketManager.class.getDeclaredMethod("onConfirmPayload", session.getClass());
        confirm.setAccessible(true);
        confirm.invoke(null, session);
        RedPacketManager.openTypeMenuTargeted(player, 1, "再次发送", player);
        assertEquals(11, count(player.getInventory(), Items.DIAMOND));
    }

    @Test
    void everyToolbarSlotRejectsPlayerItems() throws Exception {
        MenuPlayer player = menuPlayer();
        RedPacketGui.openItemInput(player, Component.literal("红包"), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, slot -> {}, () -> {});
        for (int index = 45; index < 54; index++) {
            assertFalse(player.menu.getSlot(index).mayPlace(new ItemStack(Items.DIAMOND)), "槽 " + index);
            assertFalse(player.menu.getSlot(index).mayPickup(player), "槽 " + index);
        }
        assertTrue(player.menu.getSlot(44).mayPlace(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void virtualAnvilDoesNotRequireAWorldBlock() throws Exception {
        LocalPlayer player = localPlayer();
        ContainerLevelAccess noAnvil = new ContainerLevelAccess() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> action) {
                return Optional.of((T) Boolean.FALSE);
            }
        };
        RedPacketGui.PasswordAnvilMenu menu = new RedPacketGui.PasswordAnvilMenu(
                1, player.getInventory(), noAnvil, text -> {}, () -> {});
        assertTrue(menu.stillValid(player));
    }

    @Test
    void passwordSubmissionIsFreeAndDoesNotGivePaper() throws Exception {
        LocalPlayer player = localPlayer();
        player.experienceLevel = 10;
        String[] submitted = {null};
        RedPacketGui.PasswordAnvilMenu menu = new RedPacketGui.PasswordAnvilMenu(
                1, player.getInventory(), ContainerLevelAccess.NULL, text -> submitted[0] = text, () -> {});
        menu.setItemName("口令");
        ItemStack result = menu.getSlot(2).getItem();
        menu.onTake(player, result);
        assertEquals("口令", submitted[0]);
        assertEquals(10, player.experienceLevel);
        assertTrue(result.isEmpty());
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(0, count(player.getInventory(), Items.PAPER));
    }

    @Test
    void shiftClickSubmissionCannotMovePaperIntoInventory() throws Exception {
        LocalPlayer player = localPlayer();
        RedPacketGui.PasswordAnvilMenu menu = new RedPacketGui.PasswordAnvilMenu(
                1, player.getInventory(), ContainerLevelAccess.NULL, text -> {}, () -> {});
        menu.setItemName("口令");
        menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
        assertEquals(0, count(player.getInventory(), Items.PAPER));
        assertTrue(menu.getCarried().isEmpty());
    }

    @Test
    void closingAnvilPreservesRealPaperWithTheSameName() throws Exception {
        LocalPlayer player = localPlayer();
        player.getInventory().setItem(0, RedPacketGui.PasswordAnvilMenu.passwordPaper());
        RedPacketGui.PasswordAnvilMenu menu = new RedPacketGui.PasswordAnvilMenu(
                1, player.getInventory(), ContainerLevelAccess.NULL, text -> {}, () -> {});
        menu.removed(player);
        assertEquals(1, count(player.getInventory(), Items.PAPER));
    }

    private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> sessions() throws Exception {
        Field field = RedPacketManager.class.getDeclaredField("SESSIONS");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static LocalPlayer localPlayer() throws Exception {
        LocalPlayer player = (LocalPlayer) unsafe().allocateInstance(LocalPlayer.class);
        player.inventory = new Inventory(player, new EntityEquipment());
        return player;
    }

    private static MenuPlayer menuPlayer() throws Exception {
        MenuPlayer player = (MenuPlayer) unsafe().allocateInstance(MenuPlayer.class);
        player.id = UUID.randomUUID();
        player.inventory = new Inventory(player, new EntityEquipment());
        return player;
    }

    private static final class LocalPlayer extends Player {
        private Inventory inventory;
        private LocalPlayer() { super(null, new GameProfile(UUID.randomUUID(), "测试")); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean hasInfiniteMaterials() { return false; }
        @Override public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }
        @Override public void giveExperienceLevels(int amount) { experienceLevel += amount; }
    }

    private static final class MenuPlayer extends ServerPlayer {
        private Inventory inventory;
        private UUID id;
        private AbstractContainerMenu menu;
        private MenuPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "测试"), ClientInformation.createDefault()); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public UUID getUUID() { return id; }
        @Override public Component getName() { return Component.literal("测试"); }
        @Override public boolean hasInfiniteMaterials() { return false; }
        @Override public OptionalInt openMenu(MenuProvider provider) {
            closeContainer();
            menu = provider.createMenu(1, inventory, this);
            return OptionalInt.of(1);
        }
        @Override public void closeContainer() {
            if (menu != null) {
                AbstractContainerMenu previous = menu;
                menu = null;
                previous.removed(this);
            }
        }
    }
}
