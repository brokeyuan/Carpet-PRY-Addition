package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerCommandSkinMixin - 皮肤应用（≤1.21.11 全版本的唯一实现）。
 *
 * <p>旧版 SkinRestorer 的 setSkinAsync 集合元素为 GameProfile，与本实现的
 * 传参一致；name 访问器差异（1.21.10+ record {@code name()} / 早期
 * {@code getName()}）由内联预处理分叉处理，同一份源码服务全部 ≤1.21.11 版本。</p>
 *
 * <p><b>26.1.2+ 由 versions/26.1.2 的覆盖副本接管</b>（26.x 配套的新版
 * SkinRestorer 走 SkinTarget/refreshPlayer 路径）——两份实现是按 SkinRestorer
 * 版本划分的，改皮肤逻辑时务必同步评估另一份，勿只改其一。</p>
 *
 * <p>save=false：仅对假人当前会话生效，不写入 SkinRestorer 持久存储——
 * 假人 UUID 与同名真人相同，落库会导致真人上线被换肤。</p>
 */
@Mixin(PlayerCommand.class)
public class PlayerCommandSkinMixin {

    @Inject(
            method = "spawn",
            at = @At("TAIL"),
            remap = false
    )
    private static void afterSpawn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        String mode = CarpetPrimaryuanSettings.fakePlayerSkinMode;

        if ("default".equals(mode)) {
            return;
        }

        try {
            // 获取召唤者（执行命令的玩家）
            ServerPlayer summoner = null;
            try {
                summoner = context.getSource().getPlayerOrException();
            } catch (Exception ignored) {
                // 命令可能由非玩家执行（如控制台）
            }

            var server = context.getSource().getServer();
            // 按命令参数定位本次生成的假人；spawn 失败（名字被占用）时查到的不是假人，直接跳过
            String fakeName = StringArgumentType.getString(context, "player");
            ServerPlayer spawned = server.getPlayerList().getPlayerByName(fakeName);
            if (!(spawned instanceof EntityPlayerMPFake fakePlayer)) {
                return;
            }

            // 根据模式解析皮肤目标玩家名（summon/same_skin 仅来源不同，复用同一套逻辑）
            String skinTargetName = null;

            switch (mode) {
                case "summon" -> {
                    // 使用召唤者的皮肤。根模板被 1.21.11（rootNode）不经预处理地
                    // 直接编译，fork 的活动分支必须是 1.21.11 形态（name()）
                    if (summoner == null) return;
                    skinTargetName = summoner.getGameProfile()
                            //#if MC >= 12110
                            .name()
                            //#else
                            //$$ .getName()
                            //#endif
                            ;
                }
                case "same_skin" -> {
                    // 使用 fakePlayerSkinSet 配置的玩家皮肤
                    skinTargetName = CarpetPrimaryuanSettings.fakePlayerSkinSet;
                    if (skinTargetName == null || skinTargetName.isEmpty()) {
                        return;
                    }
                }
                default -> {
                    return;
                }
            }

            applySkinToFakePlayerReflection(server, fakePlayer.getGameProfile(), skinTargetName);

        } catch (Exception e) {
        }
    }

    private static void applySkinToFakePlayerReflection(net.minecraft.server.MinecraftServer server,
                                                        GameProfile targetProfile,
                                                        String skinPlayerName) {
        try {
            Class<?> skinProviderContextClass = Class.forName("net.lionarius.skinrestorer.skin.provider.SkinProviderContext");
            Class<?> skinVariantClass = Class.forName("net.lionarius.skinrestorer.skin.SkinVariant");
            Class<?> skinServiceClass = Class.forName("net.lionarius.skinrestorer.skin.SkinService");

            Object slimVariant = skinVariantClass.getField("SLIM").get(null);
            java.lang.reflect.Constructor<?> contextConstructor = skinProviderContextClass.getConstructor(String.class, String.class, skinVariantClass);
            Object context = contextConstructor.newInstance("mojang", skinPlayerName, slimVariant);

            java.lang.reflect.Method setSkinAsyncMethod = skinServiceClass.getMethod("setSkinAsync", net.minecraft.server.MinecraftServer.class, java.util.Collection.class, skinProviderContextClass, boolean.class);
            setSkinAsyncMethod.invoke(null, server, java.util.Collections.singletonList(targetProfile), context, false);
        } catch (ClassNotFoundException e) {
        } catch (Exception e) {
        }
    }

}
