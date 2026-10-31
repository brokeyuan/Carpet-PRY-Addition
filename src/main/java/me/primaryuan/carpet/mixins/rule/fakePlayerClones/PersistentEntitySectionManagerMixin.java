package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.clones.CloneAllowlist;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 同 UUID 分身注入点一（硬拦截最深处）：knownUuids 已含该 UUID 时
 * addEntityUuid 返回 false，第二个实体不进世界。
 *
 * <p>守卫顺序：规则开启 → 该 UUID 在分身允许名单 → 重复 UUID 场景。
 * 满足三者才 cancel(true)（谎报"knownUuids 已收录"，跳过拒绝分支，
 * 实体正常进入 sectionStorage / 追踪 / Tick）。普通假人、真人、生物
 * 均不满足第二个条件，路径零改动。</p>
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {

    @Inject(
            method = "addEntityUuid",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private <T extends net.minecraft.world.level.entity.EntityAccess> void pry$allowCloneUuid(
            T entity, CallbackInfoReturnable<Boolean> cir
    ) {
        if (!CarpetPrimaryuanSettings.fakePlayerClones) {
            return;
        }
        boolean allowed = CloneAllowlist.isAllowed(entity.getUUID());
        org.apache.logging.log4j.LogManager.getLogger("CarpetPrimaryuan").info(
                "[pry:clones][DIAG] PESM.addEntityUuid entity={} id={} allowed={}",
                entity.getUUID(), entity.getId(), allowed);
        if (!allowed) {
            return;
        }
        cir.setReturnValue(true);
    }
}
