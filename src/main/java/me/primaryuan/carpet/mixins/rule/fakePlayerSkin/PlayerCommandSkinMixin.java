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
            setSkinAsyncMethod.invoke(null, server, java.util.Collections.singletonList(targetPlayer), context, true);

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

}