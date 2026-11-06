package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Entity DATA_POSE accessor */
@Mixin(Entity.class)
public interface EntityDataAccessors
{
    @Accessor("DATA_POSE")
    static EntityDataAccessor<Pose> pry$pose()
    {
        throw new AssertionError();
    }
}
