package me.primaryuan.carpet.clones;

import me.primaryuan.carpet.mixins.rule.fakePlayerClones.EntityLookupAccessor;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;

/**
 * 分身实体登记辅助：EntityLookupMixin cancel 掉 add 主体后，
 * 由这里通过访问器补齐 byId/byUuid 两个索引（等价于原方法通过检查后的 put 段）。
 */
public final class CloneEntityLookupHelper {

    private CloneEntityLookupHelper() {}

    public static void putClone(EntityLookup<EntityAccess> lookup, EntityAccess entity) {
        EntityLookupAccessor acc = (EntityLookupAccessor) lookup;
        acc.pry$byId().put(entity.getId(), entity);
        acc.pry$byUuid().put(entity.getUUID(), entity);
    }
}
