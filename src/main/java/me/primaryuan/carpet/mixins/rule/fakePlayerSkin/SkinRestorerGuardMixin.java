package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.patches.EntityPlayerMPFake;
import me.primaryuan.carpet.util.FakePlayerSkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SkinRestorer join 应用压制：SkinRestorer 在 placeNewPlayer 时按 UUID 查其持久存储，
 * 命中即把存储皮写进 profile（出生包发出前）。对"已由本模组注入皮肤"的假人，
 * 这会用存储记录盖掉注入皮肤——历史 save=true 时代落库的合成名条目即属此类。
 * 所有已注入假人都在保护集内（真人名假人也注入，见 FakePlayerSkinManager）；真人玩家
 * 因不是 EntityPlayerMPFake 实例，SkinRestorer 行为完全不变。
 *
 * <p>SkinRestorer 为可选依赖：类不存在时 mixin 静默跳过（require=0），由
 * MixinSanityCheck 在已安装但签名漂移时报 warn。SkinService/SkinValue 编译期
 * 不可知：@Mixin 用字符串 target，handler 参数以 @Coerce 占位。</p>
 *
 * <p>method 描述符里的 ServerPlayer 必须写成目标环境的运行时名：loom 对字符串
 * target 的 mixin 不做注解重映射（class 字面量 target 才会），≤1.21.x 生产环境为
 * intermediary（裸代码分支），26.x 未混淆用 mojmap（//#if MC >= 260102 的 //$$ 分支）。
 * 代价是 ≤1.21.x 的 dev 环境（mojmap）压制注入不生效——仅影响本地自测，生产不受影响。</p>
 */
@Mixin(targets = "net.lionarius.skinrestorer.skin.SkinService", remap = false)
public abstract class SkinRestorerGuardMixin {

    //#if MC >= 260102
    //$$ @Inject(
    //$$         method = "applySkin(Lnet/minecraft/server/level/ServerPlayer;Lnet/lionarius/skinrestorer/skin/SkinValue;Z)Z",
    //$$         at = @At("HEAD"),
    //$$         cancellable = true,
    //$$         require = 0
    //$$ )
    //$$ private static void pry$skipStoredSkinForInjectedFakes(@Coerce Object player, @Coerce Object skinValue,
    //$$                                                            boolean save, CallbackInfoReturnable<Boolean> cir) {
    //$$     pry$guard(player, cir);
    //$$ }
    //#else
    @Inject(
            method = "applySkin(Lnet/minecraft/class_3222;Lnet/lionarius/skinrestorer/skin/SkinValue;Z)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void pry$skipStoredSkinForInjectedFakes(@Coerce Object player, @Coerce Object skinValue,
                                                           boolean save, CallbackInfoReturnable<Boolean> cir) {
        pry$guard(player, cir);
    }
    //#endif

    private static void pry$guard(Object player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof EntityPlayerMPFake fake
                && FakePlayerSkinManager.isProtected(fake.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
