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

    @Test void ordinaryHeadItemsSwapThroughTheAirShortcut() throws Exception {
        // 空头、普通方块、玩家头颅、雕刻南瓜：允许右键换帽
        for (ItemStack head : new ItemStack[]{ItemStack.EMPTY,
                new ItemStack(Items.DIRT, 32), new ItemStack(Items.PLAYER_HEAD),
                new ItemStack(Items.CARVED_PUMPKIN)}) {
            var p = player();
            p.head = head.copy();
            var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
            assertNotNull(before, head + "");
            assertTrue(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before), head + "");
            assertEquals(Items.STONE, p.head.getItem(), head + "");
            if (head.isEmpty()) {
                assertTrue(p.main.isEmpty(), head + "");
            } else {
                assertEquals(head.getItem(), p.main.getItem(), head + "");
                assertEquals(head.getCount(), p.main.getCount(), head + "");
            }
        }
    }

    @Test void helmetsTurtleShellAndTotemAreProtectedFromTheShortcut() throws Exception {
        // 正常头盔（26.3 含铜盔）、海龟壳、图腾：右键拒绝，槽位不动
        for (ItemStack head : new ItemStack[]{new ItemStack(Items.LEATHER_HELMET),
                new ItemStack(Items.COPPER_HELMET), new ItemStack(Items.CHAINMAIL_HELMET),
                new ItemStack(Items.IRON_HELMET), new ItemStack(Items.GOLDEN_HELMET),
                new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.NETHERITE_HELMET),
                new ItemStack(Items.TURTLE_HELMET), new ItemStack(Items.TOTEM_OF_UNDYING)}) {
            var p = player();
            p.head = head.copy();
            var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
            assertNotNull(before, head + "");
            assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before), head + "");
            assertTrue(ItemStack.matches(head, p.head), head + " 槽位不得变化");
            assertEquals(Items.STONE, p.main.getItem(), head + " 主手不得变化");
            assertEquals(64, p.main.getCount(), head + " 主手数量不得变化");
            assertTrue(PlayerHatHandler.isProtectedHeadItem(head), head + "");
        }
    }

    @Test void bindingCurseAndCreativeStillBehaveForUnprotectedHeads() throws Exception {
        // 绑定诅咒（非盔甲头）拒绝交换；创造模式无视诅咒
        var p = player();
        p.head = cursedSkull();
        var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        assertFalse(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        p.creative = true;
        assertTrue(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
    }

    @Test void stackedItemsSwapByWholeStackWithoutDuplication() throws Exception {
        // 不同数量整组交换，不复制不消失
        var p = player();
        p.main = new ItemStack(Items.STONE, 3);
        p.head = new ItemStack(Items.PLAYER_HEAD, 17);
        var before = PlayerHatHandler.beforeUse(p, InteractionHand.MAIN_HAND);
        assertTrue(PlayerHatHandler.tryEquip(p, InteractionHand.MAIN_HAND, before));
        assertEquals(17, p.main.getCount());
        assertEquals(3, p.head.getCount());
        assertEquals(Items.PLAYER_HEAD, p.main.getItem());
        assertEquals(Items.STONE, p.head.getItem());
    }

    private static ItemStack cursedSkull() {
        var stack = new ItemStack(Items.PLAYER_HEAD);
        var definition = new net.minecraft.world.item.enchantment.Enchantment.EnchantmentDefinition(
                net.minecraft.core.HolderSet.direct(net.minecraft.core.Holder.direct(Items.PLAYER_HEAD)),
                java.util.Optional.empty(), 1, 1,
                net.minecraft.world.item.enchantment.Enchantment.constantCost(1),
                net.minecraft.world.item.enchantment.Enchantment.constantCost(1), 1,
                java.util.List.of());
        var enchantment = new net.minecraft.world.item.enchantment.Enchantment.Builder(definition)
                .withSpecialEffect(net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE,
                        net.minecraft.util.Unit.INSTANCE)
                .build(net.minecraft.resources.Identifier.withDefaultNamespace("binding_curse"));
        var mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        mutable.set(net.minecraft.core.Holder.direct(enchantment), 1);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    private static Fixture player() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var p = (Fixture) ((Unsafe) field.get(null)).allocateInstance(Fixture.class);
        p.main = new ItemStack(Items.STONE,64); p.head = new ItemStack(Items.DIRT,32); p.off = ItemStack.EMPTY;
        // aimsAtAir 需要真实几何字段：站位原点、包围盒一个方块，射线全部落空
        setEntityField(p, "position", net.minecraft.world.phys.Vec3.ZERO);
        setEntityField(p, "bb", new net.minecraft.world.phys.AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3));
        setEntityField(p, "level", unsafeAllocate(AirLevel.class));
        CarpetPrimaryuanSettings.playerHat = true;
        return p;
    }

    private static void setEntityField(Object target, String name, Object value) throws Exception {
        var field = net.minecraft.world.entity.Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object unsafeAllocate(Class<?> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return ((Unsafe) field.get(null)).allocateInstance(type);
    }

    /** 空 ServerLevel：方块射线 MISS、无实体，右键空气判定通过。Unsafe 构造绕过 ServerLevel 重构造。 */
    private static final class AirLevel extends net.minecraft.server.level.ServerLevel {
        @SuppressWarnings("unused")
        private AirLevel() { super(null, null, null, null, null, null, false, 0L, null, false); }
        @Override public net.minecraft.world.phys.BlockHitResult clip(net.minecraft.world.level.ClipContext context) {
            return net.minecraft.world.phys.BlockHitResult.miss(context.getFrom(), net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.containing(context.getFrom()));
        }
        @Override protected net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity> getEntities() {
            return new net.minecraft.world.level.entity.LevelEntityGetter<>() {
                @Override public net.minecraft.world.entity.Entity get(int id) { return null; }
                @Override public net.minecraft.world.entity.Entity get(java.util.UUID uuid) { return null; }
                @Override public Iterable<net.minecraft.world.entity.Entity> getAll() { return java.util.List.of(); }
                @Override public <U extends net.minecraft.world.entity.Entity> void get(
                        net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, U> type,
                        net.minecraft.util.AbortableIterationConsumer<U> consumer) {}
                @Override public void get(net.minecraft.world.phys.AABB box, java.util.function.Consumer<net.minecraft.world.entity.Entity> consumer) {}
                @Override public <U extends net.minecraft.world.entity.Entity> void get(
                        net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, U> type,
                        net.minecraft.world.phys.AABB box, net.minecraft.util.AbortableIterationConsumer<U> consumer) {}
            };
        }
        @Override public java.util.List<net.minecraft.world.entity.Entity> getEntities(net.minecraft.world.entity.Entity except,
                net.minecraft.world.phys.AABB box, java.util.function.Predicate<? super net.minecraft.world.entity.Entity> predicate) {
            return java.util.List.of();
        }
    }

    private static final class Fixture extends ServerPlayer {
        ItemStack main, head, off;
        boolean using, spectator, creative;
        private Fixture() { super(null,null,null,null); }
        @Override public ItemStack getMainHandItem() { return main; }
        @Override public ItemStack getOffhandItem() { return off; }
        @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return head; }
        @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { if (slot == EquipmentSlot.HEAD) head = stack; }
        @Override public void setItemInHand(InteractionHand hand, ItemStack stack) { if (hand == InteractionHand.MAIN_HAND) main = stack; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean canUseSlot(EquipmentSlot slot) { return true; }
        @Override public boolean isCreative() { return creative; }
        @Override public double blockInteractionRange() { return 4.5; }
        @Override public double entityInteractionRange() { return 3.0; }
        @Override public boolean isShiftKeyDown() { return false; }
        @Override public boolean isDescending() { return false; }
    }
}
