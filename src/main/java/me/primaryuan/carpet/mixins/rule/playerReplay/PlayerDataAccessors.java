package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.syncher.EntityDataAccessor;
//#if MC >= 12110
import net.minecraft.world.entity.Avatar;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 幻影元数据所需 EntityDataAccessor 暴露。
 * 皮肤层字节（DATA_PLAYER_MODE_CUSTOMISATION）全版本 Byte；
 * 1.21~1.21.8 两个 accessor 在 Player；1.21.10+ 迁移至 Avatar（主手类型 1.21.11+ 为 HumanoidArm）——
 * 泛型用 Object 承载两种形态，值由调用方按版本分支决定。
 */
//#if MC >= 12110
@Mixin(Avatar.class)
public interface PlayerDataAccessors
{
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> pry$skinLayers() { throw new AssertionError(); }

    @Accessor("DATA_PLAYER_MAIN_HAND")
    static EntityDataAccessor<Object> pry$mainHand() { throw new AssertionError(); }
}
//#else
//$$ @Mixin(net.minecraft.world.entity.player.Player.class)
//$$ public interface PlayerDataAccessors
//$$ {
//$$     @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
//$$     static EntityDataAccessor<Byte> pry$skinLayers() { throw new AssertionError(); }
//$$
//$$     @Accessor("DATA_PLAYER_MAIN_HAND")
//$$     static EntityDataAccessor<Object> pry$mainHand() { throw new AssertionError(); }
//$$ }
//#endif
