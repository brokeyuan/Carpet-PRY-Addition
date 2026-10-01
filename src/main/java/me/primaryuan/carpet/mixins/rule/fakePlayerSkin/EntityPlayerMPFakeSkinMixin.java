package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.primaryuan.carpet.util.FakePlayerSkinManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 假人构造器注入：Carpet 的 createFake 为两段式（异步拉取 profile → 回调里构造实体、
 * placeNewPlayer 发出生包），构造器收到的 profile 就是出生包所带 profile。
 * 在构造器 HEAD 就地替换 textures 属性，观战者首帧即目标皮肤，无闪皮窗口。
 *
 * <p>真人判定 = profile UUID 版本号（Mojang 正版 v4 / 离线合成名 v3）：
 * v4 直接跳过——同名真人名假人保持该玩家真实长相，SkinRestorer 的 join 应用
 * （按 UUID 查存储皮）恰好就是其真实皮肤，放行即可；v3 合成名假人才注入。
 * 可选 skinrestorer 未装时 PENDING 恒空，本注入自然旁路。</p>
 *
 * <p>TIS Addition 的 /player rejoin 内部 @Shadow 复用 Carpet 的 spawn 方法，
 * 同样经过 createFake → 本构造器，无需单独覆盖。respawnFake（假人重生）沿用
 * 旧 profile（已含注入皮肤），不经本路径也无闪皮。</p>
 *
 * <p>require=0：构造器签名漂移时静默跳过（兜底为出生后换肤），由 MixinSanityCheck 报错。</p>
 */
@Mixin(EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeSkinMixin {

    @Inject(method = "<init>", at = @At("HEAD"), require = 0)
    private static void pry$onFakeConstructed(MinecraftServer server, ServerLevel level, GameProfile profile,
                                              ClientInformation clientInformation, boolean isShadow, CallbackInfo ci) {
        if (isShadow || profile == null) {
            return;
        }
        //#if MC >= 12110
        UUID uuid = profile.id();
        String name = profile.name();
        //#else
        //$$ UUID uuid = profile.getId();
        //$$ String name = profile.getName();
        //#endif
        if (uuid == null || uuid.version() == 4) {
            FakePlayerSkinManager.PendingSkin pending = name == null ? null : FakePlayerSkinManager.peek(name);
            if (pending != null) {
                pending.markSkipped();
            }
            return;
        }
        FakePlayerSkinManager.PendingSkin entry = FakePlayerSkinManager.peek(name);
        if (entry == null) {
            return;
        }
        Property skin = FakePlayerSkinManager.pickProperty(entry);
        if (skin == null) {
            // same_skin 预热未就绪：本次降级为出生后换肤（spawn TAIL 兜底循环接管）
            return;
        }
        try {
            //#if MC >= 12110
            profile.properties().removeAll("textures");
            profile.properties().put("textures", skin);
            //#else
            //$$ profile.getProperties().removeAll("textures");
            //$$ profile.getProperties().put("textures", skin);
            //#endif
        } catch (Exception e) {
            return;
        }
        entry.markInjected();
        FakePlayerSkinManager.markProtected(uuid);
    }
}
