package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Entity DATA_SHARED_FLAGS_ID accessor（潜行 flag1=0x02 / 疾跑 flag3=0x08 / 隐身 flag5=0x20，javap 实证） */
@Mixin(Entity.class)
public interface LivingEntityDataAccessors
{
    @Accessor("DATA_SHARED_FLAGS_ID")
    static EntityDataAccessor<Byte> pry$sharedFlags()
    {
        throw new AssertionError();
    }
}
