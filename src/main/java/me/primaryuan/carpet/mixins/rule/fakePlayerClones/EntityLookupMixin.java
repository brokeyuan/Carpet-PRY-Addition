package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneEntityLookupHelper;
import me.primaryuan.carpet.clones.CloneAllowlist;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同 UUID 分身注入点二：EntityLookup.add 的 byUuid 重复检查把第二个同 UUID
 * 实体静默拒之门外（warn 后直接 return，连 byId 都不进）。
 *
 * <p>与 PESM 注入点一相同守卫。重复 UUID 且在允许名单内时 cancel 原方法
 * （跳过拒绝分支），由 CloneEntityLookupHelper 经访问器补齐 byId/byUuid
 * 两个索引。byUuid.put 后写覆盖 = UUID 查询的主实例语义，与 playersByUUID
 * 一致。非分身实体不满足守卫，原路径零改动。</p>
 */
@Mixin(EntityLookup.class)
public abstract class EntityLookupMixin {

    @Inject(
            method = "add",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends net.minecraft.world.level.entity.EntityAccess> void pry$allowCloneAdd(
            T entity, CallbackInfo ci
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        boolean allowed = CloneAllowlist.isAllowed(entity.getUUID());
        org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan").info(
                "[pry:clones][DIAG] EntityLookup.add entity={} id={} allowed={}",
                entity.getUUID(), entity.getId(), allowed);
        if (!allowed) {
            return;
        }
        CloneEntityLookupHelper.putClone(unchecked(), entity);
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    private EntityLookup<net.minecraft.world.level.entity.EntityAccess> unchecked() {
        return (EntityLookup<net.minecraft.world.level.entity.EntityAccess>) (Object) this;
    }
}
