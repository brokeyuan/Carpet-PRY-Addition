package me.primaryuan.carpet.replay.phantom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 装备字节 ↔ ItemStack（NBT 序列化，仅同 MC 版本可读——录像不跨版本使用是明确边界）。
 * 经 ItemStack.CODEC + RegistryOps（save/parse 在 26.3 不存在，javap 核实）。
 */
public final class ItemStackBytes
{
    public ItemStackBytes() {}

    public static byte[] serialize(MinecraftServer server, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return new byte[0];
        }
        try
        {
            var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var result = ItemStack.CODEC.encodeStart(ops, stack);
            net.minecraft.nbt.Tag tagRaw = result.getOrThrow();
            if (!(tagRaw instanceof CompoundTag tag))
            {
                return new byte[0];
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bos);
            return bos.toByteArray();
        }
        catch (Exception e)
        {
            return new byte[0];
        }
    }

    public static ItemStack deserialize(MinecraftServer server, byte[] bytes)
    {
        if (bytes == null || bytes.length == 0)
        {
            return ItemStack.EMPTY;
        }
        try
        {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            var ops = server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, tag).getOrThrow();
        }
        catch (Exception e)
        {
            return ItemStack.EMPTY;
        }
    }
}
