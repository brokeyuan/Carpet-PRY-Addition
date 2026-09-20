package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.context.CommandContext;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

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

            var server = context.getSource().getServer();
            final String skinTargetNameFinal = skinTargetName;

            final Set<String> existingFakeNames = new HashSet<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p instanceof EntityPlayerMPFake) {
                    existingFakeNames.add(p.getGameProfile().name());
                }
            }

            new Thread(() -> {
                try {
                    String newFakeName = null;
                    for (int i = 0; i < 50; i++) {
                        Thread.sleep(100);
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            if (p instanceof EntityPlayerMPFake && !existingFakeNames.contains(p.getGameProfile().name())) {
                                if (p.connection != null) {
                                    newFakeName = p.getGameProfile().name();
                                    break;
                                }
                            }
                        }
                        if (newFakeName != null) break;
                    }

                    if (newFakeName == null) {
                        return;
                    }

                    ServerPlayer onlineFakePlayer = server.getPlayerList().getPlayer(newFakeName);
                    if (onlineFakePlayer == null) {
                        return;
                    }
                    applySkinToFakePlayerReflection(server, onlineFakePlayer, skinTargetNameFinal);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("[PRY] 皮肤轮询线程异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("[PRY] 皮肤 afterSpawn 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void applySkinToFakePlayerReflection(net.minecraft.server.MinecraftServer server,
                                                        ServerPlayer fakePlayer,
                                                        String skinPlayerName) {
        try {
            Class<?> skinProviderContextClass = Class.forName("net.lionarius.skinrestorer.skin.provider.SkinProviderContext");
            Class<?> skinVariantClass = Class.forName("net.lionarius.skinrestorer.skin.SkinVariant");
            Class<?> skinServiceClass = Class.forName("net.lionarius.skinrestorer.skin.SkinService");

            Object slimVariant = skinVariantClass.getField("SLIM").get(null);
            Constructor<?> contextConstructor = skinProviderContextClass.getConstructor(String.class, String.class, skinVariantClass);
            Object context = contextConstructor.newInstance("mojang", skinPlayerName, slimVariant);

            Method setSkinAsyncMethod = skinServiceClass.getMethod("setSkinAsync", net.minecraft.server.MinecraftServer.class, java.util.Collection.class, skinProviderContextClass, boolean.class);
            // save=false：仅对假人当前会话生效，不写入 SkinRestorer 持久存储——
            // 假人 UUID 与同名真人相同，落库会导致真人上线被换肤
            Object future = setSkinAsyncMethod.invoke(null, server, Collections.singletonList(wrapSkinTarget(fakePlayer)), context, false);

            Class<?> futureClass = future.getClass();
            Method whenCompleteMethod = futureClass.getMethod("whenComplete", java.util.function.BiConsumer.class);
            whenCompleteMethod.invoke(future, (java.util.function.BiConsumer<Object, Throwable>) (result, throwable) -> {
                if (throwable != null) {
                    return;
                }
                try {
                    Method isSuccessMethod = result.getClass().getMethod("isSuccess");
                    if ((Boolean) isSuccessMethod.invoke(result)) {
                        server.execute(() -> {
                            try {
                                Class<?> playerUtilsClass = Class.forName("net.lionarius.skinrestorer.util.PlayerUtils");
                                Method refreshPlayerMethod = playerUtilsClass.getMethod("refreshPlayer", ServerPlayer.class);
                                refreshPlayerMethod.invoke(null, fakePlayer);
                            } catch (ClassNotFoundException e) {
                                System.err.println("[PRY] PlayerUtils 类未找到: " + e.getMessage());
                            } catch (Exception e) {
                                System.err.println("[PRY] 刷新皮肤失败: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[PRY] 检查皮肤设置结果失败: " + e.getMessage());
                    e.printStackTrace();
                }
            });

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