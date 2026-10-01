package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.google.common.collect.Iterables;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.FakePlayerSkinManager;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerCommandSkinMixin - 皮肤来源入队与兜底（全版本唯一实现）。
 *
 * <p>spawn HEAD（TIS Addition 的 rejoin @Shadow 复用本方法，同样生效）按假人名入队
 * 皮肤来源：summon=召唤者在线 profile 的纹理快照（内存直拷，零网络）；same_skin=
 * fakePlayerSkinSet（SkinRestorer provider 后台预热）。实际注入由
 * EntityPlayerMPFakeSkinMixin 在假人构造器 HEAD 完成，出生包直接带目标皮肤。</p>
 *
 * <p>真人身份（UUID v4）不入队：真人名假人保持该玩家真实长相。构造器注入未发生
 * （spawn 失败 / same_skin 预热未就绪 / 构造器注入点漂移）时 TAIL 安装下 tick 兜底：
 * 等假人上线后走 SkinRestorer setSkinAsync(save=false) 后置换肤，观感同旧行为。
 * save=false 不写入 SkinRestorer 持久存储——假人 UUID 与同名真人相同时落库会导致
 * 真人上线被换肤。</p>
 */
@Mixin(PlayerCommand.class)
public class PlayerCommandSkinMixin {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    @Inject(
            method = "spawn",
            at = @At("HEAD"),
            remap = false
    )
    private static void beforeSpawn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        String mode = CarpetPrimaryuanSettings.fakePlayerSkinMode;
        if ("default".equals(mode)) {
            return;
        }
        try {
            String fakeName = StringArgumentType.getString(context, "player");
            if ("summon".equals(mode)) {
                ServerPlayer summoner = null;
                try {
                    summoner = context.getSource().getPlayerOrException();
                } catch (Exception ignored) {
                    // 命令可能由控制台执行：无召唤者皮肤来源，假人保持本来长相
                }
                if (summoner == null) {
                    return;
                }
                //#if MC >= 12110
                Property skin = Iterables.getFirst(summoner.getGameProfile().properties().get("textures"), null);
                //#else
                //$$ Property skin = Iterables.getFirst(summoner.getGameProfile().getProperties().get("textures"), null);
                //#endif
                if (skin == null) {
                    return;
                }
                String summonerName;
                //#if MC >= 12110
                summonerName = summoner.getGameProfile().name();
                //#else
                //$$ summonerName = summoner.getGameProfile().getName();
                //#endif
                FakePlayerSkinManager.enqueue(fakeName, skin, null, summonerName);
            } else if ("same_skin".equals(mode)) {
                String skinName = CarpetPrimaryuanSettings.fakePlayerSkinSet;
                if (skinName == null || skinName.isEmpty()) {
                    return;
                }
                FakePlayerSkinManager.enqueue(fakeName, null, skinName, skinName);
            }
        } catch (Exception e) {
            LOGGER.error("[FakePlayerSkin] Failed to enqueue fake player skin source", e);
        }
    }

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
            String fakeName = StringArgumentType.getString(context, "player");
            FakePlayerSkinManager.PendingSkin entry = FakePlayerSkinManager.peek(fakeName);
            if (entry == null) {
                return;
            }
            // 构造器注入发生在 profile 异步拉取完成后（晚于本 TAIL）：兜底循环等构造器
            // 给出终态——注入成功或真人名则直接清理；无皮肤来源才后置换肤
            final String name = fakeName;
            final FakePlayerSkinManager.PendingSkin entryRef = entry;
            final int[] waited = {0};
            ServerTickScheduler.register(server -> {
                if (entryRef.injected || entryRef.skipped) {
                    // 构造器注入成功 / 真人名：无需兜底，清理退出
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                ServerPlayer spawned = server.getPlayerList().getPlayerByName(name);
                if (spawned instanceof EntityPlayerMPFake fake) {
                    if (fake.getUUID().version() != 4) {
                        FakePlayerSkinManager.applyPostSpawn(server, fake, entryRef.fallbackName);
                    }
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                // spawn 失败（名字被占用等）时假人不会上线，超时放弃
                if (++waited[0] >= 100) {
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                return true;
            });
        } catch (Exception e) {
            LOGGER.error("[FakePlayerSkin] Failed to schedule fake player skin fallback", e);
        }
    }
}
