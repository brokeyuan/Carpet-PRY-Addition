package me.primaryuan.carpet.handler.playerHat;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

class PlayerHatHandlerTest {
    @BeforeAll static void bootstrap() { TestRegistries.bootstrap(); }
    @AfterEach void reset() { CarpetPrimaryuanSettings.playerHat = false; }

    @Test void changedCountComponentsOrStackIdentityPreventsFallback() throws Exception {
        var p = player();
        var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.main.shrink(1);
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p = player(); before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.main.set(DataComponents.CUSTOM_NAME, Component.literal("changed by use"));
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p = player(); before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.main = p.main.copy();
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p = player(); before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.head.shrink(1);
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p = player(); before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.head = p.head.copy();
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
    }

    @Test void playerStateAndOffhandAreCheckedBeforeAndAfterUse() throws Exception {
        var p = player();
        assertNull(PlayerHatHandler.beforeUse(p, InteractionHand.OFF_HAND));
        var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        p.off = new ItemStack(Items.TORCH);
        assertNull(PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND));
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p.off = ItemStack.EMPTY; p.using = true;
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p.using = false; p.spectator = true;
        assertNull(PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND));
        p.spectator = false; CarpetPrimaryuanSettings.playerHat = false;
        assertNull(PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND));
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
    }

    @Test void useRayFollowsCurrentPacketRotationRatherThanHeadAnimation() throws Exception {
        var p = player();
        p.setYRot(0); p.setXRot(0); p.setYHeadRot(180);
        assertTrue(p.getViewVector(1).z < -.99);
        assertEquals(1, PlayerHatHandler.useDirection(p).z, .00001);
        p.setXRot(-90);
        assertEquals(1, PlayerHatHandler.useDirection(p).y, .00001);
    }

    private static Fixture player() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var p = (Fixture) ((Unsafe) field.get(null)).allocateInstance(Fixture.class);
        p.main = new ItemStack(Items.STONE,64); p.head = new ItemStack(Items.DIRT,32); p.off = ItemStack.EMPTY;
        CarpetPrimaryuanSettings.playerHat = true;
        return p;
    }

    private static final class Fixture extends ServerPlayer {
        ItemStack main, head, off;
        boolean using, spectator;
        private Fixture() { super(null,null,null,null); }
        @Override public ItemStack getMainHandItem() { return main; }
        @Override public ItemStack getOffhandItem() { return off; }
        @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return head; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean canUseSlot(EquipmentSlot slot) { return true; }
    }
}
