package me.primaryuan.carpet.util;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SendtoLinkManager {

    private static final int MAIN_INVENTORY_SIZE = 36;

    private SendtoLinkManager() {}

    public enum LinkResult {
        SUCCESS,
        SOURCE_NOT_FAKE,
        TARGET_OFFLINE,
        TARGET_NOT_FAKE,
        SELF_LINK,
        DUPLICATE
    }

    public enum Mode {
        NONE, CONTINUOUS, INTERVAL, AFTER, PERTICK, RANDOMLY
    }

    private static final class SourceLinks {
        final List<String> targets = new ArrayList<>();
        int cursor = 0;
        Mode mode = Mode.CONTINUOUS;
        int interval = 1;
        int min = 1;
        int max = 1;
        int ticksUntilNext = 1;
        int transferredStacks = 0;
        int transferredItems = 0;
    }

    private static final Map<String, SourceLinks> LINKS = new ConcurrentHashMap<>();

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        synchronized (SendtoLinkManager.class) {
            if (initialized) return;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (!CarpetPrimaryuanSettings.fakePlayerSendto) return;
                if (LINKS.isEmpty()) return;
                for (Map.Entry<String, SourceLinks> entry : LINKS.entrySet()) {
                    tickSource(server, entry.getKey(), entry.getValue());
                }
            });
            ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                    removeAllLinksInvolving(handler.player));
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> LINKS.clear());
            initialized = true;
        }
    }

    public static LinkResult addLink(MinecraftServer server, ServerPlayer source, String targetName) {
        if (!(source instanceof EntityPlayerMPFake)) {
            return LinkResult.SOURCE_NOT_FAKE;
        }
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return LinkResult.TARGET_OFFLINE;
        }
        if (!(target instanceof EntityPlayerMPFake)) {
            return LinkResult.TARGET_NOT_FAKE;
        }
        if (target.getUUID().equals(source.getUUID())) {
            return LinkResult.SELF_LINK;
        }
        String canonicalTargetName = target.getName().getString();
        SourceLinks links = LINKS.computeIfAbsent(source.getName().getString(), k -> new SourceLinks());
        if (indexOfTarget(links, canonicalTargetName) >= 0) {
            return LinkResult.DUPLICATE;
        }
        links.targets.add(canonicalTargetName);
        if (links.mode == Mode.NONE) {
            links.mode = Mode.CONTINUOUS;
            links.ticksUntilNext = 1;
        }
        return LinkResult.SUCCESS;
    }

    public static boolean setMode(ServerPlayer source, Mode mode, int interval, int min, int max) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null || links.targets.isEmpty()) {
            return false;
        }
        links.mode = mode;
        links.interval = interval;
        links.min = min;
        links.max = max;
        links.ticksUntilNext = mode == Mode.RANDOMLY ? min : Math.max(1, interval);
        return true;
    }

    public static int transferOnce(MinecraftServer server, ServerPlayer source) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null || links.targets.isEmpty()) {
            return -1;
        }
        ServerPlayer src = source;
        if (!isValidFake(src)) {
            LINKS.remove(source.getName().getString());
            return -1;
        }
        int moved = transferOneStack(server, source.getName().getString(), links, src);
        if (moved > 0) {
            links.transferredStacks++;
            links.transferredItems += moved;
        }
        return moved;
    }

    public static int[] stopAndRemove(ServerPlayer source) {
        SourceLinks links = LINKS.remove(source.getName().getString());
        if (links == null) {
            return null;
        }
        return new int[]{links.transferredStacks, links.transferredItems};
    }

    public static List<String> getLinks(ServerPlayer source) {
        SourceLinks links = LINKS.get(source.getName().getString());
        if (links == null) {
            return List.of();
        }
        return new ArrayList<>(links.targets);
    }

    private static void tickSource(MinecraftServer server, String sourceName, SourceLinks links) {
        if (links.mode == Mode.NONE || links.targets.isEmpty()) {
            return;
        }
        ServerPlayer source = server.getPlayerList().getPlayerByName(sourceName);
        if (!isValidFake(source)) {
            LINKS.remove(sourceName);
            return;
        }
        if (--links.ticksUntilNext > 0) {
            return;
        }
        int moved = transferOneStack(server, sourceName, links, source);
        if (moved > 0) {
            links.transferredStacks++;
            links.transferredItems += moved;
        }
        switch (links.mode) {
            case CONTINUOUS:
                links.ticksUntilNext = 1;
                break;
            case INTERVAL:
            case PERTICK:
                links.ticksUntilNext = links.interval;
                break;
            case AFTER:
                if (moved > 0) {
                    links.mode = Mode.NONE;
                } else {
                    links.ticksUntilNext = 1;
                }
                break;
            case RANDOMLY:
                links.ticksUntilNext = links.max > links.min
                        ? ThreadLocalRandom.current().nextInt(links.max - links.min + 1) + links.min
                        : links.min;
                break;
            default:
                links.mode = Mode.NONE;
                break;
        }
    }

    private static int transferOneStack(MinecraftServer server, String sourceName, SourceLinks links, ServerPlayer source) {
        if (links.targets.isEmpty()) {
            return 0;
        }
        int size = links.targets.size();
        List<ServerPlayer> roundTargets = new ArrayList<>(size);
        List<String> invalidTargets = null;
        for (int i = 0; i < size; i++) {
            String targetName = links.targets.get((links.cursor + i) % size);
            ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
            if (isValidFake(target)) {
                roundTargets.add(target);
            } else {
                if (invalidTargets == null) {
                    invalidTargets = new ArrayList<>();
                }
                invalidTargets.add(targetName);
            }
        }
        if (invalidTargets != null) {
            for (String invalidName : invalidTargets) {
                int index = indexOfTarget(links, invalidName);
                if (index >= 0) {
                    removeTargetAt(links, index);
                }
            }
            if (links.targets.isEmpty()) {
                LINKS.remove(sourceName);
                return 0;
            }
        }
        Inventory sourceInv = source.getInventory();
        int slot = -1;
        for (int i = 0; i < MAIN_INVENTORY_SIZE; i++) {
            if (!sourceInv.getItem(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return 0;
        }
        ItemStack stack = sourceInv.getItem(slot);
        int remaining = stack.getCount();
        for (ServerPlayer target : roundTargets) {
            if (remaining <= 0) {
                break;
            }
            remaining -= insertIntoInventory(target.getInventory(), stack, remaining);
        }
        int moved = stack.getCount() - remaining;
        if (moved <= 0) {
            return 0;
        }
        if (moved >= stack.getCount()) {
            sourceInv.setItem(slot, ItemStack.EMPTY);
        } else {
            stack.setCount(stack.getCount() - moved);
        }
        links.cursor = (links.cursor + 1) % links.targets.size();
        return moved;
    }

    private static int insertIntoInventory(Inventory inv, ItemStack sourceStack, int maxCount) {
        int moved = 0;
        int remaining = maxCount;
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            ItemStack targetStack = inv.getItem(i);
            if (targetStack.isEmpty() || targetStack.getCount() >= targetStack.getMaxStackSize()) {
                continue;
            }
            //#if MC >= 12108
            if (!ItemStack.isSameItemSameComponents(targetStack, sourceStack)) {
            //#else
            //$$ if (!ItemStack.isSameItemSameTags(targetStack, sourceStack)) {
            //#endif
                continue;
            }
            int take = Math.min(targetStack.getMaxStackSize() - targetStack.getCount(), remaining);
            targetStack.setCount(targetStack.getCount() + take);
            moved += take;
            remaining -= take;
        }
        for (int i = 0; i < MAIN_INVENTORY_SIZE && remaining > 0; i++) {
            if (!inv.getItem(i).isEmpty()) {
                continue;
            }
            int take = Math.min(sourceStack.getMaxStackSize(), remaining);
            ItemStack newStack = sourceStack.copy();
            newStack.setCount(take);
            inv.setItem(i, newStack);
            moved += take;
            remaining -= take;
        }
        return moved;
    }

    private static boolean isValidFake(ServerPlayer player) {
        return player instanceof EntityPlayerMPFake
                && !player.hasDisconnected()
                && !player.isRemoved();
    }

    private static void removeAllLinksInvolving(ServerPlayer player) {
        String name = player.getName().getString();
        LINKS.remove(name);
        Iterator<Map.Entry<String, SourceLinks>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            SourceLinks links = it.next().getValue();
            int index = indexOfTarget(links, name);
            if (index >= 0) {
                removeTargetAt(links, index);
                if (links.targets.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private static int indexOfTarget(SourceLinks links, String targetName) {
        for (int i = 0; i < links.targets.size(); i++) {
            if (links.targets.get(i).equalsIgnoreCase(targetName)) {
                return i;
            }
        }
        return -1;
    }

    private static void removeTargetAt(SourceLinks links, int index) {
        links.targets.remove(index);
        if (index < links.cursor) {
            links.cursor--;
        }
        if (links.targets.isEmpty()) {
            links.cursor = 0;
        } else if (links.cursor >= links.targets.size()) {
            links.cursor = 0;
        }
    }
}
