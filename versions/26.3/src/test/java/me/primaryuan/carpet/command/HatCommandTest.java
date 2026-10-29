package me.primaryuan.carpet.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/** /hat 命令不受头部保护限制：头盔/图腾在头上仍可通过命令整组交换。 */
class HatCommandTest {
    @BeforeAll static void bootstrap() { TestRegistries.bootstrap(); }

    @Test void commandSwapsEvenWithProtectedHead() throws Exception {
        for (ItemStack head : new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET),
                new ItemStack(Items.TURTLE_HELMET), new ItemStack(Items.TOTEM_OF_UNDYING)}) {
            var p = player();
            p.head = head.copy();
            assertEquals(1, execute(p));
            assertEquals(Items.STONE, p.head.getItem(), head + "");
            assertEquals(64, p.head.getCount(), head + "");
            assertEquals(head.getItem(), p.main.getItem(), head + "");
            assertEquals(head.getCount(), p.main.getCount(), head + "");
        }
    }

    @Test void commandStillRejectsEmptyMainHand() throws Exception {
        var p = player();
        p.main = ItemStack.EMPTY;
        p.head = new ItemStack(Items.DIAMOND_HELMET);
        assertEquals(0, execute(p));
        assertEquals(Items.DIAMOND_HELMET, p.head.getItem());
        assertTrue(p.main.isEmpty());
    }

    @Test void commandMessageMentionsEquippedItem() throws Exception {
        String template;
        try (var stream = getClass().getResourceAsStream("/assets/carpet-pry-addition/lang/zh_cn.json")) {
            var translations = com.google.gson.JsonParser.parseReader(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            template = translations.get("carpetprimaryuan.command.hat.equipped").getAsString();
        }
        String rendered = String.format(template, "钻石头盔");
        assertTrue(rendered.contains("钻石头盔"));
    }

    @SuppressWarnings("unchecked")
    private static int execute(ServerPlayer player) throws Exception {
        var source = new CommandSourceStack(recordSource(), Vec3.ZERO, Vec2.ZERO,
                null, PermissionSet.NO_PERMISSIONS, null, player);
        var context = (CommandContext<CommandSourceStack>) unsafe(CommandContext.class);
        setField(context, "source", source);
        setField(context, "input", "hat");
        setField(context, "arguments", new HashMap<String, ParsedArgument<CommandSourceStack, ?>>());
        setField(context, "command", null);
        setField(context, "rootNode", null);
        setField(context, "nodes", java.util.List.of());
        setField(context, "range", null);
        setField(context, "child", null);
        setField(context, "modifier", null);
        setField(context, "forks", false);
        Method execute = HatCommand.class.getDeclaredMethod("execute", CommandContext.class);
        execute.setAccessible(true);
        return (Integer) execute.invoke(null, context);
    }

    private static CommandSource recordSource() {
        return new CommandSource() {
            @Override public void sendSystemMessage(Component message) { sent.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
            final java.util.List<String> sent = new java.util.ArrayList<>();
        };
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object unsafe(Class<?> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static HatPlayer player() throws Exception {
        var p = (HatPlayer) unsafe(HatPlayer.class);
        p.main = new ItemStack(Items.STONE, 64);
        p.head = new ItemStack(Items.DIRT, 32);
        p.sent = new java.util.ArrayList<>();
        return p;
    }

    private static final class HatPlayer extends ServerPlayer {
        ItemStack main, head;
        private HatPlayer() { super(null, null, null, null); }
        @Override public ItemStack getMainHandItem() { return main; }
        @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return head; }
        @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { if (slot == EquipmentSlot.HEAD) head = stack; }
        @Override public void setItemInHand(InteractionHand hand, ItemStack stack) { if (hand == InteractionHand.MAIN_HAND) main = stack; }
        @Override public boolean isShiftKeyDown() { return false; }
        @Override public boolean isDescending() { return false; }
        @Override public double blockInteractionRange() { return 4.5; }
        @Override public double entityInteractionRange() { return 3.0; }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { sent.add(message.getString()); }
        java.util.List<String> sent = new java.util.ArrayList<>();
    }
}
