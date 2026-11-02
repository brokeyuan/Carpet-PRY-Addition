package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * EntityLookup 私有索引访问器：分身实体绕过 byUuid 重复拒绝后，
 * 由 CloneEntityLookupHelper 补齐 byId/byUuid 登记（替代被 cancel 掉的原方法主体）。
 */
@Mixin(EntityLookup.class)
public interface EntityLookupAccessor {

    @Accessor("byId")
    Int2ObjectMap<EntityAccess> pry$byId();

    @Accessor("byUuid")
    Map<UUID, EntityAccess> pry$byUuid();
}
