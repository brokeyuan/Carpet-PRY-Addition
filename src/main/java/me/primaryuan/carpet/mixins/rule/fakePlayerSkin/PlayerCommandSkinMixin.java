package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            ServerPlayer summoner = null;
            try {
                summoner = context.getSource().getPlayerOrException();
            } catch (Exception ignored) {
            }

            var server = context.getSource().getServer();
            // 按命令参数定位本次生成的假人；spawn 失败（名字被占用）时查到的不是假人，直接跳过
            String fakeName = StringArgumentType.getString(context, "player");
            ServerPlayer spawned = server.getPlayerList().getPlayerByName(fakeName);
            if (!(spawned instanceof EntityPlayerMPFake fakePlayer)) {
                return;
            }

            String skinTargetName = null;

            switch (mode) {
                case "summon" -> {
                    if (summoner == null) return;
                    skinTargetName = summoner.getGameProfile().name();
                }
                case "same_skin" -> {
                    skinTargetName = CarpetPrimaryuanSettings.fakePlayerSkinSet;
                    if (skinTargetName == null || skinTargetName.isEmpty()) {
                        return;
                    }
                }
                default -> {
                    return;
                }
            }

            applySkinToFakePlayerReflection(server, fakePlayer, skinTargetName);

        } catch (Exception e) {
            System.err.println("[PRY] 皮肤 afterSpawn 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void applySkinToFakePlayerReflection(net.minecraft.server.MinecraftServer server,
                                                        ServerPlayer targetPlayer,
                                                        String skinPlayerName) {
        try {
            Class<?> skinProviderContextClass = Class.forName("net.lionarius.skinrestorer.skin.provider.SkinProviderContext");
            Class<?> skinVariantClass = Class.forName("net.lionarius.skinrestorer.skin.SkinVariant");
            Class<?> skinServiceClass = Class.forName("net.lionarius.skinrestorer.skin.SkinService");

            Object slimVariant = skinVariantClass.getField("SLIM").get(null);
            java.lang.reflect.Constructor<?> contextConstructor = skinProviderContextClass.getConstructor(String.class, String.class, skinVariantClass);
            Object context = contextConstructor.newInstance("mojang", skinPlayerName, slimVariant);

            java.lang.reflect.Method setSkinAsyncMethod = skinServiceClass.getMethod("setSkinAsync", net.minecraft.server.MinecraftServer.class, java.util.Collection.class, skinProviderContextClass, boolean.class);
            // save=false：仅对假人当前会话生效，不写入 SkinRestorer 持久存储——
            // 假人 UUID 与同名真人相同，落库会导致真人上线被换肤
            setSkinAsyncMethod.invoke(null, server, java.util.Collections.singletonList(wrapSkinTarget(targetPlayer)), context, false);

            Class<?> playerUtilsClass = Class.forName("net.lionarius.skinrestorer.util.PlayerUtils");
            java.lang.reflect.Method refreshPlayerMethod = playerUtilsClass.getMethod("refreshPlayer", ServerPlayer.class);
            refreshPlayerMethod.invoke(null, targetPlayer);

        } catch (ClassNotFoundException e) {
            System.err.println("[PRY] SkinRestorer 类未找到，皮肤功能不可用: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[PRY] 皮肤设置失败: " + e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * skinrestorer 新版（26.1-multiloader 重构起）把 setSkinAsync 的集合元素从
     * ServerPlayer 换成了 SkinTarget 记录——反射签名因 Collection 擦除不变，
     * 直接传实体会在其内部 ClassCastException（Failed to set skin 'mojang:xxx'）。
     * 存在 SkinTarget#of(ServerPlayer) 时包装；旧版无该类，保持直接传实体。
     */
    private static Object wrapSkinTarget(ServerPlayer player) {
        try {
            Class<?> skinTargetClass = Class.forName("net.lionarius.skinrestorer.skin.SkinTarget");
            return skinTargetClass.getMethod("of", ServerPlayer.class).invoke(null, player);
        } catch (ClassNotFoundException ignored) {
            return player;
        } catch (ReflectiveOperationException e) {
            System.err.println("[PRY] SkinTarget.of 包装失败，回退直接传实体: " + e);
            return player;
        }
    }

}