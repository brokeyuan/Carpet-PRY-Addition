package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

//#if MC >= 12105
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取实体自身的 EntityEquipment（玩家为 PlayerEquipment 实例）。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityEquipmentAccessor {

    @Accessor("equipment")
    EntityEquipment pry$equipment();
}
//#endif
