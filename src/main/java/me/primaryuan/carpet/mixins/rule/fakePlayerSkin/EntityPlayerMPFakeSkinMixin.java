package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import me.primaryuan.carpet.util.FakePlayerSkinManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 假人构造器注入：Carpet 的 createFake 为两段式（异步拉取 profile → 回调里构造实体、
 * placeNewPlayer 发出生包），构造器收到的 profile 就是出生包所带 profile。在构造器
 * RETURN 处把带目标皮肤的新 profile 整体替换进实体的 gameProfile 字段——出生包
 * （placeNewPlayer 内的 PlayerInfo 包）读取的是实体字段，观战者首帧即目标皮肤，无闪皮窗口。
 *
 * <p>不能原地改 profile 的属性表：1.21.11+ 的 authlib GameProfile 属性表不可变
 * （removeAll/put 抛 UnsupportedOperationException，本地 E2E 实证——5f1448f 首版即
 * 因此静默失效）。故复制属性表 → 替换 textures → 构建新 GameProfile → 经
 * PlayerGameProfileAccessor（@Mutable，SkinRestorer 同款方案）替换 final 字段。
 * placeNewPlayer 的出生包读实体字段而非 CommonListenerCookie 里的 profile 局部变量
 * （SkinRestorer 在 placeNewPlayer HEAD 仅换字段即可改变首帧，同路径实证）。</p>
 *
 * <p>对所有假人统一注入（真人名与合成名一视同仁）：真人玩家的保护不依赖名字分流，
 * 而是双保险——皮肤从不写入 SkinRestorer 持久存储（save=false），且 SkinRestorerGuardMixin
 * 的压制仅对在线的 EntityPlayerMPFake 实例生效（真人 join 不是假人实例，不受影响）。
 * 可选 skinrestorer 未装时 PENDING 恒空，本注入自然旁路。</p>
 *
 * <p>TIS Addition 的 /player rejoin 内部 @Shadow 复用 Carpet 的 spawn 方法，
 * 同样经过 createFake → 本构造器，无需单独覆盖。respawnFake（假人重生）沿用
 * 旧实体 profile（已含注入皮肤），不经本路径也无闪皮。</p>
 *
 * <p>require=0：构造器签名漂移时静默跳过（兜底为出生后换肤），由 MixinSanityCheck 报错。
 * 本类所有放弃路径（无来源 / 快照未就绪 / 替换异常）均留日志，无静默分支。</p>
 */
@Mixin(EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeSkinMixin {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void pry$onFakeConstructed(MinecraftServer server, ServerLevel level, GameProfile profile,
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
        if (uuid == null || name == null) {
            return;
        }
        FakePlayerSkinManager.PendingSkin entry = FakePlayerSkinManager.peek(name);
        if (entry == null) {
            return;
        }
        Property skin = FakePlayerSkinManager.pickProperty(entry);
        if (skin == null) {
            // 快照缺失（离线服召唤者 profile 无纹理）或预热未就绪：转 spawn TAIL 兜底后置换肤
            LOGGER.info("[pry] fakePlayerSkin: 假人 {} 皮肤来源未就绪，转出生后换肤兜底", name);
            return;
        }
        try {
            GameProfile withSkin;
            //#if MC >= 12110
            // 新 authlib（1.21.10+）：PropertyMap 一律不可变（拷贝构造包 ImmutableMultimap），
            // 须以普通 Guava multimap 组装后重新包一层 PropertyMap
            com.google.common.collect.Multimap<String, Property> mutable =
                    com.google.common.collect.LinkedHashMultimap.create(profile.properties());
            mutable.removeAll("textures");
            mutable.put("textures", skin);
            withSkin = new GameProfile(uuid, name, new PropertyMap(mutable));
            //#else
            //$$ // 老 authlib（≤1.21.8）：仅两参构造且 getProperties() 可变（LinkedHashMultimap 支撑），新建后填充
            //$$ withSkin = new GameProfile(uuid, name);
            //$$ PropertyMap props = withSkin.getProperties();
            //$$ props.putAll(profile.getProperties());
            //$$ props.removeAll("textures");
            //$$ props.put("textures", skin);
            //#endif
            ((PlayerGameProfileAccessor) (Object) this).pry$setGameProfile(withSkin);
        } catch (Exception e) {
            LOGGER.warn("[pry] fakePlayerSkin: 假人 {} 出生前注入皮肤失败，转出生后换肤兜底: {}", name, e.toString());
            return;
        }
        entry.markInjected();
        FakePlayerSkinManager.markProtected(uuid);
        LOGGER.info("[pry] fakePlayerSkin: 假人 {} 出生前注入皮肤（首帧即目标皮肤）", name);
    }
}
